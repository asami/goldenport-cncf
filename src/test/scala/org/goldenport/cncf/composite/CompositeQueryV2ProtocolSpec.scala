package org.goldenport.cncf.composite

import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, TraceId}
import org.goldenport.cncf.job.{InMemoryJobEngine, JobDataOrigin, JobEngineTestFixture, JobPersistencePolicy}
import org.goldenport.cncf.operation.evaluation.{OperationEvaluationContext, OperationEvaluationOperationIdentity}
import org.goldenport.protocol.{Argument, Property}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryV2ProtocolSpec extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  private val _e21 = afterWord("in spec:composite-query-v2, example:E21, rules:CQ2-R6,CQ2-R7, phase:69.5, slice:JM69-07A")
  private val _e22 = afterWord("in spec:composite-query-v2, example:E22, rules:CQ2-R3,CQ2-R7, phase:69.5, slice:JM69-07A")
  private val _e23 = afterWord("in spec:composite-query-v2, example:E23, rules:CQ2-R6,CQ2-R7,CQ2-R8, phase:69.5, slice:JM69-07A")
  private val _e24 = afterWord("in spec:composite-query-v2, example:E24, rules:CQ2-R6, phase:69.5, slice:JM69-07A")
  private val _e25 = afterWord("in spec:composite-query-v2, example:E25, rules:CQ2-R6, phase:69.5, slice:JM69-07A")
  private val _e26 = afterWord("in spec:composite-query-v2, example:E26, rules:CQ2-R3,CQ2-R6,CQ2-R12, phase:69.5, slice:JM69-07A")
  private val _e27 = afterWord("in spec:composite-query-v2, example:E27, rules:CQ2-R8,CQ2-R11,CQ2-R12, phase:69.5, slice:JM69-07A")

  "CompositeQueryV2 protocol" should {
    "E21 invoke local and independently admitted target Subsystems through the V2 envelope" must _e21 {
      "when two actual component fixtures are registered" in {
        Given("two independently admitted Subsystems with real QueryAction components")
        val local = new CompositeQueryV2Fixture(marker = "local-target")
        val remote = new CompositeQueryV2Fixture(marker = "remote-target")
        try {
          val targets = _success(CompositeQueryV2Targets.create(Vector("remote" -> remote.subsystem)))
          val branches = Vector(local.branch("local"), local.branch("remote").copy(target = CompositeQueryV2Target.Subsystem("remote"), request = remote.branch("remote").request))
          When("a strict V2 envelope and the explicit engine run both target invocations")
          given ExecutionContext = ExecutionContext.test()
          val reply = _success(new CompositeQueryV2Protocol(local.subsystem, targets).invoke(CompositeQueryV2Invocation(2, "remote", "remote", branches(1).request), branches(1), summon[ExecutionContext]))
          val response = _success(_execute(local, targets, branches))
          Then("both V2 replies retain branch correlation, values, and declaration order")
          reply.version shouldBe 2
          reply.branchId shouldBe "remote"
          reply.targetId shouldBe "remote"
          response.results.map(_.branchId) shouldBe Vector("local", "remote")
          response.results.flatMap(_.response).size shouldBe 2
          response.requiredRecord("local").toOption.flatMap(_.getString("branch")) shouldBe Some("local")
          response.requiredRecord("remote").toOption.flatMap(_.getString("branch")) shouldBe Some("remote")
          reply.response match {
            case org.goldenport.protocol.operation.OperationResponse.RecordResponse(record) => record.getString("branch") shouldBe Some("remote")
            case other => fail(s"expected record reply, got $other")
          }
          local.state.observations.peek().branchId shouldBe "local"
          remote.state.observations.peek().branchId shouldBe "remote"
          local.state.queryCalls.size() shouldBe 1
          remote.state.queryCalls.size() shouldBe 2
        } finally { local.close(); remote.close() }
      }
    }

    "E22 reject unknown targets and malformed V2 invocation correlation" must _e22 {
      "when strict protocol inputs are supplied" in _with_fixture { fixture =>
        Given("a strict directory, a real local branch, and malformed envelopes")
        val protocol = new CompositeQueryV2Protocol(fixture.subsystem, CompositeQueryV2Targets.empty)
        val branch = fixture.branch("first")
        given ExecutionContext = ExecutionContext.test()
        When("non-v2, mismatched invocation and reply correlation envelopes are invoked")
        val version = protocol.invoke(CompositeQueryV2Invocation(1, "first", "local", branch.request), branch, summon[ExecutionContext])
        val branchmismatch = protocol.invoke(CompositeQueryV2Invocation(2, "other", "local", branch.request), branch, summon[ExecutionContext])
        val targetmismatch = protocol.invoke(CompositeQueryV2Invocation(2, "first", "remote", branch.request), branch, summon[ExecutionContext])
        val requestmismatch = protocol.invoke(CompositeQueryV2Invocation(2, "first", "local", fixture.request("failure")), branch, summon[ExecutionContext])
        val replymismatch = CompositeQueryV2Protocol.validateReply(
          CompositeQueryV2Invocation(2, "first", "local", branch.request),
          CompositeQueryV2Reply(2, "reply-other", "local", org.goldenport.protocol.operation.OperationResponse.void)
        )
        val unknown = CompositeQueryV2Targets.empty.resolve(CompositeQueryV2Target.Subsystem("unknown"), fixture.subsystem)
        val duplicate = CompositeQueryV2Targets.create(Vector("remote" -> fixture.subsystem, "remote" -> fixture.subsystem))
        val reserved = CompositeQueryV2Targets.create(Vector("local" -> fixture.subsystem))
        Then("all malformed protocol and directory inputs are rejected before QueryAction execution")
        version.isSuccess shouldBe false
        branchmismatch.isSuccess shouldBe false
        targetmismatch.isSuccess shouldBe false
        requestmismatch.isSuccess shouldBe false
        replymismatch.isSuccess shouldBe false
        unknown.isSuccess shouldBe false
        duplicate.isSuccess shouldBe false
        reserved.isSuccess shouldBe false
        fixture.state.queryCalls.isEmpty shouldBe true
      }
    }

    "E23 prevent CommandAction execution through the target query-only boundary" must _e23 {
      "when a branch targets the fixture command operation" in _with_fixture { fixture =>
        Given("a denied target QueryAction, forbidden CommandAction, and optional fallbacks")
        val branch = fixture.branch("command", required = false).copy(request = fixture.request("command"), fallback = Some(CompositeQueryV2Fallback(org.goldenport.protocol.operation.OperationResponse.void)))
        val denied = fixture.branch("denied", required = false).copy(request = fixture.request("deny"), fallback = Some(CompositeQueryV2Fallback(org.goldenport.protocol.operation.OperationResponse.void)))
        When("V2 invokes the target query-only boundary")
        val response = _success(_execute(fixture, CompositeQueryV2Targets.empty, Vector(branch, denied)))
        Then("target failure and command exclusion remain terminal without fallback bypass")
        fixture.state.commandCalls.isEmpty shouldBe true
        fixture.state.queryCalls.contains("deny") shouldBe false
        response.result("command").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
        response.result("denied").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Failed)
      }
    }

    "E24 preserve caller subject and correlation across real local and target branches" must _e24 {
      "when both real branches return context observations" in {
        Given("two admitted component fixtures and an explicit target directory")
        val local = new CompositeQueryV2Fixture(marker = "local-target", ownResourceScope = true)
        val remote = new CompositeQueryV2Fixture(marker = "remote-target", ownResourceScope = true)
        try {
          val targets = _success(CompositeQueryV2Targets.create(Vector("remote" -> remote.subsystem)))
          val secured = ExecutionContext.withSecurityContext(
            ExecutionContext.test(),
            ExecutionContext.test().security.copy(
              session = Some(org.goldenport.cncf.context.SessionContext(sessionId = Some("fixture-session"))),
              principal = new org.goldenport.cncf.context.Principal {
                val id = org.goldenport.cncf.context.PrincipalId("fixture-principal")
                val attributes = Map("tenant" -> "fixture-tenant")
              }
            )
          )
          val caller = ExecutionContext.withObservabilityContext(
            secured,
            ObservabilityContext(
              TraceId("fixture", "trace"),
              None,
              Some(CorrelationId("fixture", "correlation"))
            )
          )
          When("the caller executes local and remote QueryActions")
          val response = _success(_execute_using(caller, local, targets, Vector(local.branch("local"), local.branch("remote").copy(target = CompositeQueryV2Target.Subsystem("remote"), request = remote.branch("remote").request))))
          Then("both targets preserve concrete caller identity and correlation while binding target resources and configuration")
          response.results.flatMap(_.response).size shouldBe 2
          val localobservation = local.state.observations.peek()
          val remoteobservation = remote.state.observations.peek()
          localobservation.principal shouldBe caller.security.principal.id.value
          remoteobservation.principal shouldBe caller.security.principal.id.value
          localobservation.session shouldBe "fixture-session"
          remoteobservation.session shouldBe "fixture-session"
          localobservation.tenant shouldBe "fixture-tenant"
          remoteobservation.tenant shouldBe "fixture-tenant"
          localobservation.trace shouldBe caller.observability.traceId.print
          remoteobservation.trace shouldBe caller.observability.traceId.print
          remoteobservation.correlation shouldBe caller.observability.correlationId.map(_.print).getOrElse("")
          localobservation.correlation shouldBe caller.observability.correlationId.map(_.print).getOrElse("")
          localobservation.trace.nonEmpty shouldBe true
          localobservation.correlation.nonEmpty shouldBe true
          (localobservation.runtime eq caller.runtime) shouldBe false
          (remoteobservation.runtime eq caller.runtime) shouldBe false
          (localobservation.runtime eq remoteobservation.runtime) shouldBe false
          (localobservation.resources eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resources) shouldBe false
          (remoteobservation.resources eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resources) shouldBe false
          (localobservation.resources eq remoteobservation.resources) shouldBe false
          (localobservation.resourceTrees eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resourceTrees) shouldBe false
          (remoteobservation.resourceTrees eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resourceTrees) shouldBe false
          (localobservation.resolvedParameters eq caller.resolvedParameters.asInstanceOf[AnyRef]) shouldBe false
          (remoteobservation.resolvedParameters eq caller.resolvedParameters.asInstanceOf[AnyRef]) shouldBe false
          localobservation.configurationMarker shouldBe "local-target"
          remoteobservation.configurationMarker shouldBe "remote-target"
        } finally { local.close(); remote.close() }
      }
    }

    "E25 isolate actual branch runtimes, UnitOfWork, response cells, and evaluation buffer" must _e25 {
      "when two real branches run in parallel" in _with_fixture { fixture =>
        Given("one caller evaluation context with two independently admitted real branches")
        val operation = _success(OperationEvaluationOperationIdentity.createC("fixture", "sample", "caller"))
        val caller = _success(ExecutionContext.prepareOperationEvaluation(ExecutionContext.test(), operation))
        val callerresponsestate = ExecutionContext.currentExecutionResponseState(caller)
        val preadmission = _success(CompositeQueryV2ExecutionContext.branchContext(caller, fixture.subsystem, fixture.branch("preflight").request))
        When("the target dispatches each QueryAction through branchContext")
        val response = _success(_execute_using(caller, fixture, CompositeQueryV2Targets.empty, Vector(fixture.branch("first"), fixture.branch("second")), CompositeQueryV2Policy(maxParallelism = 2)))
        Then("pre-admission reset and actual sibling runtime, UnitOfWork, response-cell, and evaluation state remain independent")
        val observations = fixture.state.observations.toArray(new Array[CompositeQueryV2Fixture.Observation](0)).toVector
        observations.size shouldBe 2
        preadmission.operationEvaluation shouldBe OperationEvaluationContext.empty
        (observations(0).runtime eq observations(1).runtime) shouldBe false
        (observations(0).unitOfWork eq observations(1).unitOfWork) shouldBe false
        (observations(0).responseCell eq observations(1).responseCell) shouldBe false
        observations.forall(observation => !(observation.runtime eq caller.runtime)) shouldBe true
        observations.forall(observation => !(observation.responseCell eq caller.asInstanceOf[ExecutionContext.Instance].executionResponseCell)) shouldBe true
        // Target dispatch may admit target evaluation state after the helper's
        // empty pre-admission context; it must never retain caller state.
        observations.forall(_.operationEvaluation != caller.operationEvaluation) shouldBe true
        observations.map(_.responseState.response.map(_.admittedMode)).toSet shouldBe Set(Some("fixture-first"), Some("fixture-second"))
        ExecutionContext.currentExecutionResponseState(caller) shouldBe callerresponsestate
        response.results.forall(_.response.nonEmpty) shouldBe true
      }
    }

    "E26 reject every trace-job alias before real query execution" must _e26 {
      "when legacy and runtime trace-job properties are present" in _with_fixture { fixture =>
        Given("one real request for each legacy trace-job alias and caller trace-job mode")
        val aliases = Vector("textus.debug.trace-job", "textus.runtime.debug.trace-job", "cncf.debug.trace-job", "cncf.runtime.debug.trace-job", "x-textus-debug-trace-job")
        val requests = aliases.zipWithIndex.map { case (alias, index) =>
          fixture.branch(s"alias$index").copy(request = fixture.request().copy(properties = List(Property(alias, "true", None))))
        }
        When("each request and a trace-job caller preflight independently")
        val results = requests.map(branch => _execute(fixture, CompositeQueryV2Targets.empty, Vector(branch)))
        val tracecaller = ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.test(), enabled = true)
        val callerresult = _execute_using(tracecaller, fixture, CompositeQueryV2Targets.empty, Vector(fixture.branch("caller")))
        Then("each alias and caller mode independently prevent Job and Query dispatch")
        results.forall(_.isSuccess == false) shouldBe true
        callerresult.isSuccess shouldBe false
        fixture.state.queryCalls.isEmpty shouldBe true
      }
    }

    "E27 redact injected request and exception secrets from actual diagnostics and persistent Job projection" must _e27 {
      "when secret-bearing requests exercise exception, raw failure, and successful payload paths" in _with_fixture { fixture =>
        Given("configured durable storage and secret-bearing arguments, properties, session, failures, exceptions, and payloads")
        val secret = "secret-token"
        def _secret_branch_(branchid: String, operation: String): CompositeQueryV2Branch =
          fixture.branch(branchid).copy(request = fixture.request(operation).copy(
            arguments = List(Argument("credential", secret)),
            properties = List(Property("fixture.branch", branchid, None), Property("password", secret, None))
          ))
        val caller = ExecutionContext.withSecurityContext(
          ExecutionContext.test(),
          ExecutionContext.test().security.copy(session = Some(org.goldenport.cncf.context.SessionContext(sessionId = Some("fixture-session"), tokenId = Some(secret))))
        )
        val durablecaller = createJobEntityContext()
        val context = ExecutionContext.withSecurityContext(durablecaller, caller.security)
        When("ephemeral and PersistentDiagnostics tasks execute through the configured provider")
        val ephemeral = _success(_execute_using(context, fixture, CompositeQueryV2Targets.empty, Vector(_secret_branch_("ephemeralPayload", "payload"))))
        val beforepersistent = fixture.jobEngine.listJobs(persistentOnly = true)
        val response = _success(_execute_using(
          context,
          fixture,
          CompositeQueryV2Targets.empty,
          Vector(
            _secret_branch_("exception", "throw"),
            _secret_branch_("rawFailure", "rawfailure"),
            _secret_branch_("payload", "payload")
          ),
          CompositeQueryV2Policy(persistence = CompositeQueryV2Persistence.PersistentDiagnostics)
        ))
        val persisted = fixture.jobEngine.listJobs(persistentOnly = true)
        val durableread = persisted.flatMap(model => fixture.jobEngine.query(model.jobId))
        Then("aggregate payload remains available while diagnostics and durable Job/task/calltree channels expose no secret or retained payload")
        ephemeral.result("ephemeralPayload").flatMap(_.response) shouldBe Some(org.goldenport.protocol.operation.OperationResponse.Scalar(secret))
        beforepersistent shouldBe Vector.empty
        response.result("payload").flatMap(_.response) shouldBe Some(org.goldenport.protocol.operation.OperationResponse.Scalar(secret))
        response.result("exception").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.InternalFailure)
        response.result("rawFailure").flatMap(_.failure).map(_.code) shouldBe Some(CompositeQueryV2FailureCode.QueryRejected)
        response.diagnostics.toString should not include secret
        response.diagnostics.forall(_.jobId.nonEmpty) shouldBe true
        persisted.size shouldBe 3
        durableread.map(_.origin) shouldBe Vector.fill(3)(JobDataOrigin.Durable)
        durableread.map(_.persistence) shouldBe Vector.fill(3)(JobPersistencePolicy.Persistent)
        durableread.exists(_.result.contains(org.goldenport.protocol.operation.OperationResponse.void)) shouldBe true
        fixture.state.observations.toArray(new Array[CompositeQueryV2Fixture.Observation](0)).forall(_.token == secret) shouldBe true
        persisted.forall(_.debug.parameters.isEmpty) shouldBe true
        persisted.forall(_.input.isEmpty) shouldBe true
        persisted.forall(_.submitter.sessionId.contains("fixture-session")) shouldBe true
        persisted.foreach { model =>
          model.calltree.foreach { calltree =>
            calltree.asMap.keySet shouldBe Set("job_id", "calltree")
            calltree.getString("job_id") shouldBe Some(model.jobId.value)
            calltree.getVector("calltree") shouldBe Some(Vector.empty)
          }
        }
        durableread.forall(_.tasks.tasks.forall(_.operation.contains("composite-query-v2"))) shouldBe true
        durableread.forall(_.traceTree.toString.contains(secret) == false) shouldBe true
        persisted.forall(_.tasks.tasks.forall(_.result.message.forall(!_.contains(secret)))) shouldBe true
        persisted.toString should not include secret
      }
    }
  }

  private def _execute(fixture: CompositeQueryV2Fixture, targets: CompositeQueryV2Targets, branches: Vector[CompositeQueryV2Branch], policy: CompositeQueryV2Policy = CompositeQueryV2Policy()): Consequence[CompositeQueryV2Response] = {
    _execute_using(ExecutionContext.test(), fixture, targets, branches, policy)
  }

  private def _execute_using(context: ExecutionContext, fixture: CompositeQueryV2Fixture, targets: CompositeQueryV2Targets, branches: Vector[CompositeQueryV2Branch], policy: CompositeQueryV2Policy = CompositeQueryV2Policy()): Consequence[CompositeQueryV2Response] = {
    given ExecutionContext = context
    CompositeQueryV2Engine(fixture.subsystem, fixture.jobEngine, targets).executeBlocking(CompositeQueryV2Request(branches, policy))
  }
  private def _with_fixture(body: CompositeQueryV2Fixture => Unit): Unit = { val fixture = new CompositeQueryV2Fixture(suppliedJobEngine = createJobEngine(InMemoryJobEngine.SchedulerConfig(workerCount = 4))); try body(fixture) finally fixture.close() }
  private def _success[A](value: Consequence[A]): A = value.toOption.getOrElse(fail("expected Consequence.Success"))
}
