package co.datamechanics.delight.dto

import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._


case class DmAppId(dmAppId: String) {
  def toJson: JValue = {
    "dmAppId" -> dmAppId
  }

  override def toString: String = dmAppId
}
