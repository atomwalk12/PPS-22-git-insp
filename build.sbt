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
  Wart.ImplicitParameter,
  Wart.Nothing,
  Wart.Equals,
  Wart.Throw,
  Wart.Var,
)

wartremoverErrors ++= Warts.unsafe
wartremoverErrors --= Seq(
  Wart.Any,
  Wart.Throw,
  Wart.Var,
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
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,

    // Logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5", // Logging library
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18", // Engine for logging
  )
