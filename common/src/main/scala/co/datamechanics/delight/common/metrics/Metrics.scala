package co.datamechanics.delight.common.metrics

import org.json4s.JsonAST.JValue

trait Metrics {
  def toJson: JValue
}
