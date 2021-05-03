crossScalaVersions := Seq("2.11.12", "2.12.12")

organization := "co.datamechanics"
name := "delight"
version := "latest-SNAPSHOT"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.3"

publishTo := sonatypePublishToBundle.value
