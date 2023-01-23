publishMavenStyle := true
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

import xerial.sbt.Sonatype._
homepage := Some(url("https://sparklistener.lucia.montara.io"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/montara-io/lucia-spark-listener"),
    "scm:git@github.com:montara-io/lucia-spark-listener.git"
  )
)
developers := List(
  Developer(
    id = "montara-io",
    name = "Montara io",
    email = "montara-dev@montara.co",
    url = url("https://github.com/montara-io")
  )
)
