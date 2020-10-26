publishMavenStyle := true
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

import xerial.sbt.Sonatype._
homepage := Some(url("https://delight.datamechanics.co"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/datamechanics/delight"),
    "scm:git@github.com:datamechanics/delight.git"
  )
)
developers := List(
  Developer(id="ImpSy", name="Sebastien Maintrot", email="sebastien@datamechanics.co", url=url("https://github.com/ImpSy"))
)
