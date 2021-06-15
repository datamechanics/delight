import sbt.Keys.libraryDependencies

lazy val commonSettings = Seq(
  version := (
    if (git.gitCurrentBranch.value == "main") {
      "latest"
    } else {
      git.gitCurrentBranch.value
    }) + "-SNAPSHOT",
)

lazy val root = project
  .in(file("."))
  .aggregate(
    common,
    agent
  )
  .settings(
    publish / skip := true
  )

lazy val common = (project in file("common"))
  .settings(
    crossScalaVersions := Seq("2.11.12", "2.12.12"),
    libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.3" % "provided",
    publish / skip := true
  )


lazy val agent = (project in file("agent"))
  .settings(
    name := "delight",
    organization := "co.datamechanics",
    crossScalaVersions := Seq("2.11.12", "2.12.12"),
    libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.3" % "provided",
    publishTo := sonatypePublishToBundle.value,
  )
  .settings(commonSettings: _*)
