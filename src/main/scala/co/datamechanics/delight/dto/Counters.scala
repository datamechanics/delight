package co.datamechanics.delight.dto

import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

case class Counters(messageCounter: Int, payloadCounter: Int) {
  def toJson: JValue = {
    (("messageCounter" -> messageCounter)
      ~ ("payloadCounter" -> payloadCounter))
  }
}
