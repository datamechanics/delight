package io.montara.lucia.sparklistener.common.metrics

import org.json4s.JObject

trait Metrics {
  def toJson: JObject
}
