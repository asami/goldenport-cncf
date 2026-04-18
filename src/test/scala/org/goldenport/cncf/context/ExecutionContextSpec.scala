package org.goldenport.cncf.context

import org.goldenport.cncf.config.OperationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Dec. 23, 2025
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
  }
}
