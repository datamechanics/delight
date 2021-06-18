package co.datamechanics.delight.common.metrics.memory

import co.datamechanics.delight.common.metrics.Metrics
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

case class MemoryMetrics(
    jvmVmem: Long,
    jvmRSS: Long,
    pythonVmem: Long,
    pythonRSS: Long,
    otherVmem: Long,
    otherRSS: Long
) extends Metrics {
  def toJson: JValue = {
    (("jvmVmem" -> jvmVmem)
      ~ ("jvmRSS" -> jvmRSS)
      ~ ("pythonVmem" -> pythonVmem)
      ~ ("pythonRSS" -> pythonRSS)
      ~ ("otherVmem" -> otherVmem)
      ~ ("otherRSS" -> otherRSS))
  }
}
