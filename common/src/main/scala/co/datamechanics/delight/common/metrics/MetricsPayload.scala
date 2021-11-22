package co.datamechanics.delight.common.metrics

import co.datamechanics.delight.common.Utils.{compressString, currentTime}
import co.datamechanics.delight.common.dto.DmAppId
import org.json4s.Merge.merge
import org.json4s.{JField, JLong, JObject, JString}

import java.util.Base64

case class MetricsPayload(
    dmAppId: DmAppId,
    data: String,
    sentAt: Long
) {
  def toJson: JObject =
    merge(
      dmAppId.toJson,
      JObject(
        JField("sentAt", JLong(sentAt)),
        JField(
          "data",
          JString(Base64.getEncoder.encodeToString(compressString(data)))
        )
      )
    )
}

object MetricsPayload {
  def apply(dmAppId: DmAppId, data: Seq[String]): MetricsPayload = {
    MetricsPayload(dmAppId, data.mkString("", "\n", "\n"), currentTime)
  }
}
