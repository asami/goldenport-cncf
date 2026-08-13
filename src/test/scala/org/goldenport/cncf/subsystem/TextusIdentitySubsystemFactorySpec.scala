package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 26, 2026
 *  version Apr. 24, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusIdentitySubsystemFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "TextusIdentitySubsystemFactory" should {
    "admit the exact canonical development component identity" in {
      Given("a self-contained development repository containing one UserAccount component factory")
      _with_development_repository(classOf[TextusIdentityDevFixtureComponent]) { repository =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(s"scala-cli:${repository}")
          )),
          ConfigurationTrace.empty
        )

        When("the identity subsystem is created with the repository override")
        val subsystem = TextusIdentitySubsystemFactory.default(configuration = configuration)
        val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
        val repositorycomponents = subsystem.components.filterNot(_.origin == ComponentOrigin.Builtin)
        val exact = subsystem.findComponent(componentid)

        Then("exactly one repository component retains the canonical runtime identity alongside expected built-ins")
        repositorycomponents.map(_.componentId) shouldBe Vector(componentid)
        exact.map(_.componentId) shouldBe Some(componentid)
      }
    }

    "reject a legacy artifact label paired with a different Core identity" in {
      Given("a self-contained repository whose legacy artifact label does not match the canonical Core identity")
      _with_development_repository(classOf[TextusIdentityLegacyDevFixtureComponent]) { repository =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(s"scala-cli:${repository}")
          )),
          ConfigurationTrace.empty
        )

        When("the identity subsystem is created from the mismatched repository")
        val failure = intercept[IllegalStateException] {
          TextusIdentitySubsystemFactory.default(configuration = configuration)
        }

        Then("canonical admission fails closed with the exact identity diagnostic")
        failure.getMessage should include ("canonical component binding has no exact Core/artifact identity match")
        failure.getMessage should include ("org.simplemodeling.textus.UserAccount")
      }
    }
  }

  private def _with_development_repository[A](fixture: Class[?])(body: Path => A): A = {
    val repository = Files.createTempDirectory("textus-identity-dev-repository")
    val classes = repository.resolve("classes")
    try {
      _copy_class(fixture, classes)
      _copy_class(Class.forName(s"${fixture.getName}$$"), classes)
      body(repository)
    } finally {
      _delete_recursively(repository)
    }
  }

  private def _copy_class(cls: Class[?], classes: Path): Unit = {
    val relative = Path.of(s"${cls.getName.replace('.', '/')}.class")
    val resource = Option(getClass.getClassLoader.getResource(relative.toString))
      .getOrElse(fail(s"missing test fixture class: ${relative}"))
    val target = classes.resolve(relative)
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(resource.openStream()) { input =>
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path)) {
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
    }
}

final class TextusIdentityDevFixtureComponent extends Component

object TextusIdentityDevFixtureComponent extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component = {
    val _ = params
    new TextusIdentityDevFixtureComponent
  }

  protected def create_Core(
    params: ComponentCreate,
    component: Component
  ): Component.Core = {
    val _ = params
    component.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "test",
        name = "textus-user-account",
        version = "0.6.0-SNAPSHOT",
        component = Some("textus-user-account"),
        componentId = Some(ComponentId("org.simplemodeling.textus.UserAccount"))
      )
    )
    val componentid = ComponentId("org.simplemodeling.textus.UserAccount")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}

final class TextusIdentityLegacyDevFixtureComponent extends Component

object TextusIdentityLegacyDevFixtureComponent extends Component.Factory {
  protected def create_Component(params: ComponentCreate): Component = {
    val _ = params
    new TextusIdentityLegacyDevFixtureComponent
  }

  protected def create_Core(
    params: ComponentCreate,
    component: Component
  ): Component.Core = {
    val _ = params
    component.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "test",
        name = "textus-user-account",
        version = "0.6.0-SNAPSHOT",
        component = Some("textus-user-account"),
        componentId = Some(ComponentId("org.goldenport.cncf.test.OtherUserAccount"))
      )
    )
    val componentid = ComponentId("org.goldenport.cncf.test.OtherUserAccount")
    Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}
