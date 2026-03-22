package org.goldenport.cncf.projection

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.component._
import org.goldenport.cncf.entity.aggregate.CmlAggregateDefinition
import org.goldenport.cncf.entity.view.CmlViewDefinition
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateViewProjectionAlignmentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "projection alignment" should {
    "expose aggregate/view metadata in help/describe/schema deterministically" in {
      Given("a component with generated aggregate/view definitions")
      val component = _component_with_definitions()

      When("projecting help/describe/schema twice")
      val help = HelpProjection.project(component, Some(component.name)).asMap
      val describe = DescribeProjection.project(component, Some(component.name)).asMap
      val schema = SchemaProjection.project(component, Some(component.name)).asMap

      val help2 = HelpProjection.project(component, Some(component.name)).asMap
      val describe2 = DescribeProjection.project(component, Some(component.name)).asMap
      val schema2 = SchemaProjection.project(component, Some(component.name)).asMap

      Then("aggregate/view/operation metadata is included with stable sorted output")
      _string_vector(_record(help("details")).asMap("aggregates")) shouldBe Vector("person_aggregate", "profile_aggregate")
      _string_vector(_record(help("details")).asMap("views")) shouldBe Vector("person_view", "summary_view")
      _string_vector(_record(help("details")).asMap("operationDefinitions")) shouldBe Vector("getPerson", "savePerson")
      _string_vector(_record(help("details")).asMap("origin")).head should include("component-dir")
      _string_vector(_record(help("details")).asMap("artifactName")) shouldBe Vector("projection-alignment")
      _string_vector(_record(help("details")).asMap("artifactVersion")) shouldBe Vector("0.1.0")

      _records(describe("aggregates")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_aggregate", "profile_aggregate")
      _records(describe("views")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_view", "summary_view")
      _string_vector(_records(describe("views")).head.asMap("viewNames")) shouldBe Vector("detail", "summary")
      describe.get("origin").map(_.toString) shouldBe Some("component-dir:car:projection-alignment:0.1.0")
      _record(describe("artifact")).getString("name") shouldBe Some("projection-alignment")
      _records(describe("operationDefinitions")).map(_.getString("name").getOrElse("")) shouldBe Vector("getPerson", "savePerson")
      _records(describe("operationDefinitions")).head.getString("kind") shouldBe Some("QUERY")
      _records(describe("operationDefinitions")).head.getString("inputType") shouldBe Some("GetPerson")
      _records(describe("operationDefinitions")).head.getString("outputType") shouldBe Some("GetPersonResult")
      _records(describe("operationDefinitions")).head.getString("inputValueKind") shouldBe Some("QUERY_VALUE")
      _records(_records(describe("operationDefinitions")).head.asMap("parameters")).map(_.getString("name").getOrElse("")) shouldBe Vector("id")

      _records(schema("aggregateCollections")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_aggregate", "profile_aggregate")
      _records(schema("viewCollections")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_view", "summary_view")
      schema.get("origin").map(_.toString) shouldBe Some("component-dir:car:projection-alignment:0.1.0")
      _record(schema("artifact")).getString("version") shouldBe Some("0.1.0")
      _records(schema("operationDefinitions")).map(_.getString("name").getOrElse("")) shouldBe Vector("getPerson", "savePerson")
      _records(schema("operationDefinitions")).last.getString("kind") shouldBe Some("COMMAND")
      _records(schema("operationDefinitions")).last.getString("inputType") shouldBe Some("SavePersonInput")
      _records(schema("operationDefinitions")).last.getString("outputType") shouldBe Some("SavePersonResult")
      _records(_records(schema("operationDefinitions")).last.asMap("parameters")).map(_.getString("name").getOrElse("")) shouldBe Vector("id", "name")

      help shouldBe help2
      describe shouldBe describe2
      schema shouldBe schema2
    }

    "expose aggregate/view metadata in openapi info deterministically" in {
      Given("a component with generated aggregate/view definitions")
      val component = _component_with_definitions()

      When("projecting OpenAPI twice")
      val first = OpenApiProjection.projectComponent(component)
      val second = OpenApiProjection.projectComponent(component)

      Then("vendor extensions include deterministic aggregate/view names")
      first shouldBe second
      first should include("\"x-cncf-aggregate-collections\":[\"person_aggregate\",\"profile_aggregate\"]")
      first should include("\"x-cncf-view-collections\":[")
      first should include("\"x-cncf-operation-definitions\":[")
      first should include("\"name\":\"getPerson\"")
      first should include("\"name\":\"savePerson\"")
      first should include("\"kind\":\"QUERY\"")
      first should include("\"kind\":\"COMMAND\"")
      first should include("\"inputType\":\"GetPerson\"")
      first should include("\"outputType\":\"SavePersonResult\"")
      first should include("\"inputValueKind\":\"COMMAND_VALUE\"")
      first should include("\"datatype\":\"EntityId\"")
      first should include("\"datatype\":\"Name\"")
      first should include("\"name\":\"person_view\"")
      first should include("\"viewNames\":[\"detail\",\"summary\"]")
    }
  }

  private def _component_with_definitions(): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "entity",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(_NoopOperation("load"))
            )
          )
        )
      )
    )

    val component = new Component() {
      override def aggregateDefinitions: Vector[CmlAggregateDefinition] =
        Vector(
          CmlAggregateDefinition(name = "profile_aggregate", entityName = "profile"),
          CmlAggregateDefinition(name = "person_aggregate", entityName = "person")
        )

      override def viewDefinitions: Vector[CmlViewDefinition] =
        Vector(
          CmlViewDefinition(name = "summary_view", entityName = "person", viewNames = Vector("default")),
          CmlViewDefinition(name = "person_view", entityName = "person", viewNames = Vector("summary", "detail", "summary"))
        )

      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "savePerson",
            kind = "COMMAND",
            inputType = "SavePersonInput",
            outputType = "SavePersonResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(
              CmlOperationField("id", "EntityId", "1"),
              CmlOperationField("name", "Name", "1")
            )
          ),
          CmlOperationDefinition(
            name = "getPerson",
            kind = "QUERY",
            inputType = "GetPerson",
            outputType = "GetPersonResult",
            inputValueKind = "QUERY_VALUE",
            parameters = Vector(
              CmlOperationField("id", "EntityId", "1")
            )
          )
        )
    }

    val core = Component.Core.create(
      name = "projection_alignment_spec",
      componentid = ComponentId("projection_alignment_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("projection_alignment_spec")),
      protocol = protocol
    )
    val subsystem = TestComponentFactory.emptySubsystem("projection_alignment_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Repository("component-dir:car:projection-alignment:0.1.0")
    )
    component.initialize(params)
    component.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "car",
        name = "projection-alignment",
        version = "0.1.0",
        component = Some(component.name),
        subsystem = None
      )
    )
    subsystem.add(Vector(component))
    subsystem.components.find(_.name == component.name).getOrElse(component)
  }

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Vector[?] =>
        xs.collect { case r: Record => r }
      case xs: Seq[?] =>
        xs.toVector.collect { case r: Record => r }
      case _ =>
        Vector.empty
    }

  private def _record(value: Any): Record =
    value.asInstanceOf[Record]

  private def _string_vector(value: Any): Vector[String] =
    value match {
      case xs: Vector[?] => xs.toVector.map(_.toString)
      case xs: Seq[?] => xs.toVector.map(_.toString)
      case _ => Vector.empty
    }
}

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.failure("not used")
}
