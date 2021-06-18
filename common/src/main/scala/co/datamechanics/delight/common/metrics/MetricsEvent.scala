package co.datamechanics.delight.common.metrics

import co.datamechanics.delight.common.Utils.currentTime
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.compact

case class MetricsEvent(
    host: String,
    kind: String,
    metrics: Metrics,
    timestamp: Long
) {
  def toJson: JValue = {
    (("host" -> host)
      ~ ("kind" -> kind)
      ~ ("timestamp" -> timestamp)
      ~ ("metrics" -> metrics.toJson))
  }

  override def toString: String = compact(this.toJson)
}

object MetricsEvent {
  def apply(host: String, kind: String, metrics: Metrics): MetricsEvent = {
    MetricsEvent(host, kind, metrics, currentTime)
  }
}
