import sbt.Keys._
import sbt._

object MyBuild extends Build {

  lazy val openclrenderdemo: Project = project in file(".") dependsOn `macro`

  lazy val `macro`: Project = project in file("macro") settings (
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value)
}
