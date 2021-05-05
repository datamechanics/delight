package co.datamechanics.delight.dto

import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

case class ProcfsMetrics(
    jvmVmem: Long,
    jvmRSS: Long,
    pythonVmem: Long,
    pythonRSS: Long,
    otherVmem: Long,
    otherRSS: Long
) {
  def toJson: JValue = {
    (("jvmVmem" -> jvmVmem)
      ~ ("jvmRSS" -> jvmRSS)
      ~ ("pythonVmem" -> pythonVmem)
      ~ ("pythonRSS" -> pythonRSS)
      ~ ("otherVmem" -> otherVmem)
      ~ ("otherRSS" -> otherRSS))
  }
}
