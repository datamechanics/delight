package io.montara.lucia.sparklistener.common.metrics.memory

import io.montara.lucia.sparklistener.common.metrics.Metrics
import org.json4s.{JField, JLong, JObject}

case class MemoryMetrics(
    jvmVmem: Long,
    jvmRSS: Long,
    pythonVmem: Long,
    pythonRSS: Long,
    otherVmem: Long,
    otherRSS: Long
) extends Metrics {
  def toJson: JObject = JObject(
    JField("jvmVmem", JLong(jvmVmem)),
    JField("jvmRSS", JLong(jvmRSS)),
    JField("pythonVmem", JLong(pythonVmem)),
    JField("pythonRSS", JLong(pythonRSS)),
    JField("otherVmem", JLong(otherVmem)),
    JField("otherRSS", JLong(otherRSS))
  )
}
