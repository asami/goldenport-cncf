package org.goldenport.cncf.component

import java.nio.file.Paths

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ComponentDependencyOfflineSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ComponentDependency offline resolution" should {
    "append Coursier offline mode to both command classes when enabled" in {
      Given("a dependency configuration with offline resolution enabled")
      val config = ComponentDependencyConfig(
        offline = true,
        cacheDir = Some(Paths.get("/task-private/cache")),
        repositories = Vector("https://repository.example.test/releases"),
        coursierCommand = "cs"
      )
      val coordinates = Vector("org.jsoup:jsoup:1.18.1")

      When("the classpath-fetch and module-resolution commands are built")
      val classpathcommand = CoursierComponentDependencyResolver.classpathCommand(
        coordinates,
        config.repositories,
        config
      )
      val modulecommand = CoursierComponentDependencyResolver.moduleResolutionCommand(
        coordinates,
        config.repositories,
        config
      )

      Then("both commands preserve their existing arguments and include offline mode")
      classpathcommand shouldBe Vector(
        "cs", "fetch", "--classpath", "--cache", "/task-private/cache",
        "--repository", "https://repository.example.test/releases", "--mode",
        "offline", "org.jsoup:jsoup:1.18.1"
      )
      modulecommand shouldBe Vector(
        "cs", "resolve", "--cache", "/task-private/cache", "--repository",
        "https://repository.example.test/releases", "--mode", "offline",
        "org.jsoup:jsoup:1.18.1"
      )
    }

    "omit Coursier offline mode from both command classes when disabled" in {
      Given("the default dependency configuration")
      val config = ComponentDependencyConfig(coursierCommand = "cs")
      val coordinates = Vector("org.jsoup:jsoup:1.18.1")

      When("the classpath-fetch and module-resolution commands are built")
      val classpathcommand = CoursierComponentDependencyResolver.classpathCommand(
        coordinates,
        Vector.empty,
        config
      )
      val modulecommand = CoursierComponentDependencyResolver.moduleResolutionCommand(
        coordinates,
        Vector.empty,
        config
      )

      Then("neither command contains an offline mode argument")
      classpathcommand shouldBe Vector("cs", "fetch", "--classpath", "org.jsoup:jsoup:1.18.1")
      modulecommand shouldBe Vector("cs", "resolve", "org.jsoup:jsoup:1.18.1")
      classpathcommand.contains("--mode") shouldBe false
      modulecommand.contains("--mode") shouldBe false
    }
  }
}
