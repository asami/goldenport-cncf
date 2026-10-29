package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.CommandExecutionPolicy
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.tag.TagComponent
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.projection.model.HelpModel
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.cncf.testutil.{SubsystemTestFixture, TestComponentFactory}
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 25, 2026
 *  version Mar. 28, 2026
 *  version Apr.  6, 2026
 *  version May. 31, 2026
 *  version Aug. 13, 2026
 * @version Oct. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedHelpProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e1 = afterWord("in spec:generated-help-projection, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:generated-help-projection, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:generated-help-projection, example:E3, rules:CID05C-R9, phase:56, slice:CID-05C")

  "HelpProjection for generated component metadata" should {
    "preserve projected metadata through strict encoding and public JSON formatting" which {
      Vector(false, true).foreach { continuation =>
        s"represents optional continuation metadata when asyncContinuation is $continuation" in {
          Given("generated metadata with the default policy or a job-managed asynchronous continuation")
          val policy = if (continuation)
            CommandExecutionPolicy(managedByJob = true, asyncContinuation = true)
          else
            CommandExecutionPolicy.default
          val component = _component_with_policy(policy)
          val controls = Vector(
            (None, "subsystem", "domain", "domain"),
            (Some("org.goldenport.fixture.Domain"), "component", "domain", "address"),
            (Some("org.goldenport.fixture.Domain.address"), "service", "address", "lookupAddress")
          )

          When("actual subsystem, component, service, and operation Help records use both JSON channels")
          val controljson = controls.map { case (selector, kind, name, child) =>
            (_help_json(HelpProjection.project(component, selector)), kind, name, child)
          }
          val operationjson = Vector(
            "org.goldenport.fixture.Domain.address.lookupAddress",
            "domain.address.lookupAddress"
          ).flatMap(selector => _help_json(HelpProjection.project(component, Some(selector))))

          Then("the control targets retain their identities and discoverable children without error payloads")
          controljson.foreach { case (documents, kind, name, child) =>
            documents.foreach { case (document, formatted) =>
              val cursor = document.hcursor
              cursor.downField("error").focus shouldBe None
              cursor.get[String]("type") shouldBe Right(kind)
              cursor.get[String]("name") shouldBe Right(name)
              cursor.get[Vector[String]]("children").toOption.getOrElse(fail("children are missing")) should contain (child)
              if (kind != "subsystem")
                cursor.get[String](_wire_key("componentId", formatted)) shouldBe Right("org.goldenport.fixture.Domain")
            }
          }

          And("each operation channel retains concrete generated metadata and every canonical policy field")
          val mode = if (continuation) "JobSyncWithAsyncCont" else "Sync"
          val expectedpolicy = Json.obj(
            "mode" -> Json.fromString(mode),
            "interfaceMode" -> Json.fromString("sync"),
            "jobRunMode" -> Json.fromString("sync"),
            "managedByJob" -> Json.fromBoolean(continuation),
            "asyncContinuation" -> Json.fromBoolean(continuation),
            "callerTransactionPolicy" -> Json.fromString("join-caller"),
            "eventTransactionRequirement" -> Json.fromString("required"),
            "jobTransactionScope" -> Json.fromString("per-task"),
            "continuationEventTransactionRequirement" -> Json.fromString("required"),
            "legacyMode" -> Json.fromString(if (continuation) "SyncJob" else "SyncDirectNoJob"),
            "modeStatus" -> Json.fromString("canonical"),
            "deprecated" -> Json.False
          ).deepMerge(if (continuation)
            Json.obj("continuationMode" -> Json.fromString("event-async-same-job-task"))
          else
            Json.obj())
          operationjson.foreach { case (document, formatted) =>
            val cursor = document.hcursor
            cursor.downField("error").focus shouldBe None
            cursor.get[String]("type") shouldBe Right("operation")
            cursor.get[String]("name") shouldBe Right("lookupAddress")
            cursor.get[String](_wire_key("componentId", formatted)) shouldBe Right("org.goldenport.fixture.Domain")
            cursor.get[String]("summary") shouldBe Right("Look up an address by postal code.")
            cursor.downField("details").get[Vector[String]]("description") shouldBe Right(Vector(
              "Look up an address by postal code.Returns a normalized address representation."
            ))
            cursor.downField("details").get[Vector[String]]("arguments") shouldBe Right(Vector("description"))
            cursor.downField("details").get[Vector[String]](_wire_key("argumentDetails", formatted)) shouldBe Right(Vector(
              "description: text 1 [min-length=1, max-length=8192]"
            ))
            cursor.downField("details").get[Vector[String]]("returns") shouldBe Right(Vector("LookupAddressResult"))
            val execution = cursor.downField(_wire_key("commandExecution", formatted))
            val wirepolicy = Json.fromFields(expectedpolicy.asObject.get.toVector.map { case (key, value) =>
              _wire_key(key, formatted) -> value
            })
            execution.downField(_wire_key("commandExecutionPolicy", formatted)).focus shouldBe Some(wirepolicy)
            execution.get[String](_wire_key("effectiveCommandExecutionMode", formatted)) shouldBe Right(mode)
            val evaluation = cursor.downField("evaluation")
            evaluation.downField("corpus").get[String]("profile") shouldBe Right("postal-lookup")
            evaluation.downField("corpus").get[String]("capture") shouldBe Right("candidate")
            evaluation.downField("experiment").get[Boolean]("eligible") shouldBe Right(true)
          }
        }
      }
    }

    "E1 project service and operation help from generated specification metadata" must _e1 {
      "when exercising: project service and operation help from generated specification metadata" in {
        Given("a generated-style component with help metadata on the service and operation")
        val component = GeneratedHelpProjectionFixture.component()

        When("projecting component, service, and operation help")
        val subsystemhelp = HelpProjection.projectModel(component, None)
        val componenthelp = HelpProjection.projectModel(component, Some("org.goldenport.fixture.Domain"))
        val servicehelp = HelpProjection.projectModel(component, Some("org.goldenport.fixture.Domain.address"))
        val operationhelp = HelpProjection.projectModel(component, Some("org.goldenport.fixture.Domain.address.lookupAddress"))
        val operationdescribe = DescribeProjection.project(component, Some("org.goldenport.fixture.Domain.address.lookupAddress"))
        val operationschema = SchemaProjection.project(component, Some("org.goldenport.fixture.Domain.address.lookupAddress"))

        Then("the subsystem target resolves top-level requirement metadata")
        subsystemhelp.`type` shouldBe "subsystem"
        subsystemhelp.name shouldBe "domain"
        subsystemhelp.children should contain ("domain")
        subsystemhelp.domainVisions.map(_.name) shouldBe Vector("trusted_postal_foundation")
        subsystemhelp.domainUseCases.map(_.name) shouldBe Vector("postal_address_lifecycle")
        subsystemhelp.domainUseCases.head.primaryActor shouldBe Some("EndUser")

        Then("the component target resolves and exposes the generated service")
        componenthelp.`type` shouldBe "component"
        componenthelp.name shouldBe "domain"
        componenthelp.componentId shouldBe Some("org.goldenport.fixture.Domain")
        componenthelp.children should contain allOf ("address", "meta", "system")
        componenthelp.details("services") should contain ("address")
        componenthelp.domainVisions shouldBe Vector.empty
        componenthelp.domainUseCases shouldBe Vector.empty
        componenthelp.useCases.map(_.name) shouldBe Vector("component_postal_support")
        componenthelp.useCases.head.primaryActor shouldBe Some("EndUser")
        componenthelp.useCases.head.goal shouldBe Some("Expose reusable postal lookup behavior through the component boundary.")
        componenthelp.useCases.head.precondition shouldBe Some("The address service is configured in the component.")
        componenthelp.useCases.head.postcondition shouldBe Some("The component can answer postal lookup requests through its public service.")
        componenthelp.usage shouldBe Vector("command help org.goldenport.fixture.Domain.address")

        And("the service help uses the generated summary and description metadata")
        servicehelp.`type` shouldBe "service"
        servicehelp.name shouldBe "address"
        servicehelp.componentId shouldBe Some("org.goldenport.fixture.Domain")
        servicehelp.summary shouldBe "Address service for postal address support."
        servicehelp.children shouldBe Vector("lookupAddress")
        servicehelp.useCases.map(_.name) shouldBe Vector("postal_lookup")
        servicehelp.useCases.head.primaryActor shouldBe Some("EndUser")
        servicehelp.useCases.head.goal shouldBe Some("Resolve a postal code into a normalized address representation.")
        servicehelp.useCases.head.precondition shouldBe Some("A resolvable postal code is provided.")
        servicehelp.useCases.head.postcondition shouldBe Some("A normalized address projection is returned.")
        servicehelp.usage shouldBe Vector("command help org.goldenport.fixture.Domain.address.lookupAddress")

        And("the operation help uses the generated summary and description metadata")
        operationhelp.`type` shouldBe "operation"
        operationhelp.name shouldBe "lookupAddress"
        operationhelp.componentId shouldBe Some("org.goldenport.fixture.Domain")
        operationhelp.summary shouldBe "Look up an address by postal code."
        operationhelp.details("description") shouldBe Vector(
          "Look up an address by postal code.Returns a normalized address representation."
        )
        operationhelp.details("arguments") shouldBe Vector("description")
        operationhelp.details("argumentDetails") shouldBe Vector(
          "description: text 1 [min-length=1, max-length=8192]"
        )
        operationhelp.details("returns") shouldBe Vector("LookupAddressResult")
        val schemaparameters = operationschema
          .getRecord("request")
          .flatMap(_.getAny("parameters"))
          .collect { case xs: Seq[?] => xs.collect { case x: Record => x }.toVector }
          .getOrElse(fail("operation schema parameters are missing"))
        val descriptionparameter = schemaparameters.find(_.getString("name").contains("description")).getOrElse(
          fail("description parameter schema is missing")
        )
        descriptionparameter.getString("type") shouldBe Some("text")
        descriptionparameter.getBoolean("required") shouldBe Some(true)
        descriptionparameter.getRecord("validation").flatMap(_.getInt("minLength")) shouldBe Some(1)
        descriptionparameter.getRecord("validation").flatMap(_.getInt("maxLength")) shouldBe Some(8192)
        val commandexecution = operationhelp.commandExecution.getOrElse(fail("command execution metadata is missing"))
        commandexecution.getRecord("commandExecutionPolicy").flatMap(_.getString("mode")) shouldBe Some("Sync")
        commandexecution.getRecord("commandExecutionPolicy").flatMap(_.getString("legacyMode")) shouldBe Some("SyncDirectNoJob")
        commandexecution.getString("effectiveCommandExecutionMode") shouldBe Some("Sync")
        val evaluation = operationhelp.evaluation.getOrElse(fail("evaluation metadata is missing"))
        evaluation.getRecord("corpus").flatMap(_.getString("profile")) shouldBe Some("postal-lookup")
        evaluation.getRecord("experiment").flatMap(_.getBoolean("eligible")) shouldBe Some(true)
        operationdescribe.getRecord("evaluation").flatMap(_.getRecord("corpus")).flatMap(_.getString("capture")) shouldBe Some("candidate")
        operationschema.getRecord("evaluation").flatMap(_.getRecord("corpus")).flatMap(_.getString("capture")) shouldBe Some("candidate")
        operationhelp.usage shouldBe Vector("command org.goldenport.fixture.Domain.address.lookupAddress")

        And("the CLI renderers can emit meta.help output from the same model")
        val yaml = CliHelpYamlRenderer.render(servicehelp)
        yaml should include ("type: service")
        yaml should include ("name: address")
        yaml should include ("summary: Address service for postal address support.")
        yaml should include ("structuredUseCases:")
        yaml should include ("name: postal_lookup")
        yaml should include ("primaryActor: EndUser")
        yaml should include ("goal: Resolve a postal code into a normalized address representation.")
        yaml should include ("precondition: A resolvable postal code is provided.")
        yaml should include ("postcondition: A normalized address projection is returned.")

        val json = CliHelpJsonRenderer.render(operationhelp)
        json should include ("\"type\":\"operation\"")
        json should include ("\"name\":\"lookupAddress\"")
        json should include ("\"summary\":\"Look up an address by postal code.\"")
        json should include ("\"argumentDetails\":[\"description: text 1 [min-length=1, max-length=8192]\"]")
        json should include ("\"returns\":[\"LookupAddressResult\"]")
        json should include ("\"evaluation\"")

        val subsystemyaml = CliHelpYamlRenderer.render(subsystemhelp)
        subsystemyaml should include ("type: subsystem")
        subsystemyaml should include ("structuredDomainUseCases:")
        subsystemyaml should include ("name: postal_address_lifecycle")
      }
    }

    "E2 project builtin Tag component operation help" must _e2 {
      "when exercising: project builtin Tag component operation help" in {
        Given("the builtin Tag component is installed in a default subsystem")
        SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
          val component = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.TAG).getOrElse(fail("Tag component is missing"))

          When("projecting component, service, and operation help")
          val componenthelp = HelpProjection.projectModel(component, Some("org.goldenport.cncf.Tag"))
          val servicehelp = HelpProjection.projectModel(component, Some("org.goldenport.cncf.Tag.tag"))
          val createhelp = HelpProjection.projectModel(component, Some("org.goldenport.cncf.Tag.tag.tag_create"))
          val searchhelp = HelpProjection.projectModel(component, Some("org.goldenport.cncf.Tag.tag.tag_search_entities"))

          Then("the Tag operations are discoverable from service help")
          servicehelp.`type` shouldBe "service"
          servicehelp.name shouldBe "tag"
          servicehelp.children should contain allOf (
            "tag_tree",
            "tag_create",
            "tag_update",
            "tag_move",
            "tag_attach",
            "tag_detach",
            "tag_list_entity_tags",
            "tag_search_entities"
          )

          And("operation summaries and descriptions are projected")
          createhelp.`type` shouldBe "operation"
          createhelp.summary shouldBe "Create a Tag."
          createhelp.details("description").mkString should include ("strict-tree Tag")
          searchhelp.summary shouldBe "Search Entities by Tag."
          searchhelp.details("description").mkString should include ("EntityStore-visible rows")

          And("the CLI renderers expose the same operation names")
          val yaml = CliHelpYamlRenderer.render(servicehelp)
          yaml should include ("tag_create")
          yaml should include ("tag_search_entities")
          val json = CliHelpJsonRenderer.render(componenthelp)
          json should include ("\"componentId\":\"org.goldenport.cncf.Tag\"")
          json should include ("\"name\":\"tag\"")
          json should include ("\"services\":[\"meta\",\"system\",\"tag\"]")
        }
      }
    }

    "E3 retain old HelpModel positional fields while serializing an explicit component ID" must _e3 {
      "when exercising: retain old HelpModel positional fields while serializing an explicit component ID" in {
        Given("a HelpModel constructed with the public positional fields that predate componentId")
        val model = HelpModel(
          "service",
          "system",
          "System service",
          Some("admin"),
          Some("system"),
          componentId = Some("org.goldenport.cncf.Admin")
        )

        When("the help model is rendered for the machine-readable CLI channel")
        val json = CliHelpJsonRenderer.render(model)

        Then("the positional component and service meanings remain unchanged and canonical identity is explicit")
        model.component shouldBe Some("admin")
        model.service shouldBe Some("system")
        json should include ("\"componentId\":\"org.goldenport.cncf.Admin\"")
      }
    }
  }

  private def _component_with_policy(policy: CommandExecutionPolicy): Component = {
    val template = GeneratedHelpProjectionFixture.component()
    val factory = new Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: ComponentCreate): Component =
        new Component() {
          override def displayName: String = template.displayName

          override def operationDefinitions: Vector[CmlOperationDefinition] =
            template.operationDefinitions.map(_.copy(commandExecutionPolicy = Some(policy)))

          override def componentDefinitionRecords: Vector[Record] = template.componentDefinitionRecords

          override def subsystemDefinitionRecords: Vector[Record] = template.subsystemDefinitionRecords
        }

      override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
        Component.Core.create(
          template.componentId.name,
          template.componentId,
          ComponentInstanceId.default(template.componentId),
          template.protocol,
          this
        )
    }
    val subsystem = TestComponentFactory.emptySubsystem("domain")
    val component = factory.create(ComponentCreate(
      subsystem = subsystem,
      origin = ComponentOrigin.Repository("cozy-generated")
    )).primary
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private val _formatter_keys = Map(
    "componentId" -> "component_id",
    "commandExecution" -> "command_execution",
    "commandExecutionPolicy" -> "command_execution_policy",
    "effectiveCommandExecutionMode" -> "effective_command_execution_mode",
    "argumentDetails" -> "argument_details",
    "mode" -> "mode",
    "interfaceMode" -> "interface_mode",
    "jobRunMode" -> "job_run_mode",
    "managedByJob" -> "managed_by_job",
    "asyncContinuation" -> "async_continuation",
    "callerTransactionPolicy" -> "caller_transaction_policy",
    "eventTransactionRequirement" -> "event_transaction_requirement",
    "jobTransactionScope" -> "job_transaction_scope",
    "continuationEventTransactionRequirement" -> "continuation_event_transaction_requirement",
    "continuationMode" -> "continuation_mode",
    "legacyMode" -> "legacy_mode",
    "modeStatus" -> "mode_status",
    "deprecated" -> "deprecated"
  )

  private def _wire_key(key: String, formatted: Boolean): String =
    if (formatted) _formatter_keys(key) else key

  private def _help_json(record: Record): Vector[(Json, Boolean)] = {
    val encoded = RecordEncoder.jsonC(record).TAKE
    val request = Request.ofOperation("meta.help").copy(
      properties = List(Property("textus.output.format", "json", None))
    )
    val formatted = OperationResponseFormatter.toResponse(
      request,
      OperationResponse.RecordResponse(record),
      RunMode.Command
    ) match {
      case Response.Json(value) => value
      case other => fail(s"Expected a public JSON Help response, got $other")
    }
    Vector(encoded -> false, formatted -> true).map { case (value, channel) =>
      io.circe.parser.parse(value).fold(error => fail(s"Invalid Help JSON: $error"), identity) -> channel
    }
  }
}
