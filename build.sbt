name := "neovim-scala"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "com.eed3si9n" %% "treehugger" % "0.4.1"

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "org.msgpack" % "jackson-dataformat-msgpack" % "0.8.11"
libraryDependencies += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.8.3"
libraryDependencies += "io.reactivex" %% "rxscala" % "0.26.3"
