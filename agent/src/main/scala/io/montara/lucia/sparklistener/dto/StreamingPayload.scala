package io.montara.lucia.sparklistener.dto

import io.montara.lucia.sparklistener.common.Utils.{compressString, currentTime}
import io.montara.lucia.sparklistener.common.dto.DmAppId
import org.json4s.Merge._
import org.json4s.{JField, JLong, JObject, JString}

import java.util.Base64

case class StreamingPayload(
    dmAppId: DmAppId,
    data: String,
    counters: Counters,
    sentAt: Long
) {
  def toJson: JObject =
    merge(
      dmAppId.toJson,
      JObject(
        JField("sentAt", JLong(sentAt)),
        JField("counters", counters.toJson),
        JField(
          "data",
          JString(data)
        )
      )
    )
}

object StreamingPayload {
  def apply(
      dmAppId: DmAppId,
      data: Seq[String],
      counters: Counters
  ): StreamingPayload = {
    StreamingPayload(
      dmAppId,
      data.mkString("", "\n", "\n"),
      counters,
      currentTime
    )
  }
}
