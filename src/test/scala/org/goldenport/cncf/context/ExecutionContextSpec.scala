package org.goldenport.cncf.context

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class ExecutionContextSpec extends AnyWordSpec with Matchers {

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

    "adopt runtime config id namespace when rebinding under a global runtime" in {
      val base = ExecutionContext.create()
      val namespace = IdGenerationContext.IdNamespace("customer_b", "osaka_02")
      val global = GlobalRuntimeContext.create(
        "runtime-namespace-spec",
        RuntimeConfig.default.copy(idNamespace = namespace),
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
      val ctx = ExecutionContext.withRuntimeContext(base, runtime)

      ctx.idGeneration.namespace shouldBe namespace
      ctx.major shouldBe "customer_b"
      ctx.minor shouldBe "osaka_02"
    }
  }
}
