package org.goldenport.cncf.spi

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.operation.evaluation.{OperationEvaluationName, OperationEvaluationStartFact, OperationEvaluationTerminalFact}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSinkSocket, DeterministicCorpusEvaluationSink}
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class SpiInvokerSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "SpiInvoker canonical invocation" should {
    "materialize a typed component API from its resolved binding" in {
      Given("a component API provider whose proxy is created only after provider selection")
      val fixture = InvocationFixture.create("spi_bound_component_api")
      given ExecutionContext = ExecutionContext.create()

      When("the component API is resolved and its typed operation is called")
      val api = fixture.subsystem.componentApiResolver.resolve(
        BoundInvocationContract.contract,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      ).toOption.get
      val result = api.echo(Record.dataAuto("message" -> "bound"))

      Then("the generated-style proxy invokes the selected component operation through SpiInvoker")
      result.toOption.get.getString("message") shouldBe Some("bound")
      fixture.provider.boundProviderMaterializationCount shouldBe 1
    }

    "inject a binding-aware component API into a required socket" in {
      Given("a generated-style provider and consumer socket in the same subsystem")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("spi_bound_socket")
      val provider = InvocationFixture.addProvider(subsystem, "primary", Vector("official-site"))
      val (consumer, socket) = InvocationFixture.addBoundConsumer(subsystem, "bound_consumer", "scraper")
      given ExecutionContext = ExecutionContext.create()

      When("assembly SPI resolution installs the only compatible provider")
      InvocationFixture.installResolver(subsystem, Vector(provider, consumer))
      val result = socket.service.echo(Record.dataAuto("message" -> "installed"))

      Then("the socket receives the generated-style proxy and invokes the selected component")
      socket.isSpiInstalled shouldBe true
      result.toOption.get.getString("message") shouldBe Some("installed")
      provider.boundProviderMaterializationCount shouldBe 1
    }

    "resolve a standard SPI and component API from the same provider without ambiguity" in {
      Given("one provider publishing both a standard typed SPI and a generated-style component API")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("spi_dual_contract")
      val provider = InvocationFixture.addProvider(subsystem, "primary", Vector("official-site"))
      val (standardconsumer, standardsocket) = InvocationFixture.addConsumer(subsystem, "standard_consumer", "standard")
      val (apiconsumer, apisocket) = InvocationFixture.addBoundConsumer(subsystem, "api_consumer", "component-api")
      given ExecutionContext = ExecutionContext.create()

      When("assembly resolution installs both contracts")
      InvocationFixture.installResolver(subsystem, Vector(provider, standardconsumer, apiconsumer))
      val apiresult = apisocket.service.echo(Record.dataAuto("message" -> "component-api"))

      Then("each socket invokes its own contract through the same provider component")
      standardsocket.isSpiInstalled shouldBe true
      apiresult.toOption.get.getString("message") shouldBe Some("component-api")
      provider.boundProviderMaterializationCount shouldBe 1
    }

    "use a selected assembly binding without rematerializing the typed service" in {
      Given("an assembly-admitted provider with an exact instance and a component operation")
      val fixture = InvocationFixture.create()
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val secret = "not-for-calltree"
      val request = Record.create(Vector(
        "tag" -> "first",
        "tag" -> "second",
        "secret" -> secret
      ))
      val socket = SpiSocketRef("consumer", "catalog", InvocationContract.name)

      When("the generic invoker resolves the exact provider and invokes the operation")
      val result = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("echo", Some("api")),
        request,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary")),
        Some(socket)
      )

      Then("the Record fields and caller ExecutionContext reach the provider without materializing the typed service")
      result shouldBe a[Consequence.Success[_]]
      result.toOption.get.asMap.get("tag").collect {
        case values: Seq[?] => values.toVector.map(_.toString)
      } shouldBe Some(Vector("first", "second"))
      fixture.provider.observedExecutionContext.exists(_.runtime eq summon[ExecutionContext].runtime) shouldBe true
      fixture.provider.typedProviderMaterializationCount shouldBe fixture.materializationsAfterAssembly

      And("safe selection metadata is visible in CallTree without request values")
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include ("spi:invocation-api.echo")
      calltree should include ("socket_name=catalog")
      calltree should include ("provider_instance=primary")
      calltree should include ("selection_basis=exact-instance")
      calltree should not include secret
    }

    "trace provider resolution failure without exposing the request record" in {
      Given("an invocation selecting an unavailable provider instance with a confidential request field")
      val fixture = InvocationFixture.create("spi_invoker_unavailable_trace")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val secret = "unavailable-request-secret"
      val beforeerrors = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors

      When("provider resolution fails before operation dispatch")
      val result = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("echo", Some("api")),
        Record.dataAuto("secret" -> secret),
        ComponentSelector(component = Some("test_provider"), instance = Some("missing"))
      )

      Then("the failure is traced with bounded resolution metadata and no request values")
      result shouldBe a[Consequence.Failure[_]]
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include ("spi:invocation-api.echo")
      calltree should include ("provider_component=unresolved")
      calltree should include ("selection_basis=exact-instance")
      calltree should include ("outcome=failure")
      calltree should not include secret
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.errors should be > beforeerrors
    }
  }

  "SpiInvoker provider resolution" should {
    "select a provider by abstract purpose before invocation" in {
      Given("two provider instances with different purposes")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("spi_invoker_purpose")
      val static = InvocationFixture.addProvider(subsystem, "static", Vector("official-site"))
      val dynamic = InvocationFixture.addProvider(subsystem, "dynamic", Vector("javascript-heavy-site"))
      InvocationFixture.installResolver(subsystem, Vector(static, dynamic))
      given ExecutionContext = ExecutionContext.create()

      When("the generic invocation requests the dynamic purpose")
      val result = subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("identity", Some("api")),
        Record.empty,
        ComponentSelector(component = Some("test_provider"), purpose = Some("javascript-heavy-site"))
      )

      Then("the operation runs on the selected component instance")
      result.toOption.get.getString("instance") shouldBe Some("dynamic")
    }

    "restrict socket invocation to its assembly-bound providers" in {
      Given("one socket bound to the static provider while a dynamic provider is also assembly-admitted")
      val subsystem = TestComponentFactory.admittedEmptySubsystem("spi_invoker_socket_boundary")
      val static = InvocationFixture.addProvider(subsystem, "static", Vector("official-site"))
      val dynamic = InvocationFixture.addProvider(subsystem, "dynamic", Vector("javascript-heavy-site"))
      val (consumer, socket) = InvocationFixture.addConsumer(subsystem, "consumer", "catalog")
      val binding = SpiRuntimeBinding(
        SpiSocketSelector(
          component = Some("consumer"),
          contract = InvocationContract.name,
          name = Some("catalog")
        ),
        SpiProviderSelector(component = Some("test_provider"), instance = Some("static"))
      )
      InvocationFixture.installResolver(subsystem, Vector(static, dynamic, consumer), Vector(binding))
      given ExecutionContext = ExecutionContext.create()
      val socketref = SpiSocketRef("consumer", socket.spiSocketName, InvocationContract.name)

      When("the socket route requests the unbound dynamic provider and the programmatic route requests the same provider")
      val socketresult = subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("identity", Some("api")),
        Record.empty,
        ComponentSelector(instance = Some("dynamic")),
        Some(socketref)
      )
      val programmaticresult = subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("identity", Some("api")),
        Record.empty,
        ComponentSelector(instance = Some("dynamic"))
      )

      Then("the socket route rejects the unbound provider while programmatic assembly resolution remains available")
      socketresult shouldBe a[Consequence.Failure[_]]
      programmaticresult.toOption.get.getString("instance") shouldBe Some("dynamic")
    }

    "reject a binding resolved by another subsystem" in {
      Given("two independent subsystems and a binding resolved by the first")
      val first = InvocationFixture.create("spi_invoker_owner_first")
      val second = InvocationFixture.create("spi_invoker_owner_second")
      given ExecutionContext = ExecutionContext.create()
      val binding = first.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      ).toOption.get

      When("the second subsystem invoker receives the foreign binding")
      val result = second.subsystem.spiInvoker.invoke(
        binding,
        SpiOperationSelector("identity", Some("api")),
        Record.empty
      )

      Then("the invocation fails before provider operation dispatch")
      _failure(result) should include ("belongs to another subsystem")
    }

    "distinguish unavailable ambiguous incompatible unhealthy and policy-rejected selections" in {
      Given("independent resolver fixtures for each provider selection failure")
      given ExecutionContext = ExecutionContext.create()
      val unavailablefixture = InvocationFixture.create("spi_failure_unavailable")
      val ambiguoussubsystem = TestComponentFactory.admittedEmptySubsystem("spi_failure_ambiguous")
      val ambiguousfirst = InvocationFixture.addProvider(ambiguoussubsystem, "first", Vector("official-site"))
      val ambiguoussecond = InvocationFixture.addProvider(ambiguoussubsystem, "second", Vector("official-site"))
      InvocationFixture.installResolver(ambiguoussubsystem, Vector(ambiguousfirst, ambiguoussecond))
      val unhealthysubsystem = TestComponentFactory.admittedEmptySubsystem("spi_failure_unhealthy")
      val unhealthy = InvocationFixture.addProvider(unhealthysubsystem, "offline", Vector("official-site"))
      unhealthy.registerHealthContributor(new Component.HealthContributor {
        def name: String = "provider"
        def check(component: Component): Component.HealthCheck = Component.HealthCheck(name, "error", Some("offline"))
      })
      InvocationFixture.installResolver(unhealthysubsystem, Vector(unhealthy))
      val rejectedfixture = InvocationFixture.create("spi_failure_rejected")
      val rejectall = ComponentApiResolver(Vector.empty, new ComponentSelectionPolicy {
        def accept(member: SpiMemberMetadata, selector: ComponentSelector): Consequence[Boolean] =
          Consequence.success(false)
      })
      rejectedfixture.subsystem.withComponentApiResolver(rejectedfixture.subsystem.componentApiResolver.merge(rejectall))

      When("each invalid selection is resolved")
      val unavailable = unavailablefixture.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(instance = Some("missing"))
      )
      val ambiguous = ambiguoussubsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider"))
      )
      val incompatible = unavailablefixture.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(),
        Some(SpiSocketRef("consumer", "catalog", "other-contract"))
      )
      val unhealthyresult = unhealthysubsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider"))
      )
      val rejected = rejectedfixture.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider"))
      )

      Then("each failure keeps a deterministic semantic reason")
      _failure(unavailable) should include ("provider not found")
      _failure(ambiguous) should include ("ambiguous component API providers")
      _failure(incompatible) should include ("socket contract does not match")
      _failure(unhealthyresult) should include ("providers are unhealthy")
      _failure(rejected) should include ("rejected by policy")
    }
  }

  "SpiInvoker canonical dispatch" should {
    "capture the provider operation through the canonical operation chokepoint" in {
      Given("an undeclared provider operation with a provider-owned deterministic evaluation sink")
      val fixture = InvocationFixture.create("spi_invoker_evaluation_capture")
      given ExecutionContext = ExecutionContext.create()

      When("the operation is invoked through the generic SPI route")
      val result = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("echo", Some("api")),
        Record.dataAuto("message" -> "captured"),
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      )

      Then("the business result and exactly one provider-scoped automatic attempt are retained")
      result.toOption.flatMap(_.getString("message")) shouldBe Some("captured")
      fixture.evaluationsink.facts.map(_.factKind.token) shouldBe
        Vector("operation-start", "operation-terminal")
      fixture.evaluationsink.facts.map(_.id).distinct should have size 2
      fixture.evaluationsink.facts.collect {
        case fact: OperationEvaluationStartFact => fact.correlation.operation.component.print
        case fact: OperationEvaluationTerminalFact => fact.correlation.operation.component.print
      } should contain only OperationEvaluationName.parseC(fixture.provider.componentId.name).toOption.get.print
    }

    "produce the same business result as ordinary subsystem dispatch" in {
      Given("one provider operation and one repeated-field request")
      val fixture = InvocationFixture.create()
      given ExecutionContext = ExecutionContext.create()
      val nested = Record.dataAuto(
        "name" -> "profile",
        "selectors" -> Vector("article", ".event")
      )
      val record = Record.create(Vector(
        "tag" -> "first",
        "tag" -> "second",
        "rule" -> nested
      ))
      val direct = Request.of(
        component = "test_provider",
        service = "api",
        operation = "echo",
        properties = record.fields.map(field => Property(field.key, field.value.single, None)).toList
      )

      When("the operation is called through direct and generic routes")
      val directresult = fixture.subsystem.executeOperationResponse(direct).flatMap(SpiOperationResponseCodec.toRecord)
      val genericresult = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("echo", Some("api")),
        record,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      )

      Then("both routes preserve the same Record result")
      genericresult shouldBe directresult
      genericresult.toOption.flatMap(_.getRecord("rule")) shouldBe Some(nested)
    }

    "return structured request operation and selection failures" in {
      Given("a provider with invalid, failing, missing, and ambiguous operation paths")
      val fixture = InvocationFixture.create()
      given ExecutionContext = ExecutionContext.create()
      val binding = fixture.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      ).toOption.get

      When("each invalid path is invoked")
      val invalidrequest = fixture.subsystem.spiInvoker.invoke(binding, SpiOperationSelector("invalid", Some("api")), Record.empty)
      val operationfailure = fixture.subsystem.spiInvoker.invoke(binding, SpiOperationSelector("fail", Some("api")), Record.empty)
      val missing = fixture.subsystem.spiInvoker.invoke(binding, SpiOperationSelector("missing"), Record.empty)
      val ambiguous = fixture.subsystem.spiInvoker.invoke(binding, SpiOperationSelector("duplicate"), Record.empty)
      val mismatchedsocket = fixture.subsystem.componentApiResolver.resolveBinding(
        InvocationContract.contract,
        ComponentSelector(component = Some("test_provider")),
        Some(SpiSocketRef("consumer", "catalog", "other-contract"))
      )

      Then("each path fails deterministically without a fallback operation")
      _failure(invalidrequest) should include ("invalid invocation request")
      _failure(operationfailure) should include ("planned invocation failure")
      _failure(missing) should include ("not exposed by contract")
      _failure(ambiguous) should include ("ambiguous component API operation")
      _failure(mismatchedsocket) should include ("socket contract does not match")
    }

    "preserve operation authorization" in {
      Given("a provider operation denied by the subsystem authorization descriptor")
      val fixture = InvocationFixture.create()
      fixture.subsystem.withDescriptor(GenericSubsystemDescriptor(
        path = Path.of("<spi-invoker-authorization>"),
        subsystemName = "spi-invoker-authorization",
        operationAuthorization = Map(
          s"${fixture.provider.componentId.name}.api.echo" -> OperationAuthorizationRule(deny = true)
        )
      ))
      given ExecutionContext = ExecutionContext.create()

      When("the operation is invoked through SPI")
      val result = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("echo", Some("api")),
        Record.empty,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      )

      Then("the canonical authorization failure is returned")
      result shouldBe a[Consequence.Failure[_]]
      fixture.evaluationsink.facts shouldBe empty
    }

    "preserve managed command job UnitOfWork and event semantics" in {
      Given("a component API command that runs as a synchronous managed job and stages an event")
      val fixture = InvocationFixture.create("spi_invoker_command")
      given ExecutionContext = ExecutionContext.create()

      When("the command is invoked through the generic SPI route")
      val result = fixture.subsystem.spiInvoker.invoke(
        InvocationContract.contract,
        SpiOperationSelector("command", Some("api")),
        Record.empty,
        ComponentSelector(component = Some("test_provider"), instance = Some("primary"))
      )

      Then("the command result returns after job execution and UnitOfWork event commit")
      result.toOption.get.getString("command") shouldBe Some("ok")
      fixture.provider.jobEngine.listJobs(limit = 20, persistentOnly = false) should not be empty
      fixture.provider.eventWasStaged shouldBe true
      fixture.provider.unitOfWorkCommitted shouldBe true
    }
  }

  "SpiOperationResponseCodec" should {
    "convert supported semantic responses and reject opaque responses" in {
      Given("record scalar void JSON YAML and opaque operation responses")

      When("each response is converted to the generic Record boundary")
      val record = SpiOperationResponseCodec.toRecord(OperationResponse.RecordResponse(Record.dataAuto("name" -> "record")))
      val scalar = SpiOperationResponseCodec.toRecord(OperationResponse.Scalar("scalar"))
      val empty = SpiOperationResponseCodec.toRecord(OperationResponse.Void())
      val json = SpiOperationResponseCodec.toRecord(OperationResponse.Json(Json.obj("name" -> Json.fromString("json"))))
      val yaml = SpiOperationResponseCodec.toRecord(OperationResponse.Yaml("name: yaml"))
      val opaque = SpiOperationResponseCodec.toRecord(OperationResponse.Opaque(new Object()))

      Then("structured values have deterministic Record shapes")
      record.toOption.get.getString("name") shouldBe Some("record")
      scalar.toOption.get.getString("value") shouldBe Some("scalar")
      empty.toOption.get shouldBe Record.empty
      json.toOption.get.getString("name") shouldBe Some("json")
      yaml.toOption.get.getString("name") shouldBe Some("yaml")
      opaque shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _failure(result: Consequence[?]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.display
      case _ => fail("expected failure")
    }
}

private trait InvocationApi

private trait BoundInvocationApi {
  def echo(request: Record)(using ExecutionContext): Consequence[Record]
}

private object InvocationContract {
  val name = "invocation-api"
  val contract: SpiContract[InvocationApi] = SpiContract(name, classOf[InvocationApi])
}

private object BoundInvocationContract {
  val name = "bound-invocation-api"
  val contract: SpiContract[BoundInvocationApi] = SpiContract(name, classOf[BoundInvocationApi])
}

private final class InvocationProviderComponent(
  instance: String
) extends Component with SpiProviderComponent with CorpusEvaluationSinkSocket {
  var observedExecutionContext: Option[ExecutionContext] = None
  var typedProviderMaterializationCount: Int = 0
  var boundProviderMaterializationCount: Int = 0
  var eventWasStaged: Boolean = false
  var unitOfWorkCommitted: Boolean = false

  def spiProviders: Vector[SpiProvider[?]] =
    Vector(new SpiProvider[InvocationApi] with SpiOperationProvider {
      def spiOperations(contract: String): Vector[SpiOperationSelector] =
        if (contract == InvocationContract.name)
          Vector(
            SpiOperationSelector("echo", Some("api")),
            SpiOperationSelector("identity", Some("api")),
            SpiOperationSelector("fail", Some("api")),
            SpiOperationSelector("invalid", Some("api")),
            SpiOperationSelector("command", Some("api")),
            SpiOperationSelector("duplicate", Some("api")),
            SpiOperationSelector("duplicate", Some("secondary"))
          )
        else
          Vector.empty

      def supports(
        contract: SpiContract[InvocationApi],
        selection: SpiSelection
      )(using ExecutionContext): Boolean =
        contract.name == InvocationContract.name && contract.runtimeClass == classOf[InvocationApi]

      def provide(
        contract: SpiContract[InvocationApi],
        selection: SpiSelection
      )(using ExecutionContext): Consequence[InvocationApi] = {
        typedProviderMaterializationCount += 1
        Consequence.success(new InvocationApi {})
      }
    })

  override def componentApiProviders: Vector[SpiProvider[?]] =
    Vector(new SpiBoundProvider[BoundInvocationApi] with SpiOperationProvider {
      def spiOperations(contract: String): Vector[SpiOperationSelector] =
        if (contract == BoundInvocationContract.name)
          Vector(SpiOperationSelector("echo", Some("api")))
        else
          Vector.empty

      def supports(
        contract: SpiContract[BoundInvocationApi],
        selection: SpiSelection
      )(using ExecutionContext): Boolean =
        contract.name == BoundInvocationContract.name && contract.runtimeClass == classOf[BoundInvocationApi]

      def provideBound(
        binding: ResolvedSpiBinding
      )(using ExecutionContext): Consequence[BoundInvocationApi] = {
        boundProviderMaterializationCount += 1
        Consequence.success(new BoundInvocationApi {
          def echo(request: Record)(using ExecutionContext): Consequence[Record] =
            binding.invoke(SpiOperationSelector("echo", Some("api")), request)
        })
      }
    })

  def observe(context: ExecutionContext): Unit =
    observedExecutionContext = Some(context)

  def instanceName: String = instance

  def observeTransactionalExecution(context: ExecutionContext): Unit = {
    val unitofwork = context.runtime.unitOfWork
    unitofwork.stageEvent(InvocationEvent("spi-invoker-event"))
    eventWasStaged = unitofwork.pendingEvents.nonEmpty
    unitofwork.stagePostCommit {
      unitOfWorkCommitted = true
    }
  }
}

private object InvocationFixture {
  final case class Fixture(
    subsystem: Subsystem,
    provider: InvocationProviderComponent,
    socket: InvocationSocket,
    evaluationsink: DeterministicCorpusEvaluationSink,
    materializationsAfterAssembly: Int
  )

  def create(name: String = "spi_invoker"): Fixture = {
    val subsystem = TestComponentFactory.admittedEmptySubsystem(name)
    val provider = addProvider(subsystem, "primary", Vector("official-site"))
    val evaluationsink = DeterministicCorpusEvaluationSink
      .createC("test_provider", "evaluation_capture")
      .toOption
      .get
    provider.installSpi(evaluationsink)
    val (consumer, socket) = addConsumer(subsystem, "consumer", "catalog")
    val binding = SpiRuntimeBinding(
      SpiSocketSelector(
        component = Some("consumer"),
        contract = InvocationContract.name,
        name = Some("catalog")
      ),
      SpiProviderSelector(component = Some("test_provider"), instance = Some("primary"))
    )
    installResolver(subsystem, Vector(provider, consumer), Vector(binding))
    Fixture(
      subsystem,
      provider,
      socket,
      evaluationsink,
      provider.typedProviderMaterializationCount
    )
  }

  def addProvider(
    subsystem: Subsystem,
    instance: String,
    purposes: Vector[String]
  ): InvocationProviderComponent = {
    val provider = new InvocationProviderComponent(instance)
    val protocol = _protocol(provider)
    val componentid = ComponentId("org.goldenport.cncf.test.TestProvider")
    val metadata = ComponentInstanceMetadata(
      "test_provider",
      instance,
      purposes = purposes,
      componentId = Some(componentid)
    )
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = metadata.instanceId,
      protocol = protocol
    )
    provider.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = Some(metadata)
    ))
    subsystem.add(provider)
    provider
  }

  def addConsumer(
    subsystem: Subsystem,
    name: String,
    socketname: String
  ): (InvocationConsumerComponent, InvocationSocket) = {
    val socket = new InvocationSocket(socketname)
    val consumer = new InvocationConsumerComponent(socket)
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    val metadata = ComponentInstanceMetadata(name, "default", componentId = Some(componentid))
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = metadata.instanceId,
      protocol = Protocol.empty
    )
    consumer.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = Some(metadata)
    ))
    subsystem.add(consumer)
    consumer -> socket
  }

  def addBoundConsumer(
    subsystem: Subsystem,
    name: String,
    socketname: String
  ): (BoundInvocationConsumerComponent, BoundInvocationSocket) = {
    val socket = new BoundInvocationSocket(socketname)
    val consumer = new BoundInvocationConsumerComponent(socket)
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    val metadata = ComponentInstanceMetadata(name, "default", componentId = Some(componentid))
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = metadata.instanceId,
      protocol = Protocol.empty
    )
    consumer.initialize(ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin,
      instanceMetadata = Some(metadata)
    ))
    subsystem.add(consumer)
    consumer -> socket
  }

  def installResolver(
    subsystem: Subsystem,
    components: Vector[Component],
    bindings: Vector[SpiRuntimeBinding] = Vector.empty
  ): Unit = {
    given ExecutionContext = ExecutionContext.create()
    val resolution = SpiResolver.resolveAssembly(components, bindings).toOption.get
    subsystem.withComponentApiResolver(resolution.componentApiResolver)
  }

  private def _protocol(provider: InvocationProviderComponent): Protocol = {
    val api = spec.ServiceDefinition(
      name = "api",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(Vector(
        InvocationOperation("echo", provider, InvocationBehavior.Echo),
        InvocationOperation("identity", provider, InvocationBehavior.Identity),
        InvocationOperation("fail", provider, InvocationBehavior.Fail),
        InvocationOperation("command", provider, InvocationBehavior.Command),
        InvalidInvocationOperation(),
        InvocationOperation("duplicate", provider, InvocationBehavior.Identity)
      )))
    )
    val secondary = spec.ServiceDefinition(
      name = "secondary",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
        InvocationOperation("duplicate", provider, InvocationBehavior.Identity)
      ))
    )
    Protocol(services = spec.ServiceDefinitionGroup(Vector(api, secondary)))
  }
}

