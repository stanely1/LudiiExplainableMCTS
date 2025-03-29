ThisBuild / scalaVersion := "2.13.12"

// WA for sbt run not working (classpath issue)
import scala.sys.process._

lazy val runLudii = taskKey[Unit]("Run Ludii GUI")
runLudii := {
  "sh ./run.sh".!
}
runLudii := (runLudii dependsOn Compile / compile).value
run := runLudii.value


lazy val root = (project in file("."))
  .settings(
    name := "LudiiExplainableMCTS",
    version := "0.1.0"
  )
