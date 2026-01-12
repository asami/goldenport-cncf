//> using scala "3.6.2"
//> using repository "https://maven.pkg.github.com/asami/maven-repository"
//> using dep "org.goldenport:goldenport-cncf_3:0.3.0-SNAPSHOT"

package demo.level1

import org.goldenport.cncf.dsl.*

class DemoComponent extends DslComponent("hello") {
  operation("world", "greeting") { req =>
    result_success("ok")
  }
}
