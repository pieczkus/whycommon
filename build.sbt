import sbt.Keys.{libraryDependencies, _}
import Dependencies._

organization := "pl.why"
name := "common"

version := "1.1"
scalaVersion := "2.11.8"
resolvers += Resolver.sonatypeRepo("releases")
resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven"

enablePlugins(PlayScala)

assemblyJarName in assembly := "common.jar"
libraryDependencies ++= commonDependencies

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
