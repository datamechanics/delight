package co.datamechanics.delight.common.metrics

import org.json4s.JObject

trait Metrics {
  def toJson: JObject
}
