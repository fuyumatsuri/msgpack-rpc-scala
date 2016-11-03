name := "msgpack-rpc-scala"

organization := "xyz.aoei"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.11"
libraryDependencies += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.3"
libraryDependencies += "io.reactivex" %% "rxscala" % "0.26.3"

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
  <url>http://github.com/Chad-/msgpack-rpc-scala</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:Chad-/msgpack-rpc-scala.git</url>
    <connection>scm:git:git@github.com:Chad-/msgpack-rpc-scala.git</connection>
  </scm>
  <developers>
    <developer>
      <id>Chad-</id>
      <name>Chad Morrison</name>
      <url>http://aoei.xyz</url>
    </developer>
  </developers>