private final class InvocationSocket(
  name: String
) extends SpiSocket[InvocationApi] {
  private var _service: Option[InvocationApi] = None

  def spiContract: SpiContract[InvocationApi] = InvocationContract.contract
  override def spiSocketName: String = name
  override def isSpiInstalled: Boolean = _service.nonEmpty
  def installSpi(spi: InvocationApi): Unit = _service = Some(spi)
}

private final class InvocationConsumerComponent(
  socket: InvocationSocket
) extends Component {
  withPort(Component.Port.input(socket))
}

private final class BoundInvocationSocket(
  name: String
) extends SpiSocket[BoundInvocationApi] {
  private var _service: Option[BoundInvocationApi] = None

  def spiContract: SpiContract[BoundInvocationApi] = BoundInvocationContract.contract
  override def spiSocketName: String = name
  override def isSpiInstalled: Boolean = _service.nonEmpty
  def installSpi(spi: BoundInvocationApi): Unit = _service = Some(spi)
  def service: BoundInvocationApi =
    _service.getOrElse(throw new IllegalStateException("Bound invocation SPI is not installed"))
}

private final class BoundInvocationConsumerComponent(
  socket: BoundInvocationSocket
) extends Component {
  withPort(Component.Port.input(socket))
}

private enum InvocationBehavior {
  case Echo
  case Identity
  case Fail
  case Command
}

