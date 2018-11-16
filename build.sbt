

val commonSettings = Seq(
  organization := "com.smartthings.brave.scala",

  scalaVersion := "2.12.7",

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),

  libraryDependencies ++= Seq(
    "io.zipkin.brave" % "brave" % "5.5.0",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test
  )
)

lazy val publishSettings =
  Seq(
    homepage := Some(url("https://github.com/smartthingsoss/smartthings-brave-scala")),
    scmInfo := Some(ScmInfo(url("https://github.com/smartthingsoss/smartthings-brave-scala"),
      "git@github.com:smartthingsoss/smartthings-brave-scala.git")),
    developers += Developer("llinder",
      "Lance Linder",
      "lance@smartthings.com",
      url("https://github.com/llinder")),
    pomIncludeRepository := (_ => false),
    bintrayOrganization := Some("smartthingsoss"),
    bintrayPackage := "smartthings-brave-scala"
  )

lazy val settings = commonSettings ++ publishSettings

lazy val root = project.in(file("."))
  .enablePlugins(GitBranchPrompt)
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-project",
    publishArtifact := false,
    Compile / unmanagedSourceDirectories := Seq.empty,
    Test / unmanagedSourceDirectories    := Seq.empty,
  )
  .aggregate(core, config, akka, akkaHttp)

lazy val core = project.in(file("core"))
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-core"
  )

lazy val config = project.in(file("config"))
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-config",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.3",
      "io.zipkin.aws" % "zipkin-sender-sqs" % "0.13.0" % Optional
    )
  )

lazy val akka = project.in(file("akka"))
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-akka",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.17"
    )
  )
  .dependsOn(core, config)

lazy val akkaHttp = project.in(file("akka-http"))
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.4",
      "io.zipkin.brave" % "brave-instrumentation-http" % "5.5.0",
      "io.zipkin.brave" % "brave-instrumentation-http-tests" % "5.5.0" % Test
    )
  )
  .dependsOn(core, akka)

lazy val examples = project.in(file("examples"))
  .settings(settings)
  .settings(
    name := "smartthings-brave-scala-examples"
  )
  .dependsOn(core, akka, akkaHttp)