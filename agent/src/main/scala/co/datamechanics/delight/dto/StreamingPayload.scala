package co.datamechanics.delight.dto

import co.datamechanics.delight.common.Utils.{compressString, currentTime}
import co.datamechanics.delight.common.dto.DmAppId
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.Merge._

import java.util.Base64


case class StreamingPayload(dmAppId: DmAppId,
                            data: String,
                            counters: Counters,
                            sentAt: Long) {
  def toJson: JValue = {
    merge(
      dmAppId.toJson,
      ("sentAt" -> sentAt)
      ~ ("counters" -> counters.toJson)
      ~ ("data" -> Base64.getEncoder.encodeToString(compressString(data)))
    )
  }
}

object StreamingPayload {
  def apply(dmAppId: DmAppId, data: Seq[String], counters: Counters): StreamingPayload = {
    StreamingPayload(dmAppId, data.mkString("", "\n", "\n"), counters, currentTime)
  }
}
