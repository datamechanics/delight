package co.datamechanics.delight

import co.datamechanics.delight.common.Configs
import co.datamechanics.delight.common.Network.sendRequest
import co.datamechanics.delight.common.Utils.{
  currentTime,
  startRepeatThread,
  time
}
import co.datamechanics.delight.common.dto.DmAppId
import co.datamechanics.delight.dto.{Counters, StreamingPayload}
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.SparkListenerEvent
import org.apache.spark.{JsonProtocolProxy, SparkConf}
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.{compact, render}

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.{immutable, mutable}
import scala.util.Try

/** A class responsible for sending messages to the Data Mechanics collector API
  *
  * - Events are bufferized then send as gzip/base64 bulks as part of a Json Payload
  * - Exponential wait time before retry upon error
  */
class DelightStreamingConnector(sparkConf: SparkConf) extends Logging {

  private val dmAppId = DmAppId(Configs.getDMAppId(sparkConf))
  private val delightURL = Configs.delightUrl(sparkConf).stripSuffix("/")
  private val collectorURL = Configs.collectorUrl(sparkConf).stripSuffix("/")
  private val bufferMaxSize = Configs.bufferMaxSize(sparkConf)
  private val payloadMaxSize = Configs.payloadMaxSize(sparkConf)
  private val accessTokenOption = Configs.accessTokenOption(sparkConf)
  private val pollingInterval = Configs.pollingInterval(sparkConf)
  private val heartbeatInterval = Configs.heartbeatInterval(sparkConf)
  private val maxPollingInterval = Configs.maxPollingInterval(sparkConf)
  private val maxWaitOnEnd = Configs.maxWaitOnEnd(sparkConf)
  private val waitForPendingPayloadsSleepInterval =
    Configs.waitForPendingPayloadsSleepInterval(sparkConf)
  private val shouldLogDuration = Configs.logDuration(sparkConf)

  implicit val formats = org.json4s.DefaultFormats

  if (accessTokenOption.isEmpty) {
    logWarning(
      "Delight is not activated because an access token is missing. Go to https://www.datamechanics.co/delight to generate one."
    )
  }

  private val oldSparkEventToJsonMethod = Try(
    JsonProtocolProxy.jsonProtocol.getClass
      .getDeclaredMethod("sparkEventToJson", classOf[SparkListenerEvent])
  ).toOption

  private val newSparkEventToJsonStringMethod = Try(
    JsonProtocolProxy.jsonProtocol.getClass
      .getDeclaredMethod(
        "sparkEventToJsonString",
        classOf[SparkListenerEvent]
      )
  ).toOption

  private val sparkEventSerializerMethodIsDefined =
    oldSparkEventToJsonMethod.isDefined || newSparkEventToJsonStringMethod.isDefined

  if (!sparkEventSerializerMethodIsDefined) {
    logWarning(
      "Delight is not activated because: SparkEvent serializer method was not found"
    )
  }

  /** 2 Http Clients are created to avoid threading conflicts
    * - httpClient is used in the main thread by publishPayload() and sendAck()
    * - httpClientHeartbeat is only used in a dedicated thread for sendHeartbeat()
    */
  private val httpClient: HttpClient = new DefaultHttpClient()
  private val httpClientHeartbeat: HttpClient = new DefaultHttpClient()

  private val eventsBuffer: mutable.Buffer[SparkListenerEvent] =
    mutable.Buffer()
  private var eventsCounter: Int = 0
  private var payloadCounter: Int = 0

  private val pendingEvents: mutable.Queue[SparkListenerEvent] =
    new mutable.Queue[SparkListenerEvent]()
  private var currentPollingInterval = pollingInterval

  /** Has the polling thread started
    */
  private val started: AtomicBoolean = new AtomicBoolean(false)

  /** Send a "/heartbeat" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Uses a dedicated httpClient to avoid collision with the one used by the main thread
    * - Payload is a Json Object containing the dm_app_id
    */
  def sendHeartbeat(): Unit = time(shouldLogDuration, "sendHeartbeat") {
    val url = s"$collectorURL/heartbeat"
    try {
      sendRequest(
        httpClientHeartbeat,
        url,
        accessTokenOption.get,
        dmAppId.toJson,
        "Successfully sent heartbeat"
      )
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send heartbeat to $url: ${e.getMessage}")
    }
  }

