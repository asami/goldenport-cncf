package org.goldenport.cncf.projection

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.component._
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy}
import org.goldenport.cncf.entity.aggregate.{AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.view.{ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 23, 2026
 *  version Apr. 11, 2026
 * @version Apr. 26, 2026
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
      _string_vector(_record(help("details")).asMap("origin")) shouldBe Vector("active car projection-alignment@0.1.0")
      _string_vector(_record(help("details")).asMap("artifactName")) shouldBe Vector("projection-alignment")
      _string_vector(_record(help("details")).asMap("artifactVersion")) shouldBe Vector("0.1.0")

      _records(describe("aggregates")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_aggregate", "profile_aggregate")
      _records(describe("views")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_view", "summary_view")
      _string_vector(_records(describe("views")).head.asMap("viewNames")) shouldBe Vector("detail", "summary")
      _records(_records(describe("views")).head.asMap("queries")).map(_.getString("name").getOrElse("")) shouldBe Vector("published")
      _records(_records(describe("views")).head.asMap("queries")).head.getString("expression") shouldBe Some("status == \"published\"")
      _string_vector(_records(describe("views")).head.asMap("sourceEvents")) shouldBe Vector("person.published")
      _records(describe("views")).head.getBoolean("rebuildable") shouldBe Some(true)
      describe.get("origin").map(_.toString) shouldBe Some("active car projection-alignment@0.1.0")
      _record(describe("artifact")).getString("name") shouldBe Some("projection-alignment")
      _records(describe("componentlets")).map(_.getString("name").getOrElse("")) shouldBe Vector("notice-admin", "public-notice")
      _records(describe("componentlets")).head.getString("kind") shouldBe Some("componentlet")
      _records(describe("componentlets")).head.getBoolean("isPrimary") shouldBe Some(false)
      _records(describe("operationDefinitions")).map(_.getString("name").getOrElse("")) shouldBe Vector("getPerson", "savePerson")
      _records(describe("operationDefinitions")).head.getString("kind") shouldBe Some("QUERY")
      _records(describe("operationDefinitions")).head.getString("inputType") shouldBe Some("GetPerson")
      _records(describe("operationDefinitions")).head.getString("outputType") shouldBe Some("GetPersonResult")
      _records(describe("operationDefinitions")).head.getString("inputValueKind") shouldBe Some("QUERY_VALUE")
      _records(_records(describe("operationDefinitions")).head.asMap("parameters")).map(_.getString("name").getOrElse("")) shouldBe Vector("id")
      val describeEntity = _records(describe("entityCollections")).head
      describeEntity.getString("entityName") shouldBe Some("Person")
      describeEntity.getString("collectionId") shouldBe Some("sys-sys-Person")
      describeEntity.getString("memoryPolicy") shouldBe Some("LoadToMemory")
      val describeStorageShape = _record(describeEntity.asMap("storageShape"))
      describeStorageShape.getString("policy") shouldBe Some("simple_entity_default")
      val describeStorageFields = _records(describeStorageShape.asMap("fields"))
      _storage_kind(describeStorageFields, "shortId") shouldBe Some("expanded_column")
      _storage_name(describeStorageFields, "shortId") shouldBe Some("short_id")
      _storage_kind(describeStorageFields, "ownerId") shouldBe Some("expanded_column")
      _storage_name(describeStorageFields, "ownerId") shouldBe Some("owner_id")
      _storage_kind(describeStorageFields, "permission") shouldBe Some("compact_json_text")
      describeStorageFields.exists(_.getString("logicalName").contains("owner.read")) shouldBe false
      _storage_kind(describeStorageFields, "body") shouldBe Some("column")
      _storage_kind(describeStorageFields, "tenantId") shouldBe Some("expanded_column")
      _storage_name(describeStorageFields, "tenantId") shouldBe Some("tenant_id")
      _storage_kind(describeStorageFields, "traceId") shouldBe Some("expanded_column")
      _storage_name(describeStorageFields, "traceId") shouldBe Some("trace_id")
      _storage_kind(describeStorageFields, "lineItems") shouldBe Some("delegated_collection")
      _storage_kind(describeStorageFields, "person_aggregate") shouldBe None
      _storage_kind(describeStorageFields, "person_view") shouldBe None
      _storage_kind(describeStorageFields, "securityAttributes") shouldBe None
      _storage_kind(describeStorageFields, "lifecycleAttributes") shouldBe None

      _records(schema("aggregateCollections")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_aggregate", "profile_aggregate")
      _records(schema("viewCollections")).map(_.getString("name").getOrElse("")) shouldBe Vector("person_view", "summary_view")
      schema.get("origin").map(_.toString) shouldBe Some("active car projection-alignment@0.1.0")
      _record(schema("artifact")).getString("version") shouldBe Some("0.1.0")
      _records(schema("operationDefinitions")).map(_.getString("name").getOrElse("")) shouldBe Vector("getPerson", "savePerson")
      _records(schema("operationDefinitions")).last.getString("kind") shouldBe Some("COMMAND")
      _records(schema("operationDefinitions")).last.getString("inputType") shouldBe Some("SavePersonInput")
      _records(schema("operationDefinitions")).last.getString("outputType") shouldBe Some("SavePersonResult")
      _records(_records(schema("operationDefinitions")).last.asMap("parameters")).map(_.getString("name").getOrElse("")) shouldBe Vector("id", "name")
      val schemaEntity = _records(schema("entityCollections")).head
      schemaEntity.getString("entityName") shouldBe Some("Person")
      val schemaStorageFields = _records(_record(schemaEntity.asMap("storageShape")).asMap("fields"))
      _storage_name(schemaStorageFields, "createdAt") shouldBe Some("created_at")
      _storage_name(schemaStorageFields, "updatedBy") shouldBe Some("updated_by")
      _storage_name(schemaStorageFields, "groupId") shouldBe Some("group_id")
      _storage_name(schemaStorageFields, "privilegeId") shouldBe Some("privilege_id")
      _storage_kind(schemaStorageFields, "status") shouldBe Some("column")

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
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(name = "profile_aggregate", entityName = "profile"),
          AggregateDefinition(
            name = "person_aggregate",
            entityName = "person",
            members = Vector(AggregateMemberDefinition(name = "lineItems", entityName = "PersonLine"))
          )
        )

      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(name = "summary_view", entityName = "person", viewNames = Vector("default")),
          ViewDefinition(
            name = "person_view",
            entityName = "person",
            viewNames = Vector("summary", "detail", "summary"),
            queries = Vector(ViewQueryDefinition("published", Some("status == \"published\""))),
            sourceEvents = Vector("person.published"),
            rebuildable = Some(true)
          )
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
              CmlOperationField("id", "EntityId", "one"),
              CmlOperationField("name", "Name", "one")
            )
          ),
          CmlOperationDefinition(
            name = "getPerson",
            kind = "QUERY",
            inputType = "GetPerson",
            outputType = "GetPersonResult",
            inputValueKind = "QUERY_VALUE",
            parameters = Vector(
              CmlOperationField("id", "EntityId", "one")
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
    component.withComponentDescriptors(
      Vector(
        ComponentDescriptor(
          name = Some(component.name),
          componentName = Some(component.name),
          componentlets = Vector(
            ComponentletDescriptor(
              name = "public-notice",
              kind = Some("componentlet"),
              isPrimary = Some(false),
              archiveScope = Some("car-bundled"),
              implementationClass = Some("domain.impl.PublicNoticeComponent"),
              factoryObject = Some("domain.impl.PublicNoticeComponent")
            ),
            ComponentletDescriptor(
              name = "notice-admin",
              kind = Some("componentlet"),
              isPrimary = Some(false),
              archiveScope = Some("car-bundled"),
              implementationClass = Some("domain.impl.NoticeAdminComponent"),
              factoryObject = Some("domain.impl.NoticeAdminComponent")
            )
          ),
          entityRuntimeDescriptors = Vector(
            EntityRuntimeDescriptor(
              entityName = "Person",
              collectionId = EntityCollectionId("sys", "sys", "Person"),
              memoryPolicy = EntityMemoryPolicy.LoadToMemory,
              partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
              maxPartitions = 64,
              maxEntitiesPerPartition = 10000,
              schema = Some(Schema(Vector(
                Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("body"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("status"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("ownerId"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("securityAttributes"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("lifecycleAttributes"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
              )))
            ),
            EntityRuntimeDescriptor(
              entityName = "PersonLine",
              collectionId = EntityCollectionId("sys", "sys", "PersonLine"),
              memoryPolicy = EntityMemoryPolicy.LoadToMemory,
              partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
              maxPartitions = 64,
              maxEntitiesPerPartition = 10000,
              schema = Some(Schema(Vector(
                Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("personId"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
                Column(BaseContent.simple("label"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
              )))
            )
          )
        )
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

  private def _storage_field(
    fields: Vector[Record],
    logicalName: String
  ): Option[Record] =
    fields.find(_.getString("logicalName").contains(logicalName))

  private def _storage_kind(
    fields: Vector[Record],
    logicalName: String
  ): Option[String] =
    _storage_field(fields, logicalName).flatMap(_.getString("storageKind"))

  private def _storage_name(
    fields: Vector[Record],
    logicalName: String
  ): Option[String] =
    _storage_field(fields, logicalName).flatMap(_.getString("storageName"))
}

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.notImplemented("not used")
}
