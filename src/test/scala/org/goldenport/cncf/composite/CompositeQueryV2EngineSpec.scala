package org.goldenport.cncf.composite

import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{InMemoryJobEngine, JobDataOrigin, JobEngine, JobEngineTestFixture, JobPersistencePolicy}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryV2EngineSpec extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  private val _e01 = afterWord("in spec:composite-query-v2, example:E01, rules:CQ2-R2,CQ2-R3, phase:69.5, slice:JM69-07A")
  private val _e02 = afterWord("in spec:composite-query-v2, example:E02, rules:CQ2-R3,CQ2-R4, phase:69.5, slice:JM69-07A")
  private val _e03 = afterWord("in spec:composite-query-v2, example:E03, rules:CQ2-R4,CQ2-R10, phase:69.5, slice:JM69-07A")
  private val _e04 = afterWord("in spec:composite-query-v2, example:E04, rules:CQ2-R4,CQ2-R8, phase:69.5, slice:JM69-07A")
  private val _e05 = afterWord("in spec:composite-query-v2, example:E05, rules:CQ2-R5,CQ2-R10, phase:69.5, slice:JM69-07A")
  private val _e06 = afterWord("in spec:composite-query-v2, example:E06, rules:CQ2-R1,CQ2-R5, phase:69.5, slice:JM69-07A")
  private val _e07 = afterWord("in spec:composite-query-v2, example:E07, rules:CQ2-R4,CQ2-R10, phase:69.5, slice:JM69-07A")
  private val _e08 = afterWord("in spec:composite-query-v2, example:E08, rules:CQ2-R4,CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e09 = afterWord("in spec:composite-query-v2, example:E09, rules:CQ2-R8, phase:69.5, slice:JM69-07A")
  private val _e10 = afterWord("in spec:composite-query-v2, example:E10, rules:CQ2-R3,CQ2-R11, phase:69.5, slice:JM69-07A")
  private val _e11 = afterWord("in spec:composite-query-v2, example:E11, rules:CQ2-R11,CQ2-R12, phase:69.5, slice:JM69-07A")
  private val _e12 = afterWord("in spec:composite-query-v2, example:E12, rules:CQ2-R5,CQ2-R12, phase:69.5, slice:JM69-07A")
  private val _e28 = afterWord("in spec:composite-query-v2, example:E28, rules:CQ2-R1,CQ2-R10, phase:69.5, slice:JM69-07A")

  "CompositeQueryV2Engine" should {
    "E01 reject malformed admission before a real QueryAction enters" must _e01 {
      "when a branch ID and request are malformed" in _with_fixture { fixture =>
        Given("an admitted component, controlled official scheduler, and malformed requests")
        val controlled = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        val overbranches = Vector.tabulate(65)(index => fixture.branch(s"branch$index"))
        val overedges = Vector.tabulate(24) { index =>
          val dependencies =
            if (index == 23) (0 until 4).map(value => CompositeQueryV2Dependency(s"edge$value")).toVector
            else (0 until index).map(value => CompositeQueryV2Dependency(s"edge$value")).toVector
          fixture.branch(s"edge$index", dependencies = dependencies)
        }
        val malformed = Vector(
          CompositeQueryV2Request(Vector(fixture.branch("").copy(request = fixture.request()))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(request = fixture.request("").copy(operation = "")))),
          CompositeQueryV2Request(Vector(fixture.branch("1invalid"))),
          CompositeQueryV2Request(Vector(fixture.branch("a" * 65))),
          CompositeQueryV2Request(Vector(fixture.branch("duplicate"), fixture.branch("duplicate"))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(timeoutMillis = Some(0L)))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(fallback = Some(CompositeQueryV2Fallback(OperationResponse.Opaque(new Object)))))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(fallback = Some(CompositeQueryV2Fallback(OperationResponse.Scalar("x" * 64))))), CompositeQueryV2Policy(maxResponseBytes = 16)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), protocolVersion = 1),
          CompositeQueryV2Request(Vector(fixture.branch("valid", dependencies = Vector(CompositeQueryV2Dependency("missing"))))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(target = CompositeQueryV2Target.Subsystem("missing")))),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(target = CompositeQueryV2Target.Subsystem(" ")))),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxParallelism = 0)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxParallelism = 17)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(timeoutMillis = 0L)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(timeoutMillis = 60001L)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxBranches = 0)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxBranches = 65)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxResponseBytes = 0)),
          CompositeQueryV2Request(Vector(fixture.branch("valid")), CompositeQueryV2Policy(maxResponseBytes = 262145)),
          CompositeQueryV2Request(Vector(fixture.branch("valid").copy(timeoutMillis = Some(100L))), CompositeQueryV2Policy(timeoutMillis = 99L)),
          CompositeQueryV2Request(overbranches),
          CompositeQueryV2Request(overedges)
        )
        When("executeBlocking preflights every malformed request and a valid empty request")
        val results = malformed.map(request => _execute_request(fixture, request, controlled))
        val empty = _execute_request(fixture, CompositeQueryV2Request(Vector.empty), controlled)
        Then("all are refused before scheduler admission or QueryAction entry")
        results.forall(_.isSuccess == false) shouldBe true
        empty.isSuccess shouldBe true
        controlled.admissions.get() shouldBe 0
        fixture.state.queryCalls.isEmpty shouldBe true
      }
    }

    "E02 reject cyclic and duplicate DAG edges before a real Job is admitted" must _e02 {
      "when a declared dependency graph is invalid" in _with_fixture { fixture =>
        Given("two real query branches and a controlled official scheduler")
        val controlled = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        val duplicate = Vector(
          fixture.branch("first", dependencies = Vector(CompositeQueryV2Dependency("second"), CompositeQueryV2Dependency("second"))),
          fixture.branch("second")
        )
        val selfcycle = Vector(fixture.branch("self", dependencies = Vector(CompositeQueryV2Dependency("self"))))
        val cycle = Vector(
          fixture.branch("first", dependencies = Vector(CompositeQueryV2Dependency("second"))),
          fixture.branch("second", dependencies = Vector(CompositeQueryV2Dependency("first")))
        )
        When("each invalid dependency graph is independently admitted")
        val duplicateresult = _execute(fixture, duplicate, engine = controlled)
        val selfresult = _execute(fixture, selfcycle, engine = controlled)
        val cycleresult = _execute(fixture, cycle, engine = controlled)
        Then("the graph is refused before dispatch")
        duplicateresult.isSuccess shouldBe false
        selfresult.isSuccess shouldBe false
        cycleresult.isSuccess shouldBe false
        controlled.admissions.get() shouldBe 0
        fixture.state.queryCalls.isEmpty shouldBe true
      }
    }

    "E03 execute reverse-declared diamond dependencies with declaration-order results" must _e03 {
      "when real QueryAction branches complete" in _with_fixture { fixture =>
        Given("a root, two children, and a join listed before their prerequisites")
        val branches = Vector(
          fixture.branch("join", dependencies = Vector(CompositeQueryV2Dependency("left"), CompositeQueryV2Dependency("right"))),
          fixture.branch("left", dependencies = Vector(CompositeQueryV2Dependency("root"))),
          fixture.branch("right", dependencies = Vector(CompositeQueryV2Dependency("root"))),
          fixture.branch("root")
        )
        When("the graph runs against the admitted component")
        val response = _success(_execute(fixture, branches))
        Then("each prerequisite body entered before its dependents and results remain declaration ordered")
        response.results.map(_.branchId) shouldBe Vector("join", "left", "right", "root")
        val order = fixture.state.queryCalls.toArray(new Array[String](0)).toVector
        order.indexOf("root") should be < order.indexOf("left")
        order.indexOf("root") should be < order.indexOf("right")
        order.indexOf("left") should be < order.indexOf("join")
        order.indexOf("right") should be < order.indexOf("join")
      }
    }

    "E04 execute AfterCompletion while skipping RequireValue after real failure" must _e04 {
      "when a prerequisite QueryAction returns a failure" in _with_fixture { fixture =>
        Given("failed root, fallback root, value-dependent branches, and completion-dependent branch")
        val branches = Vector(
          fixture.branch("root").copy(request = fixture.request("failure")),
          fixture.branch("skip", dependencies = Vector(CompositeQueryV2Dependency("root"))),
          fixture.branch("after", dependencies = Vector(CompositeQueryV2Dependency("root", CompositeQueryV2DependencyMode.AfterCompletion))),
          fixture.branch("recover").copy(request = fixture.request("unavailable"), fallback = Some(CompositeQueryV2Fallback(OperationResponse.void))),
          fixture.branch("usesFallback", dependencies = Vector(CompositeQueryV2Dependency("recover")))
        )
        When("the coordinator runs the real dependency graph")
        val response = _success(_execute(fixture, branches))
        Then("failure skips RequireValue, AfterCompletion executes, and fallback satisfies RequireValue")
        response.result("skip").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Skipped)
        response.result("after").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        response.result("recover").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Fallback)
        response.result("usesFallback").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        fixture.state.queryCalls.contains("after") shouldBe true
      }
    }

    "E05 retain equal terminal values and order across sequential and parallel execution" must _e05 {
      "when the same real queries use separate scheduler ceilings" in {
        Given("two independent admitted component fixtures")
        val sequential = new CompositeQueryV2Fixture
        val parallel = new CompositeQueryV2Fixture
        val caller = ExecutionContext.test()
        val oneresult = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val tworesult = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val onecaller = new Thread(() => oneresult.set(_execute(sequential, Vector(
          sequential.branch("one").copy(request = sequential.request("gateone")),
          sequential.branch("two").copy(request = sequential.request("gatetwo"))
        ), CompositeQueryV2Policy(maxParallelism = 1), context = caller)))
        val twocaller = new Thread(() => tworesult.set(_execute(parallel, Vector(
          parallel.branch("one").copy(request = parallel.request("gateone")),
          parallel.branch("two").copy(request = parallel.request("gatetwo"))
        ), CompositeQueryV2Policy(maxParallelism = 2), context = caller)))
        try {
          When("sequential and parallel callers use opposite controlled completion permutations")
          onecaller.start()
          val sequentialfirstentered = sequential.awaitEntered()
          sequential.releaseFirst()
          val sequentialsecondentered = sequential.awaitEntered(2)
          sequential.releaseSecond()
          onecaller.join(1500L)
          twocaller.start()
          val parallelentered = parallel.awaitEntered(2)
          parallel.releaseSecond()
          parallel.releaseFirst()
          twocaller.join(1500L)
          val one = _success(oneresult.get())
          val two = _success(tworesult.get())
          Then("ordered terminal values, statuses, and outcomes agree across completion permutations")
          sequentialfirstentered shouldBe true
          sequentialsecondentered shouldBe true
          parallelentered shouldBe true
          one.status shouldBe two.status
          one.results.map(x => (x.branchId, x.outcome, x.response)) shouldBe two.results.map(x => (x.branchId, x.outcome, x.response))
        } finally {
          sequential.releaseFirst(); sequential.releaseSecond(); parallel.releaseFirst(); parallel.releaseSecond()
          onecaller.join(1500L); twocaller.join(1500L)
          sequential.close(); parallel.close()
        }
      }
    }

    "E06 show two gated QueryAction bodies concurrently and never a third admission" must _e06 {
      "when a four-worker JobEngine has maxParallelism two" in _with_fixture { fixture =>
        Given("three real branches, two of which wait on one bounded latch")
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(fixture, Vector(
          fixture.branch("one").copy(request = fixture.request("gate")),
          fixture.branch("two").copy(request = fixture.request("gate2")),
          fixture.branch("three")
        ), CompositeQueryV2Policy(maxParallelism = 2))))
        When("the caller starts and both gated QueryActions enter")
        try {
          caller.start()
          val entered = fixture.awaitEntered(2)
          Then("the active counter reaches two and no third body has entered")
          entered shouldBe true
          fixture.state.maxActive.get() shouldBe 2
          fixture.state.enteredCount.get() shouldBe 2
        } finally {
          fixture.release()
          caller.join(2000L)
        }
        result.get().isSuccess shouldBe true
      }
    }

    "E07 retain independent real successes through CollectAll failures" must _e07 {
      "when required and optional branches fail beside an independent query" in _with_fixture { fixture =>
        Given("two failed QueryActions and one successful QueryAction")
        val branches = Vector(
          fixture.branch("required").copy(request = fixture.request("failure")),
          fixture.branch("optional", required = false).copy(request = fixture.request("failure")),
          fixture.branch("success")
        )
        When("CollectAll executes")
        val response = _success(_execute(fixture, branches))
        Then("every branch is terminal and independent success survives")
        response.status shouldBe CompositeQueryV2AggregateStatus.Failed
        response.result("success").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        response.results.size shouldBe 3
      }
    }

    "E08 cancel pending required work after actual fail-fast failure" must _e08 {
      "when a required failure precedes a gated query" in _with_fixture { fixture =>
        Given("a running gated branch and a required failure on the same real scheduler")
        val branches = Vector(fixture.branch("active").copy(request = fixture.request("gate")), fixture.branch("bad").copy(request = fixture.request("failure")), fixture.branch("late"))
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(fixture, branches, CompositeQueryV2Policy(maxParallelism = 2, failureMode = CompositeQueryV2FailureMode.FailFastRequired))))
        When("the first required QueryAction fails")
        try {
          caller.start()
          val entered = fixture.awaitEntered(2)
          caller.join(1500L)
          fixture.release()
          val exited = fixture.state.awaitExited(2)
          Then("pending work is skipped and an admitted QueryAction observes fail-fast cancellation")
          entered shouldBe true
          exited shouldBe true
          _success(result.get()).result("late").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Skipped)
          fixture.state.cancelledObserved.get() shouldBe true
          fixture.state.queryCalls.contains("late") shouldBe false
        } finally {
          fixture.release()
          caller.join(1500L)
        }
        Given("a separate optional failure followed by a real gated branch")
        val optionalfixture = new CompositeQueryV2Fixture
        val optionalresult = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val optionalcaller = new Thread(() => optionalresult.set(_execute(optionalfixture, Vector(
          optionalfixture.branch("optional", required = false).copy(request = optionalfixture.request("failure")),
          optionalfixture.branch("continues").copy(request = optionalfixture.request("gate"))
        ), CompositeQueryV2Policy(maxParallelism = 1, failureMode = CompositeQueryV2FailureMode.FailFastRequired))))
        try {
          When("an optional failure completes before an independent query is admitted")
          optionalcaller.start()
          val optionalentered = optionalfixture.awaitEntered(2)
          optionalfixture.release()
          optionalcaller.join(1500L)
          Then("optional failure does not trigger required fail-fast cancellation")
          optionalentered shouldBe true
          _success(optionalresult.get()).result("continues").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        } finally {
          optionalfixture.release()
          optionalcaller.join(1500L)
          optionalfixture.close()
        }
      }
    }

    "E09 recover only a real operational failure through explicit fallback" must _e09 {
      "when a target QueryAction reports service unavailable" in _with_fixture { fixture =>
        Given("constant fallbacks for operational, authorization, route, configuration, protocol, and exception failures")
        val fallback = Some(CompositeQueryV2Fallback(OperationResponse.void))
        val branch = fixture.branch("recover").copy(request = fixture.request("unavailable"), fallback = fallback)
        val denied = fixture.branch("denied").copy(request = fixture.request("deny"), fallback = fallback)
        val rejected = fixture.branch("rejected").copy(request = fixture.request("failure"), fallback = fallback)
        val route = fixture.branch("route").copy(request = fixture.request("route"), fallback = fallback)
        val configuration = fixture.branch("configuration").copy(request = fixture.request("configuration"), fallback = fallback)
        val protocol = fixture.branch("protocol").copy(request = fixture.request("protocol"), fallback = fallback)
        val internal = fixture.branch("internal").copy(request = fixture.request("throw"), fallback = fallback)
        When("the target query-only call fails operationally")
        val response = _success(_execute(fixture, Vector(branch, denied, rejected, route, configuration, protocol, internal)))
        Then("only the operational branch becomes Fallback and each other code remains terminal")
        response.result("recover").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Fallback)
        response.result("recover").flatMap(_.recoveredFailure) shouldBe Some(CompositeQueryV2FailureCode.QueryFailed)
        response.result("denied").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.AccessDenied)
        response.result("rejected").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.QueryRejected)
        response.result("route").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        response.result("configuration").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        response.result("protocol").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        response.result("internal").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.InternalFailure)
      }
    }

    "E10 reject actual opaque and over-limit responses" must _e10 {
      "when real QueryActions return unsupported payloads" in _with_fixture { fixture =>
        Given("opaque, HTTP, hostile scalar, large response, and oversized fallback branches")
        val branches = Vector(
          fixture.branch("opaque").copy(request = fixture.request("opaque")),
          fixture.branch("http").copy(request = fixture.request("http")),
          fixture.branch("hostileScalar").copy(request = fixture.request("hostileScalar")),
          fixture.branch("large").copy(request = fixture.request("large"))
        )
        val hostile = new Object { override def toString: String = throw new IllegalStateException("must-not-stringify") }
        // Deliberately malformed producer witness; no external ScalarValue evidence is defined.
        When("the response retention boundary runs")
        val response = _success(_execute(fixture, branches, CompositeQueryV2Policy(maxResponseBytes = 16)))
        val fallback = _execute(
          fixture,
          Vector(fixture.branch("oversizedFallback").copy(fallback = Some(CompositeQueryV2Fallback(OperationResponse.Scalar("x" * 64))))),
          CompositeQueryV2Policy(maxResponseBytes = 16)
        )
        Then("the result exposes only fixed rejection codes")
        response.result("opaque").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.UnsupportedResponse)
        response.result("http").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.UnsupportedResponse)
        // The hostile scalar is observed by the existing ActionEngine boundary first;
        // its String.valueOf failure is normalized as the frozen InternalFailure.
        response.result("hostileScalar").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.InternalFailure)
        response.result("large").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.ResponseLimitExceeded)
        CompositeQueryV2ResponseEncoding.byteSize(OperationResponse.Opaque(hostile)) shouldBe None
        CompositeQueryV2ResponseEncoding.byteSize(OperationResponse.Scalar(hostile)(using null)) shouldBe None
        fallback.isSuccess shouldBe false
        fixture.state.queryCalls.contains("oversizedFallback") shouldBe false
      }
    }

    "E11 retain no real request payload in PersistentDiagnostics Jobs" must _e11 {
      "when a persistent diagnostic branch completes" in _with_fixture { fixture =>
        Given("configured durable Job storage plus ephemeral and PersistentDiagnostics queries")
        val durablecontext = createJobEntityContext()
        When("the real branch Jobs are submitted through the configured persistence provider")
        val ephemeral = _success(_execute(fixture, Vector(fixture.branch("ephemeral")), context = durablecontext))
        val beforepersistent = fixture.jobEngine.listJobs(persistentOnly = true)
        val response = _success(_execute(
          fixture,
          Vector(fixture.branch("first")),
          CompositeQueryV2Policy(persistence = CompositeQueryV2Persistence.PersistentDiagnostics),
          context = durablecontext
        ))
        val persisted = fixture.jobEngine.listJobs(persistentOnly = true)
        val durableread = persisted.flatMap(model => fixture.jobEngine.query(model.jobId))
        Then("ephemeral jobs have no durable checkpoint and durable Job/task channels retain no query payload")
        ephemeral.diagnostics.map(_.branchId) shouldBe Vector("ephemeral")
        beforepersistent shouldBe Vector.empty
        response.diagnostics.map(_.branchId) shouldBe Vector("first")
        persisted.size shouldBe 1
        durableread.map(_.origin) shouldBe Vector(JobDataOrigin.Durable)
        durableread.map(_.persistence) shouldBe Vector(JobPersistencePolicy.Persistent)
        persisted.forall(_.debug.parameters.isEmpty) shouldBe true
        persisted.forall(_.input.isEmpty) shouldBe true
        persisted.forall(_.calltree.isEmpty) shouldBe true
        persisted.forall(_.result.contains(OperationResponse.void)) shouldBe true
      }
    }

    "E12 return actual scheduler rejection without another scheduler fallback" must _e12 {
      "when the explicit JobEngine refuses admission or withholds canonical settlement" in _with_fixture { fixture =>
        Given("an official controlled engine that refuses admission and one that omits canonical settlement")
        val refused = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        refused.rejectAdmissions.set(true)
        val omitted = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        omitted.omitCanonical.set(true)
        val held = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        held.omitCanonicalCallbacks.set(1)
        When("a real QueryAction is submitted through each controlled scheduler seam")
        val response = _success(_execute(fixture, Vector(fixture.branch("first")), engine = refused))
        val absent = _success(_execute(fixture, Vector(fixture.branch("second")), CompositeQueryV2Policy(timeoutMillis = 40L), omitted))
        val heldresult = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val heldcaller = new Thread(() => heldresult.set(_execute(fixture, Vector(
          fixture.branch("withheld").copy(timeoutMillis = Some(250L)),
          fixture.branch("afterWithheld")
        ), CompositeQueryV2Policy(maxParallelism = 1, timeoutMillis = 1000L), held)))
        try {
          heldcaller.start()
          val exited = fixture.state.awaitExited(2)
          val admissionsbeforetimeout = held.admissions.get()
          heldcaller.join(1500L)
          Then("admission is sanitized and missing canonical settlement cannot publish success or free width early")
          exited shouldBe true
          admissionsbeforetimeout shouldBe 1
          _success(heldresult.get()).result("withheld").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
          _success(heldresult.get()).result("afterWithheld").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        } finally heldcaller.join(1500L)
        response.result("first").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.SchedulerRejected)
        absent.result("second").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
      }
    }

    "E28 adapt actual V2 values while preserving actual V1 execution" must _e28 {
      "when the same admitted component serves both surfaces" in _with_fixture { fixture =>
        Given("a caller context, public facade, usable and optional/required failed v2 requests, and legacy constructor values")
        given ExecutionContext = ExecutionContext.test()
        val facade = CompositeQueryEngine(fixture.subsystem)
        val v2request = CompositeQueryV2Request(Vector(
          fixture.branch("first"),
          fixture.branch("optional", required = false).copy(request = fixture.request("failure"))
        ))
        val requiredrequest = CompositeQueryV2Request(Vector(fixture.branch("required").copy(request = fixture.request("failure"))))
        val named = NamedQuery("first", fixture.request())
        val legacyinput = CompositeQueryRequest(Vector(named))
        When("the public facade executes successful, optional-failure, required-failure, and legacy requests")
        val v2 = _success(facade.executeV2Blocking(v2request))
        val required = _success(facade.executeV2Blocking(requiredrequest))
        val legacy = _success(v2.toLegacyResponse)
        val v1 = facade.execute(legacyinput)
        val NamedQuery(legacyname, legacyrequest, legacyrequired, legacydependencies) = named
        Then("the public facade reports V2 success and failures while the adapter and V1 arities/defaults remain usable")
        v2.result("first").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
        v2.result("optional").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        required.result("required").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        legacy.results.map(_.name) shouldBe Vector("first", "optional")
        required.toLegacyResponse.isSuccess shouldBe false
        (legacyname, legacyrequest, legacyrequired, legacydependencies) shouldBe ("first", fixture.request(), true, Vector.empty)
        CompositeQueryRequest(Vector(named)).policy shouldBe CompositeQueryPolicy()
        _success(v1).requiredRecord("first").isSuccess shouldBe true
      }
    }
  }

  private def _execute(
    fixture: CompositeQueryV2Fixture,
    branches: Vector[CompositeQueryV2Branch],
    policy: CompositeQueryV2Policy = CompositeQueryV2Policy(),
    engine: JobEngine = null,
    context: ExecutionContext = ExecutionContext.test()
  ): Consequence[CompositeQueryV2Response] = {
    _execute_request(fixture, CompositeQueryV2Request(branches, policy), Option(engine).getOrElse(fixture.jobEngine), context)
  }

  private def _execute_request(
    fixture: CompositeQueryV2Fixture,
    request: CompositeQueryV2Request,
    engine: JobEngine,
    context: ExecutionContext = ExecutionContext.test()
  ): Consequence[CompositeQueryV2Response] = {
    given ExecutionContext = context
    CompositeQueryV2Engine(fixture.subsystem, engine, CompositeQueryV2Targets.empty).executeBlocking(request)
  }

  private def _with_fixture(body: CompositeQueryV2Fixture => Unit): Unit = {
    val fixture = new CompositeQueryV2Fixture(suppliedJobEngine = createJobEngine(InMemoryJobEngine.SchedulerConfig(workerCount = 4)))
    try body(fixture) finally fixture.close()
  }

  private def _success[A](value: Consequence[A]): A = value.toOption.getOrElse(fail("expected Consequence.Success"))
}
