package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version May.  5, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ExecutionContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "ExecutionContext" should {

    "satisfy basic properties" in {
      pending
    }

    "preserve invariants" in {
      pending
    }

    "expose operation mode through the runtime context" in {
      val base = ExecutionContext.create()
      val runtime = new RuntimeContext(
        core = base.runtime.core,
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "operation-mode-spec",
        operationMode = OperationMode.Production
      )
      val ctx = ExecutionContext.withRuntimeContext(base, runtime)

      ctx.operationMode shouldBe OperationMode.Production
    }

    "use single/global as the default runtime id namespace" in {
      val ctx = ExecutionContext.create()

      ctx.major shouldBe "single"
      ctx.minor shouldBe "global"
      ctx.idGeneration.namespace shouldBe IdGenerationContext.DefaultNamespace
    }

    "bind namespace overload through the UnitOfWork context" in {
      val namespace = IdGenerationContext.IdNamespace("customer_a", "tokyo_01")
      val ctx = ExecutionContext.create(namespace)

      ctx.idGeneration.namespace shouldBe namespace
      ctx.unitOfWork.executionContext.idGeneration.namespace shouldBe namespace
    }

    "carry the global runtime clock into component execution" in {
      Given("a global runtime configured with a virtual execution clock")
      val base = ExecutionContext.create()
      val basestart = Instant.parse("2026-07-15T00:00:00Z")
      val virtualstart = Instant.parse("2026-07-28T09:00:00Z")
      val runtimeclock = RuntimeClock.offset(
        Clock.fixed(basestart, ZoneOffset.UTC),
        virtualstart
      )
      val global = GlobalRuntimeContext.create(
        "runtime-clock-spec",
        RuntimeConfig.default.copy(
          executionProfile = _offset_profile(runtimeclock, virtualstart)
        ),
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        base.observability,
        AliasResolver.empty
      )
      val runtime = new RuntimeContext(
        core = RuntimeContext.core(
          name = "runtime-clock-spec",
          parent = Some(global),
          observabilityContext = base.observability
        ),
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "runtime-clock-spec"
      )

      When("a component execution context is created below that runtime")
      val ctx = ExecutionContext.create(runtime)

      Then("the component context uses the selected runtime clock")
      ctx.clock should be theSameInstanceAs runtimeclock.clock
      ctx.clock.instant() shouldBe virtualstart
    }

    "adopt runtime config namespace and clock when rebinding under a global runtime" in {
      Given("a base context and a global runtime with different namespace and clock settings")
      val base = ExecutionContext.create()
      val namespace = IdGenerationContext.IdNamespace("customer_b", "osaka_02")
      val virtualstart = Instant.parse("2026-08-03T00:00:00Z")
      val runtimeclock = RuntimeClock.offset(
        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC),
        virtualstart
      )
      val global = GlobalRuntimeContext.create(
        "runtime-namespace-spec",
        RuntimeConfig.default.copy(
          idNamespace = namespace,
          executionProfile = _offset_profile(runtimeclock, virtualstart)
        ),
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        base.observability,
        AliasResolver.empty
      )
      val runtime = new RuntimeContext(
        core = RuntimeContext.core(
          name = "runtime-namespace-spec",
          parent = Some(global),
          observabilityContext = base.observability
        ),
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "runtime-namespace-spec"
      )

      When("the base execution context is rebound to that runtime")
      val ctx = ExecutionContext.withRuntimeContext(base, runtime)

      Then("the namespace and clock both follow the global runtime")
      ctx.idGeneration.namespace shouldBe namespace
      ctx.major shouldBe "customer_b"
      ctx.minor shouldBe "osaka_02"
      ctx.clock should be theSameInstanceAs runtimeclock.clock
      ctx.clock.instant() shouldBe virtualstart
    }
  }

  private def _offset_profile(
    runtimeclock: RuntimeClock,
    startat: Instant
  ): ResolvedExecutionProfile = {
    val configuration = ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("offset"),
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue(startat.toString)
      )),
      ConfigurationTrace.empty
    )
    ExecutionProfileResolver.resolveForSpec(configuration).toOption.get.copy(
      runtimeClock = runtimeclock
    )
  }
}
