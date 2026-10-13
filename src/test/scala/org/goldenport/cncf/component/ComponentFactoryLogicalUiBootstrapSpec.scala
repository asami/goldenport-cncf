package org.goldenport.cncf.component

import logicalui.{DisplayServiceReadFixture, ExpectedGeneratedLogicalUiFixture, TypedDisplayRuntimeFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.display.{DisplayBindingProvider, DisplayRuntime, DisplayService, DisplayServiceBindingProvider, GeneratedLogicalUiAbi, GeneratedLogicalUiMetadataProvider}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{GeneratedWorkflowAbi, GeneratedWorkflowAbiAdmissionException, GeneratedWorkflowMetadataProvider}
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  5, 2026
 */
final class ComponentFactoryLogicalUiBootstrapSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*

  private val _definitions = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _list = _definitions.head.screen.asInstanceOf[ListDefinition]

  "Logical UI component bootstrap" should {
    "select one structural metadata provider" which {
      "E1 preserve components without a provider" must afterWord(
        "in spec:logical-ui-generated-abi, example:E1, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain the same component and default services without Display capability" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E1: neither component nor factory supplies Logical UI metadata")
          _with_subsystem { subsystem =>
            val component = _initialized_component(subsystem, new Component() {})
            val services = component.services
            val protocol = component.protocol
            val names = protocol.services.services.map(_.name)
            When("normal ComponentFactory bootstrap admits the legacy component")
            val result = new ComponentFactory().bootstrapC(component)
            Then("bootstrap returns the same component with no admitted UI and unchanged default services")
            _expect_success(result, component)
            component._admitted_logical_ui_definitions shouldBe None
            component.collectionsBootstrapped shouldBe true
            (component.services eq services) shouldBe true
            (component.protocol eq protocol) shouldBe true
            component.protocol.services.services.map(_.name) shouldBe names
            names should not contain "display"
          }
        }
      }

      "E2 admit component metadata" must afterWord(
        "in spec:logical-ui-generated-abi, example:E2, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain the complete expected-generated fixture and its object identities after one read" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E2: the accepted external expected-generated fixture is component-owned")
          _with_subsystem { subsystem =>
            var reads = 0
            val component = _initialized_component(subsystem, _provider_component(() => {
              reads += 1
              _definitions
            }))
            When("normal bootstrap admits the selected component provider")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the same component retains every original definition in order after exactly one selected read")
            _expect_success(result, component)
            _expect_admitted(component, _definitions)
            reads shouldBe 1
            component.collectionsBootstrapped shouldBe true
          }
        }
      }

      "E3 admit factory-only metadata" must afterWord(
        "in spec:logical-ui-generated-abi, example:E3, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain the complete factory fixture after exactly one provider read" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E3: only the initialized component factory supplies the accepted fixture")
          _with_subsystem { subsystem =>
            var reads = 0
            val factory = new LogicalUiFactory(() => {
              reads += 1
              _definitions
            })
            val component = _initialized_component(subsystem, new Component() {}, Some(factory))
            When("normal bootstrap selects factory Logical UI metadata")
            val result = new ComponentFactory().bootstrapC(component)
            Then("the same component retains the factory vector and all its objects after one read")
            _expect_success(result, component)
            _expect_admitted(component, _definitions)
            reads shouldBe 1
            component.collectionsBootstrapped shouldBe true
          }
        }
      }

      "E4 prefer the component over a different valid factory vector" must afterWord(
        "in spec:logical-ui-generated-abi, example:E4, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain only component definitions and never evaluate the shadowed metadata getter" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E4: component and factory offer different valid definition revisions")
          _with_subsystem { subsystem =>
            var selectedreads = 0
            var fallbackreads = 0
            val fallback = _revised_definitions("factory-revision")
            val component = _initialized_component(subsystem, _provider_component(() => {
              selectedreads += 1
              _definitions
            }), Some(new LogicalUiFactory(() => {
              fallbackreads += 1
              fallback
            })))
            When("normal bootstrap chooses its single Logical UI provider")
            val result = new ComponentFactory().bootstrapC(component)
            Then("component metadata wins without concatenation or a shadowed factory read")
            _expect_success(result, component)
            _expect_admitted(component, _definitions)
            selectedreads shouldBe 1
            fallbackreads shouldBe 0
          }
        }
      }

      "E5 select a component provider without accessing its factory" must afterWord(
        "in spec:logical-ui-generated-abi, example:E5, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "admit through the pure selector even when the factory accessor would throw" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E5: a component provider whose factory accessor counts and throws")
          _with_subsystem { subsystem =>
            var factoryreads = 0
            var selectedreads = 0
            val component = _initialized_component(subsystem, new Component() with GeneratedLogicalUiMetadataProvider {
              override def factory: Option[Component.Factory] = {
                factoryreads += 1
                throw new IllegalStateException("shadowed factory accessor")
              }
              override def generatedLogicalUiDefinitions: Vector[Definition] = {
                selectedreads += 1
                _definitions
              }
            })
            When("the Logical UI selector admits the component provider in isolation")
            val result = new ComponentFactory()._admit_generated_logical_ui_metadata_c(component)
            Then("selection retains the exact vector without accessing the factory or publishing component state")
            val admitted = result.toOption.flatten.getOrElse(fail("E5 selected metadata must admit"))
            _expect_definitions(admitted, _definitions)
            selectedreads shouldBe 1
            factoryreads shouldBe 0
            _expect_unpublished(component)
          }
        }
      }
    }

    "reject the entire selected vector" which {
      "E6 fail closed for empty null and malformed providers" must afterWord(
        "in spec:logical-ui-generated-abi, example:E6, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "preserve ABI diagnostics for either provider location without rescuing invalid component metadata" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E6: selected empty, null and malformed vectors at component and factory locations")
          val inputs: Vector[(String, Vector[Definition])] = Vector(
            "empty" -> Vector.empty,
            "null" -> null,
            "malformed" -> _definitions.updated(0, _definitions.head.copy(screenId = " malformed "))
          )
          _with_subsystem { subsystem =>
            for ((label, input) <- inputs; componentowned <- Vector(true, false)) {
              Given(s"docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E6: $label vector, component provider = $componentowned, valid shadowed factory where present")
              var selectedreads = 0
              var fallbackreads = 0
              val selected = () => { selectedreads += 1; input }
              val component = if (componentowned) {
                _initialized_component(subsystem, _provider_component(selected), Some(new LogicalUiFactory(() => {
                  fallbackreads += 1
                  _definitions
                })))
              } else {
                _initialized_component(subsystem, new Component() {}, Some(new LogicalUiFactory(selected)))
              }
              When("normal bootstrap and the direct ABI gate interpret the same invalid selected vector")
              val expected = admitC(input)
              val result = new ComponentFactory().bootstrapC(component)
              Then("the exact ABI diagnostic is retained and no prefix, fallback or bootstrapped state is published")
              withClue(s"E6 $label component provider = $componentowned: ") {
                _expect_abi_failure(result, expected)
                _expect_unpublished(component)
                selectedreads shouldBe 1
                fallbackreads shouldBe 0
              }
            }
          }
        }
      }

      "E7 retain selected getter exceptions as structured bootstrap failure" must afterWord(
        "in spec:logical-ui-generated-abi, example:E7, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "keep the exact cause from either location without reading a valid fallback" in {
          _with_subsystem { subsystem =>
            Vector(true, false).foreach { componentowned =>
              Given(s"docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E7: a throwing selected getter, component provider = $componentowned")
              val exception = new IllegalStateException("selected Logical UI getter failure")
              var selectedreads = 0
              var fallbackreads = 0
              val selected = () => { selectedreads += 1; throw exception }
              val component = if (componentowned) {
                _initialized_component(subsystem, _provider_component(selected), Some(new LogicalUiFactory(() => {
                  fallbackreads += 1
                  _definitions
                })))
              } else {
                _initialized_component(subsystem, new Component() {}, Some(new LogicalUiFactory(selected)))
              }
              When("normal bootstrap reads the selected metadata getter")
              val result = new ComponentFactory().bootstrapC(component)
              Then("componentInvalid carries the original exception and leaves UI state and bootstrap flag empty")
              val failure = _failure(result)
              failure.conclusion.observation.taxonomy shouldBe Taxonomy.componentInvalid
              failure.conclusion.observation.cause.getException.exists(_ eq exception) shouldBe true
              selectedreads shouldBe 1
              fallbackreads shouldBe 0
              _expect_unpublished(component)
            }
          }
        }
      }

      "E8 reject invalid siblings duplicates and broken Detail targets atomically" must afterWord(
        "in spec:logical-ui-generated-abi, example:E8, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain deterministic direct ABI code and path without exposing a valid prefix" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E8: a valid prefix followed by a malformed sibling, duplicate screen ID or broken List-to-Detail target")
          val inputs = Vector(
            "invalid sibling" -> (_definitions :+ _definitions(1).copy(screenId = "third", definitionRevision = "")),
            "duplicate screen" -> (_definitions :+ _definitions(1)),
            "broken target" -> _definitions.updated(0, _definitions.head.copy(screen = _list.copy(detailTarget = DetailTarget("absent-detail"))))
          )
          _with_subsystem { subsystem =>
            inputs.foreach { case (label, input) =>
              Given(s"docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E8: $label with valid sibling definitions")
              val component = _initialized_component(subsystem, _provider_component(() => input))
              When("normal bootstrap and repeated direct admission interpret the complete selected vector")
              val expected = admitC(input)
              val repeated = admitC(input)
              val result = new ComponentFactory().bootstrapC(component)
              Then("the complete vector fails with the same deterministic diagnostic and exposes no prefix or sibling")
              withClue(s"E8 $label: ") {
                _expect_abi_failure(result, expected)
                _diagnostic(repeated) shouldBe _diagnostic(expected)
                _expect_unpublished(component)
              }
            }
          }
        }
      }
    }

    "publish only successful per-instance bootstrap state" which {
      "E9 isolate identical screen IDs across component instances" must afterWord(
        "in spec:logical-ui-generated-abi, example:E9, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "retain independent revisions even when another instance fails" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E9: two independent providers reuse screen IDs with different valid revisions and a third is invalid")
          _with_subsystem { subsystem =>
            val firstinput = _revised_definitions("instance-one")
            val secondinput = _revised_definitions("instance-two")
            val first = _initialized_component(subsystem, _provider_component(() => firstinput))
            val second = _initialized_component(subsystem, _provider_component(() => secondinput))
            val third = _initialized_component(subsystem, _provider_component(() => Vector.empty))
            val factory = new ComponentFactory()
            When("normal bootstrap admits each successful component independently")
            val firstresult = factory.bootstrapC(first)
            val secondresult = factory.bootstrapC(second)
            Then("both components retain their own complete definitions and distinct admitted wrappers")
            _expect_success(firstresult, first)
            _expect_success(secondresult, second)
            _expect_admitted(first, firstinput)
            _expect_admitted(second, secondinput)
            val firstadmitted = _admitted(first)
            val secondadmitted = _admitted(second)
            (firstadmitted eq secondadmitted) shouldBe false
            firstadmitted.definitions.map(_.screenId) shouldBe secondadmitted.definitions.map(_.screenId)
            When("normal bootstrap rejects the third instance")
            val thirdresult = factory.bootstrapC(third)
            Then("failure leaves the third empty and cannot replace either successful instance's metadata")
            thirdresult shouldBe a[Consequence.Failure[?]]
            _expect_unpublished(third)
            (_admitted(first) eq firstadmitted) shouldBe true
            (_admitted(second) eq secondadmitted) shouldBe true
            _expect_admitted(first, firstinput)
            _expect_admitted(second, secondinput)
          }
        }
      }

      "E10 keep successful bootstrap idempotent" must afterWord(
        "in spec:logical-ui-generated-abi, example:E10, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "return the same admitted wrapper without rereading a changed producer" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E10: a selected provider whose return value can change after bootstrap")
          _with_subsystem { subsystem =>
            var reads = 0
            var supplied = _definitions
            val component = _initialized_component(subsystem, _provider_component(() => {
              reads += 1
              supplied
            }))
            val factory = new ComponentFactory()
            When("normal bootstrap first admits the original producer value")
            val first = factory.bootstrapC(component)
            Then("the original complete vector is retained")
            _expect_success(first, component)
            _expect_admitted(component, _definitions)
            val admitted = _admitted(component)
            val stored = component._admitted_logical_ui_definitions
            Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E10: the already-bootstrapped producer now offers a different revision")
            supplied = _revised_definitions("changed-after-bootstrap")
            When("normal bootstrap is requested again")
            val second = factory.bootstrapC(component)
            Then("the same component, Option, admitted wrapper and definition objects are returned without refresh")
            _expect_success(second, component)
            (component._admitted_logical_ui_definitions eq stored) shouldBe true
            (_admitted(component) eq admitted) shouldBe true
            _expect_admitted(component, _definitions)
            reads shouldBe 1
            component.collectionsBootstrapped shouldBe true
          }
        }
      }

      "E11 defer publication until the existing Workflow gate succeeds" must afterWord(
        "in spec:logical-ui-generated-abi, example:E11, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "leave UI state empty when valid Logical UI metadata precedes invalid Workflow metadata" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E11: valid Logical UI definitions plus selected empty generatedWorkflowDefinitions")
          _with_subsystem { subsystem =>
            val component = _initialized_component(subsystem, new Component() with GeneratedLogicalUiMetadataProvider with GeneratedWorkflowMetadataProvider {
              override def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
              override def generatedWorkflowDefinitions: Vector[GeneratedWorkflowAbi.Definition] = Vector.empty
            })
            val factory = new ComponentFactory()
            When("static Logical UI selection alone interprets the valid vector")
            val selected = factory._admit_generated_logical_ui_metadata_c(component)
            Then("static admission succeeds without publishing component state")
            _expect_definitions(selected.toOption.flatten.getOrElse(fail("E11 static selection must admit")), _definitions)
            _expect_unpublished(component)
            When("normal bootstrap reaches the unchanged Workflow admission gate")
            val expected = GeneratedWorkflowAbi.admitC(Vector.empty)
            val result = factory.bootstrapC(component)
            Then("the existing Workflow diagnostic stops bootstrap and no admitted UI is retained")
            val actualfailure = _failure(result)
            val expectedfailure = _failure(expected)
            actualfailure.conclusion.observation.taxonomy shouldBe expectedfailure.conclusion.observation.taxonomy
            val actualdiagnostic = actualfailure.conclusion.observation.cause.getException.collect {
              case carrier: GeneratedWorkflowAbiAdmissionException => carrier.diagnostic
            }
            val expecteddiagnostic = expectedfailure.conclusion.observation.cause.getException.collect {
              case carrier: GeneratedWorkflowAbiAdmissionException => carrier.diagnostic
            }
            actualdiagnostic shouldBe defined
            actualdiagnostic shouldBe expecteddiagnostic
            _expect_unpublished(component)
          }
        }
      }
    }

    "preserve complete-vector laws" which {
      "E12 preserve arbitrary opaque IDs and both List/Detail orders" must afterWord(
        "in spec:logical-ui-generated-abi, example:E12, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "keep the exact selected vector and identities without reading the different factory vector in at least 100 cases" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E12: generated valid opaque non-ASCII IDs and complete List/Detail order permutations")
          val geninput = for {
            text <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
            suffix <- Gen.oneOf("Ω:内部.Upper", "é/Case punctuation", "日本語:MiXeD", "β.internal")
            detailfirst <- Gen.oneOf(true, false)
          } yield {
            val token = text + ":" + suffix
            val detailid = "Detail:" + token
            val pair = Vector(
              _definitions.head.copy(screenId = "List:" + token, screen = _list.copy(detailTarget = DetailTarget(detailid))),
              _definitions(1).copy(screenId = detailid)
            )
            if (detailfirst) pair.reverse else pair
          }
          _with_subsystem { subsystem =>
            val factory = new ComponentFactory()
            When("the single-provider selector admits each complete generated vector against different factory metadata")
            val property = Prop.forAll(geninput) { input =>
              var selectedreads = 0
              var fallbackreads = 0
              val component = _initialized_component(subsystem, _provider_component(() => {
                selectedreads += 1
                input
              }), Some(new LogicalUiFactory(() => {
                fallbackreads += 1
                _revised_definitions("factory-shadowed")
              })))
              val result = factory._admit_generated_logical_ui_metadata_c(component)
              _preserved(result, input) && selectedreads == 1 && fallbackreads == 0 &&
                component._admitted_logical_ui_definitions.isEmpty && !component.collectionsBootstrapped
            }
            val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
            Then("at least 100 successful, undiscarded cases preserve selected bytes, order and object identities without fallback or publication")
            withClue(s"E12 LUI96-S06/S07: $result: ") {
              result.passed shouldBe true
              result.succeeded should be >= 100
            }
          }
        }
      }

      "E13 reject an invalid definition at every generated position" must afterWord(
        "in spec:logical-ui-generated-abi, example:E13, rules:LUI96-S06,LUI96-S07, phase:96"
      ) {
        "keep deterministic direct ABI diagnostics and no partial admission in at least 100 cases" in {
          Given("docs/spec/logical-ui-generated-abi.md LUI96-S06/S07 E13: one invalid revision inserted at varying positions among unique valid Detail definitions")
          val geninput = for {
            count <- Gen.choose(1, 12)
            position <- Gen.choose(0, count)
            text <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
          } yield {
            val valid = Vector.tabulate(count)(index => _definitions(1).copy(screenId = s"Ω:$text:$index"))
            val invalid = _definitions(1).copy(screenId = s"Ω:$text:invalid", definitionRevision = "")
            (valid.patch(position, Vector(invalid), 0), position)
          }
          _with_subsystem { subsystem =>
            val factory = new ComponentFactory()
            When("the selector and repeated direct ABI admission interpret each complete invalid vector")
            val property = Prop.forAll(geninput) { case (input, position) =>
              val component = _initialized_component(subsystem, _provider_component(() => input))
              val direct = _diagnostic(admitC(input))
              val repeated = _diagnostic(admitC(input))
              val selected = _diagnostic(factory._admit_generated_logical_ui_metadata_c(component))
              direct.exists(diagnostic => diagnostic.code == "LUI96_ID_INVALID" &&
                diagnostic.path == s"definitions[$position].definitionRevision") &&
                selected == direct && repeated == direct &&
                component._admitted_logical_ui_definitions.isEmpty && !component.collectionsBootstrapped
            }
            val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
            Then("at least 100 successful, undiscarded cases fail at the deterministic invalid position without publishing a prefix")
            withClue(s"E13 LUI96-S06/S07: $result: ") {
              result.passed shouldBe true
              result.succeeded should be >= 100
            }
          }
        }
      }
    }
  }

  private final class LogicalUiFactory(read: () => Vector[Definition]) extends Component.Factory with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
    override def generatedLogicalUiDefinitions: Vector[Definition] = read()

    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
      DisplayServiceReadFixture.resourceBindings

    override def displayBindings(component: Component): Vector[DisplayRuntime.Binding] =
      TypedDisplayRuntimeFixture.installBindings(component)

    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("fixture factory does not create components")

    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      throw new UnsupportedOperationException("fixture factory does not create component cores")
  }

  private def _with_subsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("logical-ui-bootstrap")
    try body(subsystem) finally subsystem.shutdown()
  }

  private def _initialized_component(
    subsystem: Subsystem,
    component: Component,
    componentfactory: Option[Component.Factory] = None
  ): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test.LogicalUiBootstrapSpec")
    val core = componentfactory match {
      case Some(factory) => Component.Core.create(
        name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty, factory = factory
      )
      case None => Component.Core.create(
        name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty
      )
    }
    component.initialize(ComponentInit(subsystem = subsystem, core = core, origin = ComponentOrigin.Builtin))
  }

  private def _provider_component(read: () => Vector[Definition]): Component =
    new Component() with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
      override def generatedLogicalUiDefinitions: Vector[Definition] = read()
      def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
        DisplayServiceReadFixture.resourceBindings
      override def displayBindings(component: Component): Vector[DisplayRuntime.Binding] =
        TypedDisplayRuntimeFixture.installBindings(component)
    }

  private def _revised_definitions(revision: String): Vector[Definition] =
    _definitions.map(_.copy(definitionRevision = revision))

  private def _expect_success(result: Consequence[Component], component: Component): Unit = {
    result.isSuccess shouldBe true
    result.toOption.exists(_ eq component) shouldBe true
  }

  private def _admitted(component: Component): AdmittedDefinitions =
    component._admitted_logical_ui_definitions.getOrElse(fail("successful bootstrap must retain admitted Logical UI definitions"))

  private def _expect_definitions(admitted: AdmittedDefinitions, input: Vector[Definition]): Unit = {
    (admitted.definitions eq input) shouldBe true
    admitted.definitions shouldBe input
    admitted.definitions.zip(input).foreach { case (actual, expected) => (actual eq expected) shouldBe true }
  }

  private def _expect_admitted(component: Component, input: Vector[Definition]): Unit =
    _expect_definitions(_admitted(component), input)

  private def _expect_unpublished(component: Component): Unit = {
    component._admitted_logical_ui_definitions shouldBe None
    component.collectionsBootstrapped shouldBe false
  }

  private def _failure(result: Consequence[?]): Consequence.Failure[?] = result match {
    case failure: Consequence.Failure[?] => failure
    case _ => fail("a structured failure is required")
  }

  private def _diagnostic(result: Consequence[?]): Option[Diagnostic] = result match {
    case failure: Consequence.Failure[?] if failure.conclusion.observation.taxonomy == Taxonomy.argumentInvalid =>
      failure.conclusion.observation.cause.getException.collect { case carrier: AdmissionException => carrier.diagnostic }
    case _ => None
  }

  private def _expect_abi_failure(result: Consequence[?], direct: Consequence[AdmittedDefinitions]): Unit = {
    _failure(result).conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    val expected = _diagnostic(direct)
    expected shouldBe defined
    _diagnostic(result) shouldBe expected
  }

  private def _preserved(result: Consequence[Option[AdmittedDefinitions]], input: Vector[Definition]): Boolean = result match {
    case Consequence.Success(Some(admitted)) =>
      (admitted.definitions eq input) && admitted.definitions == input &&
        admitted.definitions.zip(input).forall { case (actual, expected) => actual eq expected }
    case _ => false
  }
}
