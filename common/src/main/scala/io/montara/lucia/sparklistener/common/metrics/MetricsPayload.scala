package io.montara.lucia.sparklistener.common.metrics

import io.montara.lucia.sparklistener.common.Utils.{compressString, currentTime}
import io.montara.lucia.sparklistener.common.dto.DmAppId
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
          JString(data)
        )
      )
    )
}

object MetricsPayload {
  def apply(dmAppId: DmAppId, data: Seq[String]): MetricsPayload = {
    MetricsPayload(dmAppId, data.mkString("", "\n", "\n"), currentTime)
  }
}
