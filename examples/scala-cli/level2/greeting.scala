#!/usr/bin/env -S scala-cli shebang
//> using repository "https://maven.pkg.github.com/asami/maven-repository"
//> using dep "org.goldenport:goldenport-cncf_3:0.3.2
import org.goldenport.cncf.dsl.script.*

@main def main(args: String*): Unit = run(args) { call =>
  "hello " + call.args(0)
} 
