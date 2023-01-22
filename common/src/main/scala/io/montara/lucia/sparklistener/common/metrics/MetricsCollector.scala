package io.montara.lucia.sparklistener.common.metrics

import io.montara.lucia.sparklistener.common.Configs
import io.montara.lucia.sparklistener.common.Network.sendRequest
import io.montara.lucia.sparklistener.common.Utils.startRepeatThread
import io.montara.lucia.sparklistener.common.dto.DmAppId
import io.montara.lucia.sparklistener.common.metrics.memory.{
  MemoryMetricsEvent,
  MemoryMetricsGetter
}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.spark.SparkConf
import org.apache.spark.internal.Logging

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.{immutable, mutable}
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class MetricsCollector(hostname: String, sparkConf: SparkConf) extends Logging {
  private val dmAppId = DmAppId(Configs.getDMAppId(sparkConf))
  private val collectorURL = Configs.collectorUrl(sparkConf).stripSuffix("/")

  private val started: AtomicBoolean = new AtomicBoolean(false)
  private val MetricsQueue = new mutable.Queue[MetricsEvent]()
  private val httpClientMetrics = new DefaultHttpClient()
  private val threads = new mutable.ListBuffer[Thread]()

  private def publishMetrics(payload: MetricsPayload): Unit = {
    val url = s"$collectorURL/metrics"

    try {
      sendRequest(
        httpClientMetrics,
        url,
        payload.toJson,
        "Metrics payload sent successfully"
      )
    } catch {
      case e: Exception =>
        logWarning(s"Failed to send metrics to $url: ${e.getMessage}")
        throw e
    }
  }

  private def sendMetrics(): Unit = {
    val memoryMetrics = MetricsQueue
      .synchronized(MetricsQueue.take(1000))
      .to[immutable.Seq]
      .map(_.toString)
    val payloadSize = memoryMetrics.length
    if (payloadSize > 0) {
      try {
        publishMetrics(
          MetricsPayload(
            dmAppId,
            memoryMetrics
          )
        )
        MetricsQueue.synchronized {
          for (_ <- 1 to payloadSize) MetricsQueue.dequeue()
        }
      } catch {
        case _: Exception =>
          Thread.sleep(5000)
      }
    }
  }

  def startIfNecessary(): Unit = {
    if (started.compareAndSet(false, true)) {
      threads += startRepeatThread(FiniteDuration(1, SECONDS)) {
        logDebug("Logged memory metrics Poller")
        val metrics = MemoryMetricsGetter.get().computeAllMetrics()
        val event = MemoryMetricsEvent(hostname, metrics)
        MetricsQueue.synchronized {
          MetricsQueue.enqueue(event)
        }
      }
      logInfo("Started MemoryMetrics Poller thread")
      threads += startRepeatThread(FiniteDuration(2, SECONDS)) {
        logDebug("Logged metrics Sender")
        sendMetrics()
      }
      logInfo("Started MemoryMetrics Sender thread")
    }
  }

  def stop(): Unit = {
    threads.foreach(_.stop())
  }
}

object MetricsCollector {

  private var sharedCollector: Option[MetricsCollector] = None

  def getOrCreate(hostname: String, sparkConf: SparkConf): MetricsCollector = {
    if (sharedCollector.isEmpty) {
      sharedCollector = Some(new MetricsCollector(hostname, sparkConf))
    }
    sharedCollector.get
  }
}
