import scala.sys.process.* // Used to change git hooks path
import org.scalajs.linker.interface.ModuleSplitStyle

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
ThisBuild / scalafmtOnCompile := true

// ------ Scoverage ------
ThisBuild / coverageEnabled := true

// ------ Wartremover ------
// finds potential bugs,(i.e. case classes must be final)
ThisBuild / wartremoverWarnings ++= Warts.all
ThisBuild / wartremoverWarnings --= Seq(
  Wart.Overloading,
  Wart.Nothing,
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.Equals
)

ThisBuild / wartremoverErrors ++= Warts.unsafe
ThisBuild / wartremoverErrors --= Seq(
  Wart.Any
)

// ------ Trunk ------
// Code style violations (i.e. trailing spaces)

// ------ Scalafix ------
// i.e. unused imports
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// ------ Tests ------
// Default test fork setting (will be overridden for frontend)
ThisBuild / Test / fork := true // provides isolation between test runs and the build tool
ThisBuild / Test / javaOptions ++= Seq(
  "-Xmx4G", // increase memory for tests
)

// ------ Assembly ------
enablePlugins(AssemblyPlugin)
// ------ Common settings ------
lazy val commonSettings = Seq(
  scalaVersion := scala3Version,
  version := "0.1.0-SNAPSHOT",
  scalacOptions ++= Seq(
    // Enable Scala 3 indentation syntax
    "-new-syntax",
    "-indent",
    "-feature",
    "-source:3.3",
    // Options for Scalafix
    "-Wunused:imports",
  )
)

// ------ Dependencies ------
// Common dependencies for both projects
lazy val commonDependencies = Seq(
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,

  // Logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5", // Logging library
  "ch.qos.logback" % "logback-classic" % "1.5.18", // Engine for logging
)

// ------ Projects ------
// Backend project for the server and API
lazy val backend = project
  .in(file("backend"))
  .settings(
    commonSettings,
    scalaVersion := scala3Version,
    name := "gitinsp-backend",
    // Explicitly set Test/fork to true for backend
    Test / fork := true,
    Test / baseDirectory := file("."),
    Compile / mainClass := Some("gitinsp.application.GitInspector"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    assembly / assemblyJarName := "gitinsp-backend.jar",

    libraryDependencies ++= commonDependencies ++ Seq(
      // Langchain4j dependencies
      "dev.langchain4j" % "langchain4j" % "1.0.0-beta2" % Test,
      "dev.langchain4j" % "langchain4j-ollama" % "1.0.0-beta2",
      "dev.langchain4j" % "langchain4j-qdrant" % "1.0.0-beta2",
      "dev.langchain4j" % "langchain4j-easy-rag" % "1.0.0-beta2",
      "dev.langchain4j" % "langchain4j-onnx-scoring" % "1.0.0-beta2",
      "dev.langchain4j" % "langchain4j-vertex-ai-gemini" % "1.0.0-beta2",
      "dev.langchain4j" % "langchain4j-anthropic" % "1.0.0-beta2",
      "com.microsoft.onnxruntime" % "onnxruntime_gpu" % "1.20.0",

      // Akka dependencies
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.8",
      "com.tngtech.archunit" % "archunit" % "1.4.0",

      // JSON dependency. Required for forwarding JSON responses
      "io.circe" %% "circe-parser" % "0.14.12",
    )
  )

// Frontend project for the UI
lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    commonSettings,
    scalaVersion := scala3Version,
    name := "gitinsp-frontend",
    // Explicitly set Test/fork to false for frontend. Required for Scala.js
    Test / fork := false,
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("gitinsp.GitInspectorFrontend"),

    // Disable WartRemover for the frontend project
    wartremoverErrors := Seq(),
    wartremoverWarnings := Seq(),
    // Disable coverage for frontend to avoid conflicts
    coverageEnabled := false,

    /* Configure Scala.js to emit modules in the optimal way to
     * connect to Vite's incremental reload.
     */
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("gitinsp")))
    },

    libraryDependencies ++= commonDependencies ++ Seq(
      // Scala.js dependencies
      "com.raquo" %%% "laminar" % "17.2.0",
      "com.raquo" %%% "laminar-shoelace" % "0.1.0",
      "com.lihaoyi" %%% "upickle" % "4.1.0",
      "io.circe" %%% "circe-generic" % "0.14.5",
      "io.circe" %%% "circe-parser" % "0.14.5"
    ),
  )

// Root project that aggregates both subprojects
lazy val root = project
  .in(file("."))
  .aggregate(backend, frontend)
  .settings(
    name := "pps-22-git-insp",

    // Don't publish the root
    publish := {},
    publishLocal := {}
  )
