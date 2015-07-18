
name := "openclrenderdemo"

version := "0.1"

scalaVersion := "2.11.7"

scalaVersion in macro := scalaVersion.value

scalacOptions in macro := scalacOptions.value

scalacOptions += "-optimize"

scalacOptions += "-deprecation"

fork := true

libraryDependencies += "org.jocl" % "jocl" % "0.1.9"

mainClass in(Compile, run) := Some("openclrenderdemo.MainFrame")
