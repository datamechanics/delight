package io.montara.lucia.sparklistener.common.metrics.memory

import io.montara.lucia.sparklistener.common.metrics.MetricsEvent

object MemoryMetricsEvent {
  def apply(host: String, memoryMetrics: MemoryMetrics): MetricsEvent = {
    MetricsEvent(host, "memory", memoryMetrics)
  }
}
