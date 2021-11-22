package co.datamechanics.delight.common.metrics

import co.datamechanics.delight.common.Utils.currentTime
import org.json4s.jackson.JsonMethods.compact
import org.json4s.{JField, JLong, JObject, JString}

case class MetricsEvent(
    host: String,
    kind: String,
    metrics: Metrics,
    timestamp: Long
) {
  def toJson: JObject = JObject(
    JField("host", JString(host)),
    JField("kind", JString(kind)),
    JField("timestamp", JLong(timestamp)),
    JField("metrics", metrics.toJson)
  )

  override def toString: String = compact(this.toJson)
}

object MetricsEvent {
  def apply(host: String, kind: String, metrics: Metrics): MetricsEvent = {
    MetricsEvent(host, kind, metrics, currentTime)
  }
}