  /** Send a "/ack" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Payload is a Json Object containing the dm_app_id
    */
  def sendAck(): Unit = {
    val url = s"$collectorURL/ack"

    try {
      sendRequest(
        httpClient,
        url,
        accessTokenOption.get,
        dmAppId.toJson,
        "Successfully sent ack"
      )
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send ack to $url: ${e.getMessage}")
    }
  }

  /** Send a "/bulk" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Payload is a is a Json Object representing a StreamingPayload
    */
  private def publishPayload(payload: StreamingPayload): Unit =
    time(shouldLogDuration, "publishPayload") {
      val url = s"$collectorURL/bulk"

      try {
        sendRequest(
          httpClient,
          url,
          accessTokenOption.get,
          payload.toJson,
          "Successfully sent payload"
        )
      } catch {
        case e: Exception =>
          logWarning(s"Failed to send payload to $url: ${e.getMessage}")
          throw e
      }
    }

  /** Add a Event to the event buffer
    *
    * - Events are "flushed" when (1) there are more than `bufferMaxSize` messages in the buffer or (2) when `flush`
    *   is true
    * - Flushed events are transferred to the pendingMessages queue, in order to be sent to the server
    * - This method starts the polling thread (see method `start`) if it has not started yet
    * - This method waits for all messages to be sent to the server if `blocking` set to true
    * - This method is thread-safe
    */
  def enqueueEvent(
      event: SparkListenerEvent,
      flush: Boolean = false,
      blocking: Boolean = false
  ): Unit = time(
    shouldLogDuration,
    "enqueueEvent"
  ) {
    if (accessTokenOption.nonEmpty && sparkEventSerializerMethodIsDefined) {
      val bufferSize = eventsBuffer.synchronized {
        eventsBuffer += event
        eventsBuffer.length
      }
      startIfNecessary()
      if (flush || bufferSize >= bufferMaxSize) flushEvents(blocking)
    }
  }

  /** Remove all events from the event buffer and aggregate them into a payload ready to be sent to the server
    *
    * - This method waits for all events to be sent to the server if `blocking` set to true
    */
  private def flushEvents(blocking: Boolean = false): Unit =
    time(shouldLogDuration, "flushEvents") {

      eventsBuffer.synchronized {
        pendingEvents.synchronized {
          if (eventsBuffer.nonEmpty) {
            val bufferContent = eventsBuffer.to[immutable.Seq]
            pendingEvents.enqueue(bufferContent: _*)
            logInfo(
              s"Flushing ${eventsBuffer.length} events, now ${pendingEvents.size} pending events"
            )
            eventsBuffer.clear()
          } else {
            logWarning("Nothing to flush")
          }
        }
      }

      if (blocking) waitForPendingEvents()
    }

  /** Wait for all pending events to be sent to the server
    *
    * - This method is called when an ApplicationEnd event is received. It forces Spark to wait for the last events
    *   to be sent to the server, before shutting down the context.
    * - Check every `pollingInterval` seconds that the queue of pending events is empty
    * - Do not wait more than `maxWaitOnEnd` seconds
    * - Send the ack event to notify the app has terminated
    */
  private def waitForPendingEvents(): Unit =
    time(shouldLogDuration, "waitForPendingEvents") {
      val startWaitingTime = currentTime
      var nbPendingEvents: Int = pendingEvents.synchronized(pendingEvents.size)
      while (
        (currentTime - startWaitingTime) < maxWaitOnEnd.toMillis && nbPendingEvents > 0
      ) {
        Thread.sleep(waitForPendingPayloadsSleepInterval.toMillis)
        logInfo(
          s"Waiting for all pending events to be sent ($nbPendingEvents remaining)"
        )
        nbPendingEvents = pendingEvents.synchronized(pendingEvents.size)
      }
      if (nbPendingEvents > 0) {
        logWarning(
          s"Stopped waiting for pending events to be sent (max wait duration is ${maxWaitOnEnd}), although $nbPendingEvents were remaining"
        )
      }
      sendAck()
      logInfo(
        s"Application will be available in a few minutes on Delight at this url: $delightURL/apps/$dmAppId"
      )
    }