private final case class InvocationOperation(
  operationName: String,
  component: InvocationProviderComponent,
  behavior: InvocationBehavior
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = operationName,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    behavior match {
      case InvocationBehavior.Command =>
        Consequence.success(InvocationCommandAction(req, component))
      case _ =>
        Consequence.success(InvocationAction(req, component, behavior))
    }
}

private final case class InvalidInvocationOperation() extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "invalid",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.argumentInvalid("invalid invocation request")
}

private final case class InvocationAction(
  request: Request,
  providerComponent: InvocationProviderComponent,
  behavior: InvocationBehavior
) extends QueryAction {
  override def createCall(core: ActionCall.Core): ActionCall =
    InvocationActionCall(core, providerComponent, behavior)
}

private final case class InvocationActionCall(
  core: ActionCall.Core,
  providerComponent: InvocationProviderComponent,
  behavior: InvocationBehavior
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    providerComponent.observe(core.executionContext)
    behavior match {
      case InvocationBehavior.Echo =>
        Consequence.success(OperationResponse.RecordResponse(core.action.request.toRecord))
      case InvocationBehavior.Identity =>
        Consequence.success(OperationResponse.RecordResponse(Record.dataAuto("instance" -> providerComponent.instanceName)))
      case InvocationBehavior.Fail =>
        Consequence.operationInvalid("planned invocation failure")
      case InvocationBehavior.Command =>
        Consequence.operationInvalid("command behavior must use InvocationCommandAction")
    }
  }
}

private final case class InvocationCommandAction(
  request: Request,
  providerComponent: InvocationProviderComponent
) extends CommandAction {
  override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.JobSync

  override def createCall(core: ActionCall.Core): ActionCall =
    InvocationCommandActionCall(core, providerComponent)
}

private final case class InvocationCommandActionCall(
  core: ActionCall.Core,
  providerComponent: InvocationProviderComponent
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    providerComponent.observe(core.executionContext)
    providerComponent.observeTransactionalExecution(core.executionContext)
    Consequence.success(OperationResponse.RecordResponse(Record.dataAuto("command" -> "ok")))
  }
}

private final case class InvocationEvent(name: String) extends DomainEvent
