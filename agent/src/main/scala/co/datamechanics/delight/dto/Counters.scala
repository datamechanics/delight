package co.datamechanics.delight.dto

import org.json4s.JsonAST.JInt
import org.json4s.{JField, JObject}

case class Counters(messageCounter: Int, payloadCounter: Int) {
  def toJson: JObject = JObject(
    JField("messageCounter", JInt(messageCounter)),
    JField("payloadCounter", JInt(payloadCounter))
  )
}
