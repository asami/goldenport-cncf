val scala3Version = "3.6.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "component-framework",
    version := "0.1.2-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    libraryDependencies += "org.typelevel" %% "cats-core" % "2.7.0",
    libraryDependencies += "org.typelevel" %% "cats-kernel-laws" % "2.7.0",
    libraryDependencies += "org.typelevel" %% "cats-free" % "2.7.0",
    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test",
    libraryDependencies += "org.typelevel" %% "cats-testkit" % "2.7.0" % "test",
    libraryDependencies += "org.typelevel" %% "discipline-core" % "1.3.0" % "test",
    libraryDependencies += "org.typelevel" %% "discipline-scalatest" % "2.1.5" % "test",
    libraryDependencies += "org.typelevel" %% "spire" % "0.18.0",
    libraryDependencies += "dev.zio" %% "zio" % "2.0.0-RC2",
    libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.0-RC2",
    libraryDependencies += "io.circe" %% "circe-core" % "0.14.3",
    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.3",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.3",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.18",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.18",
    libraryDependencies += "net.codingwell" %% "scala-guice" % "5.1.1",
    libraryDependencies += "com.google.inject" % "guice" % "4.2.3"
  )
