package org.goldenport.cncf.subsystem

import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.job.{JobId, TaskId}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Argument, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:subsystem-execute-http, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:subsystem-execute-http, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:subsystem-execute-http, example:E3, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e4 = afterWord("in spec:subsystem-execute-http, example:E4, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e5 = afterWord("in spec:subsystem-execute-http, example:E5, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e6 = afterWord("in spec:subsystem-execute-http, example:E6, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e7 = afterWord("in spec:subsystem-execute-http, example:E7, rules:CID05C-R1, phase:56, slice:CID-05C")
  private val _e8 = afterWord("in spec:subsystem-execute-http, example:E8, rules:CID05C-R11, phase:56, slice:CID-05C")

  "Subsystem.executeHttp" should {

    "E1 reject display-compatible slash route /admin/system/ping" must _e1 {
      "when exercising: reject display-compatible slash route /admin/system/ping" in {
        Given("a server subsystem and a noncanonical display-compatible ping route")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
        When("the subsystem executes the HTTP route")
        val res = subsystem.executeHttp(req)

        Then("the strict runtime boundary rejects the noncanonical route")
        res.code shouldBe 404
      }
    }

    "E2 return runtime introspection for exact qualified slash route" must _e2 {
      "when exercising: return runtime introspection for exact qualified slash route" in {
        Given("a server subsystem and its qualified canonical ping route")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val req = HttpRequest.fromPath(HttpRequest.GET, "/org.goldenport.cncf.Admin/system/ping")
        When("the subsystem executes the HTTP route")
        val res = subsystem.executeHttp(req)

        Then("runtime introspection is returned")
        res.code shouldBe 200
        res.getString.getOrElse("") should include ("runtime: goldenport-cncf")
      }
    }

    "E3 reject compatibility dot route /admin.system.ping" must _e3 {
      "when exercising: reject compatibility dot route /admin.system.ping" in {
        Given("a server subsystem and a noncanonical dotted ping route")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val req = HttpRequest.fromPath(HttpRequest.GET, "/admin.system.ping")
        When("the subsystem executes the HTTP route")
        val res = subsystem.executeHttp(req)

        Then("the strict runtime boundary rejects the noncanonical route")
        res.code shouldBe 404
      }
    }

    "E4 return not found for unknown operation" must _e4 {
      "when exercising: return not found for unknown operation" in {
        Given("a server subsystem and an unknown route")
        val subsystem = RuntimeBindingAdmissionFixture.default()
        val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
        When("the subsystem executes the HTTP route")
        val res = subsystem.executeHttp(req)

        Then("the route is not found")
        res.code shouldBe 404
      }
    }

    "E5 route direct Request selectors through exact identity and reject display aliases" must _e5 {
      "when exercising: route direct Request selectors through exact identity and reject display aliases" in {
        Given("a server subsystem with the builtin Admin operation")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val exact = Request.of(
          component = "org.goldenport.cncf.Admin",
          service = "system",
          operation = "ping"
        )
        val compatibility = Request.of(
          component = "admin",
          service = "system",
          operation = "ping"
        )

        When("the public Request execution boundary receives canonical and display selectors")
        val exactresult = subsystem.executeOperationResponse(exact)
        val compatibilityresult = subsystem.executeOperationResponse(compatibility)

        Then("the canonical selector succeeds while the bare display alias fails closed")
        exactresult.isSuccess shouldBe true
        compatibilityresult.isSuccess shouldBe false
      }
    }

    "E6 reject malformed direct Request component and service selectors without throwing" must _e6 {
      "when exercising: reject malformed direct Request component and service selectors without throwing" in {
        Given("a server subsystem and direct Requests with empty dot segments")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val malformedcomponent = Request.of(component = "org.goldenport..Admin", service = "system", operation = "ping")
        val malformedservice = Request.of(component = "admin", service = "org.goldenport.cncf.Admin..system", operation = "ping")

        When("the public Request execution boundary resolves malformed selectors")
        val componentresult = subsystem.executeOperationResponse(malformedcomponent)
        val serviceresult = subsystem.executeOperationResponse(malformedservice)

        Then("both malformed selectors are rejected as ordinary unsuccessful results")
        componentresult.isSuccess shouldBe false
        serviceresult.isSuccess shouldBe false
      }
    }

    "E7 reject leading, trailing, and unknown qualified direct Request selectors without normalization fallback" must _e7 {
      "when exercising: reject leading, trailing, and unknown qualified direct Request selectors without normalization fallback" in {
        Given("a server subsystem and direct Requests whose qualified component selectors cannot be exact identities")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val leadingempty = Request.of(component = ".admin", service = "system", operation = "ping")
        val trailingempty = Request.of(component = "admin.", service = "system", operation = "ping")
        val unknownqualified = Request.of(component = "org.goldenport.cncf.Admin/", service = "system", operation = "ping")

        When("the public Request execution boundary resolves the selectors")
        val leadingresult = subsystem.executeOperationResponse(leadingempty)
        val trailingresult = subsystem.executeOperationResponse(trailingempty)
        val unknownresult = subsystem.executeOperationResponse(unknownqualified)

        Then("every selector is rejected as an ordinary unsuccessful result without executing its normalized alias")
        leadingresult.isSuccess shouldBe false
        trailingresult.isSuccess shouldBe false
        unknownresult.isSuccess shouldBe false
      }
    }

    "E8 accept exact JobControl get_task_detail requests with taskId arguments" must _e8 {
      "when exercising: accept exact JobControl get_task_detail requests with taskId arguments" in {
        Given("the admitted default subsystem and its exact JobControl component")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val jobcontrol = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("JobControl is missing"))
        val jobid = JobId.generate()
        val taskid = TaskId.generate()
        val request = Request.of(
          component = "org.goldenport.cncf.JobControl",
          service = "job",
          operation = "get_task_detail",
          arguments = List(
            Argument("id", jobid.value),
            Argument("taskId", taskid.value)
          )
        )

        When("the public component logic prepares the operation request")
        val result = jobcontrol.logic.makeOperationRequest(request)

        Then("the valid generated ID arguments produce a successful operation request")
        result.isSuccess shouldBe true
      }
    }
  }

}
