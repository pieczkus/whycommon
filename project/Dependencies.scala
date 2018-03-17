import sbt._
import Keys._

object Dependencies {
  val akkaVersion = "2.5.11"
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaRemote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
  val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.83"
  val akkaPersistenceQuery = "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  val elastic4sVersion = "5.2.11"
  val elastic4s = "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion
  val elastic4sXpack = "com.sksamuel.elastic4s" %% "elastic4s-xpack-security" % elastic4sVersion

  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val proto = "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion

  val jodaConvert = "org.joda" % "joda-convert" % "1.8.1"

  val json4sVersion = "3.5.2"
  val json4sNative = "org.json4s" %% "json4s-native" % json4sVersion
  val json4sExt = "org.json4s" %% "json4s-ext" % json4sVersion

  val akkaHttpVersion = "10.0.5"
  val akkaHttpJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

  val commonDependencies: Seq[ModuleID] = Seq(
    proto,
    elastic4s,
    elastic4sXpack,
    jodaConvert,
    akkaCluster,
    akkaClusterSharding,
    akkaRemote,
    akkaPersistenceCassandra,
    akkaSlf4j,
    logbackClassic,
    akkaHttpJson,
    json4sExt,
    json4sNative
  )
}

