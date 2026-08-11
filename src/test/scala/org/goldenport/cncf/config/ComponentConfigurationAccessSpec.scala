package org.goldenport.cncf.config

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{
  Component,
  ComponentCreate,
  ComponentDescriptor,
  ComponentId,
  ComponentOrigin
}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentConfigurationAccessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e5_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E5, rules:R1,R3,R4,R10, phase:36")
  private val _cip08_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E13, rules:R3,R3a,R10, phase:47, slice:CIP-08")
  private val _configuration_key = ComponentConfigurationKey.requiredString("provider.mode")

  "Declared component configuration" should {
    "E5 resolve a public declared key with deterministic scope precedence" must _e5_metadata {
      "when component subsystem and runtime sources define the same key" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R3, R4, R10; Example: E5; generated component subsystem and runtime configuration values")
        val property = Prop.forAll(Gen.alphaStr.suchThat(_.nonEmpty)) { runtimevalue =>
          val access = ComponentConfigurationAccess(
            ComponentConfigurationSources(
              component = _configuration("provider.mode" -> "component"),
              subsystem = _configuration("provider.mode" -> "subsystem"),
              runtime = _configuration("provider.mode" -> runtimevalue)
            )
          )
          val result = access.resolve(_configuration_key)
          result.toOption.contains(
            ComponentConfigurationResolution(
              Some("component"),
              ComponentConfigurationProvenance.Component
            )
          )
        }

        When("the deterministic precedence property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every generated runtime value is shadowed by component configuration")
        checked.passed shouldBe true
      }
    }

    "E5 retain optional, malformed, and confidential outcomes as structured failures" must _e5_metadata {
      "when declared keys have unavailable or disallowed values" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R3, R4, R10; Example: E5; an empty component configuration source and malformed/confidential declarations")
        val emptyaccess = ComponentConfigurationAccess(ComponentConfigurationSources())
        val optionalkey = ComponentConfigurationKey.optionalString("provider.optional")
        val malformedkey = ComponentConfigurationKey.requiredInt("provider.limit")
        val confidentialkey = ComponentConfigurationKey.confidentialRequired("provider.token")
        val malformedaccess = ComponentConfigurationAccess(
          ComponentConfigurationSources(component = _configuration("provider.limit" -> "not-an-int"))
        )
        val confidentialaccess = ComponentConfigurationAccess(
          ComponentConfigurationSources(component = _configuration("provider.token" -> "private-token"))
        )

        When("the component resolves required optional malformed and confidential declarations")
        val required = emptyaccess.resolve(_configuration_key)
        val optional = emptyaccess.resolve(optionalkey)
        val malformed = malformedaccess.resolve(malformedkey)
        val confidential = confidentialaccess.resolve(confidentialkey)

        Then("absence policy and type/policy failures remain structured without exposing a confidential value")
        required.isSuccess shouldBe false
        optional.toOption shouldBe Some(
          ComponentConfigurationResolution(None, ComponentConfigurationProvenance.Absent)
        )
        malformed.isSuccess shouldBe false
        confidential.isSuccess shouldBe false
        confidential.toOption shouldBe None
        val confidentialdisplay = confidential match {
          case Consequence.Failure(value) => value.display
          case _ => fail("expected confidential declared configuration denial")
        }
        confidentialdisplay should not include "private-token"
      }
    }

    "E5 use subsystem and runtime values only when a component value is absent" must _e5_metadata {
      "when declared configuration falls through its runtime-owned sources" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3, R4, R10; Example: E5; component-absent configuration sources")
        val subsystemaccess = ComponentConfigurationAccess(
          ComponentConfigurationSources(
            subsystem = _configuration("provider.mode" -> "subsystem"),
            runtime = _configuration("provider.mode" -> "runtime")
          )
        )
        val runtimeaccess = ComponentConfigurationAccess(
          ComponentConfigurationSources(runtime = _configuration("provider.mode" -> "runtime"))
        )

        When("the declared key resolves without a component value")
        val subsystemresult = subsystemaccess.resolve(_configuration_key)
        val runtimeresult = runtimeaccess.resolve(_configuration_key)

        Then("the result reports the selected lower-precedence source without exposing raw source internals")
        subsystemresult.toOption shouldBe Some(
          ComponentConfigurationResolution(
            Some("subsystem"),
            ComponentConfigurationProvenance.Subsystem
          )
        )
        runtimeresult.toOption shouldBe Some(
          ComponentConfigurationResolution(
            Some("runtime"),
            ComponentConfigurationProvenance.Runtime
          )
        )
      }
    }

    "E5 resolve declared configuration through an ActionCall internal DSL" must _e5_metadata {
      "when request properties attempt to override component configuration" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R3, R4, R10; Example: E5; a component configuration value, a runtime fallback, and a conflicting action property")
        val runtimeconfiguration = _configuration("provider.mode" -> "runtime")
        val context = _runtime_context(runtimeconfiguration)
        val component = new Component() {}
        component.withApplicationConfig(
          Component.ApplicationConfig(config = Some(_configuration("provider.mode" -> "component")))
        )
        val action = new CommandAction {
          val request = Request.ofOperation("declared-component-configuration").copy(
            properties = List(Property("provider.mode", "request", None))
          )

          def createCall(core: ActionCall.Core): ActionCall =
            ConfigurationActionCall(core, _configuration_key)
        }
        val call = ConfigurationActionCall(
          ActionCall.Core(action, context, Some(component), None),
          _configuration_key
        )

        When("the ActionCall executes its protected declared-configuration access")
        val result = call.execute()

        Then("the component value is returned and request properties cannot override it")
        result.isSuccess shouldBe true
        call.resolution shouldBe Some(
          ComponentConfigurationResolution(
            Some("component"),
            ComponentConfigurationProvenance.Component
          )
        )
      }
    }

    "E13 remain separate from immutable initialization parameters" must _cip08_metadata {
      "when both lifecycles declare the same logical key" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R3,R3a,R10; Example: E13; a factory-initialized component with distinct same-name initialization-time and operation-time declarations")
        val values = Gen.zip(
          Gen.alphaStr.suchThat(_.nonEmpty),
          Gen.alphaStr.suchThat(_.nonEmpty)
        ).suchThat { case (initializationvalue, operationvalue) =>
          initializationvalue != operationvalue
        }
        val property = Prop.forAll(values) { case (initializationvalue, operationvalue) =>
          val subsystem = TestComponentFactory.emptySubsystem("configuration-coexistence")
          val descriptor = ComponentDescriptor(
            componentName = Some("org.goldenport.cncf.test.ConfigurationCoexistenceProbe"),
            config = Map("provider.mode" -> initializationvalue)
          )
          InitializationCoexistenceProbeFactory.createPrimaryC(
            ComponentCreate(
              subsystem,
              ComponentOrigin.Repository("phase-47"),
              Vector(descriptor)
            )
          ).toOption.exists { component =>
            val context = _runtime_context(Configuration.empty)
            val action = ConfigurationAction(_configuration_key)
            val snapshot = component.initializationParameters
            val before = snapshot
              .resolve(InitializationCoexistenceProbeFactory.modeKey)
              .toOption

            component.withApplicationConfig(
              Component.ApplicationConfig(
                config = Some(_configuration("provider.mode" -> operationvalue))
              )
            )
            val operationcall = ConfigurationActionCall(
              ActionCall.Core(action, context, Some(component), None),
              _configuration_key
            )
            val operation = operationcall.execute()

            component.withApplicationConfig(
              Component.ApplicationConfig(config = Some(Configuration.empty))
            )
            val missingcall = ConfigurationActionCall(
              ActionCall.Core(action, context, Some(component), None),
              _configuration_key
            )
            val missing = missingcall.execute()
            val after = component.initializationParameters
              .resolve(InitializationCoexistenceProbeFactory.modeKey)
              .toOption

            before.contains(
              ComponentParameterResolution(
                Some(initializationvalue),
                ComponentParameterProvenance.PackagedDefault
              )
            ) &&
            operation.isSuccess &&
            operationcall.resolution.contains(
              ComponentConfigurationResolution(
                Some(operationvalue),
                ComponentConfigurationProvenance.Component
              )
            ) &&
            missing.isFaillure &&
            missingcall.resolution.isEmpty &&
            (component.initializationParameters eq snapshot) &&
            after == before
          }
        }

        When("the protected ActionCall DSL resolves with and without an operation-time value")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("operation access never falls back to or mutates the immutable initialization snapshot")
        checked.passed shouldBe true
      }
    }
  }

  private def _configuration(entries: (String, String)*): Configuration =
    Configuration(entries.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap)

  private def _runtime_context(runtimeconfiguration: Configuration): ExecutionContext = {
    val resolvedconfiguration = ResolvedConfiguration(runtimeconfiguration, ConfigurationTrace.empty)
    val runtimeconfig = RuntimeConfig.from(resolvedconfiguration)
    val base = ExecutionContext.create()
    val global = GlobalRuntimeContext.create(
      "component-configuration-access-spec",
      runtimeconfig,
      resolvedconfiguration,
      base.observability,
      AliasResolver.empty
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "component-configuration-access-spec",
        parent = Some(global),
        observabilityContext = base.observability
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          Consequence.serviceUnavailable("not used by component configuration ActionCall spec")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "component-configuration-access-spec",
      operationMode = runtimeconfig.operationMode
    )
    context
  }

  private final case class ConfigurationActionCall(
    core: ActionCall.Core,
    key: ComponentConfigurationKey[String]
  ) extends ProcedureActionCall {
    private var _resolution: Option[ComponentConfigurationResolution[String]] = None

    def resolution: Option[ComponentConfigurationResolution[String]] =
      _resolution

    override def execute(): Consequence[OperationResponse] =
      component_configuration(key).map { value =>
        _resolution = Some(value)
        OperationResponse.void
      }
  }

  private final case class ConfigurationAction(
    key: ComponentConfigurationKey[String]
  ) extends CommandAction {
    val request: Request = Request.ofOperation("declared-component-configuration")

    def createCall(core: ActionCall.Core): ActionCall =
      ConfigurationActionCall(core, key)
  }

  private object InitializationCoexistenceProbeFactory extends Component.Factory {
    val modeKey: ComponentParameterKey[String] =
      ComponentParameterKey.requiredString("provider.mode")

    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector(modeKey)

    protected def create_Component(params: ComponentCreate): Component =
      new Component {}

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core =
      spec_create(
        "org.goldenport.cncf.test.ConfigurationCoexistenceProbe",
        ComponentId("org.goldenport.cncf.test.ConfigurationCoexistenceProbe"),
        Vector.empty[spec.ServiceDefinition]
      )
  }
}
