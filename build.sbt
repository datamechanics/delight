
scalaVersion := "2.11.12"

organization := "co.datamechanics"
name := "delight"
version := "2.3-latest-SNAPSHOT"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.3.0"

publishTo := sonatypePublishToBundle.value
