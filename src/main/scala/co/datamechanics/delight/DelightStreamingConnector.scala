package co.datamechanics.delight

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import co.datamechanics.delight.dto.{Counters, DmAppId, StreamingPayload}
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.apache.spark.{JsonProtocolProxy, SparkConf}
import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.SparkListenerEvent
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods.{compact, parse, render}

import scala.collection.{immutable, mutable}
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * A class responsible for sending messages to the Data Mechanics collector API
  *
  * - Events are bufferized then send as gzip/base64 bulks as part of a Json Payload
  * - Exponential wait time before retry upon error
  */
class DelightStreamingConnector(sparkConf: SparkConf) extends Logging {

  private val dmAppId = DmAppId(Configs.generateDMAppId(sparkConf))
  private val delightURL = Configs.delightUrl(sparkConf).stripSuffix("/")
  private val collectorURL = Configs.collectorUrl(sparkConf).stripSuffix("/")
  private val bufferMaxSize = Configs.bufferMaxSize(sparkConf)
  private val payloadMaxSize = Configs.payloadMaxSize(sparkConf)
  private val accessTokenOption = Configs.accessTokenOption(sparkConf)
  private val pollingInterval = Configs.pollingInterval(sparkConf)
  private val heartbeatInterval = Configs.heartbeatInterval(sparkConf)
  private val maxPollingInterval = Configs.maxPollingInterval(sparkConf)
  private val maxWaitOnEnd = Configs.maxWaitOnEnd(sparkConf)
  private val waitForPendingPayloadsSleepInterval = Configs.waitForPendingPayloadsSleepInterval(sparkConf)
  private val shouldLogDuration = Configs.logDuration(sparkConf)

  implicit val formats = org.json4s.DefaultFormats

  if(accessTokenOption.isEmpty) {
    logWarning("Delight is not activated because an access token is missing. Go to https://www.datamechanics.co/delight to generate one.")
  }

  /**
    * 2 Http Clients are created to avoid threading conflicts
    * - httpClient is used in the main thread by publishPayload() and sendAck()
    * - httpClientHeartbeat is only used in a dedicated thread for sendHeartbeat()
    */
  private val httpClient: HttpClient = new DefaultHttpClient()
  private val httpClientHeartbeat: HttpClient = new DefaultHttpClient()

  private val eventsBuffer: mutable.Buffer[SparkListenerEvent] = mutable.Buffer()
  private var eventsCounter: Int = 0
  private var payloadCounter: Int = 0

  private val pendingEvents: mutable.Queue[SparkListenerEvent] = new mutable.Queue[SparkListenerEvent]()
  private var currentPollingInterval = pollingInterval

  /**
    * Has the polling thread started
    */
  private val started: AtomicBoolean = new AtomicBoolean(false)

  private val isRateLimited: AtomicBoolean = new AtomicBoolean(false)


  /**
    * Parse an optional return message from the API
    */
  private def parseApiReturnMessage(httpResponse: HttpResponse): Option[String] = {
    Try {
      val entity = httpResponse.getEntity
      val body = EntityUtils.toString(entity)
      (parse(body) \\ "message").extract[Option[String]]
    }.toOption.flatten
  }

  /**
    * Send a POST request to Data Mechanics collector API ("the server")
    *
    * - Handles access token
    * - Status Code:
    *   -> 200: Request is a success
    *   -> 429: RateLimit has been reached for this app
    *   -> Other: Throw IOException
    *
    */
  private def sendRequest(client: HttpClient, url: String, payload: JValue, successMsg: String): Unit = {
    if (isRateLimited.get) return

    val payloadAsString = compact(render(payload))
    val requestEntity = new StringEntity(payloadAsString)

    val postMethod = new HttpPost(url)
    postMethod.setHeader("X-API-key", accessTokenOption.get)
    postMethod.setEntity(requestEntity)

    val httpResponse: HttpResponse = client.execute(postMethod)

    val apiReturnMessage: Option[String] = parseApiReturnMessage(httpResponse)
    val statusCode = httpResponse.getStatusLine.getStatusCode

    val entity = httpResponse.getEntity
    EntityUtils.consume(entity)

    statusCode match {
      case 200 => logInfo(successMsg)
      case 429 =>
        isRateLimited.set(true)
        logError(s"RateLimit has been reached, collection will now stop")
      case _ =>
        var errorMessage = s"Status $statusCode: ${httpResponse.getStatusLine.getReasonPhrase}."
        apiReturnMessage.foreach(
          m => errorMessage += s" $m."
        )
        throw new IOException(errorMessage)
    }
  }

