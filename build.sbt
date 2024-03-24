import com.typesafe.sbt.packager.docker.DockerVersion

ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "3.4.0"

lazy val root = (project in file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .enablePlugins(DockerPlugin)
  .enablePlugins(AshScriptPlugin)
  .settings(
    name := "plant-server",
    libraryDependencies ++=
      commonDeps ++
        circeDeps ++
        http4sDeps ++
        logDeps ++
        otelDeps ++
        smithyDeps ++
        testDeps ++
        testContainersDeps ++
        Seq(deps.pureConfig),
    commonSettings,
    dockerBaseImage := "amazoncorretto:17-alpine",
    dockerExposedPorts ++= Seq(8080),
    dockerUpdateLatest := true
  )

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-Wunused:all",
    "-Wvalue-discard",
    "-language:implicitConversions",
    "-source:future",
    "-feature",
    "-deprecation"
  ),
  testFrameworks += new TestFramework("weaver.framework.CatsEffect")
) ++ otelSettings ++ scalafmtSettings

lazy val otelSettings = Seq(
  Compile / javaOptions ++= Seq(
    "-Dotel.java.global-autoconfigure.enabled=true",
    s"-Dotel.service.name=${name.value}"
  ),
  Universal / javaOptions ++= (Compile / javaOptions).value
)

lazy val scalafmtSettings = Seq(scalafmtOnCompile := true)

lazy val commonDeps = Seq(deps.cats, deps.catsEffect, deps.catsEffectTime, deps.kittens)
lazy val circeDeps = Seq(deps.circe, deps.circeGeneric, deps.circeParser)
lazy val http4sDeps =
  Seq(deps.http4sClient, deps.http4sServer, deps.http4sCirce, deps.http4sDsl)
lazy val logDeps =
  Seq(deps.log4cats, deps.log4catsNoop, deps.logback)
lazy val otelDeps = Seq(deps.otel4s, deps.otelExporter, deps.otelSdk)
lazy val smithyDeps = Seq(deps.smithy4s, deps.smithy4sHttp4s, deps.smithy4sHttp4sSwagger)
lazy val testDeps = Seq(deps.weaver, deps.weaverScalacheck)
lazy val testContainersDeps = Seq(deps.testContainers)

lazy val deps = new {
  val catsVersion = "2.10.0"
  val catsEffectVersion = "3.5.3"
  val catsEffectTimeVersion = "0.2.1"
  val circeVersion = "0.14.6"
  val http4sVersion = "0.23.26"
  val kittensVersion = "3.2.0"
  val log4catsVersion = "2.6.0"
  val logbackVersion = "1.5.0"
  val otel4sVersion = "0.4.0"
  val otelVersion = "1.34.1"
  val pureConfigVersion = "0.17.6"
  val smithy4sVersion = "0.18.13"
  val testContainersVersion = "0.41.3"
  val weaverVersion = "0.8.4"

  val cats = "org.typelevel" %% "cats-core" % catsVersion
  val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectVersion
  val catsEffectTime = "io.chrisdavenport" %% "cats-effect-time" % catsEffectTimeVersion
  val kittens = "org.typelevel" %% "kittens" % kittensVersion

  val circe = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion

  val http4sClient = "org.http4s" %% "http4s-ember-client" % http4sVersion
  val http4sServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion

  val log4cats = "org.typelevel" %% "log4cats-slf4j" % log4catsVersion
  val log4catsNoop = "org.typelevel" %% "log4cats-noop" % log4catsVersion
  val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

  val otel4s = "org.typelevel" %% "otel4s-java" % otel4sVersion
  val otelExporter =
    "io.opentelemetry" % "opentelemetry-exporter-otlp" % otelVersion % Runtime
  val otelSdk =
    "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % otelVersion % Runtime

  val pureConfig = "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion

  val smithy4s = "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion
  val smithy4sHttp4s =
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion
  val smithy4sHttp4sSwagger =
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion

  val testContainers =
    "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersVersion % Test

  val weaver = "com.disneystreaming" %% "weaver-cats" % weaverVersion % Test
  val weaverScalacheck =
    "com.disneystreaming" %% "weaver-scalacheck" % weaverVersion % Test
}
