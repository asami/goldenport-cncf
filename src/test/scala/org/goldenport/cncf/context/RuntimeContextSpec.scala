package org.goldenport.cncf.context

import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import cats.~>
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class RuntimeContextSpec extends AnyWordSpec with Matchers {

  "RuntimeContext" should {

    "satisfy basic properties" in {
      pending
    }

    "preserve invariants" in {
      pending
    }

    "rebind UnitOfWork context without dropping runtime lifecycle actions" in {
      var committed = false
      var aborted = false
      val base = ExecutionContext.create()
      lazy val originalContext: ExecutionContext =
        ExecutionContext.withRuntimeContext(base, originalRuntime)
      lazy val originalRuntime: RuntimeContext = new RuntimeContext(
        core = base.runtime.core,
        unitOfWorkSupplier = () => new UnitOfWork(originalContext),
        unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
          def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
            Consequence.serviceUnavailable("not used")
        },
        commitAction = _ => committed = true,
        abortAction = _ => aborted = true,
        disposeAction = _ => (),
        token = "original"
      )
      lazy val reboundContext: ExecutionContext =
        ExecutionContext.withRuntimeContext(originalContext, reboundRuntime)
      lazy val reboundRuntime: RuntimeContext =
        originalRuntime.withUnitOfWorkContext(reboundContext, "rebound")

      reboundContext.runtime.toToken shouldBe "rebound"
      reboundContext.runtime.commit()
      reboundContext.runtime.abort()

      committed shouldBe true
      aborted shouldBe true
    }
  }
}
