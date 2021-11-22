package co.datamechanics.delight.common.dto

import org.json4s.{JField, JObject, JString}

case class DmAppId(dmAppId: String) {
  def toJson: JObject = JObject(JField("dmAppId", JString(dmAppId)))

  override def toString: String = dmAppId
}
