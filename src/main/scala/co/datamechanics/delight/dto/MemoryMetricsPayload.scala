package co.datamechanics.delight.dto

import co.datamechanics.delight.{compressString, currentTime}
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.Merge.merge

import java.util.Base64

case class MemoryMetricsPayload(
    dmAppId: DmAppId,
    data: String,
    sentAt: Long
) {
  def toJson: JValue = {
    merge(
      dmAppId.toJson,
      ("sentAt" -> sentAt)
        ~ ("data" -> Base64.getEncoder.encodeToString(compressString(data)))
    )
  }
}

object MemoryMetricsPayload {
  def apply(dmAppId: DmAppId, data: Seq[String]): MemoryMetricsPayload = {
    MemoryMetricsPayload(dmAppId, data.mkString("", "\n", "\n"), currentTime)
  }
}
