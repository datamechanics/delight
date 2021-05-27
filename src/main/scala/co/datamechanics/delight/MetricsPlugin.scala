package co.datamechanics.delight

import org.apache.spark.api.plugin.{DriverPlugin, ExecutorPlugin, PluginContext, SparkPlugin}

class MetricsPlugin extends SparkPlugin {
  override def driverPlugin(): DriverPlugin = new DriverPlugin() {}

  override def executorPlugin(): ExecutorPlugin = new ExecMetricPlugin()
}

