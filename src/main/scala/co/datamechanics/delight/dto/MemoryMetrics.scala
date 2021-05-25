package co.datamechanics.delight.dto

import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.compact

import co.datamechanics.delight._

case class MemoryMetrics(timestamp: Long, procfsMetrics: ProcfsMetrics) {
  def toJson: JValue = {
    (("timestamp" -> timestamp)
      ~ ("metrics" -> procfsMetrics.toJson))
  }

  override def toString: String = compact(this.toJson)
}

object MemoryMetrics {
  def apply(procfsMetrics: ProcfsMetrics): MemoryMetrics = {
    MemoryMetrics(currentTime, procfsMetrics)
  }
}