  /**
    * Send a "/heartbeat" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Uses a dedicated httpClient to avoid collision with the one used by the main thread
    * - Payload is a Json Object containing the dm_app_id
    */
  def sendHeartbeat(): Unit = time(shouldLogDuration, "sendHeartbeat") {
    val url = s"$collectorURL/heartbeat"
    try {
      sendRequest(httpClientHeartbeat, url, dmAppId.toJson, "Successfully sent heartbeat")
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send heartbeat to $url: ${e.getMessage}")
    }
  }

  /**
    * Send a "/ack" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Payload is a Json Object containing the dm_app_id
    */
  def sendAck(): Unit = {
    val url = s"$collectorURL/ack"

    try {
      sendRequest(httpClient, url, dmAppId.toJson, "Successfully sent ack")
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send ack to $url: ${e.getMessage}")
    }
  }


  /**
    * Send a "/bulk" request to Data Mechanics collector API ("the server")
    *
    * - Uses sendRequest and catch thrown exceptions
    * - Payload is a is a Json Object representing a StreamingPayload
    */
  private def publishPayload(payload: StreamingPayload): Unit =time(shouldLogDuration, "publishPayload") {
    val url = s"$collectorURL/bulk"

    try {
      sendRequest(httpClient, url, payload.toJson, "Successfully sent payload")
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send payload to $url: ${e.getMessage}")
        throw e
    }
  }

  /**
    * Add a Event to the event buffer
    *
    * - Events are "flushed" when (1) there are more than `bufferMaxSize` messages in the buffer or (2) when `flush`
    *   is true
    * - Flushed events are transferred to the pendingMessages queue, in order to be sent to the server
    * - This method starts the polling thread (see method `start`) if it has not started yet
    * - This method waits for all messages to be sent to the server if `blocking` set to true
    * - This method is thread-safe
    */
  def enqueueEvent(event: SparkListenerEvent, flush: Boolean = false, blocking: Boolean = false): Unit = time(
    shouldLogDuration, "enqueueEvent"
  ) {
    if(accessTokenOption.nonEmpty) {
      val bufferSize = eventsBuffer.synchronized {
        eventsBuffer += event
        eventsBuffer.length
      }
      startIfNecessary()
      if (flush || bufferSize >= bufferMaxSize) flushEvents(blocking)
    }
  }

  /**
    * Remove all events from the event buffer and aggregate them into a payload ready to be sent to the server
    *
    * - This method waits for all events to be sent to the server if `blocking` set to true
    */
  private def flushEvents(blocking: Boolean = false): Unit = time(shouldLogDuration, "flushEvents") {

    eventsBuffer.synchronized {
      pendingEvents.synchronized {
        if (eventsBuffer.nonEmpty) {
          val bufferContent = eventsBuffer.to[immutable.Seq]
          pendingEvents.enqueue(bufferContent: _*)
          logInfo(s"Flushing ${eventsBuffer.length} events, now ${pendingEvents.size} pending events")
          eventsBuffer.clear()
        } else {
          logWarning("Nothing to flush")
        }
      }
    }

    if(blocking) waitForPendingEvents()
  }

  /**
    * Wait for all pending events to be sent to the server
    *
    * - This method is called when an ApplicationEnd event is received. It forces Spark to wait for the last events
    *   to be sent to the server, before shutting down the context.
    * - Check every `pollingInterval` seconds that the queue of pending events is empty
    * - Do not wait more than `maxWaitOnEnd` seconds
    * - Send the ack event to notify the app has terminated
    */
  private def waitForPendingEvents(): Unit = time(shouldLogDuration, "waitForPendingEvents") {
    val startWaitingTime = currentTime
    var nbPendingEvents: Int = pendingEvents.synchronized(pendingEvents.size)
    while((currentTime - startWaitingTime) < maxWaitOnEnd.toMillis && nbPendingEvents > 0) {
      Thread.sleep(waitForPendingPayloadsSleepInterval.toMillis)
      logInfo(s"Waiting for all pending events to be sent ($nbPendingEvents remaining)")
      nbPendingEvents = pendingEvents.synchronized(pendingEvents.size)
    }
    if(nbPendingEvents > 0) {
      logWarning(s"Stopped waiting for pending events to be sent (max wait duration is ${maxWaitOnEnd}), although $nbPendingEvents were remaining")
    }
    sendAck()
    logInfo(s"Application will be available in a few minutes on Delight at this url: $delightURL/apps/$dmAppId")
  }

