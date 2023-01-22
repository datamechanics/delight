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
    sentAt: Long,
    pipelineId: String,
    jobId: String
) {
  def toJson: JObject =
    merge(
      dmAppId.toJson,
      JObject(
        JField("pipelineId", JString(pipelineId)),
        JField("jobId", JString(jobId)),
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
      counters: Counters,
      pipelineId: String,
      jobId: String
  ): StreamingPayload = {
    StreamingPayload(
      dmAppId,
      data.mkString("", "\n", "\n"),
      counters,
      pipelineId,
      jobId,
      currentTime
    )
  }
}
