package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.operation.evaluation.OperationEvaluationExecutionReport
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 22, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionContextActionEntitySpaceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ExecutionContextActionEntitySpaceSpec.*

  "Action EntitySpace ownership" should {
    "bind the callee owner" which {
      "exposes the selected EntitySpace to the action, its runtime and its self-bound interpreter" in {
        Given("a self-bound caller and a callee Action scope with a different component EntitySpace")
        val fixture = _fixture()
        val caller = fixture.caller
        val callerunitofwork = caller.unitOfWork
        val callercore = caller.runtime.core
        val owner = new EntitySpace
        val scope = _action_scope("callee", owner, caller.runtime)

        When("the callee scope is bound and its native UnitOfWork interpreter context is selected")
        val action = ExecutionContext.withActionScope(caller, scope)
        val actionunitofwork = action.unitOfWork
        val interpreted = _interpreter_context(action)

        Then("the action, runtime, UnitOfWork and interpreter all observe the callee owner")
        action.scope should be theSameInstanceAs scope
        action.entitySpace should be theSameInstanceAs owner
        action.runtime.entitySpace should be theSameInstanceAs owner
        actionunitofwork.executionContext should be theSameInstanceAs action
        interpreted should be theSameInstanceAs action
        interpreted.entitySpace should be theSameInstanceAs owner
        action.unitOfWork should be theSameInstanceAs actionunitofwork
        actionunitofwork should not be theSameInstanceAs(callerunitofwork)
        action.unitOfWorkInterpreter should not be theSameInstanceAs(caller.unitOfWorkInterpreter)

        And("the original caller keeps its owner, core and UnitOfWork without repeated supplier evaluation")
        caller.entitySpace should be theSameInstanceAs fixture.owner
        caller.runtime.core should be theSameInstanceAs callercore
        caller.unitOfWork should be theSameInstanceAs callerunitofwork
        callerunitofwork.executionContext should be theSameInstanceAs caller
        fixture.supplied.get() shouldBe 1
      }

      "keeps runtime and UnitOfWork construction lazy while selecting the owner eagerly from the explicit scope" in {
        Given("an unevaluated native caller UnitOfWork and a callee owner supplied through its Action scope")
        val fixture = _fixture()
        val scope = _action_scope("lazy-callee", new EntitySpace, fixture.caller.runtime)

        When("the child Action context and EntitySpace are selected before any UnitOfWork access")
        val action = ExecutionContext.withActionScope(fixture.caller, scope)
        val owner = action.entitySpace

        Then("binding does not evaluate the by-name context through a UnitOfWork supplier")
        owner should be theSameInstanceAs scope.entitySpace
        fixture.supplied.get() shouldBe 0

        When("the child UnitOfWork is demanded more than once")
        val first = action.unitOfWork
        val second = action.unitOfWork

        Then("one self-bound child is stable and its original supplier is evaluated once")
        first should be theSameInstanceAs second
        first.executionContext should be theSameInstanceAs action
        fixture.supplied.get() shouldBe 1
      }
    }

    "compose nested and sibling selections" which {
      "preserves A to B to C owners, semantic parents and each existing UnitOfWork" in {
        Given("a caller A and explicit B and C component Action scopes with independent EntitySpaces")
        val fixture = _fixture()
        val caller = fixture.caller
        val callerunitofwork = caller.unitOfWork
        val bowner = new EntitySpace
        val cowner = new EntitySpace
        val bscope = _action_scope("B", bowner, caller.runtime)
        val cscope = _action_scope("C", cowner, caller.runtime)
        val bparent = bscope.parent.get
        val cparent = cscope.parent.get

        When("B calls C and A also binds a sibling C")
        val b = ExecutionContext.withActionScope(caller, bscope)
        val bunitofwork = b.unitOfWork
        val nested = ExecutionContext.withActionScope(b, cscope)
        val sibling = ExecutionContext.withActionScope(caller, cscope)

        Then("each selected action and interpreter has its own owner and stable semantic component parent")
        b.entitySpace should be theSameInstanceAs bowner
        nested.entitySpace should be theSameInstanceAs cowner
        sibling.entitySpace should be theSameInstanceAs cowner
        _interpreter_context(nested) should be theSameInstanceAs nested
        _interpreter_context(sibling) should be theSameInstanceAs sibling
        b.scope.parent.get should be theSameInstanceAs bparent
        nested.scope.parent.get should be theSameInstanceAs cparent
        sibling.scope.parent.get should be theSameInstanceAs cparent
        nested.unitOfWork should not be theSameInstanceAs(bunitofwork)
        sibling.unitOfWork should not be theSameInstanceAs(nested.unitOfWork)

        And("neither nested nor sibling binding changes either existing parent")
        b.unitOfWork should be theSameInstanceAs bunitofwork
        bunitofwork.executionContext should be theSameInstanceAs b
        b.entitySpace should be theSameInstanceAs bowner
        caller.entitySpace should be theSameInstanceAs fixture.owner
        caller.unitOfWork should be theSameInstanceAs callerunitofwork
        callerunitofwork.executionContext should be theSameInstanceAs caller
      }

      "satisfies the owner and parent law for every generated nonempty nested and sibling selection sequence" in {
        Given("total sequences selecting either of two owners and either the current parent or original caller")
        val selections = for {
          size <- Gen.choose(1, 16)
          sequence <- Gen.listOfN(size, Gen.zip(Gen.choose(0, 1), Gen.oneOf(true, false)))
        } yield sequence
        val property = Prop.forAll(selections) { sequence =>
          val fixture = _fixture()
          val caller = fixture.caller
          val callerunitofwork = caller.unitOfWork
          val owners = Vector(new EntitySpace, new EntitySpace)
          val scopes = owners.zipWithIndex.map { case (owner, index) =>
            _action_scope(s"owner-$index", owner, caller.runtime)
          }
          var current = caller
          var preserved = true
          sequence.foreach { case (index, nested) =>
            val parent = if (nested) current else caller
            val parentowner = parent.entitySpace
            val parentunitofwork = parent.unitOfWork
            val parentscope = parent.scope
            val child = ExecutionContext.withActionScope(parent, scopes(index))
            val childunitofwork = child.unitOfWork
            preserved = preserved &&
              (child.entitySpace eq owners(index)) &&
              (child.runtime.entitySpace eq owners(index)) &&
              (_interpreter_context(child) eq child) &&
              (childunitofwork.executionContext eq child) &&
              (child.unitOfWork eq childunitofwork) &&
              !(childunitofwork eq parentunitofwork) &&
              (child.scope eq scopes(index)) &&
              (child.scope.parent.get eq scopes(index).parent.get) &&
              (parent.entitySpace eq parentowner) &&
              (parent.unitOfWork eq parentunitofwork) &&
              (parent.scope eq parentscope) &&
              (caller.entitySpace eq fixture.owner) &&
              (caller.unitOfWork eq callerunitofwork)
            current = child
          }
          preserved
        }

        When("ScalaCheck interprets at least one hundred generated selection sequences without filtering")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("all owner and parent laws succeed with zero discarded sequences")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "retain ownership across ordinary context transforms" which {
      "preserves the callee owner and caller facilities through security, framework, invocation and fresh bindings" in {
        Given("a callee Action with caller facilities, profile, configuration, lifecycle hooks and an original callback")
        val fixture = _fixture()
        val caller = fixture.caller
        val owner = new EntitySpace
        val scope = _action_scope("transformed", owner, caller.runtime)
        val originalcalls = new AtomicInteger
        caller.unitOfWork.stagePostCommitC {
          originalcalls.incrementAndGet()
          Consequence.unit
        }
        val security = caller.security.copy(session = Some(SessionContext(sessionId = Some("callee-session"))))

        When("native security, framework, actual invocation and public fresh UnitOfWork transforms are applied")
        val action = ExecutionContext.withActionScope(caller, scope)
        val secured = ExecutionContext.withSecurityContext(action, security)
        val framework = ExecutionContext.withFrameworkCallTreeEnabled(secured, true)
        val invoked = ExecutionContext.withExecutionInvocation(framework, "callee.read", Some("callee-owner-invocation"))
        val fresh = ExecutionContext.withFreshUnitOfWorkContext(invoked).TAKE
        val contexts = Vector(action, secured, framework, invoked, fresh)

        Then("every transform retains the callee EntitySpace and only that field differs in the runtime core")
        contexts.foreach { context =>
          context.entitySpace should be theSameInstanceAs owner
          context.runtime.entitySpace should be theSameInstanceAs owner
          context.runtime.core.copy(entityspace = caller.runtime.core.entityspace) shouldBe caller.runtime.core
          context.unitOfWork.executionContext should be theSameInstanceAs context
          _interpreter_context(context).entitySpace should be theSameInstanceAs owner
          context.dataStoreSpace should be theSameInstanceAs caller.dataStoreSpace
          context.entityStoreSpace should be theSameInstanceAs caller.entityStoreSpace
          context.unitOfWork.httpDriver should be theSameInstanceAs caller.unitOfWork.httpDriver
          context.runtime.toToken shouldBe caller.runtime.toToken
          context.runtime.context should be theSameInstanceAs caller.runtime.context
          context.runtime.operationMode shouldBe caller.runtime.operationMode
          context.runtime.resolvedParameters should be theSameInstanceAs caller.runtime.resolvedParameters
          context.runtime.transitionValidationHook should be theSameInstanceAs caller.runtime.transitionValidationHook
          context.runtime.entityCreateDefaultsPolicy should be theSameInstanceAs caller.runtime.entityCreateDefaultsPolicy
          context.cncfCore.resources should be theSameInstanceAs caller.cncfCore.resources
          context.cncfCore.resourceTrees should be theSameInstanceAs caller.cncfCore.resourceTrees
        }
        action.core should be theSameInstanceAs caller.core
        action.security should be theSameInstanceAs caller.security
        action.framework shouldBe caller.framework
        action.executionControl should be theSameInstanceAs caller.executionControl
        secured.security shouldBe security
        framework.framework.callTreeEnabled shouldBe true
        invoked should not be theSameInstanceAs(framework)
        invoked.executionControl.invocation.map(_.operationSelector) shouldBe Some("callee.read")
        fresh.core should be theSameInstanceAs invoked.core
        fresh.executionControl should be theSameInstanceAs invoked.executionControl
        fresh.idGeneration should be theSameInstanceAs invoked.idGeneration
        fresh.security shouldBe invoked.security
        fresh.framework shouldBe invoked.framework
        fresh.runtime.core should be theSameInstanceAs invoked.runtime.core
        fresh.asInstanceOf[ExecutionContext.Instance].executionResponseCell should be theSameInstanceAs
          caller.asInstanceOf[ExecutionContext.Instance].executionResponseCell
        caller.entitySpace should be theSameInstanceAs fixture.owner

        When("the fresh UnitOfWork commits its own callback and then its retained abort and disposal actions are invoked")
        val freshcalls = new AtomicInteger
        fresh.unitOfWork.stagePostCommitC {
          freshcalls.incrementAndGet()
          Consequence.unit
        }
        val committed = fresh.runtime.commitC()
        fresh.runtime.abort()
        fresh.runtime.dispose()

        Then("the preserved lifecycle actions act on the fresh UnitOfWork and leave the original callback pending")
        committed.isSuccess shouldBe true
        freshcalls.get() shouldBe 1
        originalcalls.get() shouldBe 0
        fixture.committed.get() shouldBe 1
        fixture.aborted.get() shouldBe 1
        fixture.disposed.get() shouldBe 1
        caller.unitOfWork.lastCommitTermination shouldBe None
      }
    }

    "preserve metadata ownership" which {
      "shares synchronous action and invocation diagnostics but copies explicit fresh metadata independently" in {
        Given("a caller with diagnostics and operation-evaluation metadata and an explicit callee owner")
        val fixture = _fixture()
        val caller = fixture.caller
        caller.runtime.noteExecutionDiagnostics(Some("caller-trace"), Some("caller-execution"), None)
        caller.runtime.updateExecutionMetadata(_.copy(operationEvaluation = Some(OperationEvaluationExecutionReport.empty)))
        val owner = new EntitySpace
        val scope = _action_scope("metadata", owner, caller.runtime)

        When("a synchronous child and its native invocation record diagnostics")
        val child = ExecutionContext.withActionScope(caller, scope)
        val invoked = ExecutionContext.withExecutionInvocation(child, "callee.metadata", Some("metadata-invocation"))
        invoked.runtime.noteExecutionDiagnostics(Some("child-trace"), Some("child-execution"), Some("child-detail"))
        val inherited = invoked.runtime.executionMetadata

        Then("the child and caller share the same accumulated metadata and retain their respective owners")
        caller.runtime.executionMetadata shouldBe inherited
        child.runtime.executionMetadata shouldBe inherited
        caller.runtime.executionMetadata.traceId shouldBe Some("child-trace")
        inherited.operationEvaluation shouldBe Some(OperationEvaluationExecutionReport.empty)
        caller.entitySpace should be theSameInstanceAs fixture.owner
        invoked.entitySpace should be theSameInstanceAs owner

        When("an explicit public fresh binding changes its metadata and the caller changes independently")
        val fresh = ExecutionContext.withFreshUnitOfWorkContext(invoked).TAKE
        val initial = fresh.runtime.executionMetadata
        fresh.runtime.noteExecutionDiagnostics(Some("fresh-trace"), Some("fresh-execution"), None)
        val freshmetadata = fresh.runtime.executionMetadata
        val beforecallerchange = caller.runtime.executionMetadata
        caller.runtime.noteExecutionDiagnostics(Some("caller-next"), Some("caller-next-execution"), None)

        Then("initial metadata is inherited by value while subsequent fresh and synchronous updates remain independent")
        initial shouldBe inherited
        beforecallerchange shouldBe inherited
        fresh.runtime.executionMetadata shouldBe freshmetadata
        freshmetadata.traceId shouldBe Some("fresh-trace")
        invoked.runtime.executionMetadata.traceId shouldBe Some("caller-next")
        child.runtime.executionMetadata shouldBe caller.runtime.executionMetadata
        fresh.runtime.core should be theSameInstanceAs invoked.runtime.core
        fresh.entitySpace should be theSameInstanceAs owner
        fresh.unitOfWork.executionContext should be theSameInstanceAs fresh
      }
    }
  }

  private def _action_scope(name: String, owner: EntitySpace, parent: ScopeContext): ScopeContext = {
    val component = ScopeContext.Instance(parent.core.copy(
      kind = ScopeKind.Component,
      name = name,
      parent = Some(parent),
      entityspace = Some(EntitySpaceContext(owner)),
      httpDriverOption = Some(FakeHttpDriver.okText("callee-only-driver"))
    ))
    component.createChildScope(ScopeKind.Action, s"$name.action")
  }

  private def _interpreter_context(context: ExecutionContext): ExecutionContext = {
    val interpreter = new UnitOfWorkInterpreter(context.unitOfWork)
    import interpreter.given
    summon[ExecutionContext]
  }

  private def _fixture(): Fixture = {
    val clock = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val owner = new EntitySpace
    val supplied = new AtomicInteger
    val committed = new AtomicInteger
    val aborted = new AtomicInteger
    val disposed = new AtomicInteger
    val global = GlobalRuntimeContext.create(
      "action-owner-spec",
      RuntimeConfig.default.copy(executionProfile = RuntimeConfig.default.executionProfile.copy(
        runtimeClock = RuntimeClock.system(clock)
      )),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      base.observability,
      AliasResolver.empty
    )
    lazy val caller: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = base.runtime.core.copy(parent = Some(global), entityspace = Some(EntitySpaceContext(owner))),
      unitofworksupplier = () => {
        supplied.incrementAndGet()
        new UnitOfWork(caller)
      },
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(runtime.unitOfWork).interpret(operation)
      },
      commitaction = unitofwork => {
        committed.incrementAndGet()
        val _ = unitofwork.commit()
      },
      abortaction = unitofwork => {
        aborted.incrementAndGet()
        val _ = unitofwork.abort()
      },
      disposeaction = unitofwork => {
        disposed.incrementAndGet()
        val _ = unitofwork.dispose()
      },
      token = "caller-action-owner"
    )
    runtime.setResolvedParameters(global.resolvedParameters)
    Fixture(caller, owner, supplied, committed, aborted, disposed)
  }
}

object ExecutionContextActionEntitySpaceSpec {
  private[context] final case class Fixture(
    caller: ExecutionContext,
    owner: EntitySpace,
    supplied: AtomicInteger,
    committed: AtomicInteger,
    aborted: AtomicInteger,
    disposed: AtomicInteger
  )
}