  /**
    * Send all pending events to the server.
    *
    * - Stop sending pending events when a call has failed. In this case, time before next call to
    *   `publishPendingEvents` is doubled (exponential retry)
    * - Time before next call to `publishPendingEvents` is reset to its initial value if a call has worked
    */
  private def publishPendingEvents(): Unit = time(shouldLogDuration, "publishPendingEvents") {
    var errorHappened = false
    var nbPendingEvents: Int = pendingEvents.synchronized(pendingEvents.size)
    while(nbPendingEvents > 0 && !errorHappened) {
      try {
        val firstEvents = pendingEvents.synchronized(pendingEvents.take(payloadMaxSize)).to[immutable.Seq]
        eventsCounter += firstEvents.length
        payloadCounter += 1
        publishPayload(
          StreamingPayload(
            dmAppId,
            firstEvents.map(e => compact(render(JsonProtocolProxy.jsonProtocol.sparkEventToJson(e)))),
            Counters(eventsCounter, payloadCounter)
          )
        )
        pendingEvents.synchronized( // if everything went well, actually remove the payload from the queue
          for(_ <- 1 to firstEvents.length) pendingEvents.dequeue()
        )
        if(currentPollingInterval > pollingInterval) {
          // if everything went well, polling interval is set back to its initial value
          currentPollingInterval = pollingInterval
          logInfo(s"Polling interval back to ${pollingInterval.toSeconds}s because last payload was successfully sent")
        }
      } catch {
        case _: Exception =>
          errorHappened = true // stop sending payload queue when an error happened until next retry
          currentPollingInterval = (2 * currentPollingInterval).min(maxPollingInterval)  // exponential retry
          logWarning(s"Polling interval increased to ${currentPollingInterval.toSeconds}s because last payload failed")
      }
      nbPendingEvents = pendingEvents.synchronized(pendingEvents.size)
    }
  }

  private def startRepeatThread(interval: FiniteDuration)(action: => Unit): Thread = {
    val thread = new Thread {
      override def run() {
        while (true) {
          val start = currentTime
          val _ = action
          val end = currentTime
          Thread.sleep(math.max(interval.toMillis - (end - start), 0))
        }
      }
    }
    thread.start()
    thread
  }

  /**
    * Start the polling thread that sends all pending payloads to the server every `currentPollingInterval` seconds.
    * Start the heartbeat thread that `sendHeartbeat()` every `heartbeatInterval` seconds.
    *
    * - The threads are started only is they have not started yet.
    */
  private def startIfNecessary(): Unit = time(shouldLogDuration, "startIfNecessary") {
    if(started.compareAndSet(false, true)) {
      startRepeatThread(currentPollingInterval) {
        publishPendingEvents()
      }
      logInfo("Started DelightStreamingConnector polling thread")
      startRepeatThread(heartbeatInterval) {
        logDebug("Logged heartbeat")
        sendHeartbeat()
      }
      logInfo("Started DelightStreamingConnector heartbeat thread")
      logInfo(s"Application will be available on Delight a few minutes after it completes at this url: $delightURL/apps/$dmAppId")
    }
  }

}

object DelightStreamingConnector {

  private var sharedConnector: Option[DelightStreamingConnector] = None

  /**
    * A connector common to the whole Scala application.
    *
    * - Will become useful if we have more than a SparkListener sending messages!
    */
  def getOrCreate(sparkConf: SparkConf): DelightStreamingConnector = {
    if(sharedConnector.isEmpty) {
      sharedConnector = Option(new DelightStreamingConnector(sparkConf))
    }
    sharedConnector.get
  }
}
