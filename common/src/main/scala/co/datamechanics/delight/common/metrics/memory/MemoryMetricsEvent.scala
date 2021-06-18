package co.datamechanics.delight.common.metrics.memory

import co.datamechanics.delight.common.metrics.MetricsEvent

object MemoryMetricsEvent {
  def apply(host: String, memoryMetrics: MemoryMetrics): MetricsEvent = {
    MetricsEvent(host, "memory", memoryMetrics)
  }
}
