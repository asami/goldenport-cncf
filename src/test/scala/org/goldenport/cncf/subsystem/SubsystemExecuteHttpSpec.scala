package org.goldenport.cncf.subsystem

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import org.goldenport.http.HttpRequest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "Subsystem.executeHttp" should {

    "return runtime introspection for canonical slash route /admin/system/ping" in {
      Given("a server subsystem and its canonical ping route")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
      When("the subsystem executes the HTTP route")
      val res = subsystem.executeHttp(req)

      Then("runtime introspection is returned")
      res.code shouldBe 200
      res.getString.getOrElse("") should include ("runtime: goldenport-cncf")
    }

    "allow compatibility dot route /admin.system.ping for now" in {
      Given("a server subsystem and its compatibility ping route")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin.system.ping")
      When("the subsystem executes the HTTP route")
      val res = subsystem.executeHttp(req)

      Then("runtime introspection is returned")
      res.code shouldBe 200
      res.getString.getOrElse("") should include ("runtime: goldenport-cncf")
    }

    "return not found for unknown operation" in {
      Given("a server subsystem and an unknown route")
      val subsystem = RuntimeBindingAdmissionFixture.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
      When("the subsystem executes the HTTP route")
      val res = subsystem.executeHttp(req)

      Then("the route is not found")
      res.code shouldBe 404
    }
  }

}
