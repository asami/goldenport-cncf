package org.goldenport.cncf.http

import java.nio.file.Paths
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebApplicationEntryPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "WebApplicationEntryPolicy" should {
    "select the sole application at the default public entry without entry configuration" in {
      Given("a sole Web application and an implicit root component")
      val descriptor = WebDescriptor(apps = Vector(WebDescriptor.App("Customer Portal")))
      val subsystem = _subsystem("customer-portal")

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, Some(subsystem))

      Then("the application is selected at /web")
      result shouldBe WebApplicationEntryPolicy.Selected(descriptor.apps.head, "customer-portal", "/web")
    }

    "select the exact entry application from multiple applications" in {
      Given("multiple Web applications with one declared entry")
      val descriptor = WebDescriptor(apps = Vector(WebDescriptor.App("one"), WebDescriptor.App("two", entry = true)))

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, Some(_subsystem("owner")))

      Then("the declared entry is selected at /web")
      result shouldBe WebApplicationEntryPolicy.Selected(descriptor.apps(1), "owner", "/web")
    }

    "reject ambiguous multiple-application entry configuration without publishing a URL" in {
      Given("multiple Web applications with no entry declaration")
      val descriptor = WebDescriptor(apps = Vector(WebDescriptor.App("one"), WebDescriptor.App("two")))

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, Some(_subsystem("owner")))

      Then("the resolution is deterministic configuration error")
      result shouldBe WebApplicationEntryPolicy.Error("No Web entry app configured for multiple Web applications")
    }

    "reject more than one declared entry application deterministically" in {
      Given("multiple Web applications with more than one entry declaration")
      val descriptor = WebDescriptor(apps = Vector(WebDescriptor.App("one", entry = true), WebDescriptor.App("two", entry = true)))

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, Some(_subsystem("owner")))

      Then("the resolution is a deterministic configuration error")
      result shouldBe WebApplicationEntryPolicy.Error("Multiple Web entry apps configured: one, two")
    }

    "leave the application entry absent when no application is declared" in {
      Given("a descriptor without Web applications")

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(WebDescriptor.empty, Some(_subsystem("owner")))

      Then("the policy declares that no application URL is publishable")
      result shouldBe WebApplicationEntryPolicy.NoApplication
    }

    "preserve an explicit default route as the published application URL" in {
      Given("a selected application with one explicit SAR default route")
      val app = WebDescriptor.App("portal")
      val descriptor = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/portal", WebDescriptor.RouteTarget("sar-owner", "portal"), WebDescriptor.RouteKind.Default)))

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, None)

      Then("the route owner and explicit path are preserved")
      result shouldBe WebApplicationEntryPolicy.Selected(app, "sar-owner", "/web/portal")
    }

    "normalize a qualified default route owner while preserving its canonical application path" in {
      Given("a selected application with a qualified SAR component owner")
      val app = WebDescriptor.App("portal")
      val descriptor = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/portal", WebDescriptor.RouteTarget("org.simplemodeling.textus.ControlCenter", "portal"), WebDescriptor.RouteKind.Default)))

      When("the public entry is resolved")
      val result = WebApplicationEntryPolicy.resolve(descriptor, None)

      Then("the route accepts the nonempty qualified owner and normalizes it for routing")
      result shouldBe WebApplicationEntryPolicy.Selected(app, "org-simplemodeling-textus-control-center", "/web/portal")
    }

    "reject invalid global default route declarations deterministically" in {
      Given("a selected application and every rejected default route shape")
      val app = WebDescriptor.App("portal")
      val foreign = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/other", WebDescriptor.RouteTarget("owner", "other"), WebDescriptor.RouteKind.Default)))
      val multiple = WebDescriptor(apps = Vector(app), routes = Vector(
        WebDescriptor.Route("/web/portal", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default),
        WebDescriptor.Route("/web/second", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)
      ))
      val nonweb = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/form/portal", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val query = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/portal?debug=true", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val fragment = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/portal#section", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val dotsegment = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/./portal", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val reserved = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/system", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val noncanonical = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web//portal", WebDescriptor.RouteTarget("owner", "portal"), WebDescriptor.RouteKind.Default)))
      val emptyowner = WebDescriptor(apps = Vector(app), routes = Vector(WebDescriptor.Route("/web/portal", WebDescriptor.RouteTarget("", "portal"), WebDescriptor.RouteKind.Default)))

      When("the public entry policy resolves each declaration")
      val results = Vector(nonweb, query, fragment, dotsegment, noncanonical, emptyowner, reserved, foreign, multiple).map(WebApplicationEntryPolicy.resolve(_, Some(_subsystem("owner"))))

      Then("none can silently select or publish an application entry")
      results.foreach(_ shouldBe a[WebApplicationEntryPolicy.Error])
    }
  }

  private def _subsystem(component: String): GenericSubsystemDescriptor =
    GenericSubsystemDescriptor(
      Paths.get("example.sar"),
      "example",
      componentBindings = Vector(GenericSubsystemComponentBinding(component)),
      implicitRootComponentName = Some(component)
    )
}
