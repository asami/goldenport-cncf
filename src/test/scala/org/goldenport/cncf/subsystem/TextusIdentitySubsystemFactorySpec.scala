package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 26, 2026
 *  version Apr. 24, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusIdentitySubsystemFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "TextusIdentitySubsystemFactory" should {
    "discover only the user account component" in {
      Given("the default standard repository contains the UserAccount CAR")
      val artifactRoot = ComponentRepository.defaultStandardRepositoryDir()
        .resolve("org")
        .resolve("simplemodeling")
        .resolve("car")
        .resolve("textus-user-account")
      if (!Files.exists(artifactRoot))
        cancel(s"default standard repository is missing textus-user-account under ${artifactRoot}")

      When("the identity subsystem is created from its standard repositories")
      val subsystem = TextusIdentitySubsystemFactory.default()
      val names = subsystem.components.map(_.name).sorted

      Then("only the descriptor-selected UserAccount component is installed")
      names shouldBe Vector("UserAccount")
    }

    "honor the development override repository" in {
      Given("a self-contained development repository containing one UserAccount component factory")
      _with_development_repository { repository =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey ->
              ConfigurationValue.StringValue(s"scala-cli:${repository}")
          )),
          ConfigurationTrace.empty
        )

        When("the identity subsystem is created with the repository override")
        val subsystem = TextusIdentitySubsystemFactory.default(configuration = configuration)
        val names = subsystem.components.map(_.name).sorted

        Then("the descriptor-selected UserAccount component is installed")
        names shouldBe Vector("UserAccount")
      }
    }
  }

  private def _with_development_repository[A](body: Path => A): A = {
    val repository = Files.createTempDirectory("textus-identity-dev-repository")
    val classes = repository.resolve("classes")
    try {
      _copy_class(classOf[TextusIdentityDevFixtureComponent], classes)
      _copy_class(TextusIdentityDevFixtureComponent.getClass, classes)
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
    val _ = (params, component)
    val componentid = ComponentId("user_account")
    Component.Core.create(
      "UserAccount",
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty,
      this
    )
  }
}
