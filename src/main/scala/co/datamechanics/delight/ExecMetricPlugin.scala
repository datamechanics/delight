package co.datamechanics.delight

import co.datamechanics.delight.Core.startRepeatThread
import co.datamechanics.delight.dto.{DmAppId, MemoryMetrics, MemoryMetricsPayload}
import co.datamechanics.delight.metrics.ProcfsMetricsGetter

import java.util.{Map => JMap}
import org.apache.spark.api.plugin.{ExecutorPlugin, PluginContext}
import org.apache.spark.internal.Logging

import scala.collection.{immutable, mutable}
import scala.concurrent.duration.{FiniteDuration, SECONDS}

class ExecMetricPlugin extends ExecutorPlugin with Logging {

  private var dmAppId: Option[DmAppId] = None
  private val memoryMetricsQueue: mutable.Queue[MemoryMetrics] = new mutable.Queue[MemoryMetrics]()

  override def init(context: PluginContext, extraConf: JMap[String, String]): Unit = {
    dmAppId = Option(DmAppId(Configs.generateDMAppId(context.conf())))

    startRepeatThread(FiniteDuration(1, SECONDS)) {
      val metrics = ProcfsMetricsGetter.get().computeAllMetrics()
      memoryMetricsQueue.synchronized {
        memoryMetricsQueue.enqueue(MemoryMetrics(metrics))
      }
    }
    startRepeatThread(FiniteDuration(10, SECONDS)) {
      sendMetrics()
    }
  }

  override def shutdown(): Unit = {}

  def sendMetrics(): Unit = {
    try {
      val memoryMetrics = memoryMetricsQueue
        .synchronized(memoryMetricsQueue.take(1000))
        .to[immutable.Seq]
        .map(_.toString)
      val payloadSize = memoryMetrics.length
      val payload = MemoryMetricsPayload(dmAppId.get, memoryMetrics)
      logInfo(payload.toString)
      memoryMetricsQueue.synchronized {
        for (_ <- 1 to payloadSize) memoryMetricsQueue.dequeue()
      }
    } catch {
      case e: Exception =>
        logWarning("An error occurred while trying to send metrics", e)
    }
  }
}
