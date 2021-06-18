package co.datamechanics.delight.common.metrics

import co.datamechanics.delight.common.Utils.{compressString, currentTime}
import co.datamechanics.delight.common.dto.DmAppId
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.Merge.merge

import java.util.Base64

case class MetricsPayload(
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

object MetricsPayload {
  def apply(dmAppId: DmAppId, data: Seq[String]): MetricsPayload = {
    MetricsPayload(dmAppId, data.mkString("", "\n", "\n"), currentTime)
  }
}
