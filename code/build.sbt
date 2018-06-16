val scalaJSReactVersion = "1.1.0"
val monocleVersion = "1.4.0-M2"

lazy val root = project.in(file("."))
  .aggregate(qamrJVM, qamrJS,
             exampleJVM, exampleJS,
             analysis)
  .settings(publish := {},
            publishLocal := {})

lazy val commonSettings = Seq(
  organization := "com.github.uwnlp",
  scalaOrganization in ThisBuild := "org.typelevel",
  scalaVersion in ThisBuild := "2.11.8",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:higherKinds"/*, "-Ypartial-unification"*/),
  addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies += "com.github.julianmichael" %%% "nlpdata" % "0.1-SNAPSHOT",
  libraryDependencies += "com.github.julianmichael" %%% "spacro" % "0.1-SNAPSHOT",
  libraryDependencies += "org.typelevel" %% "cats" % "0.9.0",
  libraryDependencies += "com.lihaoyi" %%% "upickle" % "0.4.3"
)

lazy val commonJVMSettings = Seq(
  fork in console := true,
  connectInput in run := true,
  libraryDependencies += "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0",
  libraryDependencies += "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models" // for automatically downloading pos-tagging model
)

lazy val commonJSSettings = Seq(
  relativeSourceMaps := true,
  scalaJSStage in Global := FastOptStage,
  persistLauncher in Compile := true,
  persistLauncher in Test := false,
  skip in packageJSDependencies := false)

lazy val qamr = crossProject.in(file("qamr")).settings(
  commonSettings
).settings(
  name := "qamr",
  version := "0.1-SNAPSHOT",
  libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.11.0",
  libraryDependencies += "com.github.julien-truffaut" %%% "monocle-core"  % monocleVersion,
  libraryDependencies += "com.github.julien-truffaut" %%% "monocle-macro" % monocleVersion
).jvmSettings(commonJVMSettings).jvmSettings(
  fork in console := true,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.4.8",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "com.typesafe.slick" %% "slick" % "3.2.1",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
    // java deps:
    "org.slf4j" % "slf4j-api" % "1.7.21" // decided to match scala-logging transitive dep
  )
).jsSettings(commonJSSettings).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.0",
    "com.github.japgolly.scalajs-react" %%% "core" % scalaJSReactVersion,
    "com.github.japgolly.scalajs-react" %%% "ext-monocle" % scalaJSReactVersion,
    "com.github.japgolly.scalajs-react" %%% "ext-cats" % scalaJSReactVersion,
    "com.github.japgolly.scalacss" %%% "core" % "0.5.3",
    "com.github.japgolly.scalacss" %%% "ext-react" % "0.5.3"
  ),
  jsDependencies ++= Seq(
    RuntimeDOM,
    "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js",

    "org.webjars.bower" % "react" % "15.6.1"
      /        "react-with-addons.js"
      minified "react-with-addons.min.js"
      commonJSName "React",

    "org.webjars.bower" % "react" % "15.6.1"
      /         "react-dom.js"
      minified  "react-dom.min.js"
      dependsOn "react-with-addons.js"
      commonJSName "ReactDOM",

    "org.webjars.bower" % "react" % "15.6.1"
      /         "react-dom-server.js"
      minified  "react-dom-server.min.js"
      dependsOn "react-dom.js"
      commonJSName "ReactDOMServer"
  )
)

lazy val qamrJS = qamr.js
lazy val qamrJVM = qamr.jvm

lazy val example = crossProject.in(file("qamr-example")).settings(
  commonSettings
).settings(
  name := "qamr-example",
  version := "0.1-SNAPSHOT"
).jvmSettings(
  commonJVMSettings
).jvmSettings(
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    // java deps:
    "org.slf4j" % "slf4j-api" % "1.7.21", // decided to match scala-logging transitive dep
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "log4j" % "log4j" % "1.2.17", // runtime error if not included?
    "ca.juliusdavies" % "not-yet-commons-ssl" % "0.3.11",  // runtime error if not included?
    "xerces" % "xercesImpl" % "2.9.1" // runtime error if not included?
  )
).jsSettings(commonJSSettings)

lazy val exampleJS = example.js.dependsOn(qamrJS)
lazy val exampleJVM = example.jvm.dependsOn(qamrJVM).settings(
  (resources in Compile) += (fastOptJS in (exampleJS, Compile)).value.data,
  (resources in Compile) += (packageScalaJSLauncher in (exampleJS, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (exampleJS, Compile)).value
)

lazy val analysis = project.in(file("qamr-analysis")).settings(
  name := "qamr-analysis",
  version := "0.1-SNAPSHOT",
  commonSettings,
  commonJVMSettings
).dependsOn(qamrJVM, exampleJVM)
