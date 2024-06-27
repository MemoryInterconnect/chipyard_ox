organization := "edu.berkeley.cs"
version := "1.6"
scalaVersion := "2.12.10"
name := "omnixtendagentetri"
libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.4.3",
//      "edu.berkeley.cs" %% "chisel3" % "3.2.6", // this one generate plugin error above
      "edu.berkeley.cs" %% "chiseltest" % "0.3.3" % "test",
      "edu.berkeley.cs" %% "rocketchip" % "1.2.6",
	"edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT",
      "org.scalatest" %% "scalatest" % "3.0.+" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test"
      )
