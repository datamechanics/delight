crossScalaVersions := Seq("2.12.12")
//crossScalaVersions := Seq("2.11.12", "2.12.12")

organization := "co.datamechanics"
name := "delight"
version := "test-poc-executor-SNAPSHOT"

libraryDependencies += "org.apache.spark" %% "spark-core" % "3.0.0"

publishTo := sonatypePublishToBundle.value
