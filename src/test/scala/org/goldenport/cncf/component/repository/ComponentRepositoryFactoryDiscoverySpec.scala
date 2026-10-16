package org.goldenport.cncf.component.repository

import java.net.URLClassLoader
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.component.repository.fixture.discovery.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class ComponentRepositoryFactoryDiscoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _invalid = Vector(
    classOf[AParameterizedFactory].getName,
    classOf[BPrivateConstructorFactory].getName,
    classOf[CAbstractFactory].getName,
    classOf[DFactoryInterface].getName,
    "org.goldenport.cncf.component.repository.fixture.discovery.EModuleFactory$"
  )

  private def _select(names: Vector[String]): Option[Class[?]] = {
    val loader = new URLClassLoader(Array.empty[java.net.URL], getClass.getClassLoader)
    try ComponentRepository._find_factory_class(loader, names, "factory-discovery-fixture", "component-dev-dir")
    finally loader.close()
  }

  "Repository Factory discovery" should {
    "select the actual public no-argument entry despite earlier-sorting host-only helpers" in {
      Given("parameterized/private-constructor host factories and a public no-argument launcher factory")
      val valid = classOf[ZLauncherFactory].getName
      When("the actual repository selector loads and ranks candidates in either input order")
      val first = _select(_invalid :+ valid)
      val second = _select((_invalid :+ valid).reverse)
      Then("only the launcher entry is selected without executing any constructor or module initializer")
      first shouldBe Some(classOf[ZLauncherFactory])
      second shouldBe first
    }

    "return absence for exclusively ineligible candidates without manufacturing dependencies" in {
      Given("only parameterized, private-constructor, abstract, interface and module factories")
      When("the repository selector inspects their constructor metadata")
      val selected = _select(_invalid)
      Then("no reflection-instantiable factory is claimed and no helper is constructed")
      selected shouldBe None
    }

    "retain the ordinary public no-argument plain Factory route" in {
      Given("the existing plain Component.Factory fixture with no-argument construction")
      val plain = classOf[fixture.plain.ComponentFactory]
      When("it is selected alongside ineligible BundleFactory helpers")
      val selected = _select(_invalid :+ plain.getName)
      Then("the plain Factory remains eligible despite the helpers' bundle preference")
      selected shouldBe Some(plain)
    }
  }
}
