package org.goldenport.cncf.dsl.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.cncf.*
import org.goldenport.cncf.context.*
import org.goldenport.cncf.dsl.script.*
import org.goldenport.test.matchers.ResponseMatchers

/*
 * @since   Jan. 14, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ScriptDslSpec extends AnyWordSpec with Matchers with ResponseMatchers {
  "ScriptRuntime" should {
    "execute a minimal script DSL successfully" in {
      val args = Seq("SCRIPT", "DEFAULT", "RUN")

      val result =
        ScriptRuntime.execute(args) { call =>
          "ok"
        }

      result should be_success_response("ok")
    }

    "execute a one argument script DSL" in {
      val args = Seq("SCRIPT", "DEFAULT", "RUN", "world")
      val result = ScriptRuntime.execute(args) { call =>
        "hello " + call.args(0)
      }
      result should be_success_response("hello world")
    }

    "reject unknown script component" ignore { // TODO The precise operation name is necessary.
      val args = Seq("SCRIPT", "DEFAULT", "RUNX")

      val result =
        ScriptRuntime.execute(args) { call =>
          "ok"
        }

      result should be_not_found_response
    }

    "execute with implicit DEFAULT RUN when args contains only user arguments" in {
      val args = Seq("world")
      val result = ScriptRuntime.execute(args) { call =>
        call.request.operation shouldBe "RUN"
        call.request.service shouldBe Some("DEFAULT")
        call.request.component shouldBe Some("SCRIPT")
        call.request.arguments.map(_.value) shouldBe List("world")
        "hello " + call.args(0)
      }
      result should be_success_response("hello world")
    }

    // "register ScriptExecutionComponent as a component" in {
    //   val runtime =
    //     CncfRuntimeBuilder()
    //       .addComponent(new ScriptExecutionComponent)
    //       .build()

    //   val components = runtime.componentRepository.components

    //   components.exists(_.name == "SCRIPT1") shouldBe true
    // }

    // "resolve DEFAULT RUN operation" in {
    //   val args = Seq("SCRIPT1", "DEFAULT", "RUN")

    //   val runtime =
    //     CncfRuntimeBuilder()
    //       .addComponent(new ScriptExecutionComponent)
    //       .build()

    //   val ctx = ExecutionContext.forCommand(args)

    //   val result = runtime.execute(ctx)

    //   result.isSuccess shouldBe true
    // }
  }
}
