package org.goldenport.cncf.dsl.script

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.cncf.*
import org.goldenport.cncf.context.*
import org.goldenport.cncf.dsl.script.*
import org.goldenport.test.matchers.ResponseMatchers
import org.scalatest.GivenWhenThen

/*
 * @since   Jan. 14, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class ScriptDslSpec extends AnyWordSpec with Matchers with ResponseMatchers with GivenWhenThen {
  "ScriptRuntime" should {
    "execute a minimal script DSL successfully" in {
      Given("the canonical slash-form script invocation")
      val args = Seq(
        s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
        "org.goldenport.cncf.Script/DEFAULT/RUN"
      )

      When("the script runtime executes the invocation")
      val result =
        ScriptRuntime.execute(args) { call =>
          "ok"
        }

      Then("the callback result is returned successfully")
      result should be_success_response("ok")
    }

    "execute a one argument script DSL" in {
      Given("a canonical slash-form script invocation with one user argument")
      val args = Seq(
        s"--textus.test.descriptor=${_controlled_test_descriptor_path}",
        "org.goldenport.cncf.Script/DEFAULT/RUN",
        "world"
      )
      When("the script runtime executes the invocation")
      val result = ScriptRuntime.execute(args) { call =>
        "hello " + call.args(0)
      }
      Then("the callback receives the user argument")
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
      Given("a script invocation containing only user arguments")
      val args = Seq(s"--textus.test.descriptor=${_controlled_test_descriptor_path}", "world")
      When("the runtime supplies the script aliases and invokes the callback")
      val result = ScriptRuntime.execute(args) { call =>
        call.request.operation shouldBe "RUN"
        call.request.service shouldBe Some("DEFAULT")
        call.request.component shouldBe Some("org.goldenport.cncf.Script")
        call.request.arguments.map(_.value) shouldBe List("world")
        "hello " + call.args(0)
      }
      Then("the callback sees canonical Script identity and receives the argument")
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

  private lazy val _controlled_test_descriptor_path: Path = {
    val path = Files.createTempFile("cncf-script-dsl-", ".yaml")
    Files.writeString(
      path,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: script-dsl-spec
        |  time:
        |    mode: manual
        |    start-at: 2026-08-11T00:00:00Z
        |  random:
        |    mode: seeded
        |    seed: script-dsl-spec
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }
}
