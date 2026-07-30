package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.{ExampleUrnResourceProvider, ResourceReference, ResourceUrlPolicy, TextusUrnResourcePolicy}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version May.  5, 2026
 * @version Jul. 30, 2026
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
      ctx.idGeneration.namespace shouldBe IdGenerationContext.DEFAULT_NAMESPACE
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

    "bind configured URL resource access when creating a context below a global runtime" in {
      Given("a global runtime with an HTTPS resource policy and deterministic driver")
      val base = ExecutionContext.create()
      val global = GlobalRuntimeContext.create(
        "resource-access-spec",
        RuntimeConfig.default.copy(
          httpDriver = FakeHttpDriver.okText("configured-resource"),
          resourceUrlPolicy = ResourceUrlPolicy(httpsHosts = Vector("catalog.example.test"))
        ),
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        base.observability,
        AliasResolver.empty
      )
      val runtime = new RuntimeContext(
        core = RuntimeContext.core(
          name = "resource-access-spec",
          parent = Some(global),
          observabilityContext = base.observability
        ),
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "resource-access-spec"
      )

      When("a context is created for the runtime scope")
      val context = ExecutionContext.create(runtime)
      val reference = ResourceReference.parseC("https://catalog.example.test/items/1").toOption.get
      val content = context.resources.readText(reference)

      Then("the context exposes only the configured URL provider binding")
      content.toOption shouldBe Some("configured-resource")
    }

    "bind configured Textus URN resource access when creating a context below a global runtime" in {
      Given("a global runtime with a logical Textus namespace root")
      val base = ExecutionContext.create()
      val root = java.nio.file.Files.createTempDirectory("textus-urn-context")
      java.nio.file.Files.writeString(root.resolve("catalog-1.txt"), "configured-urn")
      val global = GlobalRuntimeContext.create(
        "textus-urn-resource-access-spec",
        RuntimeConfig.default.copy(
          textusUrnResourcePolicy = TextusUrnResourcePolicy(Map("book" -> root))
        ),
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        base.observability,
        AliasResolver.empty
      )
      val runtime = new RuntimeContext(
        core = RuntimeContext.core(
          name = "textus-urn-resource-access-spec",
          parent = Some(global),
          observabilityContext = base.observability
        ),
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "textus-urn-resource-access-spec"
      )

      When("a context is created for the runtime scope")
      val context = ExecutionContext.create(runtime)
      val reference = ResourceReference.parseC("urn:textus:book:catalog-1.txt").toOption.get
      val content = context.resources.readText(reference)

      Then("the component-facing resource DSL resolves through its configured logical namespace")
      content.toOption shouldBe Some("configured-urn")
    }

    "bind configured external URN resource access when creating a context below a global runtime" in {
      Given("a global runtime whose explicit provider configuration binds an external NID")
      val base = ExecutionContext.create()
      val config = RuntimeConfig.from(ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.resourceUrnProvidersKey -> ConfigurationValue.StringValue(
            s"example=${classOf[ExampleUrnResourceProvider].getName}"
          )
        )),
        ConfigurationTrace.empty
      ))
      val global = GlobalRuntimeContext.create(
        "external-urn-resource-access-spec",
        config,
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        base.observability,
        AliasResolver.empty
      )
      val runtime = new RuntimeContext(
        core = RuntimeContext.core(
          name = "external-urn-resource-access-spec",
          parent = Some(global),
          observabilityContext = base.observability
        ),
        unitOfWorkSupplier = () => base.unitOfWork,
        unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
        commitAction = _ => (),
        abortAction = _ => (),
        disposeAction = _ => (),
        token = "external-urn-resource-access-spec"
      )

      When("a component context is created and reads the configured external URN")
      val context = ExecutionContext.create(runtime)
      val reference = ResourceReference.parseC("urn:example:catalog-1").toOption.get
      val content = context.resources.readText(reference)

      Then("the external provider is reachable only through the runtime resource DSL binding")
      content.toOption shouldBe Some("external:catalog-1")
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
