package org.goldenport.cncf.provider

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.Property
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 23, 2026
 * @version May. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class ProviderEngineSpec extends AnyWordSpec with Matchers {
  "ProviderEngine" should {
    "execute ProviderCall and expose resolved parameter configuration" in {
      val context = _context()
      context.runtime.setResolvedParameters(ResolvedParameters.fromFrameworkProperties(
        List(
          Property("provider.test.endpoint", "configured-endpoint", None),
          Property("provider.test.limit", "17", None)
        ),
        "provider",
        None
      ))
      val call = _TestProviderCall(
        ProviderCall.Core(
          ProviderRequest("test", "config", Map("target" -> "fixture")),
          context,
          Some(new Component() {}),
          None
        )
      )

      ProviderEngine.execute(call) shouldBe Consequence.success("configured-endpoint:17")
    }

    "record provider and provider-step spans in CallTree" in {
      val context = ExecutionContext.withFrameworkCallTreeEnabled(_context(), enabled = true)
      val call = _TestProviderCall(
        ProviderCall.Core(
          ProviderRequest("test", "calltree"),
          context,
          None,
          None
        )
      )

      ProviderEngine.execute(call) shouldBe Consequence.success("default-endpoint:7")
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("provider:test.calltree")
      text should include("calltree_kind=provider")
      text should include("provider:config")
      text should include("calltree_kind=provider-step")
    }

    "record provider failure in CallTree" in {
      val context = ExecutionContext.withFrameworkCallTreeEnabled(_context(), enabled = true)
      val call = _FailingProviderCall(
        ProviderCall.Core(
          ProviderRequest("test", "failure"),
          context,
          None,
          None
        )
      )

      ProviderEngine.execute(call) shouldBe a[Consequence.Failure[_]]
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("provider:test.failure")
      text should include("outcome=failure")
    }

    "record thrown provider-step failure in CallTree" in {
      val context = ExecutionContext.withFrameworkCallTreeEnabled(_context(), enabled = true)
      val call = _ThrowingProviderCall(
        ProviderCall.Core(
          ProviderRequest("test", "throwing"),
          context,
          None,
          None
        )
      )

      ProviderEngine.execute(call) shouldBe a[Consequence.Failure[_]]
      val calltree = context.observability.callTreeContext.build().getOrElse(fail("calltree missing"))
      val text = calltree.toRecord.print
      text should include("provider:test.throwing")
      text should include("provider:throwing-step")
      text should include("outcome=failure")
      text should include("boom")
    }
  }

  private def _context(): ExecutionContext =
    ExecutionContext.create()

  private final case class _TestProviderCall(
    core: ProviderCall.Core
  ) extends ProviderCall[String] {
    protected def build_Program: ExecUowM[String] =
      provider_step("provider:config", Map("key" -> "provider.test.endpoint")) {
        val endpoint = provider_config_string("provider.test.endpoint", "default-endpoint")
        val limit = provider_config_int("provider.test.limit", 7)
        Consequence.success(s"$endpoint:$limit")
      }
  }

  private final case class _FailingProviderCall(
    core: ProviderCall.Core
  ) extends ProviderCall[String] {
    protected def build_Program: ExecUowM[String] =
      provider_step("provider:failure") {
        Consequence.operationIllegal("provider.failure", "expected failure")
      }
  }

  private final case class _ThrowingProviderCall(
    core: ProviderCall.Core
  ) extends ProviderCall[String] {
    protected def build_Program: ExecUowM[String] =
      provider_step("provider:throwing-step") {
        throw new IllegalStateException("boom")
      }
  }
}
