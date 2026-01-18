#!/usr/bin/env -S scala-cli shebang
//> using scala 3.3.7
//> using repository "https://www.simplemodeling.org/maven"
//> using dep "org.goldenport:goldenport-cncf_3:0.3.1"
import org.goldenport.cncf.dsl.script.*

@main def main(args: String*): Unit = ScriptRuntime.run(args) { call =>
  "hello world"
} 
