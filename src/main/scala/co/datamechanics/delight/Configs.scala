package co.datamechanics.delight

import org.apache.spark.SparkConf

import scala.concurrent.duration._

object Configs {

  def delightUrl(sparkConf: SparkConf): String = {
    sparkConf.get("spark.delight.url", "https://delight.datamechanics.co/")
  }

  def collectorUrl(sparkConf: SparkConf): String = {
    sparkConf.get("spark.delight.collector.url", "https://api.delight.datamechanics.co/collector/")
  }

  def bufferMaxSize(sparkConf: SparkConf): Int = {
    sparkConf.getInt("spark.delight.buffer.maxNumEvents", 1000)
  }

  def payloadMaxSize(sparkConf: SparkConf): Int = {
    sparkConf.getInt("spark.delight.payload.maxNumEvents", 10000)
  }

  def accessTokenOption(sparkConf: SparkConf): Option[String] = {
    sparkConf.getOption("spark.delight.accessToken.secret") // secret is added here so that Spark redacts this config
  }

  def heartbeatInterval(sparkConf: SparkConf): FiniteDuration = {
    sparkConf.getDouble("spark.delight.heartbeatIntervalSecs", 10).seconds
  }

  def pollingInterval(sparkConf: SparkConf): FiniteDuration = {
    sparkConf.getDouble("spark.delight.pollingIntervalSecs", 0.5).seconds
  }

  def maxPollingInterval(sparkConf: SparkConf): FiniteDuration = {
    sparkConf.getDouble("spark.delight.maxPollingIntervalSecs", 60).seconds
  }

  def maxWaitOnEnd(sparkConf: SparkConf): FiniteDuration = {
    sparkConf.getDouble("spark.delight.maxWaitOnEndSecs", 10).seconds
  }

  def waitForPendingPayloadsSleepInterval(sparkConf: SparkConf): FiniteDuration = {
    sparkConf.getDouble("spark.delight.waitForPendingPayloadsSleepIntervalSecs", 1).seconds
  }

  def logDuration(sparkConf: SparkConf): Boolean = {
    sparkConf.getBoolean("spark.delight.logDuration", false)
  }

  def generateDMAppId(sparkConf: SparkConf): String = {
    val appName: String = sparkConf.get("spark.delight.appNameOverride", sparkConf.get("spark.app.name", "undefined"))
    val sanitizedAppName: String = appName.replaceAll("\\W", "-").replaceAll("--*", "-").stripSuffix("-")
    val uuid: String = java.util.UUID.randomUUID().toString
    s"$sanitizedAppName-$uuid"
  }

  val delightVersion: String = "1.0.3"

}