  /** Serialize a SparkListenerEvent and drop it if it can't be serialized
    *
    * Unfortunately this can happen on Databricks with the following stacktrace:
    *
    * com.fasterxml.jackson.databind.JsonMappingException: Exceeded 2097152 bytes (current = 2100278)
    * (through reference chain: org.apache.spark.sql.catalyst.expressions.AttributeReference["canonicalized"]->org.apache.spark.s...
    * ...
    * Caused by: com.databricks.spark.util.LimitedOutputStream$LimitExceededException: Exceeded 2097152 bytes (current = 2100278)
    */
  private def serializeEvent(event: SparkListenerEvent): Option[String] = {
    try {
      if (oldSparkEventToJsonMethod.isDefined) {
        Some(
          compact(
            render(
              oldSparkEventToJsonMethod.get
                .invoke(JsonProtocolProxy.jsonProtocol, event)
                .asInstanceOf[JValue]
            )
          )
        )
      } else {
        Some(
          newSparkEventToJsonStringMethod.get
            .invoke(JsonProtocolProxy.jsonProtocol, event)
            .asInstanceOf[String]
        )
      }
    } catch {
      case e: Exception =>
        logWarning(
          s"Could not serialize event of type ${event.getClass.getCanonicalName} because: ${e.getClass.getCanonicalName}"
        )
        None
    }
  }

  /** Send all pending events to the server.
    *
    * - Stop sending pending events when a call has failed. In this case, time before next call to
    *   `publishPendingEvents` is doubled (exponential retry)
    * - Time before next call to `publishPendingEvents` is reset to its initial value if a call has worked
    */
  private def publishPendingEvents(): Unit =
    time(shouldLogDuration, "publishPendingEvents") {
      var errorHappened = false
      var nbPendingEvents: Int = pendingEvents.synchronized(pendingEvents.size)
      while (nbPendingEvents > 0 && !errorHappened) {
        try {
          val firstEvents = pendingEvents
            .synchronized(pendingEvents.take(payloadMaxSize))
            .to[immutable.Seq]
          val serializedEvents = firstEvents.flatMap(serializeEvent)
          publishPayload(
            StreamingPayload(
              dmAppId,
              serializedEvents,
              Counters(
                eventsCounter + serializedEvents.length,
                payloadCounter + 1
              )
            )
          )
          pendingEvents
            .synchronized { // if everything went well, actually remove the payload from the queue
              for (_ <- 1 to firstEvents.length) pendingEvents.dequeue()
              eventsCounter += serializedEvents.length
              payloadCounter += 1

            }
          if (currentPollingInterval > pollingInterval) {
            // if everything went well, polling interval is set back to its initial value
            currentPollingInterval = pollingInterval
            logInfo(
              s"Polling interval back to ${pollingInterval.toSeconds}s because last payload was successfully sent"
            )
          }
        } catch {
          case e: Exception =>
            errorHappened =
              true // stop sending payload queue when an error happened until next retry
            currentPollingInterval = (2 * currentPollingInterval).min(
              maxPollingInterval
            ) // exponential retry
            logWarning(
              s"Polling interval increased to ${currentPollingInterval.toSeconds}s because last payload failed",
              e
            )
        }
        nbPendingEvents = pendingEvents.synchronized(pendingEvents.size)
      }
    }

  /** Start the polling thread that sends all pending payloads to the server every `currentPollingInterval` seconds.
    * Start the heartbeat thread that `sendHeartbeat()` every `heartbeatInterval` seconds.
    *
    * - The threads are started only is they have not started yet.
    */
  private def startIfNecessary(): Unit =
    time(shouldLogDuration, "startIfNecessary") {
      if (started.compareAndSet(false, true)) {
        startRepeatThread(currentPollingInterval) {
          publishPendingEvents()
        }
        logInfo("Started DelightStreamingConnector polling thread")
        startRepeatThread(heartbeatInterval) {
          logDebug("Logged heartbeat")
          sendHeartbeat()
        }
        logInfo("Started DelightStreamingConnector heartbeat thread")
        logInfo(
          s"Application will be available on Delight a few minutes after it completes at this url: $delightURL/apps/$dmAppId"
        )
      }
    }

}

object DelightStreamingConnector {

  private var sharedConnector: Option[DelightStreamingConnector] = None

  /** A connector common to the whole Scala application.
    *
    * - Will become useful if we have more than a SparkListener sending messages!
    */
  def getOrCreate(sparkConf: SparkConf): DelightStreamingConnector = {
    if (sharedConnector.isEmpty) {
      sharedConnector = Some(new DelightStreamingConnector(sparkConf))
    }
    sharedConnector.get
  }
}
