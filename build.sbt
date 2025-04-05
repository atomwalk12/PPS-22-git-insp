import scala.sys.process.* // Used to change git hooks path

val scala3Version = "3.6.4"

// ------ Git hooks ------
// Change git hooks path to a tracked folder
val hooks = taskKey[Unit](
  "change git hooks path to a tracked folder (run 'sbt hooks' to configure)",
)

hooks := {
  val log = sLog.value

  try {
    val currentHooksPath = "git config --get core.hooksPath".!!.trim
    if (currentHooksPath != "git-hooks") {
      val command = "git config core.hooksPath git-hooks"
      val exitCode = Process(command).run().exitValue()
      if (exitCode == 0) {
        log.info("Successfully set git hooks path.")
      } else {
        log.error(s"Failed to set git hooks path. Command exited with code: $exitCode")
      }
    } else {
      log.info("Git hooks path is already set.")
    }
  } catch {
    case e: Exception =>
      log.error(s"An error occurred while setting git hooks path: ${e.getMessage}")
      e.printStackTrace()
  }
}

// ------ Scalafmt ------
scalafmtOnCompile := true

// ------ Scoverage ------
coverageEnabled := true

// ------ Wartremover ------
// finds potential bugs,(i.e. case classes must be final)
wartremoverWarnings ++= Warts.all
wartremoverWarnings --= Seq(
  Wart.Overloading,
  Wart.Nothing,
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.Equals
)

wartremoverErrors ++= Warts.unsafe
wartremoverErrors --= Seq(
  Wart.Any
)

// ------ Trunk ------
// Code style violations (i.e. trailing spaces)

// ------ Scalafix ------
// i.e. unused imports
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// ------ Tests ------
Test / fork := true // provides isolation between test runs and the build tool
Test / javaOptions ++= Seq(
  "-Xmx4G", // increase memory for tests
)

// ------ Assembly ------
enablePlugins(AssemblyPlugin)
Compile / mainClass := Some("gitinsp.Main")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard // Metadata is discarded to avoid conflicts
  case _                        => MergeStrategy.first
}
assembly / assemblyJarName := "gitinsp.jar"

// ------ Dependencies ------
lazy val root = project
  .in(file("."))
  .settings(
    name         := "pps-22-git-insp",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq(
      // Enable Scala 3 indentation syntax
      "-new-syntax",
      "-indent",
      "-source:3.3",
      "-source 3.0-migration",
      // Options for Scalafix
      "-Wunused:imports",
    ),

    libraryDependencies ++= Seq(
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,

      // Logging
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5", // Logging library
      "ch.qos.logback" % "logback-classic" % "1.5.18", // Engine for logging

      // Langchain4j dependencies
      "dev.langchain4j" % "langchain4j" % "1.0.0-beta1" % Test,
      "dev.langchain4j" % "langchain4j-ollama" % "1.0.0-beta1",
      "dev.langchain4j" % "langchain4j-qdrant" % "1.0.0-beta1",
      "dev.langchain4j" % "langchain4j-easy-rag" % "1.0.0-beta1",
      "dev.langchain4j" % "langchain4j-onnx-scoring" % "1.0.0-beta1",
      "com.microsoft.onnxruntime" % "onnxruntime_gpu" % "1.20.0",

      // Akka dependencies
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.8",
      "com.tngtech.archunit" % "archunit" % "1.4.0",

      // JSON dependencie
      "io.circe" %% "circe-parser" % "0.14.5",
    ),
  )
