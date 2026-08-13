package org.goldenport.cncf.http

import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.{ZipEntry, ZipOutputStream}
import cats.data.State
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyVector
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{MediaType, Method, Request, Uri}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.{Argument, Property, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentFactory, ComponentId, ComponentInstanceId, ComponentletDescriptor}
import org.goldenport.cncf.testutil.DevelopmentRuntimeManifestFixture
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, PrincipalId, RuntimeContext}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.{
  EntityConcurrencyMetadata,
  EntityMutationAdapterDefaults,
  EntityPersistent,
  EntityRevisionBinding,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityRevisionTransport,
  EntityStoreSpace
}
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationDefinition, CmlOperationField, CmlOperationImageBinding, CmlOperationUpdateField}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.information.*
import org.goldenport.cncf.knowledge.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 12, 2026
 *  version May. 27, 2026
 *  version Jun. 19, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _renderer = StaticFormAppRenderer()
  private val _test_csrf_token = WebCsrf.issue(None)
  private val _notice_board_component_name = ComponentId("org.goldenport.cncf.test.NoticeBoard").name
  private val _embedded_notice_board_component_name = ComponentId("org.goldenport.cncf.test.EmbeddedNoticeBoard").name
  private val _in_phase53_spec =
    afterWord("in spec:static-web-execution-context-projection, example:PM-53-01, rules:SWEP-3, phase:53")
  private val _aes05b = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R13, phase:57.2, slice:AES-05B"
  )

  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide dashboard, system administration, Blob, and documentation contracts" which {
    "keep the generated manual available when one metadata projection fails" in {
      Given("a projection that raises while the manual assembles its independent sections")

      When("the projection boundary converts the failure to renderable metadata")
      val projected = _renderer.manual_projection_or_error("Schema", "notice-board") {
        throw new NotImplementedError("schema fixture is unavailable")
      }

      Then("the failed section is explicit without aborting the whole manual")
      projected.getString("type") shouldBe Some("error")
      projected.getString("name") shouldBe Some("notice-board")
      projected.getString("summary") shouldBe Some("Schema projection unavailable")
      projected.getString("error") shouldBe Some("schema fixture is unavailable")
    }

    "render subsystem dashboard state contract" in {
      Given("the prerequisites for render subsystem dashboard state contract")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      val json = _dashboard_state_json(subsystem, None)
      When("render subsystem dashboard state contract is exercised")
      val c = json.hcursor

      Then("the observable contract for render subsystem dashboard state contract holds")
      c.get[String]("scope") shouldBe Right("subsystem")
      c.downField("cncf").get[String]("version").isRight shouldBe true
      c.downField("subsystem").get[String]("name") shouldBe Right(subsystem.name)
      c.downField("html").downField("requests").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("day").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("hour").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("summary").downField("minute").get[Long]("count").isRight shouldBe true
      c.downField("html").downField("requests").downField("series").downField("minute").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("html").downField("requests").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("html").downField("requests").downField("series").downField("day").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("authorization").downField("diagnostics").focus.exists(_.isObject) shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("cumulative").get[Long]("count").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("cumulative").get[Long]("errors").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("series").downField("hour").focus.flatMap(_.asArray).exists(_.nonEmpty) shouldBe true
      c.downField("assembly").downField("warnings").get[Int]("count").isRight shouldBe true
      c.downField("links").get[String]("admin") shouldBe Right("/web/system/admin")
      c.downField("links").get[String]("performance") shouldBe Right("/web/system/performance")
      c.downField("links").get[String]("manual") shouldBe Right("/man/system")
      c.downField("links").get[String]("console") shouldBe Right("/web/console")
      c.downField("links").get[String]("assemblyWarnings") shouldBe Right("/web/system/admin/assembly/warnings")
    }

    "render component dashboard state contract" in {
      Given("the prerequisites for render component dashboard state contract")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val componentname = subsystem.components.headOption.map(_.name).getOrElse(fail("component is missing"))

      val json = _dashboard_state_json(subsystem, Some(componentname))
      When("render component dashboard state contract is exercised")
      val c = json.hcursor

      Then("the observable contract for render component dashboard state contract holds")
      c.get[String]("scope") shouldBe Right("component")
      c.get[String]("name") shouldBe Right(componentname)
      c.downField("components").focus.flatMap(_.asArray).map(_.size) shouldBe Some(1)
      c.downField("html").downField("requests").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("actionCalls").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("actions").downField("jobs").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("authorization").downField("decisions").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("dsl").downField("chokepoints").downField("summary").downField("hour").get[Long]("errors").isRight shouldBe true
      c.downField("links").get[String]("admin") shouldBe Right(s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentname)}/admin")
      c.downField("links").get[String]("manual") shouldBe Right(s"/man/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(componentname)}")
    }

    "preserve fallback HTTP status in non-Conclusion diagnostic records" in {
      Given("the prerequisites for preserve fallback HTTP status in non-Conclusion diagnostic records")
      val record = Http4sHttpServer.fallbackHttpDiagnosticRecord(404)

      When("the observable result for preserve fallback HTTP status in non-Conclusion diagnostic records is inspected")
      locally {
        Then("the observable contract for preserve fallback HTTP status in non-Conclusion diagnostic records holds")
        record.getInt("webStatus") shouldBe Some(404)
        record.getString("statusText") shouldBe Some("Not Found")
      }
    }

    "render dashboard pages with Bootstrap health hierarchy without changing links" in {
      Given("the prerequisites for render dashboard pages with Bootstrap health hierarchy without changing links")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      When("render dashboard pages with Bootstrap health hierarchy without changing links is exercised")
      val html = _renderer.renderSubsystemDashboard(subsystem).body

      Then("the observable contract for render dashboard pages with Bootstrap health hierarchy without changing links holds")
      html should include ("CNCF Health")
      html should include ("class=\"card h-100 shadow-sm border-success\"")
      html should include ("class=\"badge text-bg-success\"")
      html should include ("Recent failures")
      html should include ("Recent failures are diagnostics; they do not change runtime Health.")
      html should include ("id=\"httpRecentErrorsLink\"")
      html should include ("/web/system/performance#recent-errors")
      html should include ("/web/system/performance#authorization")
      html should include ("/form/admin/execution/history")
      html should include ("const health = data.status || \"UP\";")
      html should not include ("const health = (failedJobs > 0 || recentFailures > 0 || recentDenials > 0) ? \"WARN\"")
      html should include ("table table-sm table-hover align-middle")
      html should include ("class=\"list-group list-group-flush\"")
      html should include ("class=\"progress\"")
      html should include ("class=\"dashboard-spark\"")
      html should not include (".bar { display: grid")
      html should not include (".bars { display: grid")
      html should include ("/web/system/admin")
      html should include ("/web/system/performance")
      html should include ("/man/system")
    }

    "render system admin configuration detail page" in {
      Given("the prerequisites for render system admin configuration detail page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      When("render system admin configuration detail page is exercised")
      val html = _renderer.renderSystemAdmin(subsystem).body

      Then("the observable contract for render system admin configuration detail page holds")
      html should include ("System Admin Configuration")
      html should include ("/web/assets/bootstrap.min.css")
      html should not include ("cdn.jsdelivr")
      html should not include ("article { background")
      html should include ("class=\"card admin-card")
      html should include ("nav nav-pills")
      html should include ("class=\"list-group")
      html should include ("class=\"admin-action-row d-flex flex-wrap gap-2")
      html should include ("class=\"table-responsive\"")
      html should include ("table table-sm table-hover align-middle mb-0")
      html should include ("CNCF version")
      html should include ("Subsystem")
      html should include ("/web/system/dashboard")
      html should include ("/web/system/performance")
      html should include ("/man/system")
      html should include ("/web/console")
      html should include ("Component Management Console")
      html should include ("Component admin")
      html should include ("Web Descriptor")
      html should include ("Using built-in Web HTML app defaults.")
      html should include ("/web/system/admin/descriptor")
      html should include ("Runtime Configuration")
      html should include ("Configuration mutation must use a separate admin action surface")
      html should include ("audit logging")
      html should include ("Job Control")
      html should include ("authoritative source for cross-component continuation observability")
      html should include ("Operator Checklist")
      html should include ("job_control.job.get_job_status")
      html should include ("event.event.load_event")
      html should include ("/web/system/admin/jobs")
      html should include ("/form/admin/execution/diagnostics")
      html should include ("Operational Details")
      html should include ("Assembly")
      html should include ("/web/system/admin/assembly/warnings")
      html should include ("/web/system/admin/assembly/report")
      html should include ("Execution")
      html should include ("/form/admin/execution/history")
      html should include ("/form/admin/execution/calltree")
    }

    "render component development directory diagnostics on system admin page" in {
      Given("the prerequisites for render component development directory diagnostics on system admin page")
      val devroot = Files.createTempDirectory("cncf-component-dev-root-")
      Files.createDirectories(devroot.resolve("target").resolve("cncf.d"))
      Files.createDirectories(devroot.resolve("src").resolve("main").resolve("web"))
      val subsystem = _management_console_fixture_subsystem()
        .add(Vector(TestComponentFactory.create("dev_component", Protocol.empty)))
      val devcomponent = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.DevComponent")).getOrElse(fail("dev component missing"))
      devcomponent.withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata(
          sourceType = "component-dev-dir",
          name = "dev-component",
          version = "0.1.0",
          component = Some("dev-component"),
          archivePath = Some(devroot.toString)
        )
      )

      When("render component development directory diagnostics on system admin page is exercised")
      val html = _renderer.renderSystemAdmin(subsystem).body

      Then("the observable contract for render component development directory diagnostics on system admin page holds")
      html should include ("Component Development Directories")
      html should include (devcomponent.name)
      html should include (devroot.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt").toString)
      html should include (devroot.resolve("src").resolve("main").resolve("web").toString)
    }

    "render system admin jobs list and detail pages" in {
      Given("the prerequisites for render system admin jobs list and detail pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val action = RendererJobAction(GRequest.of(
        component = "renderer",
        service = "job",
        operation = "debug-query"
      ))
      val task = ActionTask(ActionId.generate(), action, ActionEngine.create(), None)
      val jobid = subsystem.jobEngine.submit(
        List(task),
        ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.test(), enabled = true),
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Sync,
          executionNotes = Vector("debug trace query")
        )
      ).toOption.getOrElse(fail("job submission failed"))
      val model = subsystem.jobEngine.query(jobid).getOrElse(fail("job read model missing"))

      val list = _renderer.renderSystemAdminJobs(subsystem).body
      When("render system admin jobs list and detail pages is exercised")
      val detail = _renderer.renderSystemAdminJob(subsystem, model).body

      Then("the observable contract for render system admin jobs list and detail pages holds")
      list should include ("System Admin Jobs")
      list should include (jobid.value)
      list should include (s"/web/system/admin/jobs/${jobid.value}")
      list should include ("class=\"card admin-card")
      list should include ("nav nav-pills")
      list should include ("table table-sm table-hover align-middle mb-0")
      detail should include ("Job-managed trace and calltree detail")
      detail should include ("Calltree")
      detail should include ("Timeline")
      detail should include ("debug-query")
      detail should include ("class=\"card admin-card")
      detail should include ("nav nav-pills")
      detail should include ("table table-sm table-hover align-middle mb-0")
    }

    "render system admin knowledge pages" in {
      Given("the prerequisites for render system admin knowledge pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      subsystem.add(TestComponentFactory.create("knowledge_component", Protocol.empty))
      val component = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.KnowledgeComponent")).getOrElse(fail("knowledge component missing"))
      val ext = ExternalKnowledgeIdentifier.entity("customer", "customer-1")
      val provenance = KnowledgeProvenance(KnowledgeProvenanceId("prov-1"), "renderer-spec", Some("cncf"))
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-1"),
        "entity-record",
        KnowledgeSourceRef("entity", "customer-1"),
        Some("Customer source record"),
        Some(provenance.id)
      )
      val customer = KnowledgeNode(
        id = KnowledgeNodeId("node-customer"),
        category = KnowledgeNodeCategory.Entity,
        identity = KnowledgeNodeIdentity(
          rdfNode = Some(RdfNodeName("rdf:customer-1")),
          externalIdentifiers = Vector(ext)
        ),
        presentation = KnowledgeNodePresentation.label("Customer"),
        semantics = KnowledgeNodeSemantics(
          semanticTypes = Vector(KnowledgeSemanticType("cncf", "customer")),
          roles = Set("business-entity")
        ),
        sources = KnowledgeNodeSources(provenanceIds = Vector(provenance.id)),
        bindings = KnowledgeNodeBindings.from(Vector(ext)),
        similarity = KnowledgeNodeSimilarity(
          representations = Vector(KnowledgeSimilarityRepresentation(method = Some("embedding"), model = Some("demo-model"), metric = Some("cosine"))),
          searchEntries = Vector(KnowledgeSimilaritySearchEntry(provider = Some("demo"), collection = Some("customers"), searchId = Some("search-customer-1")))
        ),
        attributes = KnowledgeAttributes("segment" -> "enterprise")
      )
      val concept = KnowledgeNode(KnowledgeNodeId("node-concept"), "concept", Some("Important customer"))
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        KnowledgeRelationshipKind.ClassifiedBy,
        customer.id,
        concept.id,
        rdfPredicate = Some(RdfPredicateName("rdf:type")),
        semanticTypes = Vector(KnowledgeRelationshipSemanticType("rdf", "type")),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val fact = KnowledgeFact(
        KnowledgeFactId("fact-1"),
        KnowledgeFactKind.EntityDerived,
        subjectNodeId = Some(customer.id),
        predicate = Some("customer.status"),
        value = Some("active"),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val frame = KnowledgeFrame(
        KnowledgeFrameId("frame-1"),
        KnowledgeFrameKind.EntityContext,
        focusNodeIds = Vector(customer.id),
        nodeIds = Vector(customer.id, concept.id),
        relationshipIds = Vector(relationship.id),
        factIds = Vector(fact.id),
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id),
        origin = KnowledgeFrameOrigin(
          KnowledgeFrameInputRoute.EntityProjection,
          operation = Some("customer.search"),
          provenanceId = Some(provenance.id)
        )
      )
      _success(component.knowledgeSpace.replace(KnowledgeWorkingSetSnapshot(
        nodes = Vector(customer, concept),
        relationships = Vector(relationship),
        evidence = Vector(evidence),
        provenance = Vector(provenance),
        frames = Vector(frame),
        facts = Vector(fact)
      ))(using ExecutionContext.test()))

      val index = _renderer.renderSystemAdminKnowledge(subsystem).body
      val detail = _renderer.renderSystemAdminKnowledgeComponent(subsystem, "knowledge-component").map(_.body).getOrElse(fail("knowledge component page missing"))
      When("render system admin knowledge pages is exercised")
      val node = _renderer.renderSystemAdminKnowledgeNode(subsystem, "knowledge_component", "node-customer").map(_.body).getOrElse(fail("knowledge node page missing"))

      Then("the observable contract for render system admin knowledge pages holds")
      index should include ("System Knowledge")
      index should include (component.displayName)
      index should include ("/web/system/admin/knowledge/knowledge_component")
      detail should include (s"System Knowledge ${component.displayName}")
      detail should include ("node-customer")
      detail should include ("/web/system/admin/knowledge/knowledge_component/nodes/node-customer")
      detail should not include (s"/web/system/admin/knowledge/${component.name}/nodes/node-customer")
      detail should include ("rel-1")
      detail should include ("frame-1")
      detail should include ("fact-1")
      node should include ("Knowledge Node node-customer")
      node should include ("rdf:customer-1")
      node should include ("cncf:customer")
      node should include ("cncf.entity")
      node should include ("customer-1")
      node should include ("classified-by")
      node should include ("rdf:type")
      node should include ("embedding")
      node should include ("search-customer-1")
      node should include ("Frames")
      node should include ("Facts")
      node should include ("Outgoing relationships")
      node should include ("Customer source record")
      node should include ("renderer-spec")
      val limited = StaticFormAppRenderer(StaticFormAppRendererConfig(previewLimit = 1))
        .renderSystemAdminKnowledgeComponent(subsystem, "knowledge-component")
        .map(_.body)
        .getOrElse(fail("knowledge component page missing"))
      limited should include ("Showing first 1 of 2 nodes.")
      val unconfigured = _renderer.renderSystemAdminKnowledgeComponent(subsystem, "knowledge-component")
        .map(_.body)
        .getOrElse(fail("knowledge component page missing"))
      unconfigured should not include ("Showing first 1 of 2 nodes.")
      _renderer.renderSystemAdminKnowledgeComponent(subsystem, "missing") shouldBe None
      _renderer.renderSystemAdminKnowledgeNode(subsystem, "knowledge_component", "missing") shouldBe None
    }

    "render system admin information pages" in {
      Given("a subsystem containing published Information")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      subsystem.add(TestComponentFactory.create("information_component", Protocol.empty))
      val component = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.InformationComponent")).getOrElse(fail("information component missing"))
      given ExecutionContext = component.logic.executionContext()
      val batch = _success(component.informationSpace.registerInformation(
        "paper",
        Vector(Record.data(
          "title" -> "Knowledge Import",
          "authors" -> "Alice Example",
          "venue" -> "CNCF Notes"
        ))
      ))
      val record = batch.headOption.getOrElse(fail("information record missing"))
      _success(component.informationSpace.validateInformation(record.id))
      val item = _success(component.informationSpace.confirmInformation(record.id))
      _success(component.informationSpace.publishInformation(item.id, "fuseki", Some("published")))

      When("the system Information index and component detail are rendered")
      val index = _renderer.renderSystemAdminInformation(subsystem).body
      val detail = _renderer.renderSystemAdminInformationComponent(subsystem, "information-component").map(_.body).getOrElse(fail("information component page missing"))

      Then("the pages expose component identity and published Information metadata")
      index should include ("System Information")
      index should include (component.displayName)
      index should include ("/web/system/admin/information/information_component")
      detail should include (s"System Information ${component.displayName}")
      detail should include ("Knowledge Import")
      detail should include ("paper")
      detail should include ("published")
      _renderer.renderSystemAdminInformationComponent(subsystem, "missing") shouldBe None
    }

    "reject ambiguous display aliases in system admin projection routes" in {
      Given("two qualified components sharing one system admin display alias")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val first = TestComponentFactory.create(
        "org.example.First",
        Protocol.empty,
        displayName = Some("shared-console")
      )
      val second = TestComponentFactory.create(
        "org.example.Second",
        Protocol.empty,
        displayName = Some("shared-console")
      )
      subsystem.add(Vector(first, second))

      When("system Information and Knowledge selectors are rendered")
      val information = _renderer.renderSystemAdminInformation(subsystem).body
      val knowledge = _renderer.renderSystemAdminKnowledge(subsystem).body
      val ambiguousinformation = _renderer.renderSystemAdminInformationComponent(subsystem, "shared-console")
      val ambiguousknowledge = _renderer.renderSystemAdminKnowledgeComponent(subsystem, "shared-console")
      val firstinformation = _renderer.renderSystemAdminInformationComponent(subsystem, first.name)
      val secondinformation = _renderer.renderSystemAdminInformationComponent(subsystem, second.name)
      val firstknowledge = _renderer.renderSystemAdminKnowledgeComponent(subsystem, first.name)
      val secondknowledge = _renderer.renderSystemAdminKnowledgeComponent(subsystem, second.name)

      Then("ambiguous display aliases are rejected and qualified routes remain distinct")
      information should include (s"/web/system/admin/information/${first.name}")
      information should include (s"/web/system/admin/information/${second.name}")
      knowledge should include (s"/web/system/admin/knowledge/${first.name}")
      knowledge should include (s"/web/system/admin/knowledge/${second.name}")
      ambiguousinformation shouldBe None
      ambiguousknowledge shouldBe None
      firstinformation should not be empty
      secondinformation should not be empty
      firstknowledge should not be empty
      secondknowledge should not be empty
    }

    "reject ambiguous aliases in component dashboard presentation while resolving an exact component ID" in {
      Given("two components sharing both display and artifact presentation aliases")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val first = TestComponentFactory.create(
        "org.example.FirstDashboard",
        Protocol.empty,
        displayName = Some("shared-dashboard")
      )
      val second = TestComponentFactory.create(
        "org.example.SecondDashboard",
        Protocol.empty,
        displayName = Some("shared-dashboard")
      )
      first.withArtifactMetadata(Component.ArtifactMetadata(
        sourceType = "spec",
        name = "shared-dashboard",
        version = "1.0.0",
        component = Some("shared-dashboard"),
        componentId = Some(first.componentId)
      ))
      second.withArtifactMetadata(Component.ArtifactMetadata(
        sourceType = "spec",
        name = "shared-dashboard",
        version = "1.0.0",
        component = Some("shared-dashboard"),
        componentId = Some(second.componentId)
      ))
      subsystem.add(Vector(first, second))

      When("the admin dashboard state selector uses the shared presentation alias")
      val ambiguous = _renderer.renderDashboardState(subsystem, Some("shared-dashboard"))

      And("the selector uses the exact qualified component identity")
      val canonical = _renderer.renderDashboardState(subsystem, Some(first.componentId.name))

      Then("the ambiguous selector dispatches no component result and the canonical selector resolves the intended component")
      ambiguous shouldBe None
      val canonicalbody = canonical.map(_.body).getOrElse(fail("canonical dashboard state is missing"))
      canonicalbody should include (first.componentId.name)
      canonicalbody should not include second.componentId.name
    }

    "render Blob admin read-only pages" in {
      Given("the prerequisites for render Blob admin read-only pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "image", None),
        Property("filename", "cover.png", None),
        Property("contentType", ContentType.IMAGE_PNG.header, None),
        Property("externalUrl", "https://example.test/cover.png", None)
      ))))
      val id = blob.getString("id").getOrElse(fail("Blob id is missing"))
      val sourceid = _notice_entity_id_from_shortid("article_1").value
      _success(subsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", sourceid, None),
        Property("id", id, None),
        Property("role", "mainImage", None)
      )))

      val home = _renderer.renderBlobAdmin().body
      val list = _success(_renderer.renderBlobAdminBlobs(subsystem)).body
      val detail = _success(_renderer.renderBlobAdminBlobDetail(subsystem, id)).body
      val delete = _success(_renderer.renderBlobAdminBlobDelete(subsystem, id)).body
      val associations = _success(_renderer.renderBlobAdminAssociations(subsystem, Map("sourceEntityId" -> sourceid))).body
      When("render Blob admin read-only pages is exercised")
      val store = _success(_renderer.renderBlobAdminStore(subsystem)).body

      Then("the observable contract for render Blob admin read-only pages holds")
      home should include ("Blob Admin")
      home should include ("/web/blob/admin/blobs")
      home should include ("class=\"card admin-card")
      home should include ("class=\"row g-3\"")
      home should include ("Authorization requirements")
      home should include ("collection:blob:delete")
      home should include ("association:blob_attachment:create/delete/search/list")
      home should include ("store:blobstore:status")
      list should include ("Blob Admin Blobs")
      list should include (id)
      list should include ("/web/blob/admin/blobs/")
      list should include ("class=\"table table-sm table-hover align-middle mb-0\"")
      detail should include ("Blob metadata detail")
      detail should include ("https://example.test/cover.png")
      detail should include (s"/web/blob/admin/blobs/${id}/delete")
      detail should include ("class=\"admin-action-row d-flex flex-wrap gap-2\"")
      detail should include ("Raw Blob metadata")
      delete should include ("Delete Blob")
      delete should include ("name=\"force\"")
      delete should include ("class=\"admin-action-row d-flex flex-wrap gap-2\"")
      associations should include ("Blob Admin Associations")
      associations should include ("class=\"row g-3\"")
      associations should include ("class=\"table table-sm table-hover align-middle mb-0\"")
      associations should include ("action=\"/web/blob/admin/associations/attach\"")
      associations should include ("action=\"/web/blob/admin/associations/detach\"")
      associations should include ("data-bs-toggle=\"modal\"")
      associations should include ("class=\"modal fade\"")
      associations should include ("<noscript>")
      associations should include (sourceid)
      associations should include ("mainImage")
      store should include ("Blob Admin Store")
      store should include ("Store Status")
    }

    "render unsafe external Blob URLs as text on admin pages" in {
      Given("the prerequisites for render unsafe external Blob URLs as text on admin pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val id = _create_legacy_external_blob(subsystem, "unsafe.png", "javascript:alert(1)")

      When("render unsafe external Blob URLs as text on admin pages is exercised")
      val list = _success(_renderer.renderBlobAdminBlobs(subsystem)).body

      Then("the observable contract for render unsafe external Blob URLs as text on admin pages holds")
      list should include (id)
      list should include ("javascript:alert(1)")
      list should not include ("href=\"javascript:alert(1)\"")
      subsystem.executeOperationResponse(_blob_request("resolve_blob_url", Property("id", id, None))) shouldBe a[Consequence.Failure[_]]
    }

    "serve Blob admin read-only pages from Web routes" in {
      Given("the prerequisites for serve Blob admin read-only pages from Web routes")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "manual.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/manual.pdf", None)
      ))))
      val id = blob.getString("id").getOrElse(fail("Blob id is missing"))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val home = server.routes(null).orNotFound.run(_get_request("/web/blob/admin")).unsafeRunSync()
      val list = server.routes(null).orNotFound.run(_get_request("/web/blob/admin/blobs")).unsafeRunSync()
      val detail = server.routes(null).orNotFound.run(_get_request(s"/web/blob/admin/blobs/${java.net.URLEncoder.encode(id, StandardCharsets.UTF_8)}")).unsafeRunSync()
      val delete = server.routes(null).orNotFound.run(_get_request(s"/web/blob/admin/blobs/${java.net.URLEncoder.encode(id, StandardCharsets.UTF_8)}/delete")).unsafeRunSync()
      val associations = server.routes(null).orNotFound.run(_get_request("/web/blob/admin/associations")).unsafeRunSync()
      When("serve Blob admin read-only pages from Web routes is exercised")
      val store = server.routes(null).orNotFound.run(_get_request("/web/blob/admin/store")).unsafeRunSync()

      Then("the observable contract for serve Blob admin read-only pages from Web routes holds")
      home.status.code shouldBe 200
      list.status.code shouldBe 200
      detail.status.code shouldBe 200
      delete.status.code shouldBe 200
      associations.status.code shouldBe 200
      store.status.code shouldBe 200
      list.as[String].unsafeRunSync() should include (id)
      detail.as[String].unsafeRunSync() should include ("https://example.test/manual.pdf")
      delete.as[String].unsafeRunSync() should include ("Confirm controlled Blob deletion")
      store.as[String].unsafeRunSync() should include ("BlobStore backend status")
    }

    "serve managed Blob payloads and GET-backed HEAD through the CNCF content route" in {
      Given("the prerequisites for serve managed Blob payloads and GET-backed HEAD through the CNCF content route")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val bytes = "route image".getBytes(StandardCharsets.UTF_8)
      val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary(bytes))),
        properties = List(
          Property("sourceMode", "managed", None),
        Property("kind", "image", None),
        Property("filename", "route.png", None),
        Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val displayurl = blob.getString("displayPath").getOrElse(fail("displayPath is missing"))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val contenteventsbefore = RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.total

      val inline = server.routes(null).orNotFound.run(_get_request(displayurl)).unsafeRunSync()
      val download = server.routes(null).orNotFound.run(_get_request(s"$displayurl?download=true")).unsafeRunSync()
      When("serve managed Blob payloads and GET-backed HEAD through the CNCF content route is exercised")
      def _header_(response: org.http4s.Response[IO], name: String): Option[String] =
        response.headers.get(org.typelevel.ci.CIString(name)).map(_.head.value)

      Then("the observable contract for serve managed Blob payloads and GET-backed HEAD through the CNCF content route holds")
      inline.status.code shouldBe 200
      _header_(inline, "Content-Disposition") shouldBe Some("""inline; filename="route.png"""")
      _header_(inline, "ETag").getOrElse(fail("ETag is missing")) should startWith ("\"")
      _header_(inline, "Last-Modified").getOrElse(fail("Last-Modified is missing")) should include ("GMT")
      _header_(inline, "Content-Length") shouldBe Some(bytes.length.toString)
      _header_(inline, "Cache-Control") shouldBe Some("private, max-age=60")
      _header_(inline, "X-Content-Type-Options") shouldBe Some("nosniff")
      inline.body.compile.to(Array).unsafeRunSync().toVector shouldBe bytes.toVector
      RuntimeDashboardMetrics.blobOperationSnapshot.summary.cumulative.total should be > contenteventsbefore
      download.status.code shouldBe 200
      _header_(download, "Content-Disposition") shouldBe Some("""attachment; filename="route.png"""")
      download.body.compile.to(Array).unsafeRunSync().toVector shouldBe bytes.toVector
      val headinline = server.routes(null).orNotFound.run(_head_request(displayurl)).unsafeRunSync()
      headinline.status.code shouldBe 200
      _header_(headinline, "Content-Disposition") shouldBe _header_(inline, "Content-Disposition")
      _header_(headinline, "ETag") shouldBe _header_(inline, "ETag")
      _header_(headinline, "Last-Modified") shouldBe _header_(inline, "Last-Modified")
      _header_(headinline, "Content-Length") shouldBe _header_(inline, "Content-Length")
      _header_(headinline, "Cache-Control") shouldBe _header_(inline, "Cache-Control")
      _header_(headinline, "X-Content-Type-Options") shouldBe _header_(inline, "X-Content-Type-Options")
      headinline.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty

      val notmodified = server.routes(null).orNotFound.run(
        _get_request(displayurl).putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("If-None-Match"), _header_(inline, "ETag").get)
        )
      ).unsafeRunSync()
      notmodified.status.code shouldBe 304
      _header_(notmodified, "ETag") shouldBe _header_(inline, "ETag")
      notmodified.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
      val headnotmodified = server.routes(null).orNotFound.run(
        _head_request(displayurl).putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("If-None-Match"), _header_(inline, "ETag").get)
        )
      ).unsafeRunSync()
      headnotmodified.status.code shouldBe 304
      _header_(headnotmodified, "ETag") shouldBe _header_(inline, "ETag")
      headnotmodified.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty

      _success(subsystem.executeOperationResponse(_blob_request(
        "admin_delete_blob",
        arguments = Nil,
        properties = List(Property("id", blob.getString("id").getOrElse(fail("id is missing")), None))
      )))
      val missing = server.routes(null).orNotFound.run(
        _get_request(displayurl).putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("If-None-Match"), _header_(inline, "ETag").get)
        )
      ).unsafeRunSync()
      missing.status.code shouldBe 404
      RuntimeDashboardMetrics.blobDiagnosticCounts.getOrElse("not_found", 0L) should be >= 1L
      val headmissing = server.routes(null).orNotFound.run(_head_request(displayurl)).unsafeRunSync()
      headmissing.status.code shouldBe 404
      headmissing.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty

      val refroute = server.routes(null).orNotFound.run(_get_request("/web/blob/content/default/storage-key")).unsafeRunSync()
      refroute.status.code shouldBe 404

      val external = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "image", None),
        Property("filename", "external.png", None),
        Property("contentType", ContentType.IMAGE_PNG.header, None),
        Property("externalUrl", "https://example.test/external.png", None)
      ))))
      val externalid = external.getString("id").getOrElse(fail("external Blob id is missing"))
      val externalcontent = server.routes(null).orNotFound.run(_get_request(s"/web/blob/content/$externalid")).unsafeRunSync()
      externalcontent.status.code shouldBe 400
      val externalhead = server.routes(null).orNotFound.run(_head_request(s"/web/blob/content/$externalid")).unsafeRunSync()
      externalhead.status.code shouldBe 400
      externalhead.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty

      val unsafeblob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("unsafe".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "image", None),
          Property("filename", "bad\";\r\n/name-画像.png", None),
          Property("contentType", ContentType.IMAGE_PNG.header, None)
        )
      ))))
      val unsafeinline = server.routes(null).orNotFound.run(
        _get_request(unsafeblob.getString("displayPath").getOrElse(fail("displayPath is missing")))
      ).unsafeRunSync()
      _header_(unsafeinline, "Content-Disposition") shouldBe
        Some("""inline; filename="bad_____name-__.png"; filename*=UTF-8''bad%22%3B%0D%0A%2Fname-%E7%94%BB%E5%83%8F.png""")
    }

    "serve structured Blob content errors when managed payload is missing" in {
      Given("the prerequisites for serve structured Blob content errors when managed payload is missing")
      val root = Files.createTempDirectory("cncf-blob-content-missing-payload-spec")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(
        Some("server"),
        ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.blobStoreBackendKey -> ConfigurationValue.StringValue(BlobStoreConfig.BackendLocal),
            RuntimeConfig.blobStoreLocalRootKey -> ConfigurationValue.StringValue(root.toString)
          )),
          ConfigurationTrace.empty
        )
      )
      val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        arguments = List(Argument("payload", Bag.binary("missing payload".getBytes(StandardCharsets.UTF_8)))),
        properties = List(
          Property("sourceMode", "managed", None),
          Property("kind", "attachment", None),
          Property("filename", "missing.txt", None),
          Property("contentType", ContentType.TEXT_PLAIN.header, None)
        )
      ))))
      val displaypath = blob.getString("displayPath").getOrElse(fail("displayPath is missing"))
      val storageref = blob.getString("storageRef").getOrElse(fail("storageRef is missing"))
      val key = storageref.stripPrefix("local://default/")
      val payloadpath = root.resolve("default").resolve(key)
      Files.delete(payloadpath)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(_get_request(displaypath)).unsafeRunSync()
      When("serve structured Blob content errors when managed payload is missing is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for serve structured Blob content errors when managed payload is missing holds")
      response.status.code shouldBe 500
      body should include ("Request failed")
      body should include ("<strong>Status:</strong>")
      body should include ("<strong>Status text:</strong>")
      body should include ("managed Blob metadata points at a missing payload")
      body should include ("state")
      body should include ("invalid")
      body should include ("previous")
    }

    "serve Blob admin mutation routes" in {
      Given("the prerequisites for serve Blob admin mutation routes")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val first = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "image", None),
        Property("filename", "delete-me.png", None),
        Property("contentType", ContentType.IMAGE_PNG.header, None),
        Property("externalUrl", "https://example.test/delete-me.png", None)
      ))))
      val firstid = first.getString("id").getOrElse(fail("Blob id is missing"))
      val second = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "attachment", None),
        Property("filename", "attach-me.pdf", None),
        Property("contentType", "application/pdf", None),
        Property("externalUrl", "https://example.test/attach-me.pdf", None)
      ))))
      val secondid = second.getString("id").getOrElse(fail("Blob id is missing"))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val sourceid = _notice_entity_id_from_shortid("product_1").value

      val attach = server.routes(null).orNotFound.run(_post_form_request(
        "/web/blob/admin/associations/attach",
        s"sourceEntityId=${java.net.URLEncoder.encode(sourceid, StandardCharsets.UTF_8)}&id=${java.net.URLEncoder.encode(secondid, StandardCharsets.UTF_8)}&role=manual&sortOrder=7"
      )).unsafeRunSync()
      When("serve Blob admin mutation routes is exercised")
      val attached = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "admin_list_blob_associations",
        Property("sourceEntityId", sourceid, None),
        Property("id", secondid, None)
      ))))

      Then("the observable contract for serve Blob admin mutation routes holds")
      attach.status.code shouldBe 200
      val attachbody = attach.as[String].unsafeRunSync()
      attachbody should include ("Blob Association Attached")
      attachbody should include ("class=\"admin-action-row d-flex flex-wrap gap-2\"")
      attached.getInt("fetchedCount") shouldBe Some(1)

      val detach = server.routes(null).orNotFound.run(_post_form_request(
        "/web/blob/admin/associations/detach",
        s"sourceEntityId=${java.net.URLEncoder.encode(sourceid, StandardCharsets.UTF_8)}&id=${java.net.URLEncoder.encode(secondid, StandardCharsets.UTF_8)}&role=manual"
      )).unsafeRunSync()
      val detached = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "admin_list_blob_associations",
        Property("sourceEntityId", sourceid, None),
        Property("id", secondid, None)
      ))))

      detach.status.code shouldBe 200
      val detachbody = detach.as[String].unsafeRunSync()
      detachbody should include ("Blob Association Detached")
      detachbody should include ("class=\"admin-action-row d-flex flex-wrap gap-2\"")
      detached.getInt("fetchedCount") shouldBe Some(0)

      val delete = server.routes(null).orNotFound.run(_post_form_request(
        s"/web/blob/admin/blobs/${java.net.URLEncoder.encode(firstid, StandardCharsets.UTF_8)}/delete",
        "force=false"
      )).unsafeRunSync()

      delete.status.code shouldBe 200
      val deletebody = delete.as[String].unsafeRunSync()
      deletebody should include ("Blob Deleted")
      deletebody should include ("class=\"admin-action-row d-flex flex-wrap gap-2\"")
      subsystem.executeOperationResponse(_blob_request("admin_get_blob", Property("id", firstid, None))) shouldBe a[Consequence.Failure[_]]
    }

    "render structured Blob admin delete failure and allow forced delete" in {
      Given("the prerequisites for render structured Blob admin delete failure and allow forced delete")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
        "register_blob",
        Property("sourceMode", "external_url", None),
        Property("kind", "image", None),
        Property("filename", "attached.png", None),
        Property("contentType", ContentType.IMAGE_PNG.header, None),
        Property("externalUrl", "https://example.test/attached.png", None)
      ))))
      val id = blob.getString("id").getOrElse(fail("Blob id is missing"))
      val sourceid = _notice_entity_id_from_shortid("product_2").value
      _success(subsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", sourceid, None),
        Property("id", id, None),
        Property("role", "mainImage", None)
      )))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val rejected = server.routes(null).orNotFound.run(_post_form_request(
        s"/web/blob/admin/blobs/${java.net.URLEncoder.encode(id, StandardCharsets.UTF_8)}/delete",
        "force=false"
      )).unsafeRunSync()
      When("render structured Blob admin delete failure and allow forced delete is exercised")
      val forced = server.routes(null).orNotFound.run(_post_form_request(
        s"/web/blob/admin/blobs/${java.net.URLEncoder.encode(id, StandardCharsets.UTF_8)}/delete",
        "force=true"
      )).unsafeRunSync()

      Then("the observable contract for render structured Blob admin delete failure and allow forced delete holds")
      rejected.status.code shouldBe 400
      rejected.as[String].unsafeRunSync() should include ("<strong>Status:</strong>")
      forced.status.code shouldBe 200
      forced.as[String].unsafeRunSync() should include ("Blob Deleted")
      subsystem.executeOperationResponse(_blob_request("admin_get_blob", Property("id", id, None))) shouldBe a[Consequence.Failure[_]]
    }

    "serve structured Blob admin errors instead of missing-page fallbacks" in {
      Given("the prerequisites for serve structured Blob admin errors instead of missing-page fallbacks")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(_get_request("/web/blob/admin/blobs/missing-blob")).unsafeRunSync()
      When("serve structured Blob admin errors instead of missing-page fallbacks is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for serve structured Blob admin errors instead of missing-page fallbacks holds")
      response.status.code shouldBe 400
      body should include ("<strong>Status:</strong>")
      body should include ("missing-blob")
      body should not include ("System job result")
    }

    "deny anonymous Blob admin subroutes in production operation mode" in {
      Given("the prerequisites for deny anonymous Blob admin subroutes in production operation mode")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(
        Some("server"),
        ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"))),
          ConfigurationTrace.empty
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(_get_request("/web/blob/admin/blobs")).unsafeRunSync()
      val mutation = server.routes(null).orNotFound.run(_post_form_request("/web/blob/admin/associations/attach", "sourceEntityId=x&id=y&role=z")).unsafeRunSync()
      When("deny anonymous Blob admin subroutes in production operation mode is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for deny anonymous Blob admin subroutes in production operation mode holds")
      response.status.code shouldBe 403
      mutation.status.code shouldBe 403
      body should include ("Request failed")
      body should include ("<strong>Status:</strong>")
      body should include ("<strong>Status text:</strong>")
    }

    "render resolved runtime configuration with masking rules on system admin page" in {
      Given("the prerequisites for render resolved runtime configuration with masking rules on system admin page")
      val subsystem = new Subsystem(
        name = "masked-system",
        version = Some("1.0.0"),
        configuration = ResolvedConfiguration(
          Configuration(
            Map(
              "textus.runtime.mode" -> ConfigurationValue.StringValue("server"),
              "textus.auth.secret" -> ConfigurationValue.StringValue("open-sesame"),
              "other.value" -> ConfigurationValue.StringValue("hidden-by-scope")
            )
          ),
          ConfigurationTrace.empty
        )
      )

      When("render resolved runtime configuration with masking rules on system admin page is exercised")
      val html = _renderer.renderSystemAdmin(subsystem).body

      Then("the observable contract for render resolved runtime configuration with masking rules on system admin page holds")
      html should include ("Runtime Configuration")
      html should include ("Effective Runtime Policy")
      html should include ("textus.operation-mode")
      html should include ("develop")
      html should include ("textus.runtime.mode")
      html should include ("server")
      html should include ("textus.auth.secret")
      html should include ("********")
      html should include ("masked")
      html should not include ("open-sesame")
      html should not include ("other.value")
    }

    "render resolved Web Descriptor summary on system admin page" in {
      Given("the prerequisites for render resolved Web Descriptor summary on system admin page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val descriptor = WebDescriptor(
        assets = WebDescriptor.Assets(
          autoComplete = false,
          css = Vector("/web/assets/site.css"),
          js = Vector("/web/assets/site.js")
        ),
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(
          roles = Vector("reader"),
          scopes = Vector("notice:read"),
          capabilities = Vector("notice.search"),
          operationModes = Vector(org.goldenport.cncf.config.OperationMode.Develop),
          anonymousOperationModes = Vector(org.goldenport.cncf.config.OperationMode.Test),
          allowAnonymous = true
        )),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(
          enabled = Some(true),
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/search-notices.css"),
            js = Vector("/web/notice-board/notice-board/assets/search-notices.js")
          )
        )),
        apps = Vector(WebDescriptor.App(
          "notice-board",
          "/web/notice-board",
          "static-form",
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/app.css"),
            js = Vector("/web/notice-board/notice-board/assets/app.js")
          )
        )),
        routes = Vector(WebDescriptor.Route(
          "/web/board",
          WebDescriptor.RouteTarget("notice-board", "notice-board")
        )),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      When("render resolved Web Descriptor summary on system admin page is exercised")
      val html = _renderer.renderSystemAdmin(subsystem, descriptor).body

      Then("the observable contract for render resolved Web Descriptor summary on system admin page holds")
      html should include ("Web Descriptor")
      html should include ("configured")
      html should include ("notice-board.notice.search-notices")
      html should include ("public")
      html should include ("Manuals")
      html should include ("/man/system")
      html should include ("Admin entries")
      html should include ("Management Console Controls")
      html should include ("Deferred or unsupported")
      html should include ("Raw selector")
      html should include ("entity.notice")
      html should include ("Destination")
      html should include ("Type")
      html should include ("optional")
    }

    "render resolved Web Descriptor drill-down page" in {
      Given("the prerequisites for render resolved Web Descriptor drill-down page")
      val descriptor = WebDescriptor(
        assets = WebDescriptor.Assets(
          autoComplete = false,
          css = Vector("/web/assets/site.css"),
          js = Vector("/web/assets/site.js")
        ),
        expose = Map("notice-board.notice.search-notices" -> WebDescriptor.Exposure.Public),
        authorization = Map("notice-board.notice.search-notices" -> WebDescriptor.Authorization(
          roles = Vector("reader"),
          scopes = Vector("notice:read"),
          capabilities = Vector("notice.search"),
          operationModes = Vector(org.goldenport.cncf.config.OperationMode.Develop),
          anonymousOperationModes = Vector(org.goldenport.cncf.config.OperationMode.Test),
          allowAnonymous = true
        )),
        form = Map("notice-board.notice.search-notices" -> WebDescriptor.Form(
          enabled = Some(true),
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/search-notices.css"),
            js = Vector("/web/notice-board/notice-board/assets/search-notices.js")
          )
        )),
        apps = Vector(WebDescriptor.App(
          "notice-board",
          "/web/notice-board",
          "static-form",
          assets = WebDescriptor.Assets(
            css = Vector("/web/notice-board/notice-board/assets/app.css"),
            js = Vector("/web/notice-board/notice-board/assets/app.js")
          )
        )),
        routes = Vector(WebDescriptor.Route(
          "/web/board",
          WebDescriptor.RouteTarget("notice-board", "notice-board")
        )),
        admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional))
      )

      When("render resolved Web Descriptor drill-down page is exercised")
      val html = _renderer.renderSystemAdminDescriptor(descriptor).body

      Then("the observable contract for render resolved Web Descriptor drill-down page holds")
      html should include ("System Web Descriptor")
      html should include ("Descriptor Sections")
      html should include ("card admin-card")
      html should include ("nav nav-pills")
      html should include ("href=\"#descriptor-controls\"")
      html should include ("href=\"#asset-composition\"")
      html should include ("href=\"#completed-descriptor\"")
      html should include ("href=\"#configured-descriptor\"")
      html should include ("Descriptor Controls")
      html should include ("Filter descriptor tables")
      html should include ("data-textus-descriptor-filter")
      html should include ("No descriptor rows match the filter.")
      html should include ("table-responsive")
      html should include ("Apps")
      html should include ("Routes")
      html should include ("Form Access And Authorization")
      html should include ("Admin Surfaces")
      html should include ("href=\"/web/notice-board\"")
      html should include ("href=\"/web/board\"")
      html should include ("href=\"/form/notice-board/notice/search-notices\"")
      html should include ("notice:read")
      html should include ("notice.search")
      html should include ("develop")
      html should include ("test")
      html should include ("Asset Composition")
      html should include ("Configured Scopes")
      html should include ("Resolved Form Pages")
      html should include ("component form index")
      html should include ("operation input")
      html should include ("operation result")
      html should include ("Completed Descriptor JSON")
      html should include ("Configured Descriptor JSON")
      html should include ("descriptor-json-details")
      html should include ("<details")
      html should include (">JSON<")
      html should include (">YAML<")
      html should include ("/web/system/admin")
      html should include ("&quot;status&quot; : &quot;configured&quot;")
      html should include ("&quot;notice-board.notice.search-notices&quot; : &quot;public&quot;")
      html should include ("&quot;entity.notice&quot;")
      html should include ("&quot;totalCount&quot; : &quot;optional&quot;")
      html should include ("&quot;roles&quot;")
      html should include ("&quot;reader&quot;")
      html should include ("&quot;enabled&quot; : true")
      html should include ("&quot;path&quot; : &quot;/web/notice-board&quot;")
      html should include ("&quot;root&quot; : &quot;/web/notice-board&quot;")
      html should include ("&quot;route&quot; : &quot;/web/{component}/notice-board&quot;")
      html should include ("&quot;assetComposition&quot;")
      html should include ("&quot;global&quot;")
      html should include ("&quot;apps&quot;")
      html should include ("&quot;forms&quot;")
      html should include ("&quot;resolvedForms&quot;")
      html should include ("&quot;componentFormIndex&quot;")
      html should include ("&quot;operationInput&quot;")
      html should include ("&quot;operationResult&quot;")
      html should include ("/web/assets/site.css")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/notice-board/notice-board/assets/search-notices.css")
    }

    "render component admin configuration detail page" in {
      Given("the prerequisites for render component admin configuration detail page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentlets = Vector(
        ComponentletDescriptor(
          name = "notice-admin",
          kind = Some("componentlet"),
          archiveScope = Some("car-bundled"),
          implementationClass = Some("domain.impl.NoticeAdminComponent"),
          factoryObject = Some("domain.impl.NoticeAdminComponent")
        ),
        ComponentletDescriptor(
          name = "public-notice",
          kind = Some("componentlet"),
          archiveScope = Some("car-bundled"),
          implementationClass = Some("domain.impl.PublicNoticeComponent"),
          factoryObject = Some("domain.impl.PublicNoticeComponent")
        )
      )
      component.withComponentDescriptors(
        if (component.componentDescriptors.nonEmpty)
          component.componentDescriptors.map(_.copy(componentlets = componentlets))
        else
          Vector(ComponentDescriptor(
            name = Some(component.name),
            componentName = Some(component.name),
            componentlets = componentlets
          ))
      )

      When("render component admin configuration detail page is exercised")
      val html = _renderer.renderComponentAdmin(subsystem, component.name).map(_.body).getOrElse(fail("component admin is missing"))

      Then("the observable contract for render component admin configuration detail page holds")
      html should include (s"${component.name} Admin Configuration")
      html should not include ("article { background")
      html should include ("class=\"card admin-card")
      html should include ("nav nav-pills")
      html should include ("class=\"admin-action-row d-flex flex-wrap gap-2")
      html should include ("CNCF version")
      html should include (component.name)
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/dashboard")
      html should include ("/web/system/performance")
      html should include ("/man/system")
      html should include ("/web/console")
      html should include ("Component Admin")
      html should include ("/web/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/descriptor")
      html should include ("Use Application Admin for ordinary operator workflows")
      html should include ("Open Entities")
      html should include ("Open Data")
      html should include ("Open Aggregates")
      html should include ("Open Views")
      html should include ("Open Descriptor")
      html should include ("Open Forms")
      html should include ("Technical details")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/entities")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/data")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/aggregates")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/views")
      html should include ("Componentlets")
      html should include ("notice-admin")
      html should include ("public-notice")
      html should include ("car-bundled")
      html should not include ("Operational Details")
      html should not include ("/web/system/admin/assembly/warnings")
      html should not include ("/form/admin/execution/history")
    }

    "render component-scoped Web Descriptor drill-down page" in {
      Given("the prerequisites for render component-scoped Web Descriptor drill-down page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("notice-board"), WebDescriptor.App("other-board")),
        routes = Vector(
          WebDescriptor.Route("/web/notice", WebDescriptor.RouteTarget(componentpath, "notice-board")),
          WebDescriptor.Route("/web/other", WebDescriptor.RouteTarget("other-board", "other-board"))
        ),
        expose = Map(
          s"${componentpath}.notice.search-notices" -> WebDescriptor.Exposure.Public,
          "other-board.notice.search-notices" -> WebDescriptor.Exposure.Public
        ),
        admin = Map(
          "entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional),
          "other-board.entity.secret" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Required)
        )
      )

      When("render component-scoped Web Descriptor drill-down page is exercised")
      val html = _renderer.renderComponentAdminDescriptor(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("component descriptor admin is missing"))

      Then("the observable contract for render component-scoped Web Descriptor drill-down page holds")
      html should include (s"${component.name} Web Descriptor")
      html should include ("Component Management Console descriptor view")
      html should include (s"/web/${componentpath}/admin")
      html should include ("/web/system/admin/descriptor")
      html should include ("Descriptor Sections")
      html should include ("Completed Descriptor JSON")
      html should include ("Configured Descriptor JSON")
      html should include ("descriptor-json-details")
      html should include (">JSON<")
      html should include (">YAML<")
      html should include ("Descriptor Controls")
      html should include ("Filter descriptor tables")
      html should include ("No descriptor rows match the filter.")
      html should include ("Apps")
      html should include ("Routes")
      html should include ("Form Access And Authorization")
      html should include ("Admin Surfaces")
      html should include ("Destination")
      html should include ("Support")
      html should include ("Raw selector")
      html should include (s"href=\"/web/${componentpath}/notice-board\"")
      html should include ("href=\"/web/notice\"")
      html should include (s"href=\"/form/${componentpath}/notice/search-notices\"")
      html should include (s"href=\"/web/${componentpath}/admin/entities/notice\"")
      html should not include ("href=\"/web/other\"")
      html should not include ("href=\"/form/other-board/notice/search-notices\"")
      html should not include (s"href=\"/web/${componentpath}/admin/entities/secret\"")
      html should include ("Asset Composition")
      html should include ("Configured Scopes")
      html should include ("Resolved Form Pages")
      html should include ("&quot;root&quot; : &quot;/web/notice-board&quot;")
      html should include (s"&quot;route&quot; : &quot;/web/${componentpath}/notice-board&quot;")
      html should include ("&quot;kind&quot; : &quot;static-form&quot;")
    }

    "render read-only system and component manual pages" in {
      Given("the prerequisites for render read-only system and component manual pages")
      val subsystem = _aggregate_http_fixture_subsystem()
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).foreach { component =>
        val componentlets = Vector(
          ComponentletDescriptor(
            name = "notice-admin",
            kind = Some("componentlet"),
            archiveScope = Some("car-bundled"),
            implementationClass = Some("domain.impl.NoticeAdminComponent"),
            factoryObject = Some("domain.impl.NoticeAdminComponent")
          )
        )
        val entitydescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(Schema(Vector(
              Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(BaseContent.simple("body"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(BaseContent.simple("securityAttributes"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
            )))
          )
        )
        component.withComponentDescriptors(
          if (component.componentDescriptors.nonEmpty)
            component.componentDescriptors.map(_.copy(componentlets = componentlets, entityRuntimeDescriptors = entitydescriptors))
          else
            Vector(ComponentDescriptor(
              name = Some(component.name),
              componentName = Some(component.name),
              componentlets = componentlets,
              entityRuntimeDescriptors = entitydescriptors
            ))
        )
      }

      val systemdocumenthtml = _renderer.renderSystemDocument(subsystem).body
      val systemhtml = _renderer.renderSystemManual(subsystem).body
      val componentdocumenthtml = _renderer.renderComponentDocument(subsystem, "notice-board").map(_.body).getOrElse(fail("component document is missing"))
      val componenthtml = _renderer.renderComponentManual(subsystem, "notice-board").map(_.body).getOrElse(fail("component specification is missing"))
      val servicehtml = _renderer.renderComponentManualService(subsystem, "notice-board", "notice-aggregate").map(_.body).getOrElse(fail("service specification is missing"))
      When("render read-only system and component manual pages is exercised")
      val operationhtml = _renderer.renderComponentManualOperation(subsystem, "notice-board", "notice-aggregate", "approve-notice-aggregate").map(_.body).getOrElse(fail("operation specification is missing"))

      Then("the observable contract for render read-only system and component manual pages holds")
      systemdocumenthtml should include ("System Documents")
      systemdocumenthtml should include ("Generated Help")
      systemdocumenthtml should include ("/help/system")
      systemdocumenthtml should include ("/openapi.json")
      systemdocumenthtml should include ("User Guide")
      componentdocumenthtml should include ("org.goldenport.cncf.test.NoticeBoard Documents")
      componentdocumenthtml should include ("Generated Help")
      componentdocumenthtml should include ("/help/notice-board")
      componentdocumenthtml should include ("/openapi.json")
      componentdocumenthtml should include ("Reference Manual")
      systemhtml should include ("System Specification")
      systemhtml should include ("OpenAPI JSON")
      systemhtml should include ("MCP endpoint")
      systemhtml should include ("/help/notice-board")
      systemhtml should include ("class=\"card manual-card shadow-sm\"")
      componenthtml should include ("NoticeBoard Specification")
      componenthtml should include ("Help")
      componenthtml should include ("Describe")
      componenthtml should include ("Schema")
      componenthtml should include ("Componentlets")
      componenthtml should include ("notice-admin")
      componenthtml should include ("car-bundled")
      componenthtml should include ("Raw Describe")
      componenthtml should include ("Raw Schema")
      componenthtml should include ("Storage shape")
      componenthtml should include ("simple_entity_default")
      componenthtml should include ("short_id")
      componenthtml should include ("created_at")
      componenthtml should include ("updated_by")
      componenthtml should include ("owner_id")
      componenthtml should include ("group_id")
      componenthtml should include ("privilege_id")
      componenthtml should include ("permission")
      componenthtml should include ("compact_json_text")
      componenthtml should include ("body")
      componenthtml should include ("scalar_attribute")
      componenthtml should include ("delegated_collection")
      componenthtml should not include ("<td><code>securityAttributes</code></td>")
      componenthtml should include ("/help/notice-board/notice-aggregate")
      componenthtml should include ("manual-summary-table")
      servicehtml should include ("Generated service specification")
      servicehtml should include ("/help/notice-board/notice-aggregate/approve-notice-aggregate")
      operationhtml should include ("Generated operation specification")
      operationhtml should include ("/rest/v1/notice-board/notice-aggregate/approve-notice-aggregate")
      operationhtml should include ("Parameters")
      operationhtml should include ("Raw Help")
      operationhtml should include (">JSON<")
      operationhtml should include (">YAML<")
      operationhtml should include ("approve-notice-aggregate")
      operationhtml should not include ("admin entity")
      operationhtml should not include ("method=\"post\"")

      val blobsubsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val blobattachhtml = _renderer.renderComponentManualOperation(blobsubsystem, "blob", "blob", "admin-attach-blob-to-entity").map(_.body).getOrElse(fail("blob attach specification is missing"))
      blobattachhtml should include ("Image Binding")
      blobattachhtml should include ("existing Blob id")
      blobattachhtml should include ("attach")
      blobattachhtml should include ("primary, cover, thumbnail, gallery, inline")
      blobattachhtml should include ("sourceEntityId")
      blobattachhtml should include ("sortOrder")
      val associationattachhtml = _renderer.renderComponentManualOperation(blobsubsystem, "admin", "association", "admin-attach-association").map(_.body).getOrElse(fail("association attach specification is missing"))
      associationattachhtml should include ("Association Binding")
      associationattachhtml should include ("create")
      associationattachhtml should include ("sourceEntityId")
      associationattachhtml should include ("targetEntityId")
      associationattachhtml should include ("sortOrder")
    }

    "preserve real componentlet path in rendered specification admin and form links" in {
      Given("the prerequisites for preserve real componentlet path in rendered specification admin and form links")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()

      val manualhtml = _renderer.renderComponentManual(subsystem, "notice-admin").map(_.body).getOrElse(fail("component specification is missing"))
      val adminhtml = _renderer.renderComponentAdmin(subsystem, "notice-admin").map(_.body).getOrElse(fail("component admin is missing"))
      When("preserve real componentlet path in rendered specification admin and form links is exercised")
      val formhtml = _renderer.renderFormIndex(subsystem, "notice-admin").map(_.body).getOrElse(fail("form index is missing"))

      Then("the observable contract for preserve real componentlet path in rendered specification admin and form links holds")
      manualhtml should include ("/help/notice-admin/notice-aggregate")
      adminhtml should include ("/web/notice-admin/dashboard")
      adminhtml should include ("/form/notice-admin")
      formhtml should include ("/web/notice-admin/dashboard")
      formhtml should include ("/web/notice-admin/admin")
      formhtml should include ("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
    }

    "not resolve componentlet metadata alone as runtime component in rendered pages" in {
      Given("the prerequisites for not resolve componentlet metadata alone as runtime component in rendered pages")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlet_metadata_only()

      When("the observable result for not resolve componentlet metadata alone as runtime component in rendered pages is inspected")
      locally {
        Then("the observable contract for not resolve componentlet metadata alone as runtime component in rendered pages holds")
        _renderer.renderComponentManual(subsystem, "notice-admin") shouldBe None
        _renderer.renderComponentAdmin(subsystem, "notice-admin") shouldBe None
        _renderer.renderFormIndex(subsystem, "notice-admin") shouldBe None
      }
    }

    "serve generated help and packaged manual routes through standard and compatibility paths" in {
      Given("a subsystem with aggregate componentlet metadata")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("generated help and packaged manual routes are requested")
      val manualresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/document/specification/notice-aggregate/approve-notice-aggregate"))
        .unsafeRunSync()
      val manualhtml = manualresponse.as[String].unsafeRunSync()
      val aliasmanualresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-admin/document/specification/notice-aggregate/approve-notice-aggregate"))
        .unsafeRunSync()
      val aliasmanualhtml = aliasmanualresponse.as[String].unsafeRunSync()
      val openapiresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/system/document/specification/openapi.json"))
        .unsafeRunSync()
      val openapijson = openapiresponse.as[String].unsafeRunSync()
      val canonicalopenapiresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/openapi.json"))
        .unsafeRunSync()
      val canonicalopenapijson = canonicalopenapiresponse.as[String].unsafeRunSync()
      val helpresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/help/notice-board/notice-aggregate/approve-notice-aggregate"))
        .unsafeRunSync()
      val helphtml = helpresponse.as[String].unsafeRunSync()
      val helpopenapiresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/help/system/openapi.json"))
        .unsafeRunSync()
      val helpopenapijson = helpopenapiresponse.as[String].unsafeRunSync()
      val manresponse = server
        .routes(null)
        .orNotFound
        .run(_get_request("/man/notice-board"))
        .unsafeRunSync()
      val manhtml = manresponse.as[String].unsafeRunSync()

      Then("standard and compatibility routes render generated help, OpenAPI, and manuals")
      manualresponse.status.code shouldBe 200
      manualhtml should include ("Generated operation specification")
      manualhtml should include ("approve-notice-aggregate")
      manualhtml should include ("cncf command meta.help notice-board")
      manualhtml should include ("/help/notice-board")
      manualhtml should include ("/man/notice-board")
      manualhtml should include ("/openapi.json")
      manualhtml should include ("/mcp")
      aliasmanualresponse.status.code shouldBe 200
      aliasmanualhtml should include ("Generated operation specification")
      aliasmanualhtml should include ("approve-notice-aggregate")
      openapiresponse.status.code shouldBe 200
      openapijson should include (""""openapi"""")
      openapijson should include ("/rest/v1/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate")
      canonicalopenapiresponse.status.code shouldBe 200
      canonicalopenapijson shouldBe openapijson
      helpresponse.status.code shouldBe 200
      helphtml should include ("Generated operation specification")
      helphtml should include ("approve-notice-aggregate")
      helpopenapiresponse.status.code shouldBe 200
      helpopenapijson should include (""""openapi"""")
      helpopenapijson shouldBe canonicalopenapijson
      manresponse.status.code shouldBe 200
      manhtml should include ("org.goldenport.cncf.test.NoticeBoard Documents")
      manhtml should include ("Packaged component documents")
    }

    "serve the canonical CAR manual source through the component manual route" in {
      Given("a component loaded from a CAR archive containing src/main/car/manual output")
      val archive = _web_archive_fixture(
        "notice-board.car",
        Vector("manual/index.md" -> "# Notice Board Reference Manual\n\nCanonical CAR manual content.\n")
      )
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()
      val component = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).getOrElse(fail("notice-board component is missing"))
      component.withArtifactMetadata(org.goldenport.cncf.component.Component.ArtifactMetadata(
        sourceType = "car",
        name = "notice-board",
        version = "0.1.0",
        component = Some("notice-board"),
        archivePath = Some(archive.toString)
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the component manual index and canonical reference manual are requested")
      val indexresponse = server.routes(null).orNotFound.run(_get_request("/man/notice-board")).unsafeRunSync()
      val indexhtml = indexresponse.as[String].unsafeRunSync()
      val manualresponse = server.routes(null).orNotFound.run(_get_request("/man/notice-board/index.md")).unsafeRunSync()
      val manualcontent = manualresponse.as[String].unsafeRunSync()

      Then("CNCF discovers and serves the CAR manual subtree")
      indexresponse.status.code shouldBe 200
      indexhtml should include ("Reference Manual")
      indexhtml should include ("/man/notice-board/index.md")
      manualresponse.status.code shouldBe 200
      manualcontent should include ("Canonical CAR manual content")
    }

    "apply the existing system document authorization policy to the canonical OpenAPI route" in {
      Given("a subsystem whose Web descriptor denies the system Help OpenAPI selector")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()
      val descriptor = WebDescriptor(authorization = Map(
        "system.help.openapi" -> WebDescriptor.Authorization(deny = true)
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      When("the canonical and compatibility Help OpenAPI routes are requested")
      val canonicalresponse = server.routes(null).orNotFound.run(_get_request("/openapi.json")).unsafeRunSync()
      val compatibilityresponse = server.routes(null).orNotFound.run(_get_request("/help/system/openapi.json")).unsafeRunSync()

      Then("both routes reject the request through the same authorization selector")
      canonicalresponse.status.code shouldBe 403
      compatibilityresponse.status.code shouldBe 403
    }

    "hide generated help and packaged manuals in production operation mode" in {
      Given("a production-mode subsystem")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets(
        Configuration(Map(RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("generated help, packaged manuals, and compatibility document routes are requested")
      val helpresponse = server.routes(null).orNotFound.run(_get_request("/help/notice-board")).unsafeRunSync()
      val manresponse = server.routes(null).orNotFound.run(_get_request("/man/notice-board")).unsafeRunSync()
      val systemhelpresponse = server.routes(null).orNotFound.run(_get_request("/help/system")).unsafeRunSync()
      val canonicalopenapiresponse = server.routes(null).orNotFound.run(_get_request("/openapi.json")).unsafeRunSync()
      val systemopenapiresponse = server.routes(null).orNotFound.run(_get_request("/help/system/openapi.json")).unsafeRunSync()
      val legacyopenapiresponse = server.routes(null).orNotFound.run(_get_request("/web/system/document/specification/openapi.json")).unsafeRunSync()
      val systemmanresponse = server.routes(null).orNotFound.run(_get_request("/man/system")).unsafeRunSync()
      val compatssystemhelpresponse = server.routes(null).orNotFound.run(_get_request("/web/system/document/specification")).unsafeRunSync()
      val compatssystemmanresponse = server.routes(null).orNotFound.run(_get_request("/web/system/document")).unsafeRunSync()
      val compathelpresponse = server.routes(null).orNotFound.run(_get_request("/web/notice-board/document/specification")).unsafeRunSync()
      val compatmanresponse = server.routes(null).orNotFound.run(_get_request("/web/notice-board/document")).unsafeRunSync()

      Then("CNCF hides the inspection surfaces")
      helpresponse.status.code shouldBe 404
      manresponse.status.code shouldBe 404
      systemhelpresponse.status.code shouldBe 404
      canonicalopenapiresponse.status.code shouldBe 404
      systemopenapiresponse.status.code shouldBe 404
      legacyopenapiresponse.status.code shouldBe 404
      systemmanresponse.status.code shouldBe 404
      compatssystemhelpresponse.status.code shouldBe 404
      compatssystemmanresponse.status.code shouldBe 404
      compathelpresponse.status.code shouldBe 404
      compatmanresponse.status.code shouldBe 404
    }

    }

    "provide entity administration and mutation contracts" which {
    "render component entity administration page" in {
      Given("the prerequisites for render component entity administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      When("render component entity administration page is exercised")
      val html = _renderer.renderComponentAdminEntities(subsystem, component.name).map(_.body).getOrElse(fail("component entity admin is missing"))

      Then("the observable contract for render component entity administration page holds")
      html should include (s"${component.name} Entity Administration")
      html should include ("Entity CRUD")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("entity runtime descriptors")
      component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).headOption match {
        case Some(descriptor) =>
          html should include ("class=\"table table-sm table-hover align-middle\"")
          html should include ("Status")
          html should include ("Resident")
          html should include (s"/web/${componentpath}/admin/entities/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(descriptor.entityName)}")
        case None =>
          html should include ("No entity runtime descriptors")
          html should include ("admin-empty-state")
      }
    }

    "render component entity type list page contract" in {
      Given("the prerequisites for render component entity type list page contract")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      When("render component entity type list page contract is exercised")
      val html = _renderer.renderComponentAdminEntityType(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity type admin is missing"))

      Then("the observable contract for render component entity type list page contract holds")
      html should include (s"${component.name} Sales Order Administration")
      html should include ("Sales Order records")
      html should include ("List with paging")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("class=\"btn btn-primary\"")
      html should include (s"/web/${componentpath}/admin/entities")
      html should include (s"/web/${componentpath}/admin/entities/sales-order/new")
      html should include ("No records are currently available")
      html should include ("admin-empty-state")
      html should not include ("sample-id")
      html should include ("Previous")
      html should include ("Next")
    }

    "reject a scalar component entity detail and edit route before it can render actions" in {
      Given("a component entity route with an old scalar ID")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("detail and edit pages are rendered")
      val detail = _renderer.renderComponentAdminEntityDetail(subsystem, component.name, "sales-order", "missing-id")
      val edit = _renderer.renderComponentAdminEntityEdit(subsystem, component.name, "sales-order", "missing-id")

      Then("neither page is emitted and no action route can be exposed")
      detail shouldBe None
      edit shouldBe None
    }

    "render component entity pages from a live EntityCollection fixture" in {
      Given("the prerequisites for render component entity pages from a live EntityCollection fixture")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordentityid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id
      val recordid = recordentityid.value

      val list = _renderer.renderComponentAdminEntityType(subsystem, componentname, entitypath).map(_.body).getOrElse(fail("component entity type admin is missing"))
      val firstpage = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1)
      ).map(_.body).getOrElse(fail("component entity first page admin is missing"))
      val secondpage = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1)
      ).map(_.body).getOrElse(fail("component entity second page admin is missing"))
      val totalpage = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
        WebDescriptor(admin = Map("entity.notice" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
      ).map(_.body).getOrElse(fail("component entity total page admin is missing"))
      val detail = _renderer.renderComponentAdminEntityDetail(subsystem, componentname, entitypath, recordid).map(_.body).getOrElse(fail("component entity detail admin is missing"))
      When("render component entity pages from a live EntityCollection fixture is exercised")
      val edit = _renderer.renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for render component entity pages from a live EntityCollection fixture holds")
      list should include ("Storage shape")
      list should include ("admin-search-card")
      list should include ("name=\"q\"")
      list should include ("name=\"searchMode\"")
      list should include ("<option value=\"full-text\" selected>")
      list should include ("<option value=\"semantic\"")
      list should include ("name=\"sort\"")
      list should include ("simple_entity_default")
      list should include ("admin-storage-shape-fields")
      list should include ("short_id")
      list should include ("created_at")
      list should include ("updated_by")
      list should include ("owner_id")
      list should include ("group_id")
      list should include ("privilege_id")
      list should include ("permission")
      list should include ("compact_json_text")
      list should include ("title")
      list should include ("scalar_attribute")
      list should include (recordid)
      list should include ("<th>id</th><th>shortid</th><th>title</th><th>author</th><th>Actions</th>")
      list should include ("class=\"btn-group btn-group-sm\"")
      list should include ("board update")
      list should include ("alice")
      list should include (s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}")
      list should include (s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}/edit")
      list should not include ("No records are currently available")
      firstpage should include ("Page 1")
      firstpage should include ("page=2&amp;pageSize=1")
      firstpage should include ("Next")
      secondpage should include ("Page 2")
      secondpage should include ("page=1&amp;pageSize=1")
      secondpage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/notice-board/admin/entities/notice?page=3&amp;pageSize=1\">Next")
      totalpage should include ("total 2")
      totalpage should include ("includeTotal=true")
      detail should include ("board update")
      detail should include ("alice")
      detail should include ("Images")
      detail should include ("Representative Image")
      detail should include ("Attach Existing Blob")
      detail should include ("Associated Images")
      detail should include ("action=\"/web/blob/admin/associations/attach\"")
      detail should include ("name=\"sourceEntityId\"")
      detail should include ("entityImageRoleOptions")
      detail should include ("No BlobAttachment images are associated with this Entity.")
      edit should include ("name=\"title\"")
      edit should include ("value=\"board update\"")
      edit should include (
        s"""type="hidden" name="version" value="${_notice_entity_version(subsystem, recordid)}""""
      )
      edit should include (s"/form/${componentpath}/admin/entities/${entitypath}/${recordid}/update")

      val searched = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        StaticFormAppRenderer.PageRequest(page = 1, pageSize = 20),
        pageContext = Map("q" -> "board", "author" -> "alice", "sort" -> "title", "direction" -> "desc")
      ).map(_.body).getOrElse(fail("component entity searched admin is missing"))
      searched should include ("Active filters")
      searched should include ("q: board")
      searched should include ("author: alice")
      searched should include ("sort: title")
      searched should include ("value=\"board\"")
      searched should include ("value=\"alice\"")
      searched should include ("<option value=\"title\" selected>")
      searched should include ("<option value=\"desc\" selected>")
      searched should include ("board update")
      searched should not include ("No records are currently available")

      val semantic = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        pageContext = Map("q" -> "board", "searchMode" -> "semantic")
      ).map(_.body).getOrElse(fail("component entity semantic admin is missing"))
      semantic should include ("admin-search-feedback")
      semantic should include ("Semantic or hybrid search is not configured")
      semantic should include ("No records are currently available")
      semantic should not include ("board update")
    }

    "reject a foreign canonical component entity detail and edit route before it can render actions" in {
      Given("a foreign EntityId with the same local timestamp and entropy as a stored notice")
      val subsystem = _management_console_fixture_subsystem()
      val storedid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head.id
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = EntityId(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,
        timestamp = storedid.timestamp,
        entropy = storedid.entropy
      ).value

      When("detail and edit pages are rendered for the foreign owner")
      val detail = _renderer.renderComponentAdminEntityDetail(subsystem, "notice_board", "notice", foreignid)
      val edit = _renderer.renderComponentAdminEntityEdit(subsystem, "notice_board", "notice", foreignid)

      Then("neither page is emitted and no foreign-owner action route can be exposed")
      detail shouldBe None
      edit shouldBe None
    }

    "render generic non-image Associations on entity detail pages" in {
      Given("the prerequisites for render generic non-image Associations on entity detail pages")
      val relationships = Vector(
        CmlEntityRelationshipDefinition(
          name = "Notice.related",
          kind = CmlEntityRelationshipDefinition.KindAssociation,
          sourceEntityName = "notice",
          targetEntityName = "notice",
          multiplicity = Some("one-to-many"),
          storageMode = CmlEntityRelationshipDefinition.StorageAssociationRecord,
          associationDomain = Some("related_entity"),
          targetKind = Some("notice"),
          targetRole = Some("related"),
          lifecyclePolicy = Some(CmlEntityRelationshipDefinition.LifecycleIndependent)
        ),
        CmlEntityRelationshipDefinition(
          name = "Notice.readOnly",
          kind = CmlEntityRelationshipDefinition.KindAssociation,
          sourceEntityName = "notice",
          targetEntityName = "notice",
          storageMode = CmlEntityRelationshipDefinition.StorageAssociationRecord
        ),
        CmlEntityRelationshipDefinition(
          name = "Notice.images",
          kind = CmlEntityRelationshipDefinition.KindAssociation,
          sourceEntityName = "notice",
          targetEntityName = "Blob",
          storageMode = CmlEntityRelationshipDefinition.StorageAssociationRecord,
          associationDomain = Some("blob_attachment"),
          targetKind = Some("blob")
        )
      )
      val subsystem = _management_console_fixture_subsystem(relationships = relationships)
      val component = _notice_fixture_component(subsystem)
      val notices = component.entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.toVector
      given EntityPersistent[NoticeEntity] = _notice_persistent
      given ExecutionContext = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component is missing")).logic.executionContext()
      notices.foreach { notice =>
        org.goldenport.cncf.entity.EntityStore.standard().save(notice)
          .getOrElse(fail(s"admin notice fixture seed failed: ${notice.id.print}"))
      }
      val source = notices.find(_.title == "board update").getOrElse(fail("source notice missing")).id
      val target = notices.find(_.title == "board followup").getOrElse(fail("target notice missing")).id
      _admin_record_response(
        subsystem,
        "association",
        "admin_attach_association",
        "domain" -> "related_entity",
        "sourceEntityId" -> source.value,
        "targetEntityId" -> target.value,
        "targetKind" -> "notice",
        "role" -> "related",
        "sortOrder" -> "1"
      )
      _admin_record_response(
        subsystem,
        "association",
        "admin_attach_association",
        "domain" -> "related_entity",
        "sourceEntityId" -> source.value,
        "targetEntityId" -> source.value,
        "targetKind" -> "notice",
        "role" -> "self",
        "sortOrder" -> "2"
      )

      val detail = _renderer.renderComponentAdminEntityDetail(subsystem, "notice_board", "notice", source.value).map(_.body).getOrElse(fail("component entity detail admin is missing"))
      val manual = _renderer.renderComponentManual(subsystem, "notice_board").map(_.body).getOrElse(fail("component manual is missing"))
      When("render generic non-image Associations on entity detail pages is exercised")
      val associationpage = _renderer.renderAdminAssociations(subsystem, Map("domain" -> "related_entity", "sourceEntityId" -> source.value, "pageSize" -> "1")).map(_.body).getOrElse(fail("association admin page is missing"))

      Then("the observable contract for render generic non-image Associations on entity detail pages holds")
      detail should include ("Associations")
      detail should include ("Notice.related")
      detail should include ("Notice.readOnly")
      detail should include ("related_entity")
      detail should include (target.value)
      detail should include ("action=\"/web/admin/associations/attach\"")
      detail should include ("action=\"/web/admin/associations/detach\"")
      detail should include ("metadata-only")
      detail should not include ("domain=&amp;sourceEntityId")
      detail should not include ("Notice.images")
      manual should include ("Relationships")
      manual should include ("related_entity")
      manual should include ("Target kind")
      manual should include ("Lifecycle")
      associationpage should include ("Association Administration")
      associationpage should include ("Attach Association")
      associationpage should include ("page 1, page size 1")
      associationpage should include ("page=2")
      associationpage should include (target.value)
      associationpage should include ("class=\"row g-3\"")
      associationpage should include ("class=\"table table-sm table-hover align-middle mb-0\"")
      associationpage should include ("data-bs-toggle=\"modal\"")
      associationpage should include ("class=\"modal fade\"")
      associationpage should include ("<noscript>")
    }

    "render generic Tag admin and entity TagAttachment surfaces" in {
      Given("the prerequisites for render generic Tag admin and entity TagAttachment surfaces")
      val subsystem = _management_console_fixture_subsystem()
      val component = _notice_fixture_component(subsystem)
      val notice = component.entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head
      val source = notice.id
      val sourceid = source.value
      given EntityPersistent[NoticeEntity] = _notice_persistent
      given ExecutionContext = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component is missing")).logic.executionContext()
      org.goldenport.cncf.entity.EntityStore.standard().save(notice)
        .getOrElse(fail(s"admin notice fixture seed failed: ${notice.id.print}"))
      val root = _tag_record_response(
        subsystem,
        "tag_create",
        "key" -> "admin-tag-root",
        "tagSpace" -> "admin-tag-space",
        "title" -> "Admin Tag Root"
      )
      val rootpath = root.getString("path").getOrElse(fail("tag path is missing"))
      val child = _tag_record_response(
        subsystem,
        "tag_create",
        "key" -> "child",
        "tagSpace" -> "admin-tag-space",
        "parentTagRef" -> rootpath,
        "usageKind" -> "cms",
        "title" -> "Child tag"
      )
      val childpath = child.getString("path").getOrElse(fail("child tag path is missing"))
      val childid = child.getString("id").getOrElse(fail("child tag id is missing"))
      _tag_record_response(
        subsystem,
        "tag_attach",
        "sourceEntityId" -> childid,
        "tagSpace" -> "admin-tag-space",
        "tagRef" -> childpath,
        "role" -> "tag"
      )
      _tag_record_response(
        subsystem,
        "tag_attach",
        "sourceEntityId" -> childid,
        "tagSpace" -> "admin-tag-space",
        "tagRef" -> childpath,
        "role" -> "category",
        "sortOrder" -> "2"
      )
      _tag_record_response(
        subsystem,
        "tag_attach",
        "sourceEntityId" -> sourceid,
        "tagSpace" -> "admin-tag-space",
        "tagRef" -> childpath,
        "role" -> "notice"
      )

      val tagpage = _page_body(_renderer.renderAdminTags(subsystem, Map("tagSpace" -> "admin-tag-space")), "Tag admin page")
      val apptagpage = _page_body(_renderer.renderAppTags(subsystem, Map("tagSpace" -> "admin-tag-space")), "app Tag page")
      val editableapptagpage = _page_body(_renderer.renderAppTags(
        subsystem,
        Map("tagSpace" -> "admin-tag-space"),
        Vector("pageContext.session.authenticated" -> "true", "x-textus-session" -> "session-1")
      ), "editable app Tag page")
      val forgedapptagpage = _page_body(_renderer.renderAppTags(
        subsystem,
        Map("tagSpace" -> "admin-tag-space"),
        Vector("x-textus-session" -> "forged-session")
      ), "forged app Tag page")
      val forgedcreate = _renderer.renderAppTagCreateResult(
        subsystem,
        Map("tagSpace" -> "admin-tag-space", "key" -> "forged"),
        Vector("x-textus-session" -> "forged-session")
      )
      val searchpage = _page_body(_renderer.renderAdminTags(subsystem, Map(
        "tagSpace" -> "admin-tag-space",
        "component" -> BuiltinComponentIdentity.TAG.name,
        "entity" -> "tag",
        "tagRef" -> rootpath,
        "role" -> "tag"
      )), "Tag search page")
      val appsearchpage = _page_body(_renderer.renderAppTags(subsystem, Map(
        "tagSpace" -> "admin-tag-space",
        "component" -> BuiltinComponentIdentity.TAG.name,
        "entity" -> "tag",
        "tagRef" -> rootpath,
        "role" -> "tag"
      )), "app Tag search page")
      val emptytagpage = _page_body(_renderer.renderAdminTags(subsystem, Map("tagSpace" -> "admin-empty-tag-space")), "empty Tag admin page")
      val emptysearchpage = _page_body(_renderer.renderAdminTags(subsystem, Map(
        "tagSpace" -> "admin-tag-space",
        "component" -> BuiltinComponentIdentity.TAG.name,
        "entity" -> "tag",
        "tagRef" -> rootpath,
        "role" -> "missing-role"
      )), "empty Tag search page")
      When("render generic Tag admin and entity TagAttachment surfaces is exercised")
      val detail = _renderer.renderComponentAdminEntityDetail(
        subsystem,
        "tag",
        "tag",
        childid,
        values = Map("tagSpace" -> "admin-tag-space")
      ).map(_.body).getOrElse(fail("component entity detail admin is missing"))

      Then("the observable contract for render generic Tag admin and entity TagAttachment surfaces holds")
      tagpage should include ("Tag Administration")
      tagpage should include ("TagSpace selector")
      tagpage should include ("Current TagSpace <span class=\"badge text-bg-secondary\">admin-tag-space</span>")
      tagpage should include ("Create Tag")
      tagpage should include ("Search Entities by Tag")
      tagpage should include ("class=\"row g-3\"")
      tagpage should include ("class=\"card admin-card h-100\"")
      tagpage should include ("class=\"table table-sm table-hover align-middle mb-0\"")
      tagpage should include ("class=\"badge text-bg-secondary\">admin-tag-space</span>")
      tagpage should include ("class=\"badge text-bg-light border\">cms</span>")
      tagpage should include ("class=\"input-group input-group-sm\"")
      tagpage should include ("action=\"/web/admin/tags/create\"")
      tagpage should include ("action=\"/web/admin/tags/update\"")
      tagpage should include ("action=\"/web/admin/tags/move\"")
      tagpage should include ("action=\"/web/admin/tags\"")
      tagpage should include ("name=\"tagSpace\" value=\"admin-tag-space\"")
      tagpage should include ("<input type=\"hidden\" name=\"tagSpace\" value=\"admin-tag-space\">")
      tagpage should include ("name=\"includeDescendants\"")
      tagpage should include ("admin-tag-root.child")
      apptagpage should include ("Application TagSpace browser and editor")
      apptagpage should include ("TagSpace <span class=\"badge text-bg-secondary\">admin-tag-space</span>")
      apptagpage should include ("action=\"/web/tag/tags\"")
      apptagpage should include ("action=\"/web/tag/tags/create\"")
      apptagpage should include ("data-textus-capability=\"tag:edit\"")
      apptagpage should include ("data-textus-capability-policy=\"authenticated\"")
      apptagpage should include ("disabled")
      apptagpage should not include ("Raw tag tree")
      apptagpage should not include ("action=\"/web/tag/tags/update\"")
      editableapptagpage should include ("action=\"/web/tag/tags/update\"")
      editableapptagpage should include ("action=\"/web/tag/tags/move\"")
      editableapptagpage should not include ("Raw tag tree")
      forgedapptagpage should include ("disabled")
      forgedapptagpage should not include ("action=\"/web/tag/tags/update\"")
      forgedcreate shouldBe a[Consequence.Failure[_]]
      emptytagpage should include ("No Tags are available for this TagSpace.")
      emptytagpage should include ("alert alert-secondary")
      searchpage should include ("admin-tag-root.child")
      searchpage should include (childid)
      searchpage should include ("/web/org-goldenport-cncf-tag/admin/entities/tag/")
      searchpage should include ("Tag search result")
      searchpage should include ("class=\"badge text-bg-light border align-self-start\">")
      appsearchpage should include ("Tag search result")
      appsearchpage should include ("action=\"/web/tag/tags\"")
      appsearchpage should not include ("Raw tag tree")
      emptysearchpage should include ("No visible Entities matched this Tag filter.")
      emptysearchpage should include ("alert alert-secondary")
      detail should include ("Tags")
      detail should include ("Attached Tags")
      detail should include ("action=\"/web/admin/tags/attach\"")
      detail should include ("action=\"/web/admin/tags/detach\"")
      detail should include ("data-bs-toggle=\"modal\"")
      detail should include ("class=\"modal fade\"")
      detail should include ("<noscript>")
      detail should include ("admin-tag-root.child")
      detail should include ("name=\"sourceEntityId\" value=\"" + childid + "\"")
      detail should include ("name=\"tagSpace\" value=\"admin-tag-space\"")
      detail should include ("name=\"role\" value=\"tag\"")
      detail should include ("name=\"role\" value=\"category\"")
      detail should include ("<td>category</td>")
    }

    "render component admin storage shape from projection metadata without legacy containers" in {
      Given("the prerequisites for render component admin storage shape from projection metadata without legacy containers")
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "title", "securityAttributes"))
      When("render component admin storage shape from projection metadata without legacy containers is exercised")
      val html = _renderer.renderComponentAdminEntityType(subsystem, "notice_board", "notice").map(_.body).getOrElse(fail("component entity type admin is missing"))

      Then("the observable contract for render component admin storage shape from projection metadata without legacy containers holds")
      html should include ("Storage shape")
      html should include ("simple_entity_default")
      html should include ("permission")
      html should include ("compact_json_text")
      html should not include ("<td><code>securityAttributes</code></td>")
    }

    "render delegated collection storage shape in component admin entity type page" in {
      Given("the prerequisites for render delegated collection storage shape in component admin entity type page")
      val subsystem = _aggregate_http_fixture_subsystem()
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).foreach { component =>
        component.withComponentDescriptors(Vector(ComponentDescriptor(
          name = Some(component.name),
          componentName = Some(component.name),
          entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "body"))
          ))
        )))
      }

      When("render delegated collection storage shape in component admin entity type page is exercised")
      val html = _renderer.renderComponentAdminEntityType(subsystem, "notice-board", "notice").map(_.body).getOrElse(fail("component entity type admin is missing"))

      Then("the observable contract for render delegated collection storage shape in component admin entity type page holds")
      html should include ("Storage shape")
      html should include ("delegated_collection")
      html should include ("aggregate")
    }

    "preserve list paging and search context through entity detail and edit links" in {
      Given("the prerequisites for preserve list paging and search context through entity detail and edit links")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordentityid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id
      val recordid = recordentityid.value
      val context = Map(
        "search.author" -> "alice",
        "paging.page" -> "2",
        "paging.pageSize" -> "1",
        "crud.origin.href" -> s"/web/${componentpath}/admin/entities/${entitypath}?page=2&pageSize=1&search.author=alice"
      )

      val list = _renderer.renderComponentAdminEntityType(
        subsystem,
        componentname,
        entitypath,
        StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1),
        pageContext = context
      ).map(_.body).getOrElse(fail("component entity list admin is missing"))
      val detail = _renderer.renderComponentAdminEntityDetail(
        subsystem,
        componentname,
        entitypath,
        recordid,
        values = context
      ).map(_.body).getOrElse(fail("component entity detail admin is missing"))
      When("preserve list paging and search context through entity detail and edit links is exercised")
      val edit = _renderer.renderComponentAdminEntityEdit(
        subsystem,
        componentname,
        entitypath,
        recordid,
        values = context
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for preserve list paging and search context through entity detail and edit links holds")
      list should include (s"/web/${componentpath}/admin/entities/${entitypath}/")
      list should include ("?crud.origin.href=")
      list should include ("paging.page=2")
      list should include ("paging.pageSize=1")
      list should include ("search.author=alice")
      list should include ("/edit?crud.origin.href=")
      detail should include (s"/web/${componentpath}/admin/entities/${entitypath}?crud.origin.href=")
      detail should include (s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}/edit?crud.origin.href=")
      edit should include ("type=\"hidden\" name=\"crud.origin.href\"")
      edit should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      edit should include ("type=\"hidden\" name=\"paging.pageSize\" value=\"1\"")
      edit should include ("type=\"hidden\" name=\"search.author\" value=\"alice\"")
    }

    "apply component entity update form POST through EntityCollection into EntityStoreSpace" in {
      Given("the prerequisites for apply component entity update form POST through EntityCollection into EntityStoreSpace")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordentityid = collection.storage.storeRealm.values.head.id
      val recordid = recordentityid.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=board+updated&author=bob&version=${_notice_entity_version(subsystem, recordid)}"
      )

      When("apply component entity update form POST through EntityCollection into EntityStoreSpace is exercised")
      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for apply component entity update form POST through EntityCollection into EntityStoreSpace holds")
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      val updated = collection.storage.storeRealm.values.find(_.id.value == recordid).getOrElse(fail("updated entity is missing"))
      updated.title shouldBe "board updated"
      updated.author shouldBe "bob"
      val stored = _load_notice_store_record(subsystem, updated.id)
      stored.getString("title") shouldBe Some("board updated")
      stored.getString("author") shouldBe Some("bob")
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/update")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some(recordid)
    }

    "reject unresolved scalar and canonical component entity update routes before Admin dispatch" in {
      Given("update forms whose route locators cannot resolve to the route collection")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val canonicalid = _new_notice_entity_id().value
      val storedid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head.id
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = EntityId(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,
        timestamp = Some(java.time.Instant.EPOCH),
        entropy = Some(storedid.parts.entropy)
      ).value

      When("an unresolved scalar route is submitted")
      val scalarresponse = server
        ._submit_component_admin_entity_update(
          _post_form_request("/form/notice-board/admin/entities/notice/unknown/update", "title=valid&author=route"),
          "notice-board",
          "notice",
          "unknown"
        )
        .unsafeRunSync()

      And("a canonical ID is paired with an unknown entity route")
      val canonicalresponse = server
        ._submit_component_admin_entity_update(
          _post_form_request(s"/form/notice-board/admin/entities/missing/${canonicalid}/update", "title=valid&author=route"),
          "notice-board",
          "missing",
          canonicalid
        )
        .unsafeRunSync()

      And("a foreign canonical ID collides with a local short route ID")
      val foreignresponse = server
        ._submit_component_admin_entity_update(
          _post_form_request(s"/form/notice-board/admin/entities/notice/${foreignid}/update", "title=valid&author=route"),
          "notice-board",
          "notice",
          foreignid
        )
        .unsafeRunSync()

      Then("all requests return a client error without forwarding an ID to Admin")
      scalarresponse.status.code shouldBe 400
      scalarresponse.as[String].unsafeRunSync() should include ("Unknown entity route id")
      canonicalresponse.status.code shouldBe 400
      canonicalresponse.as[String].unsafeRunSync() should include ("Unknown entity route id")
      foreignresponse.status.code shouldBe 400
      foreignresponse.as[String].unsafeRunSync() should include ("Unknown entity route id")
      dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/entity/update")
    }

    "reject a stale component entity update form without replacing the committed Entity" in {
      Given("two admin forms rendered from the same authoritative Entity version")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher =
        new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server =
        HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection =
        _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val entityid = collection.storage.storeRealm.values.head.id
      val version = _notice_entity_version(subsystem, entityid.value)

      When("the first form commits and the second form submits the stale version")
      val first = server
        ._submit_component_admin_entity_update(
          _post_form_request(
            s"/form/notice-board/admin/entities/notice/${entityid.value}/update",
            s"title=first+writer&author=alice&version=${version}"
          ),
          "notice-board",
          "notice",
          entityid.value
        )
        .unsafeRunSync()
      val stale = server
        ._submit_component_admin_entity_update(
          _post_form_request(
            s"/form/notice-board/admin/entities/notice/${entityid.value}/update",
            s"title=stale+writer&author=bob&version=${version}"
          ),
          "notice-board",
          "notice",
          entityid.value
        )
        .unsafeRunSync()
      val firsthtml = first.as[String].unsafeRunSync()
      val stalehtml = stale.as[String].unsafeRunSync()

      Then("the stale form reports conflict and cannot replace the first commit")
      firsthtml should include ("Applied</th><td>true")
      stalehtml should include ("Applied</th><td>false")
      stalehtml should include ("result.status</th><td>400")
      stalehtml should include ("operation.conflict")
      val stored = _load_notice_store_record(subsystem, entityid)
      stored.getString("title") shouldBe Some("first writer")
      stored.getString("author") shouldBe Some("alice")
      _notice_entity_version(subsystem, entityid.value) should not be version
    }

    "treat an unchanged component entity update form as a successful revision-preserving no-op" in {
      Given("an admin form carrying the current detached Entity revision and unchanged business values")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher =
        new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server =
        HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection =
        _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val entity = collection.storage.storeRealm.values.head
      val version = _notice_entity_version(subsystem, entity.id.value)
      val before = _load_notice_store_record(subsystem, entity.id)

      When("the generated Web form submits the unchanged Entity state")
      val response = server
        ._submit_component_admin_entity_update(
          _post_form_request(
            s"/form/notice-board/admin/entities/notice/${entity.id.value}/update",
            s"title=board+update&author=alice&version=${version}"
          ),
          "notice-board",
          "notice",
          entity.id.value
        )
        .unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      Then("WriteIfChanged reports success without advancing the authoritative revision")
      response.status.code shouldBe 200
      html should include ("Applied</th><td>true")
      dispatcher.headers.last.getString(
        EntityMutationAdapterDefaults.profilePropertyName
      ) shouldBe Some(EntityMutationAdapterDefaults.webFormProfile)
      val stored = _load_notice_store_record(subsystem, entity.id)
      stored.asMap.filterNot(_._1 == "cncf_revision") shouldBe
        before.asMap.filterNot(_._1 == "cncf_revision")
      _notice_entity_version(subsystem, entity.id.value) shouldBe version
      stored.getString("title") shouldBe Some("board update")
      stored.getString("author") shouldBe Some("alice")
    }

    "redirect component entity update by admin form descriptor transition" in {
      Given("the prerequisites for redirect component entity update by admin form descriptor transition")
      val subsystem = _management_console_fixture_subsystem()
      val descriptor = WebDescriptor(
        form = Map(
          "notice-board.admin.entities.notice.update" -> WebDescriptor.Form(
            successRedirect = Some("/web/${component}/admin/${surface}/${collection}/${id}")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=board+redirected&author=bob&version=${_notice_entity_version(subsystem, recordid)}"
      )

      When("redirect component entity update by admin form descriptor transition is exercised")
      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .unsafeRunSync()

      Then("the observable contract for redirect component entity update by admin form descriptor transition holds")
      response.status.code shouldBe 303
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some(s"/web/notice-board/admin/entities/notice/${recordid}")
      collection.storage.storeRealm.values.exists(x => x.id.value == recordid && x.title == "board redirected") shouldBe true
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/update")
    }

    "redisplay component entity update form with submitted values when admin stayOnError is enabled" in {
      Given("the prerequisites for redisplay component entity update form with submitted values when admin stayOnError is enabled")
      val subsystem = _management_console_fixture_subsystem()
      val descriptor = WebDescriptor(
        form = Map(
          "notice-board.admin.entities.notice.update" -> WebDescriptor.Form(stayOnError = true)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid entity update", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=bad+title&author=bob&version=${_notice_entity_version(subsystem, recordid)}"
      )

      When("redisplay component entity update form with submitted values when admin stayOnError is enabled is exercised")
      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for redisplay component entity update form with submitted values when admin stayOnError is enabled holds")
      html should include ("Edit Notice")
      html should include ("error.status")
      html should include ("400")
      html should include ("invalid entity update")
      html should include ("value=\"bad title\"")
      html should include ("value=\"bob\"")
    }

    "redisplay component entity update form with field validation errors before dispatch" in {
      Given("the prerequisites for redisplay component entity update form with field validation errors before dispatch")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=&author=bob&version=${_notice_entity_version(subsystem, recordid)}&crud.origin.href=%2Fweb%2Fnotice-board%2Fadmin%2Fentities%2Fnotice%3Fpage%3D2&paging.page=2&search.author=bob"
      )

      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .unsafeRunSync()
      When("redisplay component entity update form with field validation errors before dispatch is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay component entity update form with field validation errors before dispatch holds")
      response.status.code shouldBe 400
      html should include ("Edit Notice")
      html should include ("Validation failed.")
      html should include ("data-textus-validation-summary=\"form\"")
      html should include ("data-textus-validation-message=\"error\"")
      html should include ("data-textus-validation-field=\"title\"")
      html should include ("data-textus-validation-code=\"required\"")
      html should include ("data-textus-issue-scope=\"form\"")
      html should include ("data-textus-issue-scope=\"field\"")
      html should include ("admin-feedback")
      html should include ("title is required.")
      html should include ("is-invalid")
      html should include ("value=\"bob\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board/admin/entities/notice?page=2\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"search.author\" value=\"bob\"")
      dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/entity/update")
    }

    "validate component entity update forms by detail view fields before full schema fields" in {
      Given("the prerequisites for validate component entity update forms by detail view fields before full schema fields")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewfields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id.value
      val edit = _renderer
        .renderComponentAdminEntityEdit(subsystem, "notice_board", "notice", recordid)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=detail+only&version=${_notice_entity_version(subsystem, recordid)}"
      )

      When("validate component entity update forms by detail view fields before full schema fields is exercised")
      val html = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for validate component entity update forms by detail view fields before full schema fields holds")
      edit should include ("name=\"title\"")
      edit should not include ("name=\"author\"")
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.exists(x => x.id.value == recordid && x.title == "detail only") shouldBe true
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/update")
    }

    "reject component entity update forms when a required detail view field is empty" in {
      Given("the prerequisites for reject component entity update forms when a required detail view field is empty")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewfields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id.value
      val req = _post_form_request(
        s"/form/notice-board/admin/entities/notice/${recordid}/update",
        s"title=&author=ignored&version=${_notice_entity_version(subsystem, recordid)}"
      )

      val response = server
        ._submit_component_admin_entity_update(req, "notice-board", "notice", recordid)
        .unsafeRunSync()
      When("reject component entity update forms when a required detail view field is empty is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for reject component entity update forms when a required detail view field is empty holds")
      response.status.code shouldBe 400
      html should include ("Edit Notice")
      html should include ("Validation failed.")
      html should include ("admin-feedback")
      html should include ("title is required.")
      html should include ("is-invalid")
      html should not include ("name=\"author\"")
      dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/entity/update")
    }

    "apply component entity create form POST through EntityCollection into EntityStoreSpace" in {
      Given("the prerequisites for apply component entity create form POST through EntityCollection into EntityStoreSpace")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val before = collection.storage.storeRealm.values.size
      val id = _new_notice_entity_id()
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        s"fields=id%3D${java.net.URLEncoder.encode(id.value, StandardCharsets.UTF_8)}%0Atitle%3Dnew+notice%0Aauthor%3Dbob"
      )

      When("apply component entity create form POST through EntityCollection into EntityStoreSpace is exercised")
      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for apply component entity create form POST through EntityCollection into EntityStoreSpace holds")
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.size shouldBe before + 1
      collection.storage.storeRealm.values.exists(x => x.title == "new notice" && x.author == "bob") shouldBe true
      val created = collection.storage.storeRealm.values.find(x => x.title == "new notice" && x.author == "bob")
        .getOrElse(fail("created entity is missing"))
      val stored = _load_notice_store_record(subsystem, created.id)
      stored.getString("title") shouldBe Some("new notice")
      stored.getString("author") shouldBe Some("bob")
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "create an Embedded SimpleEntity through the admin operation without accepting managed revision input" in {
      Given("an Embedded revision collection and a create request containing only application fields")
      val subsystem = _embedded_revision_fixture_subsystem()
      val component = subsystem
        .findComponent(ComponentId("org.goldenport.cncf.test.EmbeddedNoticeBoard"))
        .getOrElse(fail("embedded fixture component is missing"))
      val collection =
        component.entitySpace.entity[EmbeddedNoticeEntity]("notice")

      When("the admin operation creates the Entity")
      val response = _success(
        subsystem.executeOperationResponse(
          GRequest.of(
            component = BuiltinComponentIdentity.ADMIN.name,
            service = "entity",
            operation = "create",
            arguments = List(
              Argument("component", _embedded_notice_board_component_name, None),
              Argument("entity", "notice", None),
              Argument("title", "embedded create", None)
            )
          )
        )
      )

      Then("the collection boundary initializes one managed revision and persists the Entity")
      response shouldBe
        OperationResponse.Scalar("Entity record was applied.")
      val created = collection.storage.storeRealm.values
        .find(_.title == "embedded create")
        .getOrElse(fail("embedded Entity was not created"))
      created.revision shouldBe EntityRevision.INITIAL
    }

    "read and list an Embedded SimpleEntity through its owning component context" in {
      Given("an Embedded Entity whose revision binding belongs to a non-Admin component")
      val subsystem = _embedded_revision_fixture_subsystem()
      val component = subsystem
        .findComponent(ComponentId("org.goldenport.cncf.test.EmbeddedNoticeBoard"))
        .getOrElse(fail("embedded fixture component is missing"))
      val collection =
        component.entitySpace.entity[EmbeddedNoticeEntity]("notice")
      _success(
        subsystem.executeOperationResponse(
          GRequest.of(
            component = BuiltinComponentIdentity.ADMIN.name,
            service = "entity",
            operation = "create",
            arguments = List(
              Argument("component", _embedded_notice_board_component_name, None),
              Argument("entity", "notice", None),
              Argument("title", "embedded read", None)
            )
          )
        )
      )
      val created = collection.storage.storeRealm.values
        .find(_.title == "embedded read")
        .getOrElse(fail("embedded Entity was not created"))

      When("the Admin component reads and searches the owning component collection")
      val read = _admin_record_response(
        subsystem,
        "entity",
        "read",
        "component" -> _embedded_notice_board_component_name,
        "entity" -> "notice",
        "id" -> created.id.value
      )
      val listed = _admin_record_response(
        subsystem,
        "entity",
        "list",
        "component" -> _embedded_notice_board_component_name,
        "entity" -> "notice"
      )

      Then("both surfaces resolve the owning revision binding and expose its managed revision")
      val record = read
        .getAny("record")
        .collect { case value: Record => value }
        .getOrElse(fail("embedded read record is missing"))
      record.getLong("revision") shouldBe Some(EntityRevision.INITIAL.value)
      listed.getAny("items").map(_.toString).getOrElse("") should include (
        s"revision=${EntityRevision.INITIAL.value}"
      )
    }

    "keep REST adapter revision semantics independent from body version metadata" in {
      Given("an Embedded Entity and REST adapter profiles paired with conflicting body versions")
      val subsystem = _embedded_revision_fixture_subsystem()
      val component = subsystem
        .findComponent(ComponentId("org.goldenport.cncf.test.EmbeddedNoticeBoard"))
        .getOrElse(fail("embedded fixture component is missing"))
      val collection =
        component.entitySpace.entity[EmbeddedNoticeEntity]("notice")
      _success(
        subsystem.executeOperationResponse(
          GRequest.of(
            component = BuiltinComponentIdentity.ADMIN.name,
            service = "entity",
            operation = "create",
            arguments = List(
              Argument("component", _embedded_notice_board_component_name, None),
              Argument("entity", "notice", None),
              Argument("title", "before REST updates", None)
            )
          )
        )
      )
      val created = collection.storage.storeRealm.values
        .find(_.title == "before REST updates")
        .getOrElse(fail("embedded Entity was not created"))

      When("idempotent and strict REST profiles update through their framework-owned revision sources")
      val idempotent = subsystem.executeOperationResponse(
        GRequest.of(
          component = BuiltinComponentIdentity.ADMIN.name,
          service = "entity",
          operation = "update",
          arguments = List(
            Argument("component", _embedded_notice_board_component_name, None),
            Argument("entity", "notice", None),
            Argument("id", created.id.value, None),
            Argument("title", "idempotent REST update", None),
            Argument("version", "999", None)
          ),
          properties = List(
            Property(
              EntityMutationAdapterDefaults.profilePropertyName,
              EntityMutationAdapterDefaults.idempotentRestProfile,
              None
            )
          )
        )
      )
      val strict = subsystem.executeOperationResponse(
        GRequest.of(
          component = BuiltinComponentIdentity.ADMIN.name,
          service = "entity",
          operation = "update",
          arguments = List(
            Argument("component", _embedded_notice_board_component_name, None),
            Argument("entity", "notice", None),
            Argument("id", created.id.value, None),
            Argument("title", "strict REST update", None),
            Argument("version", "999", None)
          ),
          properties = List(
            Property(
              EntityMutationAdapterDefaults.profilePropertyName,
              EntityMutationAdapterDefaults.strictRestProfile,
              None
            ),
            Property(
              EntityRevisionTransport.observedRevisionPropertyName,
              "2",
              None
            )
          )
        )
      )

      Then("managed REST ignores body version and strict REST honors only the observed validator")
      idempotent shouldBe a[Consequence.Success[?]]
      strict shouldBe a[Consequence.Success[?]]
      val updated = collection.storage.storeRealm.values
        .find(_.id == created.id)
        .getOrElse(fail("updated Embedded Entity is missing"))
      updated.title shouldBe "strict REST update"
      updated.revision.value shouldBe 3L
    }

    "attach uploaded and existing Blob images during admin entity create" in {
      Given("the prerequisites for attach uploaded and existing Blob images during admin entity create")
      val subsystem = _management_console_fixture_subsystem()
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val existingblobid = _register_external_blob(subsystem, "existing-admin.png", "https://example.com/existing-admin.png")
      val localid = s"notice_admin_image_${java.util.UUID.randomUUID().toString.replace("-", "")}"
      val filename = s"${localid}.png"

      When("attach uploaded and existing Blob images during admin entity create is exercised")
      val response = _success(subsystem.executeOperationResponse(GRequest.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "entity",
        operation = "create",
        arguments = List(
          Argument("component", _notice_board_component_name, None),
          Argument("entity", "notice", None),
          Argument("title", "image create", None),
          Argument("author", "dana", None),
          Argument("imageAttachments.0.role", "primary", None),
          Argument("imageAttachments.0.file", MimeBody(ContentType.IMAGE_PNG, Bag.binary("uploaded-image".getBytes(StandardCharsets.UTF_8))), None),
          Argument("imageAttachments.0.file.filename", filename, None),
          Argument("blobId.cover", existingblobid, None)
        )
      )))

      Then("the observable contract for attach uploaded and existing Blob images during admin entity create holds")
      response shouldBe OperationResponse.Scalar("Entity record was applied.")
      val created = collection.storage.storeRealm.values.find(_.title == "image create").getOrElse(fail("created entity is missing"))
      val stored = created.toRecord()
      stored.getString("imageAttachments.0.role") shouldBe None
      stored.getString("blobId.cover") shouldBe None

      given ExecutionContext = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).getOrElse(fail("Blob component is missing")).logic.executionContext()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val rows = _success(repository.list(AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some(created.id.value),
        targetKind = Some("blob")
      )))
      rows.map(_.role).toSet shouldBe Set("primary", "cover")
      rows.find(_.role == "cover").map(_.targetEntityId) shouldBe Some(existingblobid)
      val uploadedid = EntityId.parse(rows.find(_.role == "primary").map(_.targetEntityId).getOrElse(fail("uploaded association is missing"))).toOption.getOrElse(fail("uploaded Blob id is invalid"))
      val uploaded = _success(BlobRepository.entityStore().get(uploadedid))
      uploaded.filename shouldBe Some(filename)
      uploaded.byteSize shouldBe Some("uploaded-image".getBytes(StandardCharsets.UTF_8).length.toLong)
    }

    "submit multipart admin entity create with image attachment through Web handler" in {
      Given("the prerequisites for submit multipart admin entity create with image attachment through Web handler")
      val subsystem = _management_console_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val filename = s"web-admin-${java.util.UUID.randomUUID().toString.replace("-", "")}.png"
      val id = _new_notice_entity_id()
      val req = _post_multipart_request(
        "/form/notice-board/admin/entities/notice/create",
        Vector(
          "id" -> id.value,
          "title" -> "multipart image create",
          "author" -> "web",
          "imageAttachments.0.role" -> "primary"
        ),
        Vector(
          ("imageAttachments.0.file", filename, "image/png", "web-upload-image".getBytes(StandardCharsets.UTF_8))
        )
      )

      When("submit multipart admin entity create with image attachment through Web handler is exercised")
      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for submit multipart admin entity create with image attachment through Web handler holds")
      html should include ("Entity record was applied")
      val created = collection.storage.storeRealm.values.find(_.title == "multipart image create").getOrElse(fail("created entity is missing"))
      created.author shouldBe "web"
      given ExecutionContext = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).getOrElse(fail("Blob component is missing")).logic.executionContext()
      val rows = _success(AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault).list(AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some(created.id.value),
        targetKind = Some("blob")
      )))
      rows.map(_.role) shouldBe Vector("primary")
      val uploadedid = EntityId.parse(rows.head.targetEntityId).toOption.getOrElse(fail("uploaded Blob id is invalid"))
      _success(BlobRepository.entityStore().get(uploadedid)).filename shouldBe Some(filename)
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "compensate admin entity create when image attachment fails" in {
      Given("the prerequisites for compensate admin entity create when image attachment fails")
      val subsystem = _management_console_fixture_subsystem()
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val localid = s"notice_admin_compensate_${java.util.UUID.randomUUID().toString.replace("-", "")}"
      val missingtoken = s"missing_${localid}"
      val missingblobid = EntityId(
        BlobRepository.CollectionId.major,
        BlobRepository.CollectionId.minor,
        BlobRepository.CollectionId,
        entropy = Some(missingtoken)
      ).value

      When("compensate admin entity create when image attachment fails is exercised")
      val result = subsystem.executeOperationResponse(GRequest.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "entity",
        operation = "create",
        arguments = List(
          Argument("component", _notice_board_component_name, None),
          Argument("entity", "notice", None),
          Argument("title", "compensated image create", None),
          Argument("author", "erin", None),
          Argument("imageAttachments.0.role", "primary", None),
          Argument("imageAttachments.0.file", MimeBody(ContentType.IMAGE_PNG, Bag.binary("temporary-image".getBytes(StandardCharsets.UTF_8))), None),
          Argument("imageAttachments.0.file.filename", s"${localid}.png", None),
          Argument("blobId.cover", missingblobid, None)
        )
      ))

      Then("the observable contract for compensate admin entity create when image attachment fails holds")
      result shouldBe a[Consequence.Failure[_]]
      collection.storage.storeRealm.values.exists(_.title == "compensated image create") shouldBe false
      given ExecutionContext = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).getOrElse(fail("Blob component is missing")).logic.executionContext()
      _success(BlobRepository.entityStore().list()).flatMap(_.filename).filter(_ == s"${localid}.png") shouldBe Vector.empty
    }

    "keep admin entity update when image attachment fails" in {
      Given("the prerequisites for keep admin entity update when image attachment fails")
      val subsystem = _management_console_fixture_subsystem()
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
      val recordid = collection.storage.storeRealm.values.head.id
      val missingtoken = s"missing_update_${java.util.UUID.randomUUID().toString.replace("-", "")}"
      val missingblobid = EntityId(
        BlobRepository.CollectionId.major,
        BlobRepository.CollectionId.minor,
        BlobRepository.CollectionId,
        entropy = Some(missingtoken)
      ).value

      When("keep admin entity update when image attachment fails is exercised")
      val result = subsystem.executeOperationResponse(GRequest.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "entity",
        operation = "update",
        arguments = List(
          Argument("component", _notice_board_component_name, None),
          Argument("entity", "notice", None),
          Argument("id", recordid.value, None),
          Argument("version", _notice_entity_version(subsystem, recordid.value), None),
          Argument("title", "updated despite image failure", None),
          Argument("author", "frank", None),
          Argument("imageAttachments.0.role", "primary", None),
          Argument("imageAttachments.0.blobId", missingblobid, None)
        )
      ))

      Then("the observable contract for keep admin entity update when image attachment fails holds")
      result shouldBe a[Consequence.Failure[_]]
      val stored = collection.storage.storeRealm.values.find(_.id == recordid).map(_.toRecord()).getOrElse(fail("updated notice is missing"))
      stored.getString("title") shouldBe Some("updated despite image failure")
      stored.getString("imageAttachments.0.blobId") shouldBe None
    }

    "render admin entity create and update forms from derived alias schema fields" in {
      Given("the prerequisites for render admin entity create and update forms from derived alias schema fields")
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordentityid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id
      val recordid = recordentityid.value

      val newhtml = _renderer
        .renderComponentAdminEntityNew(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      When("render admin entity create and update forms from derived alias schema fields is exercised")
      val edithtml = _renderer
        .renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for render admin entity create and update forms from derived alias schema fields holds")
      newhtml should include (s"/form/${componentpath}/admin/entities/${entitypath}/create")
      newhtml should include ("enctype=\"multipart/form-data\"")
      newhtml should include ("name=\"subject\"")
      newhtml should include ("name=\"body\"")
      newhtml should include ("name=\"imageAttachments.0.file\"")
      newhtml should include ("Image Attachments")
      newhtml should not include ("name=\"title\"")
      newhtml should not include ("name=\"content\"")
      edithtml should include (s"/form/${componentpath}/admin/entities/${entitypath}/${recordid}/update")
      edithtml should include ("enctype=\"multipart/form-data\"")
      edithtml should include ("name=\"subject\"")
      edithtml should include ("name=\"body\"")
      edithtml should include ("name=\"imageAttachments.0.blobId\"")
      edithtml should not include ("name=\"title\"")
      edithtml should not include ("name=\"content\"")
    }

    "honor SimpleEntity platform fields when admin schema includes them" in {
      Given("the prerequisites for honor SimpleEntity platform fields when admin schema includes them")
      val subsystem = _management_console_fixture_subsystem(schema = _schema(
        "id",
        "nameAttributes",
        "descriptiveAttributes",
        "lifecycleAttributes",
        "publicationAttributes",
        "securityAttributes",
        "resourceAttributes",
        "auditAttributes",
        "mediaAttributes",
        "contextualAttribute",
        "senderName",
        "subject",
        "body"
      ))
      val componentname = "notice_board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      val newhtml = _renderer
        .renderComponentAdminEntityNew(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      When("honor SimpleEntity platform fields when admin schema includes them is exercised")
      val edithtml = _renderer
        .renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for honor SimpleEntity platform fields when admin schema includes them holds")
      newhtml should include ("name=\"id\"")
      newhtml should include ("name=\"nameAttributes\"")
      newhtml should include ("name=\"lifecycleAttributes\"")
      newhtml should include ("name=\"securityAttributes\"")
      newhtml should include ("name=\"senderName\"")
      newhtml should include ("name=\"subject\"")
      newhtml should include ("name=\"body\"")
      edithtml should include ("name=\"nameAttributes\"")
      edithtml should include ("name=\"lifecycleAttributes\"")
      edithtml should include ("name=\"securityAttributes\"")
    }

    "render admin entity list and detail with derived alias fields" in {
      Given("the prerequisites for render admin entity list and detail with derived alias fields")
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      val list = _renderer
        .renderComponentAdminEntityType(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity type admin is missing"))
      When("render admin entity list and detail with derived alias fields is exercised")
      val detail = _renderer
        .renderComponentAdminEntityDetail(subsystem, componentname, entitypath, recordid)
        .map(_.body)
        .getOrElse(fail("component entity detail admin is missing"))

      Then("the observable contract for render admin entity list and detail with derived alias fields holds")
      list should include ("<th>id</th><th>shortid</th><th>senderName</th><th>recipientName</th><th>subject</th><th>body</th><th>Actions</th>")
      list should include ("board update")
      list should not include ("<th>title</th>")
      list should not include ("<th>content</th>")
      detail should include ("<th>subject</th><td>board update</td>")
      detail should not include ("<th>title</th>")
      detail should not include ("<th>content</th>")
    }

    "render admin entity list detail edit and new from view fields before full schema fields" in {
      Given("the prerequisites for render admin entity list detail edit and new from view fields before full schema fields")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema(
          "id",
          "nameAttributes",
          "lifecycleAttributes",
          "securityAttributes",
          "senderName",
          "recipientName",
          "subject",
          "body"
        ),
        viewfields = Map(
          "summary" -> Vector("id", "subject"),
          "detail" -> Vector("id", "senderName", "recipientName", "subject", "body"),
          "create" -> Vector("senderName", "recipientName", "subject", "body")
        )
      )
      val componentname = "notice_board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      val list = _renderer
        .renderComponentAdminEntityType(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity type admin is missing"))
      val detail = _renderer
        .renderComponentAdminEntityDetail(subsystem, componentname, entitypath, recordid)
        .map(_.body)
        .getOrElse(fail("component entity detail admin is missing"))
      val edit = _renderer
        .renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid)
        .map(_.body)
        .getOrElse(fail("component entity edit admin is missing"))
      val newly = _renderer
        .renderComponentAdminEntityNew(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val formdefinition = parse(_renderer
        .renderComponentAdminEntityFormDefinition(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      When("render admin entity list detail edit and new from view fields before full schema fields is exercised")
      val formdefinitionfields = formdefinition.hcursor.downField("fields")

      Then("the observable contract for render admin entity list detail edit and new from view fields before full schema fields holds")
      list should include ("<th>id</th><th>subject</th><th>Actions</th>")
      list should not include ("nameAttributes")
      list should not include ("lifecycleAttributes")
      detail should include ("<th>senderName</th>")
      detail should include ("<th>subject</th>")
      detail should not include ("<th>securityAttributes</th>")
      edit should include ("name=\"senderName\"")
      edit should include ("name=\"subject\"")
      edit should include ("name=\"body\"")
      edit should not include ("name=\"nameAttributes\"")
      edit should not include ("name=\"lifecycleAttributes\"")
      edit should not include ("name=\"securityAttributes\"")
      newly should include ("name=\"senderName\"")
      newly should include ("name=\"subject\"")
      newly should include ("name=\"body\"")
      newly should not include ("name=\"id\"")
      newly should not include ("name=\"nameAttributes\"")
      newly should not include ("name=\"lifecycleAttributes\"")
      newly should not include ("name=\"securityAttributes\"")
      formdefinitionfields.downN(0).downField("name").as[String].toOption shouldBe Some("senderName")
      formdefinitionfields.downN(1).downField("name").as[String].toOption shouldBe Some("recipientName")
      formdefinitionfields.downN(2).downField("name").as[String].toOption shouldBe Some("subject")
      formdefinitionfields.downN(3).downField("name").as[String].toOption shouldBe Some("body")
    }

    "create admin entity records without exposing id when create view fields omit it" in {
      Given("the prerequisites for create admin entity records without exposing id when create view fields omit it")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewfields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title", "author"),
          "create" -> Vector("title", "author")
        )
      )
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val newhtml = _renderer
        .renderComponentAdminEntityNew(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      val formdefinition = parse(_renderer
        .renderComponentAdminEntityFormDefinition(subsystem, componentname, entitypath)
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val collection = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath)
      val before = collection.storage.storeRealm.values.size
      val req = _post_form_request(
        s"/form/${componentpath}/admin/entities/${entitypath}/create",
        "title=idless+notice&author=carol"
      )

      When("create admin entity records without exposing id when create view fields omit it is exercised")
      val html = server
        ._submit_component_admin_entity_create(req, componentpath, entitypath)
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for create admin entity records without exposing id when create view fields omit it holds")
      newhtml should include ("name=\"title\"")
      newhtml should include ("name=\"author\"")
      newhtml should not include ("name=\"id\"")
      formdefinition.hcursor.downField("fields").downN(0).downField("name").as[String].toOption shouldBe Some("title")
      formdefinition.hcursor.downField("fields").downN(1).downField("name").as[String].toOption shouldBe Some("author")
      formdefinition.hcursor.downField("fields").downN(2).downField("name").as[String].toOption shouldBe None
      html should include ("Entity record was applied")
      html should include ("Applied</th><td>true")
      collection.storage.storeRealm.values.size shouldBe before + 1
      val created = collection.storage.storeRealm.values.find(_.title == "idless notice").getOrElse(fail("created notice is missing"))
      created.author shouldBe "carol"
      created.id.value should not be "notice_1"
      created.id.collection shouldBe NoticeEntity.collectionid
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "pass derived alias admin entity create fields through to the dispatcher" in {
      Given("the prerequisites for pass derived alias admin entity create fields through to the dispatcher")
      val subsystem = _management_console_fixture_subsystem(schema = _schema("id", "senderName", "recipientName", "subject", "body"))
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val id = _new_notice_entity_id()
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        s"id=${java.net.URLEncoder.encode(id.value, StandardCharsets.UTF_8)}&senderName=alice&recipientName=bob&subject=Phase+12&body=Alias+body"
      )

      When("pass derived alias admin entity create fields through to the dispatcher is exercised")
      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for pass derived alias admin entity create fields through to the dispatcher holds")
      html should include ("Entity record was applied")
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("admin entity create form was not dispatched"))
      submitted.getString("subject") shouldBe Some("Phase 12")
      submitted.getString("body") shouldBe Some("Alias body")
      submitted.getString("title") shouldBe None
      submitted.getString("content") shouldBe None
    }

    "keep static result page convention out of built-in admin entity create flow" in {
      Given("the prerequisites for keep static result page convention out of built-in admin entity create flow")
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__200.html",
            "<article><h2>Static Operation Result</h2></article>"
          ).resolve("web.yaml").toString)
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val id = _new_notice_entity_id()
      val req = _post_form_request(
        "/form/notice-board/admin/entities/notice/create",
        s"fields=id%3D${java.net.URLEncoder.encode(id.value, StandardCharsets.UTF_8)}%0Atitle%3Dstatic+guard%0Aauthor%3Dbob"
      )

      When("keep static result page convention out of built-in admin entity create flow is exercised")
      val html = server
        ._submit_component_admin_entity_create(req, "notice-board", "notice")
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for keep static result page convention out of built-in admin entity create flow holds")
      html should include ("Entity record was applied")
      html should include ("Create submitted")
      html should not include ("Static Operation Result")
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    }

    "provide Static Web application routing and template contracts" which {
    "define Static Form Web App template lookup precedence as route-local before common templates" in {
      Given("the prerequisites for define Static Form Web App template lookup precedence as route-local before common templates")
      val subsystem = _management_console_fixture_subsystem()
      When("define Static Form Web App template lookup precedence as route-local before common templates is exercised")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      Then("the observable contract for define Static Form Web App template lookup precedence as route-local before common templates holds")
      server._form_result_template_candidates("notice-board", "notice", "post-notice", 200) shouldBe Vector(
        java.nio.file.Paths.get("notice-board", "notice", "post-notice__200.html"),
        java.nio.file.Paths.get("notice-board", "post-notice__200.html"),
        java.nio.file.Paths.get("notice-board", "notice", "post-notice__success.html"),
        java.nio.file.Paths.get("notice-board", "post-notice__success.html"),
        java.nio.file.Paths.get("notice-board", "notice", "__200.html"),
        java.nio.file.Paths.get("notice-board", "__200.html"),
        java.nio.file.Paths.get("notice-board", "notice", "__success.html"),
        java.nio.file.Paths.get("notice-board", "__success.html"),
        java.nio.file.Paths.get("post-notice__200.html"),
        java.nio.file.Paths.get("__200.html"),
        java.nio.file.Paths.get("post-notice__success.html"),
        java.nio.file.Paths.get("__success.html")
      )
    }

    "load Static Form Web App result templates from the descriptor root with route-local precedence" in {
      Given("the prerequisites for load Static Form Web App result templates from the descriptor root with route-local precedence")
      val root = Files.createTempDirectory("cncf-web-template-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("notice"))
      Files.writeString(root.resolve("post-notice__200.html"), "ROOT OPERATION", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("__200.html"), "APP COMMON", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("post-notice__200.html"), "APP OPERATION", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("notice").resolve("post-notice__200.html"), "SERVICE OPERATION", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      When("load Static Form Web App result templates from the descriptor root with route-local precedence is exercised")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      Then("the observable contract for load Static Form Web App result templates from the descriptor root with route-local precedence holds")
      server._web_resource_roots().map(_.name) shouldBe Vector(root.toString)
      server._form_result_static_template("notice-board", "notice", "post-notice", 200) shouldBe Some("SERVICE OPERATION")
    }

    "compose Static Form Web App result templates with WEB-INF layouts" in {
      Given("the prerequisites for compose Static Form Web App result templates with WEB-INF layouts")
      val root = Files.createTempDirectory("cncf-web-result-layout-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |      layout: default
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(root.resolve("WEB-INF").resolve("partials"))
      Files.writeString(
        root.resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${partial.header}<main>${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("header.html"), "<header>Result Header</header>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("post-notice__200.html"), "<section>${operation}</section>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("compose Static Form Web App result templates with WEB-INF layouts is exercised")
      val template = server._form_result_static_template("notice-board", "notice", "post-notice", 200).getOrElse(fail("template is missing"))

      Then("the observable contract for compose Static Form Web App result templates with WEB-INF layouts holds")
      template should include ("Result Header")
      template should include ("<main><section>${operation}</section></main>")
    }

    "serve app-local assets from the canonical component Web app route" in {
      Given("the prerequisites for serve app-local assets from the canonical component Web app route")
      val root = Files.createTempDirectory("cncf-web-asset-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._web_app_asset("notice-board", "notice-board", "app.css")
        .unsafeRunSync()
      When("serve app-local assets from the canonical component Web app route is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for serve app-local assets from the canonical component Web app route holds")
      response.status.code shouldBe 200
      body should include (".notice-board")
      response.contentType.map(_.mediaType) shouldBe Some(MediaType.text.css)
      server._web_app_asset("missing", "notice-board", "app.css").unsafeRunSync().status.code shouldBe 404
    }

    "serve static Web app HTML from the canonical component Web app route" in {
      Given("the prerequisites for serve static Web app HTML from the canonical component Web app route")
      val root = Files.createTempDirectory("cncf-web-html-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("about.html"), "<h1>About Notice Board</h1>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val index = server._component_web_app("notice-board", "notice-board", Vector.empty).unsafeRunSync()
      val about = server._component_web_app("notice-board", "notice-board", Vector("about")).unsafeRunSync()
      When("serve static Web app HTML from the canonical component Web app route is exercised")
      val missingcomponent = server._component_web_app("missing", "notice-board", Vector.empty).unsafeRunSync()

      Then("the observable contract for serve static Web app HTML from the canonical component Web app route holds")
      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("Notice Board")
      about.status.code shouldBe 200
      about.as[String].unsafeRunSync() should include ("About Notice Board")
      missingcomponent.status.code shouldBe 404
    }

    "keep component Web app routes separate from component form indexes" in {
      // Given
      Given("the prerequisites for keep component Web app routes separate from component form indexes")
      val root = Files.createTempDirectory("cncf-web-form-separation-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>ArtScene</h1>", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      val canonical = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/textus-art-scene"))).unsafeRunSync()
      val toplevel = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/textus-art-scene"))).unsafeRunSync()
      val componentroot = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()
      When("keep component Web app routes separate from component form indexes is exercised")
      val formindex = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/form/art-scene"))).unsafeRunSync()

      // Then
      Then("the observable contract for keep component Web app routes separate from component form indexes holds")
      canonical.status.code shouldBe 200
      canonical.as[String].unsafeRunSync() should include ("ArtScene")
      toplevel.status.code shouldBe 404
      toplevel.as[String].unsafeRunSync() should not include ("Forms")
      componentroot.status.code shouldBe 404
      componentroot.as[String].unsafeRunSync() should not include ("Forms")
      formindex.status.code shouldBe 200
      formindex.as[String].unsafeRunSync() should include ("org.goldenport.cncf.test.ArtScene Forms")
    }

    "serve explicit component Web entry app from the component root" in {
      // Given
      Given("the prerequisites for serve explicit component Web entry app from the component root")
      val root = Files.createTempDirectory("cncf-web-component-entry-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |      entry: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>Entry ArtScene</h1>", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      val componentroot = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()
      val componentslash = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/"))).unsafeRunSync()
      val componentindex = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/index"))).unsafeRunSync()
      val componentindexhtml = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/index.html"))).unsafeRunSync()
      val canonical = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/textus-art-scene"))).unsafeRunSync()
      val admin = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/admin"))).unsafeRunSync()
      When("serve explicit component Web entry app from the component root is exercised")
      val formindex = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/form/art-scene"))).unsafeRunSync()

      // Then
      Then("the observable contract for serve explicit component Web entry app from the component root holds")
      Vector(componentroot, componentslash, componentindex, componentindexhtml, canonical).foreach { response =>
        response.status.code shouldBe 200
        response.as[String].unsafeRunSync() should include ("Entry ArtScene")
      }
      admin.as[String].unsafeRunSync() should not include ("Entry ArtScene")
      formindex.status.code shouldBe 200
      formindex.as[String].unsafeRunSync() should include ("org.goldenport.cncf.test.ArtScene Forms")
    }

    "prefer explicit Web route aliases over component Web entry shortcuts" in {
      // Given
      Given("the prerequisites for prefer explicit Web route aliases over component Web entry shortcuts")
      val root = Files.createTempDirectory("cncf-web-component-entry-alias-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |      entry: true
          |    - name: art-alias
          |  routes:
          |    - path: /web/art-scene
          |      kind: alias
          |      target:
          |        component: art-scene
          |        app: art-alias
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.createDirectories(root.resolve("art-alias"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>Entry ArtScene</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("art-alias").resolve("index.html"), "<h1>Alias ArtScene</h1>", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      val aliasroot = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()
      val aliasslash = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/"))).unsafeRunSync()
      When("prefer explicit Web route aliases over component Web entry shortcuts is exercised")
      val aliasindex = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/index"))).unsafeRunSync()

      // Then
      Then("the observable contract for prefer explicit Web route aliases over component Web entry shortcuts holds")
      Vector(aliasroot, aliasslash, aliasindex).foreach { response =>
        response.status.code shouldBe 200
        val body = response.as[String].unsafeRunSync()
        body should include ("Alias ArtScene")
        body should not include ("Entry ArtScene")
      }
    }

    "reject ambiguous component Web entry apps deterministically" in {
      // Given
      Given("the prerequisites for reject ambiguous component Web entry apps deterministically")
      val root = Files.createTempDirectory("cncf-web-component-entry-ambiguous-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |      entry: true
          |    - name: art-gallery
          |      componentEntry: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.createDirectories(root.resolve("art-gallery"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>Entry ArtScene</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("art-gallery").resolve("index.html"), "<h1>Entry Gallery</h1>", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      When("reject ambiguous component Web entry apps deterministically is exercised")
      val response = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()

      // Then
      Then("the observable contract for reject ambiguous component Web entry apps deterministically holds")
      response.status.code shouldBe 500
      response.as[String].unsafeRunSync() should include ("Multiple component Web entry apps")
    }

    "serve top-level component Web app aliases only when the descriptor declares them" in {
      // Given
      Given("the prerequisites for serve top-level component Web app aliases only when the descriptor declares them")
      val root = Files.createTempDirectory("cncf-web-explicit-alias-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |  routes:
          |    - path: /web/art
          |      kind: alias
          |      target:
          |        component: art-scene
          |        app: textus-art-scene
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>Aliased ArtScene</h1>", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      val alias = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art"))).unsafeRunSync()
      val canonical = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/textus-art-scene"))).unsafeRunSync()
      When("serve top-level component Web app aliases only when the descriptor declares them is exercised")
      val componentroot = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()

      // Then
      Then("the observable contract for serve top-level component Web app aliases only when the descriptor declares them holds")
      alias.status.code shouldBe 200
      alias.as[String].unsafeRunSync() should include ("Aliased ArtScene")
      canonical.status.code shouldBe 200
      canonical.as[String].unsafeRunSync() should include ("Aliased ArtScene")
      componentroot.status.code shouldBe 404
    }

    "resolve component Web app routes from apps route declarations without aliases" in {
      // Given
      Given("the prerequisites for resolve component Web app routes from apps route declarations without aliases")
      val root = Files.createTempDirectory("cncf-web-app-route-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-art-scene
          |      route: /web/{component}/gallery
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("textus-art-scene"))
      Files.createDirectories(root.resolve("textus-art-scene").resolve("assets"))
      Files.writeString(root.resolve("textus-art-scene").resolve("index.html"), "<h1>Routed ArtScene</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("textus-art-scene").resolve("assets").resolve("app.css"), ".routed-art-scene { color: #0f766e; }\n", StandardCharsets.UTF_8)
      val base = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val subsystem = base.add(Vector(TestComponentFactory.create("art_scene", Protocol.empty)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound

      // When
      val routed = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/gallery"))).unsafeRunSync()
      val routedasset = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/gallery/assets/app.css"))).unsafeRunSync()
      When("resolve component Web app routes from apps route declarations without aliases is exercised")
      val undeclared = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/gallery"))).unsafeRunSync()

      // Then
      Then("the observable contract for resolve component Web app routes from apps route declarations without aliases holds")
      routed.status.code shouldBe 200
      routed.as[String].unsafeRunSync() should include ("Routed ArtScene")
      routedasset.status.code shouldBe 200
      routedasset.as[String].unsafeRunSync() should include ("routed-art-scene")
      undeclared.status.code shouldBe 404
    }

    "serve explicit Web route alias pages from the Web root" in {
      Given("the prerequisites for serve explicit Web route alias pages from the Web root")
      val root = Files.createTempDirectory("cncf-web-flat-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("assets"))
      Files.createDirectories(root.resolve("notice-board"))
      Files.writeString(root.resolve("index.html"), "<h1>Flat Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("publicblogs.html"), "<h1>Flat Public Blogs</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("status.html"), "<h1>${app}</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("assets").resolve("app.css"), ".flat-notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Fallback Notice Board</h1>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val index = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      val page = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/publicblogs"))).unsafeRunSync()
      val pagehtml = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/publicblogs.html"))).unsafeRunSync()
      val status = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/status"))).unsafeRunSync()
      When("serve explicit Web route alias pages from the Web root is exercised")
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/assets/app.css"))).unsafeRunSync()

      Then("the observable contract for serve explicit Web route alias pages from the Web root holds")
      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("Flat Notice Board")
      page.status.code shouldBe 200
      page.as[String].unsafeRunSync() should include ("Flat Public Blogs")
      pagehtml.status.code shouldBe 200
      pagehtml.as[String].unsafeRunSync() should include ("Flat Public Blogs")
      status.status.code shouldBe 200
      status.as[String].unsafeRunSync() should include ("<h1>notice-board</h1>")
      asset.status.code shouldBe 200
      asset.as[String].unsafeRunSync() should include ("flat-notice-board")
    }

    "compose Static Form Web App pages with WEB-INF layouts and partials" in {
      Given("the prerequisites for compose Static Form Web App pages with WEB-INF layouts and partials")
      val root = Files.createTempDirectory("cncf-web-layout-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(root.resolve("WEB-INF").resolve("partials").resolve("publicblogs"))
      Files.writeString(
        root.resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html>
          |<html><head><title>${app}</title></head><body>
          |${partial.header}
          |<main class="container">${content}</main>
          |${partial.footer}
          |</body></html>""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("WEB-INF").resolve("layouts").resolve("lower-private.html"), "<h1>lowercase private</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("header.html"), "<header>Global Header</header>", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("WEB-INF").resolve("partials").resolve("publicblogs").resolve("header.html"),
        """<header>Public Blogs Header <a data-notification-indicator ${pageContext.notification.indicatorHidden}>Notifications <span data-notification-badge ${pageContext.notification.badgeHidden}>${pageContext.notification.unconfirmedCount}</span></a></header>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("navigation.html"), "<nav>Shared Navigation</nav>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("footer.html"), "<footer>Shared Footer</footer>", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("publicblogs.html"),
        """<section>
          |  <textus-include name="navigation"></textus-include>
          |  <h1>${app}</h1>
          |  <p>${noticeKind}</p>
          |  <p>${query.noticeKind}</p>
          |</section>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/publicblogs?noticeKind=import"))).unsafeRunSync()
      val html = response.as[String].unsafeRunSync()
      val webinf = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/WEB-INF/layouts/default.html"))).unsafeRunSync()
      When("compose Static Form Web App pages with WEB-INF layouts and partials is exercised")
      val lowerwebinf = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/web-inf/layouts/lower-private.html"))).unsafeRunSync()

      Then("the observable contract for compose Static Form Web App pages with WEB-INF layouts and partials holds")
      withClue(html) {
        response.status.code shouldBe 200
      }
      html should include ("Public Blogs Header")
      html should include ("data-notification-indicator hidden")
      html should include ("data-notification-badge hidden>0</span>")
      html should not include ("Global Header")
      html should include ("Shared Navigation")
      html should include ("Shared Footer")
      html should include ("<main class=\"container\">")
      html should include ("<h1>notice-board</h1>")
      html should include ("<p>import</p>")
      html should not include ("<textus-include")
      html should not include ("${content}")
      webinf.status.code shouldBe 404
      lowerwebinf.status.code shouldBe 404
    }

    "render page context in a partial included by a full HTML Static Form page" in {
      Given("the prerequisites for render page context in a partial included by a full HTML Static Form page")
      val root = Files.createTempDirectory("cncf-web-full-html-partial-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("WEB-INF").resolve("partials"))
      Files.writeString(
        root.resolve("WEB-INF").resolve("partials").resolve("topbar.html"),
        """<header><a data-notification-indicator ${pageContext.notification.indicatorHidden}>Notifications <span data-notification-badge ${pageContext.notification.badgeHidden}>${pageContext.notification.unconfirmedCount}</span></a></header>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("index.html"),
        """<!doctype html><html><body><textus:include name="topbar"></textus:include><main>Board</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      When("render page context in a partial included by a full HTML Static Form page is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render page context in a partial included by a full HTML Static Form page holds")
      response.status.code shouldBe 200
      html should include ("data-notification-indicator hidden")
      html should include ("data-notification-badge hidden>0</span>")
      html should not include ("<textus:include")
      html should not include ("${pageContext.notification")
    }

    "prefer the route target component layout for standalone app pages" in {
      Given("the prerequisites for prefer the route target component layout for standalone app pages")
      val descriptorroot = Files.createTempDirectory("cncf-app-layout-descriptor-")
      val editorroot = Files.createTempDirectory("cncf-app-layout-editor-")
      val notificationroot = Files.createTempDirectory("cncf-app-layout-notification-")
      Files.createDirectories(editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor"))
      Files.createDirectories(notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("partials"))
      Files.writeString(
        descriptorroot.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: textus-knowledge-editor
          |      layout: default
          |      composition: disabled
          |    - name: notifications
          |      layout: default
          |      composition: disabled
          |  routes:
          |    - path: /web/textus-knowledge-editor
          |      target:
          |        component: textus-knowledge-editor
          |        app: textus-knowledge-editor
          |    - path: /web/notifications
          |      target:
          |        component: textus-user-notification
          |        app: notifications
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><head><title>TKE</title></head><body>${partial.topbar}<aside>${partial.sidebar}</aside><main>${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("WEB-INF").resolve("partials").resolve("topbar.html"),
        "<header>Textus Knowledge Editor</header>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("WEB-INF").resolve("partials").resolve("sidebar.html"),
        "<nav>TKE Sidebar</nav>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        editorroot.resolve("src").resolve("main").resolve("web").resolve("textus-knowledge-editor").resolve("dashboard.html"),
        "<section>TKE Dashboard</section>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><head><title>Notifications</title></head><body>${partial.topbar}<main>${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("partials").resolve("topbar.html"),
        "<header>Notifications</header>",
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(descriptorroot.resolve("web-descriptor.yaml").toString)
        ))
      ).add(Vector(
        TestComponentFactory.create("textus_knowledge_editor", Protocol.empty),
        TestComponentFactory.create("textus_user_notification", Protocol.empty)
      ))
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusKnowledgeEditor")).getOrElse(fail("editor component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-knowledge-editor", "0.1.0", component = Some("textus-knowledge-editor"), archivePath = Some(editorroot.toString))
      )
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusUserNotification")).getOrElse(fail("notification component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-user-notification", "0.1.0", component = Some("textus-user-notification"), archivePath = Some(notificationroot.toString))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/textus-knowledge-editor/dashboard"))).unsafeRunSync()
      When("prefer the route target component layout for standalone app pages is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for prefer the route target component layout for standalone app pages holds")
      response.status.code shouldBe 200
      html should include ("<title>TKE</title>")
      html should include ("Textus Knowledge Editor")
      html should include ("TKE Sidebar")
      html should include ("TKE Dashboard")
      html should not include ("<title>Notifications</title>")
    }

    "compose component Web pages into a subsystem shell only when explicitly enabled" in {
      Given("the prerequisites for compose component Web pages into a subsystem shell only when explicitly enabled")
      val root = Files.createTempDirectory("cncf-web-composition-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |      composition: article
          |  pages:
          |    login:
          |      mode: screen
          |      layout: login
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(root.resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(root.resolve("notice-board").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(root.resolve("notice-board").resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(root.resolve("notice-board"))
      Files.writeString(
        root.resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${partial.header}${partial.navigation}<aside>${partial.sidebar}</aside><article>${content}</article>${partial.footer}</body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("header.html"), "<header>Subsystem Header</header>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("navigation.html"), "<nav>Subsystem Navigation</nav>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("sidebar.html"), "<nav>Subsystem Sidebar</nav>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("footer.html"), "<footer>Subsystem Footer</footer>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("WEB-INF").resolve("partials").resolve("header.html"), "<header>Component Header</header>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("WEB-INF").resolve("partials").resolve("navigation.html"), "<nav>Component Navigation</nav>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("WEB-INF").resolve("layouts").resolve("login.html"), "<!doctype html><html><body><main class=\"login-screen\">${content}</main></body></html>", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("notice-board").resolve("publicblogs.html"),
        """<section><textus-include name="navigation"></textus-include><h1>Public notices</h1></section>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("notice-board").resolve("standalone.html"),
        """<!doctype html><html><body><main class="standalone-screen">Standalone Screen</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("notice-board").resolve("login.html"), "<section>Login Screen</section>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val articleresponse = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/publicblogs"))).unsafeRunSync()
      val articlehtml = articleresponse.as[String].unsafeRunSync()
      val screenresponse = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/login"))).unsafeRunSync()
      val screenhtml = screenresponse.as[String].unsafeRunSync()
      val standaloneresponse = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/standalone"))).unsafeRunSync()
      When("compose component Web pages into a subsystem shell only when explicitly enabled is exercised")
      val standalonehtml = standaloneresponse.as[String].unsafeRunSync()

      Then("the observable contract for compose component Web pages into a subsystem shell only when explicitly enabled holds")
      articleresponse.status.code shouldBe 200
      articlehtml should include ("Subsystem Header")
      articlehtml should include ("Subsystem Navigation")
      articlehtml should include ("Subsystem Sidebar")
      articlehtml should include ("Subsystem Footer")
      articlehtml should include ("Component Navigation")
      articlehtml should include ("<article><section>")
      articlehtml should not include ("Component Header")
      screenresponse.status.code shouldBe 200
      screenhtml should include ("login-screen")
      screenhtml should include ("Login Screen")
      screenhtml should not include ("Subsystem Header")
      screenhtml should not include ("<article>")
      standaloneresponse.status.code shouldBe 200
      standalonehtml should include ("standalone-screen")
      standalonehtml should include ("Standalone Screen")
      standalonehtml should not include ("Subsystem Header")
      standalonehtml should not include ("<article>")
    }

    "render article-capable component pages standalone when no subsystem shell is available" in {
      Given("the prerequisites for render article-capable component pages standalone when no subsystem shell is available")
      val descriptorroot = Files.createTempDirectory("cncf-article-no-shell-descriptor-")
      val notificationroot = Files.createTempDirectory("cncf-article-no-shell-notification-")
      val editorroot = Files.createTempDirectory("cncf-article-no-shell-editor-")
      Files.createDirectories(notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications"))
      Files.createDirectories(editorroot.resolve("src").resolve("main").resolve("web").resolve("editor"))
      Files.writeString(
        descriptorroot.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notifications
          |      layout: notifications
          |      composition: article
          |    - name: editor
          |      layout: default
          |      composition: disabled
          |  pages:
          |    index:
          |      layout: notifications
          |      mode: article
          |  routes:
          |    - path: /web/notifications
          |      target:
          |        component: textus-user-notification
          |        app: notifications
          |    - path: /web/editor
          |      target:
          |        component: editor
          |        app: editor
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("layouts").resolve("notifications.html"),
        """<!doctype html><html><head><title>Notifications</title></head><body>${partial.topbar}<main>${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("WEB-INF").resolve("partials").resolve("topbar.html"),
        "<header>Notification Header</header>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        notificationroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("index.html"),
        "<section>Notification Inbox</section>",
        StandardCharsets.UTF_8
      )
      Files.writeString(editorroot.resolve("src").resolve("main").resolve("web").resolve("editor").resolve("index.html"), "<section>Editor</section>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(descriptorroot.resolve("web-descriptor.yaml").toString)
        ))
      ).add(Vector(
        TestComponentFactory.create("textus_user_notification", Protocol.empty),
        TestComponentFactory.create("editor", Protocol.empty)
      ))
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusUserNotification")).getOrElse(fail("notification component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-user-notification", "0.1.0", component = Some("textus-user-notification"), archivePath = Some(notificationroot.toString))
      )
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.Editor")).getOrElse(fail("editor component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "editor", "0.1.0", component = Some("editor"), archivePath = Some(editorroot.toString))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notifications"))).unsafeRunSync()
      When("render article-capable component pages standalone when no subsystem shell is available is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render article-capable component pages standalone when no subsystem shell is available holds")
      response.status.code shouldBe 200
      html should include ("<title>Notifications</title>")
      html should include ("Notification Header")
      html should include ("Notification Inbox")
      html should not include ("subsystem-shell-layout-not-found")
    }

    "merge subsystem Web app composition override without dropping component app assets" in {
      Given("the prerequisites for merge subsystem Web app composition override without dropping component app assets")
      val componentdescriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          name = "blog",
          path = "/web/blog",
          root = Some("/web/blog"),
          route = Some("/web/blog"),
          assets = WebDescriptor.Assets(css = Vector("/web/blog/assets/blog.css"), js = Vector("/web/blog/assets/blog.js")),
          layout = Some("reader")
        ))
      )
      val subsystemdescriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          name = "blog",
          composition = WebDescriptor.ComponentWebComposition.Article,
          compositionRaw = Some("article")
        ))
      )

      When("merge subsystem Web app composition override without dropping component app assets is exercised")
      val app = componentdescriptor.mergeOverride(subsystemdescriptor).apps.headOption.getOrElse(fail("merged app is missing"))

      Then("the observable contract for merge subsystem Web app composition override without dropping component app assets holds")
      app.path shouldBe "/web/blog"
      app.root shouldBe Some("/web/blog")
      app.route shouldBe Some("/web/blog")
      app.assets.css should contain ("/web/blog/assets/blog.css")
      app.assets.js should contain ("/web/blog/assets/blog.js")
      app.layout shouldBe Some("reader")
      app.composition shouldBe WebDescriptor.ComponentWebComposition.Article
    }

    "limit deemed-subsystem shell fallback to a single component Web root" in {
      Given("the prerequisites for limit deemed-subsystem shell fallback to a single component Web root")
      val singleroot = Files.createTempDirectory("cncf-single-component-shell-")
      val firstroot = Files.createTempDirectory("cncf-first-component-shell-")
      val secondroot = Files.createTempDirectory("cncf-second-component-shell-")
      Files.createDirectories(singleroot.resolve("src").resolve("main").resolve("web"))
      Files.createDirectories(firstroot.resolve("src").resolve("main").resolve("web"))
      Files.createDirectories(secondroot.resolve("src").resolve("main").resolve("web"))
      val singlesubsystem = _management_console_fixture_subsystem()
        .add(Vector(TestComponentFactory.create("single_shell", Protocol.empty)))
      singlesubsystem.findComponent(ComponentId("org.goldenport.cncf.test.SingleShell")).getOrElse(fail("single component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "single-shell", "0.1.0", archivePath = Some(singleroot.toString))
      )
      val multisubsystem = _management_console_fixture_subsystem()
        .add(Vector(
          TestComponentFactory.create("first_shell", Protocol.empty),
          TestComponentFactory.create("second_shell", Protocol.empty)
        ))
      multisubsystem.findComponent(ComponentId("org.goldenport.cncf.test.FirstShell")).getOrElse(fail("first component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "first-shell", "0.1.0", archivePath = Some(firstroot.toString))
      )
      multisubsystem.findComponent(ComponentId("org.goldenport.cncf.test.SecondShell")).getOrElse(fail("second component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "second-shell", "0.1.0", archivePath = Some(secondroot.toString))
      )

      val singleserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(singlesubsystem))
      When("limit deemed-subsystem shell fallback to a single component Web root is exercised")
      val multiserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(multisubsystem))

      Then("the observable contract for limit deemed-subsystem shell fallback to a single component Web root holds")
      singleserver._subsystem_shell_web_roots().map(_.name) should contain (singleroot.resolve("src").resolve("main").resolve("web").toString)
      multiserver._subsystem_shell_web_roots().map(_.name) should not contain firstroot.resolve("src").resolve("main").resolve("web").toString
      multiserver._subsystem_shell_web_roots().map(_.name) should not contain secondroot.resolve("src").resolve("main").resolve("web").toString
    }

    "prefer main project Web root over same-name repository CAR root" in {
      Given("a main component development project and a same-name repository component")
      val mainroot = Files.createDirectories(Files.createTempDirectory("cncf-main-web-root-").resolve("textus-knowledge-editor"))
      val carroot = Files.createTempDirectory("cncf-car-web-root-")
      Files.createDirectories(mainroot.resolve("src").resolve("main").resolve("web"))
      Files.createDirectories(carroot.resolve("src").resolve("main").resolve("web"))
      val classdir = Files.createDirectories(mainroot.resolve("target").resolve("scala-3.3.8").resolve("classes"))
      DevelopmentRuntimeManifestFixture.write(
        mainroot,
        classdir,
        "textus-knowledge-editor",
        "0.1.0-SNAPSHOT",
        "textus-knowledge-editor"
      )
      val maincomponent = new org.goldenport.cncf.component.Component() {}
      val carcomponent = new org.goldenport.cncf.component.Component() {}
      _initialize_component_with_id(
        "textus_knowledge_editor",
        "textus_knowledge_editor_main",
        maincomponent,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main
      ).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-knowledge-editor", "0.1.0-SNAPSHOT", component = Some("textus-knowledge-editor"), archivePath = Some(mainroot.toString), componentId = Some(org.goldenport.cncf.testutil.TestComponentFactory.componentId("textus_knowledge_editor")))
      )
      _initialize_component_with_id(
        "textus_knowledge_editor",
        "textus_knowledge_editor_car",
        carcomponent,
        origin = org.goldenport.cncf.component.ComponentOrigin.Repository("standard-repository:car")
      ).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-knowledge-editor", "0.1.0-SNAPSHOT", component = Some("textus-knowledge-editor"), archivePath = Some(carroot.toString))
      )
      val subsystem = _management_console_fixture_subsystem().add(Vector(maincomponent, carcomponent))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the component Web roots are resolved")
      val roots = server._component_web_roots("textus-knowledge-editor").map(_.name)
      val allroots = server._component_web_roots().map(_.name)

      Then("the main development project root takes precedence over the repository CAR root")
      roots should contain (mainroot.resolve("src").resolve("main").resolve("web").toString)
      roots should not contain carroot.resolve("src").resolve("main").resolve("web").toString
      allroots should contain (mainroot.resolve("src").resolve("main").resolve("web").toString)
      allroots should not contain carroot.resolve("src").resolve("main").resolve("web").toString
    }

    "compose child component article pages with an explicit subsystem shell owner" in {
      Given("the prerequisites for compose child component article pages with an explicit subsystem shell owner")
      val descriptorroot = Files.createTempDirectory("cncf-explicit-shell-descriptor-")
      val shellroot = Files.createTempDirectory("cncf-explicit-shell-owner-")
      val childroot = Files.createTempDirectory("cncf-explicit-shell-child-")
      Files.createDirectories(shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(childroot.resolve("src").resolve("main").resolve("web").resolve("notifications"))
      Files.writeString(
        descriptorroot.resolve("web-descriptor.yaml"),
        """web:
          |  shell:
          |    component: blog-component
          |    app: blog
          |    layout: default
          |  apps:
          |    - name: blog
          |    - name: notifications
          |      composition: article
          |  pages:
          |    index:
          |      mode: article
          |  routes:
          |    - path: /web/notifications
          |      target:
          |        component: textus-user-notification
          |        app: notifications
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${partial.header}<main class="blog-shell">${content}</main>${partial.footer}</body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("partials").resolve("header.html"),
        "<header>Blog Shell Header</header>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("partials").resolve("footer.html"),
        "<footer>Blog Shell Footer</footer>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        childroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("index.html"),
        "<section>Notification Article</section>",
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(descriptorroot.resolve("web-descriptor.yaml").toString)
        ))
      ).add(Vector(
        TestComponentFactory.create("blog_component", Protocol.empty),
        TestComponentFactory.create("textus_user_notification", Protocol.empty)
      ))
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.BlogComponent")).getOrElse(fail("blog component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "blog-component", "0.1.0", component = Some("blog-component"), archivePath = Some(shellroot.toString))
      )
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusUserNotification")).getOrElse(fail("notification component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-user-notification", "0.1.0", component = Some("textus-user-notification"), archivePath = Some(childroot.toString))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notifications"))).unsafeRunSync()
      When("compose child component article pages with an explicit subsystem shell owner is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for compose child component article pages with an explicit subsystem shell owner holds")
      response.status.code shouldBe 200
      html should include ("Blog Shell Header")
      html should include ("Blog Shell Footer")
      html should include ("Notification Article")
      html should include ("blog-shell")
    }

    "compose child component form result templates through the route Web app shell" in {
      Given("the prerequisites for compose child component form result templates through the route Web app shell")
      val descriptorroot = Files.createTempDirectory("cncf-explicit-shell-form-descriptor-")
      val shellroot = Files.createTempDirectory("cncf-explicit-shell-form-owner-")
      val childroot = Files.createTempDirectory("cncf-explicit-shell-form-child-")
      Files.createDirectories(shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("partials"))
      Files.createDirectories(childroot.resolve("src").resolve("main").resolve("web").resolve("notifications"))
      Files.writeString(
        descriptorroot.resolve("web-descriptor.yaml"),
        """web:
          |  shell:
          |    component: blog-component
          |    app: blog
          |    layout: default
          |  apps:
          |    - name: blog
          |    - name: notifications
          |      composition: article
          |    - name: alerts
          |      composition: article
          |  pages:
          |    notifications.notifications:
          |      mode: article
          |  form:
          |    textus-user-notification.notification.search-my-notifications:
          |      layout: notifications
          |  routes:
          |    - path: /web/notifications
          |      target:
          |        component: textus-user-notification
          |        app: notifications
          |    - path: /web/alerts
          |      target:
          |        component: textus-user-notification
          |        app: alerts
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${partial.header}<main class="blog-shell">${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        shellroot.resolve("src").resolve("main").resolve("web").resolve("blog").resolve("WEB-INF").resolve("partials").resolve("header.html"),
        "<header>Blog Shell Header</header>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        childroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("notifications__success.html"),
        "<section>Notification Result</section>",
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(descriptorroot.resolve("web-descriptor.yaml").toString)
        ))
      ).add(Vector(
        TestComponentFactory.create("blog_component", Protocol.empty),
        TestComponentFactory.create("textus_user_notification", Protocol.empty)
      ))
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.BlogComponent")).getOrElse(fail("blog component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "blog-component", "0.1.0", component = Some("blog-component"), archivePath = Some(shellroot.toString))
      )
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusUserNotification")).getOrElse(fail("notification component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-user-notification", "0.1.0", component = Some("textus-user-notification"), archivePath = Some(childroot.toString))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("compose child component form result templates through the route Web app shell is exercised")
      val html = server._prepared_form_result_template(
        "textus-user-notification",
        "notification",
        "search-my-notifications",
        200,
        Map("textus.form.page" -> "notifications")
      ).toOption.flatten.getOrElse(fail("notification result is missing"))

      Then("the observable contract for compose child component form result templates through the route Web app shell holds")
      html should include ("Blog Shell Header")
      html should include ("Notification Result")
      html should include ("blog-shell")
    }

    "fail when explicit subsystem shell owner has no component Web root" in {
      Given("the prerequisites for fail when explicit subsystem shell owner has no component Web root")
      val descriptorroot = Files.createTempDirectory("cncf-missing-explicit-shell-owner-")
      val childroot = Files.createTempDirectory("cncf-missing-explicit-shell-child-")
      Files.createDirectories(descriptorroot.resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(childroot.resolve("src").resolve("main").resolve("web").resolve("notifications"))
      Files.writeString(
        descriptorroot.resolve("web-descriptor.yaml"),
        """web:
          |  shell:
          |    component: missing-shell
          |    app: blog
          |    layout: default
          |  apps:
          |    - name: notifications
          |      composition: article
          |  routes:
          |    - path: /web/notifications
          |      target:
          |        component: textus-user-notification
          |        app: notifications
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        descriptorroot.resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${content}</body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        childroot.resolve("src").resolve("main").resolve("web").resolve("notifications").resolve("index.html"),
        "<section>Notification Article</section>",
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(descriptorroot.resolve("web-descriptor.yaml").toString)
        ))
      ).add(Vector(TestComponentFactory.create("textus_user_notification", Protocol.empty)))
      subsystem.findComponent(ComponentId("org.goldenport.cncf.test.TextusUserNotification")).getOrElse(fail("notification component missing")).withArtifactMetadata(
        org.goldenport.cncf.component.Component.ArtifactMetadata("test", "textus-user-notification", "0.1.0", component = Some("textus-user-notification"), archivePath = Some(childroot.toString))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notifications"))).unsafeRunSync()
      When("fail when explicit subsystem shell owner has no component Web root is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for fail when explicit subsystem shell owner has no component Web root holds")
      response.status.code shouldBe 500
      html should include ("Static Form subsystem shell component Web root not found: missing-shell")
    }

    "compose form result templates into a subsystem shell when app composition is article" in {
      Given("the prerequisites for compose form result templates into a subsystem shell when app composition is article")
      val root = Files.createTempDirectory("cncf-web-form-composition-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |      composition: article
          |  pages:
          |    login:
          |      mode: screen
          |  form:
          |    notice-board.notice.post-notice:
          |      layout: default
          |    notice-board.notice.login-notice:
          |      layout: login
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("WEB-INF").resolve("layouts"))
      Files.createDirectories(root.resolve("WEB-INF").resolve("partials"))
      Files.writeString(
        root.resolve("WEB-INF").resolve("layouts").resolve("default.html"),
        """<!doctype html><html><body>${partial.header}<article>${content}</article>${partial.footer}</body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("WEB-INF").resolve("layouts").resolve("login.html"),
        """<!doctype html><html><body><main class="login-screen">${content}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("header.html"), "<header>Subsystem Header</header>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("WEB-INF").resolve("partials").resolve("footer.html"), "<footer>Subsystem Footer</footer>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("post-notice__200.html"), "<section>Posted</section>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("login-notice__200.html"), "<section>Login Result</section>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val articlehtml = server._prepared_form_result_template("notice-board", "notice", "post-notice", 200).toOption.flatten.getOrElse(fail("article result is missing"))
      When("compose form result templates into a subsystem shell when app composition is article is exercised")
      val screenhtml = server._prepared_form_result_template(
        "notice-board",
        "notice",
        "login-notice",
        200,
        Map("textus.form.page" -> "login")
      ).toOption.flatten.getOrElse(fail("screen result is missing"))

      Then("the observable contract for compose form result templates into a subsystem shell when app composition is article holds")
      articlehtml should include ("Subsystem Header")
      articlehtml should include ("<article><section>Posted</section></article>")
      articlehtml should include ("Subsystem Footer")
      screenhtml should include ("login-screen")
      screenhtml should include ("Login Result")
      screenhtml should not include ("Subsystem Header")
      screenhtml should not include ("<article>")
    }

    "reject invalid Web app composition and page mode values" in {
      Given("the prerequisites for reject invalid Web app composition and page mode values")
      val invalidcomposition = Files.createTempDirectory("cncf-web-invalid-composition-")
      Files.writeString(
        invalidcomposition.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |      composition: sideways
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val invalidmode = Files.createTempDirectory("cncf-web-invalid-page-mode-")
      When("reject invalid Web app composition and page mode values is exercised")
      Files.writeString(
        invalidmode.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  pages:
          |    login:
          |      mode: popup
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      Then("the observable contract for reject invalid Web app composition and page mode values holds")
      WebDescriptor.load(invalidcomposition.resolve("web-descriptor.yaml")) match {
        case Consequence.Success(_) => fail("invalid app composition should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.toString should include ("invalid app composition")
      }
      WebDescriptor.load(invalidmode.resolve("web-descriptor.yaml")) match {
        case Consequence.Success(_) => fail("invalid page mode should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.toString should include ("invalid page mode")
      }
    }

    "fail deterministically when an explicit Static Form layout is missing" in {
      Given("the prerequisites for fail deterministically when an explicit Static Form layout is missing")
      val root = Files.createTempDirectory("cncf-web-missing-layout-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  pages:
          |    publicblogs:
          |      layout: missing
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("publicblogs.html"), "<h1>Public Blogs</h1>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/publicblogs"))).unsafeRunSync()
      When("fail deterministically when an explicit Static Form layout is missing is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for fail deterministically when an explicit Static Form layout is missing holds")
      response.status.code shouldBe 500
      html should include ("Static Form layout not found: missing")
    }

    "fail form result layout composition as a Consequence failure" in {
      Given("the prerequisites for fail form result layout composition as a Consequence failure")
      val root = Files.createTempDirectory("cncf-web-form-missing-layout-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  form:
          |    notice-board.notice.post-notice:
          |      layout: missing
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("post-notice__200.html"), "<section>Result</section>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      When("fail form result layout composition as a Consequence failure is exercised")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      Then("the observable contract for fail form result layout composition as a Consequence failure holds")
      server._prepared_form_result_template("notice-board", "notice", "post-notice", 200) match {
        case Consequence.Success(_) => fail("missing explicit layout should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.toString should include ("Static Form layout not found: missing")
          conclusion.observation.taxonomy shouldBe Taxonomy.resourceInvalid
          conclusion.observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
          conclusion.observation.cause.descriptor.facets should contain (Descriptor.Facet.Name("missing"))
      }
    }

    "prefer app-named pages when multiple static-form apps are declared" in {
      Given("the prerequisites for prefer app-named pages when multiple static-form apps are declared")
      val root = Files.createTempDirectory("cncf-web-multi-app-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: app-a
          |    - name: app-b
          |  routes:
          |    - path: /web/a
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: app-a
          |    - path: /web/b
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: app-b
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("app-a").resolve("assets"))
      Files.createDirectories(root.resolve("app-b").resolve("assets"))
      Files.createDirectories(root.resolve("assets"))
      Files.writeString(root.resolve("index.html"), "<h1>Flat Root</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("assets").resolve("app.css"), ".flat-root { color: red; }\n", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("app-b").resolve("index.html"), "<h1>App B</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("app-b").resolve("assets").resolve("app.css"), ".app-b { color: blue; }\n", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val appb = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/b"))).unsafeRunSync()
      val assetb = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/b/assets/app.css"))).unsafeRunSync()
      val appbhtml = appb.as[String].unsafeRunSync()
      When("prefer app-named pages when multiple static-form apps are declared is exercised")
      val assetbcss = assetb.as[String].unsafeRunSync()

      Then("the observable contract for prefer app-named pages when multiple static-form apps are declared holds")
      appb.status.code shouldBe 200
      appbhtml should include ("App B")
      appbhtml should not include "Flat Root"
      assetb.status.code shouldBe 200
      assetbcss should include ("app-b")
      assetbcss should not include "flat-root"
    }

    "serve static Web app HTML and assets through descriptor route aliases" in {
      Given("the prerequisites for serve static Web app HTML and assets through descriptor route aliases")
      val root = Files.createTempDirectory("cncf-web-alias-root-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: notice-board
          |  routes:
          |    - path: /web/board
          |      kind: alias
          |      target:
          |        component: notice-board
          |        app: notice-board
          |    - path: /web
          |      kind: default
          |      target:
          |        component: notice-board
          |        app: notice-board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Aliased Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("about.html"), "<h1>Aliased About</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".alias-notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val index = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      val indexslash = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/"))).unsafeRunSync()
      val about = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/about"))).unsafeRunSync()
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board/assets/app.css"))).unsafeRunSync()
      When("serve static Web app HTML and assets through descriptor route aliases is exercised")
      val default = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()

      Then("the observable contract for serve static Web app HTML and assets through descriptor route aliases holds")
      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("Aliased Notice Board")
      indexslash.status.code shouldBe 200
      indexslash.as[String].unsafeRunSync() should include ("Aliased Notice Board")
      about.status.code shouldBe 200
      about.as[String].unsafeRunSync() should include ("Aliased About")
      asset.status.code shouldBe 200
      asset.as[String].unsafeRunSync() should include ("alias-notice-board")
      default.status.code shouldBe 200
      default.as[String].unsafeRunSync() should include ("Aliased Notice Board")
    }

    "redirect / to /web and render onboarding help on /web in non-production when no default web route is configured" in {
      Given("the prerequisites for redirect / to /web and render onboarding help on /web in non-production when no default web route is configured")
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("develop")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val root = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))).unsafeRunSync()
      val web = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val webslash = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/"))).unsafeRunSync()
      When("redirect / to /web and render onboarding help on /web in non-production when no default web route is configured is exercised")
      val webhtml = web.as[String].unsafeRunSync()

      Then("the observable contract for redirect / to /web and render onboarding help on /web in non-production when no default web route is configured holds")
      root.status.code shouldBe 307
      root.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      webslash.status.code shouldBe 307
      webslash.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      web.status.code shouldBe 200
      webhtml should include ("CNCF Runtime Help")
      webhtml should include ("/man/system")
      webhtml should include ("/web/notice-board")
      webhtml should not include ("/form/notice-board")
    }

    "render runtime landing app links from WebDescriptor routes without implicit component aliases" in {
      Given("the prerequisites for render runtime landing app links from WebDescriptor routes without implicit component aliases")
      val root = Files.createTempDirectory("cncf-runtime-landing-routes-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: board
          |      kind: static-form
          |  routes:
          |    - path: /web/board
          |      target:
          |        component: notice-board
          |        app: board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("develop"),
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val web = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val componentalias = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board"))).unsafeRunSync()
      When("render runtime landing app links from WebDescriptor routes without implicit component aliases is exercised")
      val webhtml = web.as[String].unsafeRunSync()

      Then("the observable contract for render runtime landing app links from WebDescriptor routes without implicit component aliases holds")
      web.status.code shouldBe 200
      webhtml should include ("""href="/web/board"""")
      webhtml should not include ("""href="/web/notice-board"""")
      componentalias.status.code shouldBe 404
    }

    "redirect / to /web and keep /web strict in production when no default web route is configured" in {
      Given("the prerequisites for redirect / to /web and keep /web strict in production when no default web route is configured")
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val root = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))).unsafeRunSync()
      When("redirect / to /web and keep /web strict in production when no default web route is configured is exercised")
      val web = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()

      Then("the observable contract for redirect / to /web and keep /web strict in production when no default web route is configured holds")
      root.status.code shouldBe 307
      root.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      web.status.code shouldBe 404
    }

    "redirect /rest to the latest stable REST namespace" in {
      Given("the prerequisites for redirect /rest to the latest stable REST namespace")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("redirect /rest to the latest stable REST namespace is exercised")
      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/rest?mode=test"))).unsafeRunSync()

      Then("the observable contract for redirect /rest to the latest stable REST namespace holds")
      response.status.code shouldBe 307
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/rest/v1?mode=test")
    }

    "redirect versionless REST requests to /rest/v1 with method-preserving redirects" in {
      Given("the prerequisites for redirect versionless REST requests to /rest/v1 with method-preserving redirects")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("redirect versionless REST requests to /rest/v1 with method-preserving redirects is exercised")
      val response = server.routes(null).orNotFound.run(Request[IO](Method.POST, Uri.unsafeFromString("/rest/admin/system/ping?mode=test"))).unsafeRunSync()

      Then("the observable contract for redirect versionless REST requests to /rest/v1 with method-preserving redirects holds")
      response.status.code shouldBe 307
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/rest/v1/admin/system/ping?mode=test")
    }

    "dispatch canonical REST requests through /rest/v1" in {
      Given("the prerequisites for dispatch canonical REST requests through /rest/v1")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/rest/v1/admin/system/ping"))).unsafeRunSync()
      When("dispatch canonical REST requests through /rest/v1 is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for dispatch canonical REST requests through /rest/v1 holds")
      response.status.code shouldBe 200
      body should include ("runtime: goldenport-cncf")
    }

    "return not found for implicit top-level REST routes once /rest/v1 is canonical" in {
      Given("the prerequisites for return not found for implicit top-level REST routes once /rest/v1 is canonical")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("return not found for implicit top-level REST routes once /rest/v1 is canonical is exercised")
      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/admin/system/ping"))).unsafeRunSync()

      Then("the observable contract for return not found for implicit top-level REST routes once /rest/v1 is canonical holds")
      response.status.code shouldBe 404
    }

    "leave /api unsupported" in {
      Given("the prerequisites for leave /api unsupported")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("leave /api unsupported is exercised")
      val response = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/api/v1/admin/system/ping"))).unsafeRunSync()

      Then("the observable contract for leave /api unsupported holds")
      response.status.code shouldBe 404
    }

    "not infer public routes for a single component Web app" in {
      // Given
      Given("the prerequisites for not infer public routes for a single component Web app")
      val root = Files.createTempDirectory("cncf-web-implicit-alias-root-")
      Files.writeString(root.resolve("web-descriptor.yaml"), "web:\n  apps:\n    - name: notice-board\n", StandardCharsets.UTF_8)
      Files.createDirectories(root.resolve("notice-board").resolve("assets"))
      Files.writeString(root.resolve("notice-board").resolve("index.html"), "<h1>Implicit Notice Board</h1>", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("notice-board").resolve("assets").resolve("app.css"), ".implicit-notice-board { color: #14532d; }\n", StandardCharsets.UTF_8)
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        )),
        ConfigurationTrace.empty
      )
      val component = TestComponentFactory.create("notice_board", Protocol.empty)
      val subsystem = new Subsystem(
        name = "implicit-web",
        configuration = configuration
      ).add(Vector(component))
      val engine = new HttpExecutionEngine(subsystem)
      When("not infer public routes for a single component Web app is exercised")
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      // When
      Then("the observable contract for not infer public routes for a single component Web app holds")
      engine.webDescriptor.routes shouldBe Vector.empty
      val alias = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board"))).unsafeRunSync()
      val default = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board/assets/app.css"))).unsafeRunSync()
      val canonical = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board/notice-board"))).unsafeRunSync()

      // Then
      alias.status.code shouldBe 404
      default.status.code shouldBe 200
      default.as[String].unsafeRunSync() should not include ("Implicit Notice Board")
      asset.status.code shouldBe 404
      canonical.status.code shouldBe 200
      canonical.as[String].unsafeRunSync() should include ("Implicit Notice Board")
    }

    "load Static Form Web App descriptor, templates, and assets from a CAR archive Web root" in {
      Given("the prerequisites for load Static Form Web App descriptor, templates, and assets from a CAR archive Web root")
      val path = _web_archive_fixture(
        "sample.car",
        Vector(
          "web/web-descriptor.yaml" -> "web:\n  apps:\n    - name: notice-board\n",
          "web/notice-board/index.html" -> "<h1>Archive Notice Board</h1>",
          "web/notice-board/notice/post-notice__200.html" -> "ARCHIVE SERVICE OPERATION",
          "web/notice-board/assets/app.css" -> ".archive-notice-board { color: #14532d; }\n"
        )
      )
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(path.toString)
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      When("load Static Form Web App descriptor, templates, and assets from a CAR archive Web root is exercised")
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      Then("the observable contract for load Static Form Web App descriptor, templates, and assets from a CAR archive Web root holds")
      engine.webDescriptor.apps.map(_.name) should contain ("notice-board")
      server._web_resource_roots().map(_.name) shouldBe Vector(path.toString)
      server._component_web_app("notice-board", "notice-board", Vector.empty).unsafeRunSync().as[String].unsafeRunSync() should include ("Archive Notice Board")
      server._form_result_static_template("notice-board", "notice", "post-notice", 200) shouldBe Some("ARCHIVE SERVICE OPERATION")
      server._web_app_asset_content("notice-board", "app.css")
        .map(x => new String(x._1.openInputStream().readAllBytes(), StandardCharsets.UTF_8)) shouldBe Some(".archive-notice-board { color: #14532d; }\n")
    }

    }

    "provide entity edit, data, and form-definition contracts" which {
    "render component entity edit page contract" in {
      Given("a live entity collection and its canonical stored Entity ID")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      When("the edit page is rendered with its canonical route locator")
      val html = _renderer.renderComponentAdminEntityEdit(subsystem, componentname, entitypath, recordid).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the canonical route is used in form, value, and navigation contracts")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Edit")
      html should include ("Edit Notice")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentpath}/admin/entities/${entitypath}/${recordid}/update")
      html should include ("name=\"id\"")
      html should include (s"value=\"${recordid}\"")
      html should include ("Update")
      html should include ("Cancel")
      html should include (s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}")
    }

    "render component entity edit page with hidden form context" in {
      Given("a live entity collection, a canonical stored Entity ID, and hidden navigation context")
      val subsystem = _management_console_fixture_subsystem()
      val componentname = "notice_board"
      val componentpath = "notice-board"
      val entitypath = "notice"
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity](entitypath).storage.storeRealm.values.head.id.value

      When("the edit page is rendered")
      val html = _renderer.renderComponentAdminEntityEdit(
        subsystem,
        componentname,
        entitypath,
        recordid,
        values = Map(
          "crud.origin.href" -> s"/web/${componentpath}/admin/entities/${entitypath}?page=2&pageSize=20",
          "crud.success.href" -> s"/web/${componentpath}/admin/entities/${entitypath}/${recordid}",
          "paging.page" -> "2",
          "paging.pageSize" -> "20",
          "search.status" -> "open",
          "etag" -> "v1",
          "id" -> "missing-id"
        )
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the hidden context remains in form fields and never leaks into visible links")
      html should include ("type=\"hidden\" name=\"crud.origin.href\"")
      html should include ("type=\"hidden\" name=\"crud.success.href\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"paging.pageSize\" value=\"20\"")
      html should include ("type=\"hidden\" name=\"search.status\" value=\"open\"")
      html should include ("type=\"hidden\" name=\"etag\" value=\"v1\"")
      html should include (s"value=\"${recordid}\"")
      html should not include ("value=\"missing-id\"")
      html should not include ("crud.origin.href=")
      html should not include ("search.status=")
    }

    "render component entity new page contract" in {
      Given("the prerequisites for render component entity new page contract")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)

      When("render component entity new page contract is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, component.name, "sales-order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page contract holds")
      html should include (s"${component.name} Sales Order New")
      html should include ("New Sales Order")
      html should include ("<form method=\"post\"")
      html should include ("class=\"admin-form\"")
      html should include ("class=\"card admin-card")
      html should include (s"/form/${componentpath}/admin/entities/sales-order/create")
      html should include ("name=\"fields\"")
      html should include ("Use one name=value pair per line")
      html should include ("Create")
      html should include ("Cancel")
      html should include (s"/web/${componentpath}/admin/entities/sales-order")
    }

    "render component entity new page from CML schema descriptor without WebDescriptor" in {
      Given("the prerequisites for render component entity new page from CML schema descriptor without WebDescriptor")
      val descriptor = ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(Schema(Vector(
              Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("body").label("Notice body").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("textarea"),
                  readonly = true,
                  placeholder = Some("Write the notice body."),
                  help = Some("Notice body shown on the board.")
                )
              ),
              Column(
                BaseContent.Builder("status").label("Publication status").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("select"),
                  values = Vector("draft", "published"),
                  required = Some(true)
                )
              )
            )))
          )
        )
      )
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      When("render component entity new page from CML schema descriptor without WebDescriptor is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, "notice_board", "notice").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from CML schema descriptor without WebDescriptor holds")
      html should include ("name=\"id\"")
      html should include ("name=\"title\"")
      html should include ("name=\"body\"")
      html should include ("name=\"status\"")
      html should include ("Notice body")
      html should include ("Publication status")
      html should include ("<textarea")
      html should include ("<select")
      html should include ("<option value=\"draft\"")
      html should include ("<option value=\"published\"")
      html should include ("readonly")
      html should include ("required")
      html should include ("placeholder=\"Write the notice body.\"")
      html should include ("Notice body shown on the board.")
    }

    "render component entity new page from generated companion schema" in {
      Given("the prerequisites for render component entity new page from generated companion schema")
      val component = TestComponentFactory
        .create("generated_schema_component", Protocol.empty)
        .withComponentDescriptors(Vector(ComponentDescriptor(
          componentName = Some("generated_schema_component"),
          entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
            entityName = "order",
            collectionId = EntityCollectionId("test", "a", "order"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ))
        )))
      val bootstrapped = new ComponentFactory().bootstrap(component)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(bootstrapped))

      When("render component entity new page from generated companion schema is exercised")
      val html = _renderer.renderComponentAdminEntityNew(subsystem, "generated_schema_component", "order").map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from generated companion schema holds")
      html should include ("name=\"id\"")
      html should include ("name=\"name\"")
      html should include ("name=\"status\"")
      html should include ("Order status")
      html should include ("<select")
      html should include ("<option value=\"submitted\"")
      html should include ("CML generated status hint.")
      html should include ("Use one name=value pair per line")
    }

    "render component entity new page from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for render component entity new page from merged Schema and WebDescriptor controls")
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()

      When("render component entity new page from merged Schema and WebDescriptor controls is exercised")
      val html = _renderer.renderComponentAdminEntityNew(
        subsystem,
        "notice_board",
        "notice",
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity new admin is missing"))

      Then("the observable contract for render component entity new page from merged Schema and WebDescriptor controls holds")
      html should include ("name=\"id\"")
      html should include ("name=\"body\"")
      html should include ("name=\"status\"")
      html should include ("Notice body")
      html should include ("<textarea")
      html should include ("placeholder=\"Descriptor body placeholder.\"")
      html should include ("Descriptor body help.")
      html should include ("<select")
      html should include ("<option value=\"archived\"")
    }

    "expose Schema labels in admin entity Form API and HTML" in {
      Given("the prerequisites for expose Schema labels in admin entity Form API and HTML")
      val schema = Schema(Vector(
        Column(
          BaseContent.Builder("senderName").label("Sender").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            placeholder = Some("Your name"),
            help = Some("Name displayed as the poster."),
            validation = WebValidationHints(minLength = Some(1))
          )
        ),
        Column(
          BaseContent.Builder("body").label("Body").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(
            controlType = Some("textarea"),
            placeholder = Some("Notice body"),
            help = Some("Main notice text."),
            validation = WebValidationHints(minLength = Some(1))
          )
        )
      ))
      val subsystem = _management_console_fixture_subsystem(schema = schema)

      val definition = parse(_renderer
        .renderComponentAdminEntityFormDefinition(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity form definition is missing")))
        .getOrElse(fail("component entity form definition JSON is invalid"))
      When("expose Schema labels in admin entity Form API and HTML is exercised")
      val fields = definition.hcursor.downField("fields")
      Then("the observable contract for expose Schema labels in admin entity Form API and HTML holds")
      fields.downN(0).downField("label").as[String].toOption shouldBe Some("Sender")
      fields.downN(0).downField("placeholder").as[String].toOption shouldBe Some("Your name")
      fields.downN(0).downField("validation").downField("minLength").as[Int].toOption shouldBe Some(1)
      fields.downN(1).downField("label").as[String].toOption shouldBe Some("Body")
      fields.downN(1).downField("type").as[String].toOption shouldBe Some("textarea")

      val html = _renderer
        .renderComponentAdminEntityNew(subsystem, "notice_board", "notice")
        .map(_.body)
        .getOrElse(fail("component entity new admin is missing"))
      html should include ("""<label class="form-label" for="new-field-sender-name">Sender</label>""")
      html should include ("""<label class="form-label" for="new-field-body">Body</label>""")
      html should include ("""placeholder="Your name"""")
      html should include ("""minlength="1"""")
    }

    "redisplay admin entity create validation errors before dispatching" in {
      Given("the prerequisites for redisplay admin entity create validation errors before dispatching")
      val descriptor = ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(Schema(Vector(
              Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(BaseContent.simple("title"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("status").label("Publication status").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(
                  controlType = Some("select"),
                  values = Vector("draft", "published"),
                  required = Some(true)
                )
              )
            )))
          )
        )
      )
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "id=notice_1&title=hello&status=archived"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("redisplay admin entity create validation errors before dispatching is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay admin entity create validation errors before dispatching holds")
      response.status.code shouldBe 400
      html should include ("New Notice")
      html should include ("Validation failed.")
      html should include ("archived is not an allowed value for Publication status.")
      html should include ("is-invalid")
      html should include ("value=\"notice_1\"")
      html should include ("value=\"hello\"")
    }

    "validate admin entity create and update POST values against Schema hints" in {
      Given("the prerequisites for validate admin entity create and update POST values against Schema hints")
      val schema = Schema(Vector(
        Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
        Column(
          BaseContent.Builder("title").label("Title").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(validation = WebValidationHints(minLength = Some(3)))
        ),
        Column(
          BaseContent.Builder("author").label("Author").build(),
          ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
          web = WebColumn(required = Some(true))
        )
      ))
      val subsystem = _management_console_fixture_subsystem(schema = schema)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val recordid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head.id.value

      val createresponse = server
        ._submit_component_admin_entity_create(
          _post_form_request("/form/notice-board/admin/entities/notice/create", "title=Hi&author="),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("validate admin entity create and update POST values against Schema hints is exercised")
      val createhtml = createresponse.as[String].unsafeRunSync()

      Then("the observable contract for validate admin entity create and update POST values against Schema hints holds")
      createresponse.status.code shouldBe 400
      createhtml should include ("Validation failed.")
      createhtml should include ("admin-feedback")
      createhtml should include ("Title must be at least 3 characters.")
      createhtml should include ("Author is required.")
      createhtml should include ("is-invalid")

      val updateresponse = server
        ._submit_component_admin_entity_update(
          _post_form_request(s"/form/notice-board/admin/entities/notice/${recordid}/update", "title=No&author="),
          "notice-board",
          "notice",
          recordid
        )
        .unsafeRunSync()
      val updatehtml = updateresponse.as[String].unsafeRunSync()

      updateresponse.status.code shouldBe 400
      updatehtml should include ("Validation failed.")
      updatehtml should include ("admin-feedback")
      updatehtml should include ("Title must be at least 3 characters.")
      updatehtml should include ("Author is required.")
      updatehtml should include ("is-invalid")
    }

    "render component entity update submission result contract" in {
      Given("the prerequisites for render component entity update submission result contract")
      val html = _renderer.renderComponentAdminEntityUpdateResult(
        "admin",
        "sales-order",
        "sales-order-1",
        Map("status" -> "confirmed")
      ).body

      When("the observable result for render component entity update submission result contract is inspected")
      locally {
        Then("the observable contract for render component entity update submission result contract holds")
        html should include ("admin Sales Order Update Result")
        html should include ("Update submitted")
        html should include ("nav nav-pills")
        html should include ("class=\"card admin-card")
        html should include ("table table-sm table-hover align-middle mb-0")
        html should include ("Entity update execution is not enabled in this baseline")
        html should include ("result.status")
        html should include ("result.ok")
        html should include ("result.body")
        html should include ("status")
        html should include ("confirmed")
        html should include ("/web/admin/admin/entities/sales-order/sales-order-1")
        html should include ("/web/admin/admin/entities/sales-order/sales-order-1/edit")
      }
    }

    "render component entity create submission result contract" in {
      Given("the prerequisites for render component entity create submission result contract")
      val html = _renderer.renderComponentAdminEntityCreateResult(
        "admin",
        "sales-order",
        Map("status" -> "draft")
      ).body

      When("the observable result for render component entity create submission result contract is inspected")
      locally {
        Then("the observable contract for render component entity create submission result contract holds")
        html should include ("admin Sales Order Create Result")
        html should include ("Create submitted")
        html should include ("nav nav-pills")
        html should include ("class=\"card admin-card")
        html should include ("table table-sm table-hover align-middle mb-0")
        html should include ("Entity create execution is not enabled in this baseline")
        html should include ("result.status")
        html should include ("result.ok")
        html should include ("result.body")
        html should include ("status")
        html should include ("draft")
        html should include ("/web/admin/admin/entities/sales-order")
        html should include ("/web/admin/admin/entities/sales-order/new")
      }
    }

    "render component entity edit page from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for render component entity edit page from merged Schema and WebDescriptor controls")
      val subsystem = _management_console_fixture_subsystem()
      val recordid = _notice_fixture_component(subsystem).
        entitySpace.
        entity[NoticeEntity]("notice").
        storage.
        storeRealm.
        values.
        head.
        id.
        value
      val descriptor = WebDescriptor(admin = Map(
        "notice-board.entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
          WebDescriptor.AdminField("id", WebDescriptor.FormControl(readonly = true)),
          WebDescriptor.AdminField(
            "title",
            WebDescriptor.FormControl(
              placeholder = Some("Descriptor title placeholder."),
              help = Some("Descriptor title help.")
            )
          ),
          WebDescriptor.AdminField("author", WebDescriptor.FormControl(hidden = true))
        ))
      ))

      When("render component entity edit page from merged Schema and WebDescriptor controls is exercised")
      val html = _renderer.renderComponentAdminEntityEdit(
        subsystem,
        "notice_board",
        "notice",
        recordid,
        webDescriptor = descriptor
      ).map(_.body).getOrElse(fail("component entity edit admin is missing"))

      Then("the observable contract for render component entity edit page from merged Schema and WebDescriptor controls holds")
      html should include ("id=\"field-id\"")
      html should include ("readonly")
      html should include ("value=\"board update\"")
      html should include ("placeholder=\"Descriptor title placeholder.\"")
      html should include ("Descriptor title help.")
      html should include ("type=\"hidden\" id=\"field-author\" name=\"author\" value=\"alice\"")
    }

    "render component data administration page" in {
      Given("the prerequisites for render component data administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component data administration page is exercised")
      val html = _renderer.renderComponentAdminData(subsystem, component.name).map(_.body).getOrElse(fail("component data admin is missing"))

      Then("the observable contract for render component data administration page holds")
      html should include (s"${component.name} Data Administration")
      html should include ("Data record management")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin/descriptor")
      html should include ("Data record management")
      html should include ("Implemented baseline")
      html should include ("Delete flows")
    }

    "render component data pages from a live DataStore fixture" in {
      Given("the prerequisites for render component data pages from a live DataStore fixture")
      val fixture = _data_fixture()
      When("the observable result for render component data pages from a live DataStore fixture is inspected")
      locally {
        Then("the observable contract for render component data pages from a live DataStore fixture holds")
        _with_global_runtime(fixture.runtime) {
        val html = _renderer.renderComponentAdminDataType(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data type admin is missing"))
        val firstpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data first page admin is missing"))
        val secondpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 2, pageSize = 1)
        ).map(_.body).getOrElse(fail("component data second page admin is missing"))
        val totalpage = _renderer.renderComponentAdminDataType(
          fixture.subsystem,
          "notice_board",
          "audit",
          StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
          WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
        ).map(_.body).getOrElse(fail("component data total page admin is missing"))
        val unsupportedfixture = _data_fixture(TotalCountCapability.Unsupported)
        val unsupportedtotalpage = _with_global_runtime(unsupportedfixture.runtime) {
          _renderer.renderComponentAdminDataType(
            unsupportedfixture.subsystem,
            "notice_board",
            "audit",
            StaticFormAppRenderer.PageRequest(page = 1, pageSize = 1, includeTotal = true),
            WebDescriptor(admin = Map("data.audit" -> WebDescriptor.AdminSurface(WebDescriptor.TotalCountPolicy.Optional)))
          ).map(_.body).getOrElse(fail("component data unsupported total page admin is missing"))
        }
        val detail = _renderer.renderComponentAdminDataDetail(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data detail admin is missing"))
        val edit = _renderer.renderComponentAdminDataEdit(fixture.subsystem, "notice_board", "audit", "audit_1").map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = _renderer.renderComponentAdminDataNew(fixture.subsystem, "notice_board", "audit").map(_.body).getOrElse(fail("component data new admin is missing"))

        html should include ("audit_1")
        html should include ("<th>id</th><th>action</th><th>actor</th><th>Actions</th>")
        html should include ("class=\"card admin-card")
        html should include ("class=\"table table-sm table-hover align-middle\"")
        html should include ("class=\"btn-group btn-group-sm\"")
        html should include ("created")
        html should include ("alice")
        html should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1")
        firstpage should include ("Page 1")
        firstpage should include ("page=2&amp;pageSize=1")
        secondpage should include ("Page 2")
        secondpage should include ("page-item disabled\"><a class=\"page-link\" href=\"/web/org-goldenport-cncf-test-notice-board/admin/data/audit?page=3&amp;pageSize=1\">Next")
        totalpage should include ("total 2")
        totalpage should include ("includeTotal=true")
        unsupportedtotalpage should include ("alert-warning")
        unsupportedtotalpage should include ("admin-feedback")
        unsupportedtotalpage should include ("total count is not available for data.audit")
        detail should include ("created")
        detail should include ("alice")
        detail should include ("class=\"card admin-card")
        detail should include ("class=\"btn btn-primary\"")
        detail should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        detail should include ("/web/org-goldenport-cncf-test-notice-board/admin")
        edit should include ("name=\"action\"")
        edit should include ("value=\"created\"")
        edit should include ("class=\"admin-form\"")
        edit should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1")
        edit should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        newly should include ("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/create")
        newly should include ("class=\"admin-form\"")
        newly should include ("/web/org-goldenport-cncf-test-notice-board/admin/data/audit")
        newly should include ("/web/org-goldenport-cncf-test-notice-board/admin/data")
      }
      }
    }

    "render admin CRUD forms from WebDescriptor field controls" in {
      Given("the prerequisites for render admin CRUD forms from WebDescriptor field controls")
      val fixture = _data_fixture()
      When("render admin CRUD forms from WebDescriptor field controls is exercised")
      val descriptor = _data_schema_web_descriptor()
      Then("the observable contract for render admin CRUD forms from WebDescriptor field controls holds")
      _with_global_runtime(fixture.runtime) {
        val edit = _renderer.renderComponentAdminDataEdit(
          fixture.subsystem,
          "notice_board",
          "audit",
          "audit_1",
          webDescriptor = descriptor
        ).map(_.body).getOrElse(fail("component data edit admin is missing"))
        val newly = _renderer.renderComponentAdminDataNew(
          fixture.subsystem,
          "notice_board",
          "audit",
          webDescriptor = descriptor
        ).map(_.body).getOrElse(fail("component data new admin is missing"))

        edit should include ("name=\"action\"")
        edit should include ("<select")
        edit should include ("<option value=\"created\" selected>")
        edit should include ("name=\"actor\"")
        edit should include ("required")
        edit should include ("placeholder=\"Descriptor actor placeholder.\"")
        edit should include ("Descriptor actor help.")
        edit should include ("name=\"note\"")
        edit should include ("<textarea")
        newly should include ("name=\"note\"")
        newly should include ("<textarea")
      }
    }

    "apply component data update/create form POST into the DataStore fixture" in {
      Given("the prerequisites for apply component data update/create form POST into the DataStore fixture")
      val fixture = _data_fixture()
      When("the observable result for apply component data update/create form POST into the DataStore fixture is inspected")
      locally {
        Then("the observable contract for apply component data update/create form POST into the DataStore fixture holds")
        _with_global_runtime(fixture.runtime) {
        val engine = new HttpExecutionEngine(fixture.subsystem)
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val updatereq = _post_form_request(
          "/form/notice-board/admin/data/audit/audit_1/update",
          "action=updated&actor=bob"
        )
        val updatehtml = server
          ._submit_component_admin_data_update(updatereq, "notice-board", "audit", "audit_1")
          .flatMap(_.as[String])
          .unsafeRunSync()

        updatehtml should include ("Data record was applied")
        updatehtml should include ("Applied</th><td>true")
        _load_data_record(fixture.datastorespace, "audit", "audit_1").getString("action") shouldBe Some("updated")
        _load_data_record(fixture.datastorespace, "audit", "audit_1").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/update")

        val createreq = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_2%0Aaction%3Dcreated%0Aactor%3Dbob"
        )
        val createhtml = server
          ._submit_component_admin_data_create(createreq, "notice-board", "audit")
          .flatMap(_.as[String])
          .unsafeRunSync()

        createhtml should include ("Data record was applied")
        createhtml should include ("Applied</th><td>true")
        _load_data_record(fixture.datastorespace, "audit", "audit_2").getString("action") shouldBe Some("created")
        _load_data_record(fixture.datastorespace, "audit", "audit_2").getString("actor") shouldBe Some("bob")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "redirect component data create by admin form descriptor transition" in {
      Given("the prerequisites for redirect component data create by admin form descriptor transition")
      val fixture = _data_fixture()
      When("the observable result for redirect component data create by admin form descriptor transition is inspected")
      locally {
        Then("the observable contract for redirect component data create by admin form descriptor transition holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = WebDescriptor(
          form = Map(
            "notice-board.admin.data.audit.create" -> WebDescriptor.Form(
              successRedirect = Some("/web/${component}/admin/${surface}/${collection}/${result.id}")
            )
          )
        )
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_3%0Aaction%3Dcreated%0Aactor%3Dbob"
        )

        val response = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .unsafeRunSync()

        response.status.code shouldBe 303
        response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
          Some("/web/notice-board/admin/data/audit/audit_3")
        _load_data_record(fixture.datastorespace, "audit", "audit_3").getString("action") shouldBe Some("created")
        dispatcher.paths should contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "redisplay component data create form with submitted fields when admin stayOnError is enabled" in {
      Given("the prerequisites for redisplay component data create form with submitted fields when admin stayOnError is enabled")
      val fixture = _data_fixture()
      When("the observable result for redisplay component data create form with submitted fields when admin stayOnError is enabled is inspected")
      locally {
        Then("the observable contract for redisplay component data create form with submitted fields when admin stayOnError is enabled holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = WebDescriptor(
          form = Map(
            "notice-board.admin.data.audit.create" -> WebDescriptor.Form(stayOnError = true)
          )
        )
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.BadRequest,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("invalid data create", StandardCharsets.UTF_8)
          )
        )
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "fields=id%3Daudit_bad%0Aaction%3Dcreated%0Aactor%3Dbob"
        )

        val html = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .flatMap(_.as[String])
          .unsafeRunSync()

        html should include ("New Audit")
        html should include ("error.status")
        html should include ("400")
        html should include ("invalid data create")
        html should include ("name=\"id\"")
        html should include ("value=\"audit_bad\"")
        html should include ("name=\"action\"")
        html should include ("value=\"created\"")
        html should include ("name=\"actor\"")
        html should include ("value=\"bob\"")
      }
      }
    }

    "redisplay component data create form with descriptor validation errors before dispatch" in {
      Given("the prerequisites for redisplay component data create form with descriptor validation errors before dispatch")
      val fixture = _data_fixture()
      When("the observable result for redisplay component data create form with descriptor validation errors before dispatch is inspected")
      locally {
        Then("the observable contract for redisplay component data create form with descriptor validation errors before dispatch holds")
        _with_global_runtime(fixture.runtime) {
        val descriptor = _data_schema_web_descriptor()
        val engine = new HttpExecutionEngine(fixture.subsystem, Some(descriptor))
        val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
        val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
        val req = _post_form_request(
          "/form/notice-board/admin/data/audit/create",
          "id=audit_invalid&action=created&actor="
        )

        val response = server
          ._submit_component_admin_data_create(req, "notice-board", "audit")
          .unsafeRunSync()
        val html = response.as[String].unsafeRunSync()

        response.status.code shouldBe 400
        html should include ("New Audit")
        html should include ("Validation failed.")
        html should include ("admin-feedback")
        html should include ("actor is required.")
        html should include ("Descriptor actor help.")
        html should include ("is-invalid")
        html should include ("audit_invalid")
        dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/data/create")
      }
      }
    }

    "extract structured result metadata for form redirect templates" in {
      Given("supported structured and scalar result body representations")
      val structuredidbody = """{"id":"notice_1"}"""

      When("template metadata is extracted from the result body")
      val structuredidvalues =
        FormResultMetadata.fromBody(structuredidbody).toTemplateValues

      Then("the supported representations produce canonical template values")
      structuredidvalues shouldBe Map("result.id" -> "notice_1")
      FormResultMetadata.fromBody("""{"id":"urn:textus:image:abc"}""").toTemplateValues shouldBe Map("result.id" -> "urn:textus:image:abc")
      FormResultMetadata.fromBody("""{"result":{"id":"notice_2"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_2")
      FormResultMetadata.fromBody("""{"item":{"id":"notice_3"}}""").toTemplateValues shouldBe Map("result.id" -> "notice_3")
      FormResultMetadata.fromBody("created:notice_4").toTemplateValues shouldBe Map("result.id" -> "notice_4")
      FormResultMetadata.fromBody("created:notice_5\ndebug:\n  calltree: ...").toTemplateValues shouldBe Map("result.id" -> "notice_5")
      FormResultMetadata.fromBody("""{"message":"created"}""").toTemplateValues shouldBe Map("result.message" -> "created")
      FormResultMetadata.fromBody("""{"result":{"outcome":"created","message":"Notice created"}}""").toTemplateValues shouldBe Map(
        "result.outcome" -> "created",
        "result.message" -> "Notice created"
      )
      FormResultMetadata.fromBody(
        """{"data":{"action_status":"seeded","resolver_status":"resolved","added_candidate_count":2,"suggested_field_count":4,"current":{"information_id":"single-global-entity-information-1"}}}"""
      ).toTemplateValues should contain allOf (
        "result.id" -> "single-global-entity-information-1",
        "result.outcome" -> "seeded",
        "result.resolverStatus" -> "resolved",
        "result.candidateCount" -> "2",
        "result.suggestedFieldCount" -> "4"
      )
      FormResultMetadata.fromBody("cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa"
      )
      FormResultMetadata.fromBody("""{"jobId":"cncf-job-job-1"}""").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-1"
      )
      FormResultMetadata.fromBody("""{"jobId":"cncf-job-job-2","jobStatus":"running"}""").toTemplateValues shouldBe Map(
        "result.job.id" -> "cncf-job-job-2",
        "result.job.status" -> "running"
      )
      FormResultMetadata.fromBody(
        """{"actions":[{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      ).toTemplateValues should contain allOf (
        "result.actions.count" -> "1",
        "result.action.primary.name" -> "detail",
        "result.action.primary.label" -> "Open detail",
        "result.action.primary.href" -> "/web/notice-board/admin/entities/notice/notice_1",
        "result.action.primary.method" -> "GET",
        "result.action.detail.href" -> "/web/notice-board/admin/entities/notice/notice_1",
        "result.action.0.href" -> "/web/notice-board/admin/entities/notice/notice_1"
      )
      val jsonaction = parse("""{"name":"approve","label":"Approve","href":"/form/approve","method":"POST"}""")
        .toOption
        .flatMap(FormResultMetadata.Action.fromJson)
        .getOrElse(fail("action JSON should parse"))
      jsonaction.toTemplateValues("result.action.approve") should contain allOf (
        "result.action.approve.name" -> "approve",
        "result.action.approve.label" -> "Approve",
        "result.action.approve.href" -> "/form/approve",
        "result.action.approve.method" -> "POST"
      )
    }

    "project form metadata from explicit execution transport state" must _aes05b {
      Given("responses carrying direct, accepted-job, job-result, or no execution-result headers")
      val directjsonresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"running","message":"direct"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "direct",
        "X-Textus-Job-Id" -> "stale-header-job"
      ))
      val directscalarresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-body-job"
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "direct",
        "X-Textus-Job-Id" -> "stale-header-job"
      ))
      val acceptedresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "accepted-job",
        "X-Textus-Job-Id" -> "cncf-job-authoritative"
      ))
      val jobresultresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job","jobStatus":"completed"}"""
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "job-result",
        "X-Textus-Job-Id" -> "cncf-job-authoritative-result"
      ))
      val acceptedwithoutidresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-without-header","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "AcCePtEd-JoB"
      ))
      val jobresultwithoutidresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-without-header","jobStatus":"completed"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "JoB-ReSuLt"
      ))
      val duplicateheaderresponse = HttpResponse.text(
        HttpStatus.Ok,
        """{"jobId":"body-job-duplicate","jobStatus":"running"}"""
      ).withHeader(Record.data(
        "x-textus-execution-result" -> "accepted-job",
        "X-Textus-Execution-Result" -> "direct",
        "x-textus-job-id" -> "cncf-job-first",
        "X-Textus-Job-Id" -> "cncf-job-second"
      ))
      val legacyresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-legacy"
      )
      val unknownresponse = HttpResponse.text(
        HttpStatus.Ok,
        "cncf-job-unknown"
      ).withHeader(Record.data(
        "X-Textus-Execution-Result" -> "future-result"
      ))

      When("form metadata is extracted from each response")
      val directjsonmetadata = FormResultMetadata.fromHttpResponse(directjsonresponse)
      val directscalarmetadata = FormResultMetadata.fromHttpResponse(directscalarresponse)
      val acceptedmetadata = FormResultMetadata.fromHttpResponse(acceptedresponse)
      val jobresultmetadata = FormResultMetadata.fromHttpResponse(jobresultresponse)
      val acceptedwithoutidmetadata = FormResultMetadata.fromHttpResponse(acceptedwithoutidresponse)
      val jobresultwithoutidmetadata = FormResultMetadata.fromHttpResponse(jobresultwithoutidresponse)
      val duplicateheadermetadata = FormResultMetadata.fromHttpResponse(duplicateheaderresponse)
      val legacymetadata = FormResultMetadata.fromHttpResponse(legacyresponse)
      val unknownmetadata = FormResultMetadata.fromHttpResponse(unknownresponse)

      Then("direct responses suppress body and stale-header Job IDs")
      directjsonmetadata.jobId shouldBe None
      directjsonmetadata.jobStatus shouldBe None
      directscalarmetadata.jobId shouldBe None
      directscalarmetadata.jobStatus shouldBe None

      And("accepted-job responses use the authoritative header Job ID and status")
      acceptedmetadata.jobId shouldBe Some("cncf-job-authoritative")
      acceptedmetadata.jobStatus shouldBe Some("accepted")

      And("job-result responses use the authoritative header Job ID and retain its result status")
      jobresultmetadata.jobId shouldBe Some("cncf-job-authoritative-result")
      jobresultmetadata.jobStatus shouldBe Some("completed")

      And("recognized results use case-insensitive headers and values without body fallback")
      acceptedwithoutidmetadata.jobId shouldBe None
      acceptedwithoutidmetadata.jobStatus shouldBe None
      jobresultwithoutidmetadata.jobId shouldBe None
      jobresultwithoutidmetadata.jobStatus shouldBe Some("completed")

      And("duplicate headers use the first matching field exposed by HttpResponse")
      duplicateheadermetadata.jobId shouldBe Some("cncf-job-first")
      duplicateheadermetadata.jobStatus shouldBe Some("accepted")

      And("responses without execution-result state preserve legacy body inference")
      legacymetadata.jobId shouldBe Some("cncf-job-legacy")

      And("unknown execution-result values preserve legacy body inference")
      unknownmetadata.jobId shouldBe Some("cncf-job-unknown")
    }

    }

    "provide view, aggregate, and operation-action contracts" which {
    "render component view administration page" in {
      Given("the prerequisites for render component view administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component view administration page is exercised")
      val html = _renderer.renderComponentAdminViews(subsystem, component.name).map(_.body).getOrElse(fail("component view admin is missing"))

      Then("the observable contract for render component view administration page holds")
      html should include (s"${component.name} View Administration")
      html should include ("View read")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("view definitions")
    }

    "render component view read page from a live ViewSpace fixture" in {
      Given("the prerequisites for render component view read page from a live ViewSpace fixture")
      val subsystem = _view_fixture_subsystem()

      When("render component view read page from a live ViewSpace fixture is exercised")
      val html = _renderer.renderComponentAdminViewDetail(subsystem, "notice_board", "notice_view").map(_.body).getOrElse(fail("component view detail admin is missing"))

      Then("the observable contract for render component view read page from a live ViewSpace fixture holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice View View")
      html should include ("Notice View metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_view")
      html should include ("notice")
      html should include ("recent")
      html should include ("Read result")
      html should include ("notice summary")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view/notice%20summary")
      html should include ("Result pages")
      html should not include ("Edit")
      html should not include ("New")
      html should not include ("Create")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views")
    }

    "render component view instance detail page through context-aware read" in {
      Given("the prerequisites for render component view instance detail page through context-aware read")
      val subsystem = _view_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component view instance detail page through context-aware read is exercised")
      val html = _renderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", id).map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      Then("the observable contract for render component view instance detail page through context-aware read holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice View View Detail")
      html should include ("class=\"card admin-card")
      html should include (id)
      html should include ("label")
      html should include ("title")
      html should include ("notice detail notice_1")
      html should not include ("Edit")
      html should not include ("Update")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view")
    }

    "reject a foreign canonical instance locator without rendering Admin reads or aggregate operations" in {
      Given("view and aggregate fixtures with a local short ID colliding with a foreign canonical ID")
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = EntityId(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,
        timestamp = Some(java.time.Instant.EPOCH),
        entropy = Some("notice_1")
      ).value
      val viewsubsystem = _view_fixture_subsystem()
      val aggregatesubsystem = _aggregate_fixture_subsystem()

      When("the foreign canonical locator is rendered through view and aggregate detail routes")
      val viewhtml = _renderer.renderComponentAdminViewInstanceDetail(
        viewsubsystem,
        "notice_board",
        "notice_view",
        foreignid
      ).map(_.body).getOrElse(fail("component view instance detail admin is missing"))
      val aggregatehtml = _renderer.renderComponentAdminAggregateInstanceDetail(
        aggregatesubsystem,
        "notice_board",
        "notice_aggregate",
        foreignid
      ).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("no Admin read result or instance operation is rendered for the foreign locator")
      viewhtml should include (s"No canonical Entity ID is available for ${foreignid}.")
      viewhtml should not include ("notice detail notice_1")
      aggregatehtml should include (s"No canonical Entity ID is available for ${foreignid}.")
      aggregatehtml should include (s"No instance operations are available for unresolved id ${foreignid}.")
      aggregatehtml should not include ("aggregate id prefilled")
      aggregatehtml should not include (s"id=${foreignid}")
    }

    "reject foreign canonical IDs at direct Admin view and aggregate reads" in {
      Given("direct Admin surfaces with a local entropy collision in another collection")
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = EntityId(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,
        timestamp = Some(java.time.Instant.EPOCH),
        entropy = Some("notice_1")
      ).value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem())
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem())

      When("the foreign canonical ID is submitted to direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> foreignid)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> foreignid)
      ))

      Then("both boundaries reject it before view or aggregate resolution")
      viewresponse.code shouldBe 400
      aggregateresponse.code shouldBe 400
    }

    "reject direct Admin ID reads whose declared backing collection is absent" in {
      Given("view and aggregate definitions that name a missing backing entity")
      val id = _notice_entity_id_from_shortid("notice_1").value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem(backingentityname = "missing"))
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem(backingentityname = "missing"))

      When("a canonical local ID is submitted to the direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> id)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> id)
      ))

      Then("neither surface infers a same-base collection from its surface name")
      viewresponse.code shouldBe 400
      aggregateresponse.code shouldBe 400
    }

    "reject direct Admin ID reads whose declared backing collection is ambiguous" in {
      Given("view and aggregate definitions whose entity name has two exact runtime collections")
      val id = _notice_entity_id_from_shortid("notice_1").value
      val viewengine = new HttpExecutionEngine(_view_fixture_subsystem(ambiguousbacking = true))
      val aggregateengine = new HttpExecutionEngine(_aggregate_fixture_subsystem(ambiguousbacking = true))

      When("a canonical local ID is submitted to the direct Admin reads")
      val viewresponse = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> id)
      ))
      val aggregateresponse = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> id)
      ))

      Then("both boundaries expose the ambiguous server configuration rather than choosing a collection")
      viewresponse.code shouldBe 500
      aggregateresponse.code shouldBe 500
    }

    "render component view instance detail with descriptor field schema" in {
      Given("the prerequisites for render component view instance detail with descriptor field schema")
      val subsystem = _view_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "view.notice-view" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("value"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component view instance detail with descriptor field schema is exercised")
      val html = _renderer.renderComponentAdminViewInstanceDetail(subsystem, "notice_board", "notice_view", id, descriptor).map(_.body).getOrElse(fail("component view instance detail admin is missing"))

      Then("the observable contract for render component view instance detail with descriptor field schema holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
    }

    "render component aggregate administration page" in {
      Given("the prerequisites for render component aggregate administration page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component aggregate administration page is exercised")
      val html = _renderer.renderComponentAdminAggregates(subsystem, component.name).map(_.body).getOrElse(fail("component aggregate admin is missing"))

      Then("the observable contract for render component aggregate administration page holds")
      html should include (s"${component.name} Aggregate Administration")
      html should include ("Aggregate CRUD")
      html should include ("class=\"card admin-card")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/admin")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
      html should include ("aggregate definitions")
    }

    "render component aggregate read page from a live AggregateSpace fixture" in {
      Given("the prerequisites for render component aggregate read page from a live AggregateSpace fixture")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate read page from a live AggregateSpace fixture is exercised")
      val html = _renderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate").map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      Then("the observable contract for render component aggregate read page from a live AggregateSpace fixture holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Aggregate Aggregate")
      html should include ("Notice Aggregate metadata")
      html should include ("class=\"card admin-card")
      html should include ("class=\"table table-sm table-hover align-middle\"")
      html should include ("notice_aggregate")
      html should include ("notice")
      html should include ("notice:notice")
      html should include ("Read result")
      html should include ("<th>id</th><th>Short ID</th><th>label</th><th>status</th><th>Actions</th>")
      html should include ("notice_1")
      html should include (s"/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/${id}")
      html should include ("Result pages")
      html should include ("Operations")
      html should include ("create-notice-aggregate")
      html should include ("approve-notice-aggregate")
      html should include ("read-notice-aggregate")
      html should include ("Create operations construct a new aggregate root")
      html should include ("Update and command operations mutate aggregate state")
      html should include ("Create aggregate")
      html should include ("Read aggregate")
      html should include ("Run update command")
      html should include ("btn-warning")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/create-notice-aggregate")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate")
      html should not include ("approve-notice-aggregate__success.html")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/aggregates")
    }

    "render component aggregate list with descriptor field columns" in {
      Given("the prerequisites for render component aggregate list with descriptor field columns")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )

      When("render component aggregate list with descriptor field columns is exercised")
      val html = _renderer.renderComponentAdminAggregateDetail(subsystem, "notice_board", "notice_aggregate", webDescriptor = descriptor).map(_.body).getOrElse(fail("component aggregate detail admin is missing"))

      Then("the observable contract for render component aggregate list with descriptor field columns holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>note</th>")
      html should include (s"/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/${id}")
    }

    "render component aggregate instance detail page through context-aware read" in {
      Given("the prerequisites for render component aggregate instance detail page through context-aware read")
      val subsystem = _aggregate_fixture_subsystem()
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate instance detail page through context-aware read is exercised")
      val html = _renderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", id).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("the observable contract for render component aggregate instance detail page through context-aware read holds")
      html should include ("org.goldenport.cncf.test.NoticeBoard Notice Aggregate Aggregate Detail")
      html should include ("class=\"card admin-card")
      html should include (id)
      html should include ("label")
      html should include ("title")
      html should include ("notice aggregate")
      html should include ("Instance operations")
      html should include ("aggregate id prefilled")
      html should include ("Read aggregate")
      html should include ("Run update command")
      html should not include ("Create aggregate")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate?")
      html should include ("/form/org-goldenport-cncf-test-notice-board/notice-aggregate/read-notice-aggregate?")
      html should include (s"id=${id}")
      html should include ("crud.success.href=")
      html should not include ("textus.admin.principalId=system")
      html should include ("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate")
    }

    "render component aggregate instance detail with descriptor field schema" in {
      Given("the prerequisites for render component aggregate instance detail with descriptor field schema")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("value"),
              WebDescriptor.AdminField("note")
            )
          )
        )
      )
      val id = _notice_entity_id_from_shortid("notice_1").value

      When("render component aggregate instance detail with descriptor field schema is exercised")
      val html = _renderer.renderComponentAdminAggregateInstanceDetail(subsystem, "notice_board", "notice_aggregate", id, descriptor).map(_.body).getOrElse(fail("component aggregate instance detail admin is missing"))

      Then("the observable contract for render component aggregate instance detail with descriptor field schema holds")
      html should include ("<th>id</th>")
      html should include ("<th>label</th>")
      html should include ("<th>value</th>")
      html should include ("<th>note</th>")
      html.indexOf("<th>id</th>") should be < html.indexOf("<th>label</th>")
      html.indexOf("<th>label</th>") should be < html.indexOf("<th>value</th>")
      html.indexOf("<th>value</th>") should be < html.indexOf("<th>note</th>")
    }

    "execute admin read/list operations for entity data view and aggregate surfaces" in {
      Given("the prerequisites for execute admin read/list operations for entity data view and aggregate surfaces")
      val entitysubsystem = _management_console_fixture_subsystem()
      val entityengine = new HttpExecutionEngine(entitysubsystem)
      val entitycollection = _notice_fixture_component(entitysubsystem).entitySpace.entity[NoticeEntity]("notice")
      val entityid = entitycollection.storage.storeRealm.values.head.id.value

      val entitylist = entityengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/entity/list", form = Record.data("component" -> _notice_board_component_name, "entity" -> "notice")))
      When("execute admin read/list operations for entity data view and aggregate surfaces is exercised")
      val entityread = entityengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/entity/read", form = Record.data("component" -> _notice_board_component_name, "entity" -> "notice", "id" -> entityid)))

      Then("the observable contract for execute admin read/list operations for entity data view and aggregate surfaces holds")
      entitylist.code shouldBe 200
      entitylist.getString.getOrElse("") should include (entityid)
      entityread.code shouldBe 200
      entityread.getString.getOrElse("") should include ("title=board update")
      val entitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice")
      entitylistrecord.getString("kind") shouldBe Some("entity.list")
      entitylistrecord.getAny("ids").map(_.toString).getOrElse("") should include (entityid)
      entitylistrecord.getAny("items").map(_.toString).getOrElse("") should include (entityid)
      entitylistrecord.getAny("items").map(_.toString).getOrElse("") should include ("board update")
      entitylistrecord.getInt("page") shouldBe Some(1)
      entitylistrecord.getInt("pageSize") shouldBe Some(20)
      entitylistrecord.getBoolean("hasNext") shouldBe Some(false)
      entitylistrecord.getInt("total") shouldBe None
      entitylistrecord.getBoolean("totalAvailable") shouldBe Some(false)
      val ignoredtotalentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true")
      ignoredtotalentitylistrecord.getInt("total") shouldBe None
      ignoredtotalentitylistrecord.getBoolean("totalAvailable") shouldBe Some(false)
      val totalentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalentitylistrecord.getInt("total") shouldBe Some(2)
      totalentitylistrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val requiredentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "includeTotal" -> "true", "totalCountPolicy" -> "required")
      requiredentitylistrecord.getInt("total") shouldBe Some(2)
      val pagedentitylistrecord = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "2", "pageSize" -> "5")
      pagedentitylistrecord.getInt("page") shouldBe Some(2)
      pagedentitylistrecord.getInt("pageSize") shouldBe Some(5)
      val firstentitypage = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "pageSize" -> "1")
      val firstentitypageids = firstentitypage.getAny("ids").map(_.toString).getOrElse("")
      firstentitypageids should not be empty
      firstentitypage.getBoolean("hasNext") shouldBe Some(true)
      val secondentitypage = _admin_record_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "2", "pageSize" -> "1")
      val secondentitypageids = secondentitypage.getAny("ids").map(_.toString).getOrElse("")
      secondentitypageids should not be empty
      secondentitypageids should not be firstentitypageids
      secondentitypage.getBoolean("hasNext") shouldBe Some(false)
      val entityreadrecord = _admin_record_response(entitysubsystem, "entity", "read", "component" -> _notice_board_component_name, "entity" -> "notice", "id" -> entityid)
      entityreadrecord.getString("kind") shouldBe Some("entity.read")
      entityreadrecord.getString("label") shouldBe Some("board update")
      entityreadrecord.getAny("item").map(_.toString).getOrElse("") should include (entityid)
      entityreadrecord.getString("fields").getOrElse("") should include ("title=board update")
      _admin_response(entitysubsystem, "entity", "list", "component" -> _notice_board_component_name, "entity" -> "notice", "page" -> "0") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"invalid page should fail: ${other}")
      }

      val datafixture = _data_fixture()
      _with_global_runtime(datafixture.runtime) {
        val dataengine = new HttpExecutionEngine(datafixture.subsystem)
        val datalist = dataengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/data/list", form = Record.data("component" -> _notice_board_component_name, "data" -> "audit")))
        val dataread = dataengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/data/read", form = Record.data("component" -> _notice_board_component_name, "data" -> "audit", "id" -> "audit_1")))

        datalist.code shouldBe 200
        datalist.getString.getOrElse("") should include ("audit_1")
        dataread.code shouldBe 200
        dataread.getString.getOrElse("") should include ("actor=alice")
        val datalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit")
        datalistrecord.getString("kind") shouldBe Some("data.list")
        datalistrecord.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        datalistrecord.getAny("items").map(_.toString).getOrElse("") should include ("audit_1")
        datalistrecord.getAny("items").map(_.toString).getOrElse("") should include ("created")
        datalistrecord.getInt("total") shouldBe None
        val totaldatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
        totaldatalistrecord.getInt("total") shouldBe Some(2)
        totaldatalistrecord.getBoolean("totalAvailable") shouldBe Some(true)
        val requireddatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required")
        requireddatalistrecord.getInt("total") shouldBe Some(2)
        val unsupporteddatafixture = _data_fixture(TotalCountCapability.Unsupported)
        _with_global_runtime(unsupporteddatafixture.runtime) {
          val optionalunsupported = _admin_record_response(unsupporteddatafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
          optionalunsupported.getInt("total") shouldBe None
          optionalunsupported.getBoolean("totalAvailable") shouldBe Some(false)
          optionalunsupported.getString("totalUnavailableReason") shouldBe Some("unsupported")
          optionalunsupported.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for data.audit")
          _admin_response(unsupporteddatafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
            case Consequence.Failure(_) => succeed
            case other => fail(s"required total on unsupported datastore should fail: ${other}")
          }
        }
        val datareadrecord = _admin_record_response(datafixture.subsystem, "data", "read", "component" -> _notice_board_component_name, "data" -> "audit", "id" -> "audit_1")
        datareadrecord.getString("kind") shouldBe Some("data.read")
        datareadrecord.getString("label") shouldBe Some("audit_1")
        datareadrecord.getAny("item").map(_.toString).getOrElse("") should include ("audit_1")
        datareadrecord.getString("fields").getOrElse("") should include ("actor=alice")
        val pageddatalistrecord = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "page" -> "3", "pageSize" -> "7")
        pageddatalistrecord.getInt("page") shouldBe Some(3)
        pageddatalistrecord.getInt("pageSize") shouldBe Some(7)
        val firstdatapage = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "pageSize" -> "1")
        firstdatapage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_1")
        firstdatapage.getBoolean("hasNext") shouldBe Some(true)
        val seconddatapage = _admin_record_response(datafixture.subsystem, "data", "list", "component" -> _notice_board_component_name, "data" -> "audit", "page" -> "2", "pageSize" -> "1")
        seconddatapage.getAny("ids").map(_.toString).getOrElse("") should include ("audit_existing")
        seconddatapage.getBoolean("hasNext") shouldBe Some(false)
      }

      val viewsubsystem = _view_fixture_subsystem()
      val viewengine = new HttpExecutionEngine(viewsubsystem)
      val viewid = _notice_entity_id_from_shortid("notice_2").value
      val viewread = viewengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/view/read", form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view")))
      val invalidviewread = viewengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/view/read",
        form = Record.data("component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> "legacy-view-id")
      ))

      viewread.code shouldBe 200
      invalidviewread.code shouldBe 400
      viewread.getString.getOrElse("") should include ("notice summary")
      val viewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view")
      viewreadrecord.getString("kind") shouldBe Some("view.read")
      viewreadrecord.getString("fields").getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("values").map(_.toString).getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice summary")
      viewreadrecord.getAny("items").map(_.toString).getOrElse("") should include ("label")
      viewreadrecord.getInt("page") shouldBe Some(1)
      viewreadrecord.getInt("pageSize") shouldBe Some(20)
      viewreadrecord.getBoolean("hasNext") shouldBe Some(false)
      viewreadrecord.getInt("total") shouldBe None
      val totalviewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalviewreadrecord.getInt("total") shouldBe None
      totalviewreadrecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalviewreadrecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalviewreadrecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for view.notice-view")
      _admin_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on view should fail: ${other}")
      }
      val countedviewsubsystem = _view_fixture_subsystem(totalcountcapability = TotalCountCapability.Supported)
      val countedviewreadrecord = _admin_record_response(countedviewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedviewreadrecord.getInt("total") shouldBe Some(2)
      countedviewreadrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedviewreadrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "page" -> "4", "pageSize" -> "9")
      pagedviewreadrecord.getInt("page") shouldBe Some(4)
      pagedviewreadrecord.getInt("pageSize") shouldBe Some(9)
      val firstviewpage = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "pageSize" -> "1")
      firstviewpage.getString("fields") shouldBe Some("notice summary")
      firstviewpage.getBoolean("hasNext") shouldBe Some(true)
      val secondviewpage = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "page" -> "2", "pageSize" -> "1")
      secondviewpage.getString("fields") shouldBe Some("notice next")
      secondviewpage.getBoolean("hasNext") shouldBe Some(false)
      val viewblobid = _register_external_blob(viewsubsystem, "view-image.png", "https://example.test/view-image.png")
      val viewnoblobrecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> viewid)
      viewnoblobrecord.getAny("images") shouldBe Some(Vector.empty)
      viewnoblobrecord.getAny("representativeImage") shouldBe Some(None)
      val viewsourceentityid = viewnoblobrecord.getString("sourceEntityId").getOrElse(fail("view sourceEntityId is missing"))
      _success(viewsubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", viewsourceentityid, None),
        Property("id", viewblobid, None),
        Property("role", "primary", None),
        Property("sortOrder", "1", None)
      )))
      val viewinstancerecord = _admin_record_response(viewsubsystem, "view", "read", "component" -> _notice_board_component_name, "view" -> "notice-view", "id" -> viewid)
      viewinstancerecord.getString("kind") shouldBe Some("view.read")
      viewinstancerecord.getString("id") shouldBe Some(viewid)
      viewinstancerecord.getString("label").getOrElse("") should include ("notice detail notice_2")
      viewinstancerecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_2")
      viewinstancerecord.getString("fields").getOrElse("") should include ("notice detail notice_2")
      viewinstancerecord.getAny("images").map(_.toString).getOrElse("") should include (viewblobid)
      viewinstancerecord.getAny("images").map(_.toString).getOrElse("") should include ("primary")
      viewinstancerecord.getAny("representativeImage").map(_.toString).getOrElse("") should include (viewblobid)
      viewinstancerecord.getAny("representativeImage").map(_.toString).getOrElse("") should include ("primary")
      _blob_records(viewinstancerecord).map(_.getString("id").getOrElse("")) shouldBe Vector(viewblobid)
      _blob_records(viewinstancerecord).foreach { blob =>
        blob.getAny("payload") shouldBe None
      }

      val aggregatesubsystem = _aggregate_fixture_subsystem()
      val aggregateengine = new HttpExecutionEngine(aggregatesubsystem)
      val aggregateid = _notice_entity_id_from_shortid("notice_1").value
      val aggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-image.png", "https://example.test/aggregate-image.png")
      val earlyaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-early.png", "https://example.test/aggregate-early.png")
      val lateaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-late.png", "https://example.test/aggregate-late.png")
      val unorderedaggregateblobid = _register_external_blob(aggregatesubsystem, "aggregate-unordered.png", "https://example.test/aggregate-unordered.png")
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", lateaggregateblobid, None),
        Property("role", "lateImage", None),
        Property("sortOrder", "20", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", aggregateblobid, None),
        Property("role", "heroImage", None),
        Property("sortOrder", "2", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", unorderedaggregateblobid, None),
        Property("role", "unorderedImage", None)
      )))
      _success(aggregatesubsystem.executeOperationResponse(_blob_request(
        "admin_attach_blob_to_entity",
        Property("sourceEntityId", aggregateid, None),
        Property("id", earlyaggregateblobid, None),
        Property("role", "earlyImage", None),
        Property("sortOrder", "1", None)
      )))
      val aggregateread = aggregateengine.execute(HttpRequest.fromPath(HttpRequest.POST, "/org.goldenport.cncf.Admin/aggregate/read", form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate")))
      val invalidaggregateread = aggregateengine.execute(HttpRequest.fromPath(
        HttpRequest.POST,
        "/org.goldenport.cncf.Admin/aggregate/read",
        form = Record.data("component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> "legacy-aggregate-id")
      ))

      aggregateread.code shouldBe 200
      invalidaggregateread.code shouldBe 400
      aggregateread.getString.getOrElse("") should include ("notice aggregate")
      val aggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate")
      aggregatereadrecord.getString("kind") shouldBe Some("aggregate.read")
      aggregatereadrecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("values").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice_1")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("notice aggregate")
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include (aggregateblobid)
      aggregatereadrecord.getAny("items").map(_.toString).getOrElse("") should include ("heroImage")
      aggregatereadrecord.getInt("total") shouldBe None
      val totalaggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      totalaggregatereadrecord.getInt("total") shouldBe None
      totalaggregatereadrecord.getBoolean("totalAvailable") shouldBe Some(false)
      totalaggregatereadrecord.getString("totalUnavailableReason") shouldBe Some("unsupported")
      totalaggregatereadrecord.getAny("warnings").map(_.toString).getOrElse("") should include ("total count is not available for aggregate.notice-aggregate")
      _admin_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "required") match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"required total on aggregate should fail: ${other}")
      }
      val countedaggregatesubsystem = _aggregate_fixture_subsystem(totalcountcapability = TotalCountCapability.Supported)
      val countedaggregatereadrecord = _admin_record_response(countedaggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "includeTotal" -> "true", "totalCountPolicy" -> "optional")
      countedaggregatereadrecord.getInt("total") shouldBe Some(2)
      countedaggregatereadrecord.getBoolean("totalAvailable") shouldBe Some(true)
      val pagedaggregatereadrecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "page" -> "5", "pageSize" -> "11")
      pagedaggregatereadrecord.getInt("page") shouldBe Some(5)
      pagedaggregatereadrecord.getInt("pageSize") shouldBe Some(11)
      val firstaggregatepage = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "pageSize" -> "1")
      firstaggregatepage.getString("fields").getOrElse("") should include ("notice aggregate")
      firstaggregatepage.getBoolean("hasNext") shouldBe Some(true)
      val secondaggregatepage = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "page" -> "2", "pageSize" -> "1")
      secondaggregatepage.getString("fields").getOrElse("") should include ("notice next")
      secondaggregatepage.getBoolean("hasNext") shouldBe Some(false)
      val aggregateinstancerecord = _admin_record_response(aggregatesubsystem, "aggregate", "read", "component" -> _notice_board_component_name, "aggregate" -> "notice-aggregate", "id" -> aggregateid)
      aggregateinstancerecord.getString("kind") shouldBe Some("aggregate.read")
      aggregateinstancerecord.getString("id") shouldBe Some(aggregateid)
      aggregateinstancerecord.getString("label") shouldBe Some("notice aggregate")
      aggregateinstancerecord.getAny("item").map(_.toString).getOrElse("") should include ("notice_1")
      aggregateinstancerecord.getString("fields").getOrElse("") should include ("notice aggregate")
      aggregateinstancerecord.getAny("record").map(_.toString).getOrElse("") should not include ("blobs")
      aggregateinstancerecord.getAny("record").map(_.toString).getOrElse("") should not include ("images")
      aggregateinstancerecord.getAny("images").map(_.toString).getOrElse("") should include (aggregateblobid)
      aggregateinstancerecord.getAny("images").map(_.toString).getOrElse("") should include ("heroImage")
      val aggregateblobs = _blob_records(aggregateinstancerecord)
      aggregateblobs.map(_.getString("id").getOrElse("")) shouldBe Vector(
        earlyaggregateblobid,
        aggregateblobid,
        lateaggregateblobid,
        unorderedaggregateblobid
      )
      aggregateblobs.map(_.getString("role").getOrElse("")) shouldBe Vector(
        "earlyImage",
        "heroImage",
        "lateImage",
        "unorderedImage"
      )
      aggregateblobs.foreach { blob =>
        blob.getAny("payload") shouldBe None
      }
    }

    "submit aggregate create/update actions through the discovered operation form route" in {
      Given("the prerequisites for submit aggregate create/update actions through the discovered operation form route")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))
      val before = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total

      val createhtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      When("submit aggregate create/update actions through the discovered operation form route is exercised")
      val updatehtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for submit aggregate create/update actions through the discovered operation form route holds")
      createhtml should include ("notice-board.notice-aggregate.create-notice-aggregate")
      createhtml should include ("result.status")
      updatehtml should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      updatehtml should include ("result.status")
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total should be > before
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice-aggregate/create-notice-aggregate")
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice-aggregate/approve-notice-aggregate")
    }

    "keep form control and security values out of operation arguments" in {
      Given("the prerequisites for keep form control and security values out of operation arguments")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&approved=true&crud.success.href=/web/notice-board/admin/aggregates/notice-aggregate/notice_1&textus.admin.principalId=system&textus.admin.privilege=application_content_manager"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      When("keep form control and security values out of operation arguments is exercised")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      Then("the observable contract for keep form control and security values out of operation arguments holds")
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("approved") shouldBe Some("true")
      submitted.getString("crud.success.href") shouldBe None
      submitted.getString("textus.admin.principalId") shouldBe None
      submitted.getString("textus.admin.privilege") shouldBe None
    }

    "stage pasted fileContent as managed job input before operation dispatch" in {
      Given("the prerequisites for stage pasted fileContent as managed job input before operation dispatch")
      val subsystem = _aggregate_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&fileName=fixture.csv&fileContent=a%2Cb%0A1%2C2%0A"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      When("stage pasted fileContent as managed job input before operation dispatch is exercised")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      Then("the observable contract for stage pasted fileContent as managed job input before operation dispatch holds")
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("fileContent") shouldBe None
      submitted.getString("cncf.job.input.fieldName") shouldBe Some("fileContent")
      submitted.getString("cncf.job.input.filename") shouldBe Some("fixture.csv")
      submitted.getString("cncf.job.input.storage") shouldBe Some("inline")
      submitted.getString("cncf.job.input.inlineBase64").map(java.util.Base64.getDecoder.decode).map(new String(_, StandardCharsets.UTF_8)) shouldBe Some("a,b\n1,2\n")
    }

    "preserve hidden form context for result templates without dispatching it as operation arguments" in {
      Given("the prerequisites for preserve hidden form context for result templates without dispatching it as operation arguments")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(
          selector -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Context Result</h2>
                |  <p>${crud.origin.href}</p>
                |  <p>${crud.success.href}</p>
                |  <p>${crud.error.href}</p>
                |  <p>${paging.page}</p>
                |  <p>${paging.pageSize}</p>
                |  <p>${paging.chunkSize}</p>
                |  <p>${paging.href}</p>
                |  <p>${search.keyword}</p>
                |  <p>${csrf}</p>
                |  <p>form id: ${form.id}</p>
                |  <p>form page: ${form.paging.page}</p>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("preserve hidden form context for result templates without dispatching it as operation arguments is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&crud.success.href=/web/notice-board/admin/aggregates/notice-aggregate/notice_1&crud.error.href=/form/notice-board/notice-aggregate/approve-notice-aggregate&paging.page=2&paging.pageSize=20&paging.chunkSize=1000&paging.href=%2Fform%2Fnotice-board%2Fnotice-aggregate%2Fapprove-notice-aggregate%2Fcontinue%2Ftest-id%3Fpage%3D%7Bpage%7D%26pageSize%3D%7BpageSize%7D&search.keyword=phase12&csrf=token-1&version=7&etag=abc"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve hidden form context for result templates without dispatching it as operation arguments holds")
      html should include ("Context Result")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      html should include ("/form/notice-board/notice-aggregate/approve-notice-aggregate")
      html should include ("2")
      html should include ("20")
      html should include ("1000")
      html should include ("phase12")
      html should include (_test_csrf_token)
      html should include ("form id: notice_1")
      html should include ("form page: </p>")
      html should not include ("${crud.origin.href}")
      val submitted = dispatcher.forms.lastOption.getOrElse(fail("operation form was not dispatched"))
      submitted.getString("id") shouldBe Some("notice_1")
      submitted.getString("crud.origin.href") shouldBe None
      submitted.getString("crud.success.href") shouldBe None
      submitted.getString("crud.error.href") shouldBe None
      submitted.getString("paging.page") shouldBe None
      submitted.getString("paging.pageSize") shouldBe None
      submitted.getString("paging.chunkSize") shouldBe None
      submitted.getString("paging.href") shouldBe None
      submitted.getString("search.keyword") shouldBe None
      submitted.getString("csrf") shouldBe None
      submitted.getString("version") shouldBe None
      submitted.getString("etag") shouldBe None
    }

    "await asynchronous command job result through the form job route" in {
      Given("the prerequisites for await asynchronous command job result through the form job route")
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("await asynchronous command job result through the form job route is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-board",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for await asynchronous command job result through the form job route holds")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some("cncf-job-job-1")
      html should include ("created:notice_1")
      html should include ("result.id")
      html should include ("notice_1")
    }

    "preserve componentlet alias when awaiting asynchronous command job result through the form job route" in {
      Given("the prerequisites for preserve componentlet alias when awaiting asynchronous command job result through the form job route")
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("preserve componentlet alias when awaiting asynchronous command job result through the form job route is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-admin/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-admin",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve componentlet alias when awaiting asynchronous command job result through the form job route holds")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
      dispatcher.forms.lastOption.flatMap(_.getString("id")) shouldBe Some("cncf-job-job-1")
      html should include ("created:notice_1")
      html should include ("notice_1")
      html should include ("/form/notice-admin/notice/post-notice")
      html should not include ("/form/notice-board/notice/post-notice")
    }

    "render awaited job result through descriptor result template when static template is absent" in {
      Given("the prerequisites for render awaited job result through descriptor result template when static template is absent")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice.post-notice" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Descriptor Await Result</h2>
                |  <p>${result.job.id}</p>
                |  <p>${result.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render awaited job result through descriptor result template when static template is absent is exercised")
      val html = server
        ._await_operation_form_job(
          _post_form_request("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await", ""),
          "notice-board",
          "notice",
          "post-notice",
          "cncf-job-job-1"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render awaited job result through descriptor result template when static template is absent holds")
      html should include ("Descriptor Await Result")
      html should include ("cncf-job-job-1")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
      dispatcher.paths.lastOption shouldBe Some("/org.goldenport.cncf.JobControl/job/await_job_result")
    }

    }

    "provide HTTP operation dispatch and response contracts" which {
    "execute aggregate create/update actions through an HTTP ingress-capable component" in {
      Given("the prerequisites for execute aggregate create/update actions through an HTTP ingress-capable component")
      val subsystem = _aggregate_http_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val createhtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      When("execute aggregate create/update actions through an HTTP ingress-capable component is exercised")
      val updatehtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for execute aggregate create/update actions through an HTTP ingress-capable component holds")
      createhtml should include ("result.status")
      createhtml should include ("200")
      createhtml should include ("aggregate-created:hello")
      createhtml should not include ("HTTP ingress not configured")
      updatehtml should include ("result.status")
      updatehtml should include ("200")
      updatehtml should include ("aggregate-updated:notice_1")
      updatehtml should not include ("HTTP ingress not configured")
    }

    "build REST operation dispatch requests without executing local operation logic" in {
      Given("the prerequisites for build REST operation dispatch requests without executing local operation logic")
      val driver = new RecordingRestDriver
      val dispatcher = WebOperationDispatcher.Rest("http://app.example/base", driver)
      val request = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/notice-board/notice-aggregate/create-notice-aggregate",
        query = Record.data("page" -> "1"),
        header = Record.data("X-Trace-Id" -> "trace-1"),
        form = Record.data("title" -> "hello world")
      )

      When("build REST operation dispatch requests without executing local operation logic is exercised")
      val response = dispatcher.dispatch(request)

      Then("the observable contract for build REST operation dispatch requests without executing local operation logic holds")
      response.code shouldBe 200
      driver.calls should contain (
        RecordingRestDriver.Call(
          method = "POST",
          path = "http://app.example/base/notice-board/notice-aggregate/create-notice-aggregate?page=1",
          body = Some("title=hello+world"),
          headers = Map("X-Trace-Id" -> "trace-1")
        )
      )
    }

    "canonicalize only the first WebPath component segment before recording and remote REST dispatch" in {
      Given("a Web alias request with independent transport fields and a recording remote dispatcher")
      val subsystem = _form_type_fixture_subsystem()
      val driver = new RecordingRestDriver
      val recorder = new RecordingWebOperationDispatcher(
        WebOperationDispatcher.Rest("http://app.example/base", driver)
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem),
        operationDispatcherOption = Some(recorder)
      )
      val context = HttpContext(
        Some("https"),
        Some("web.example"),
        Some("https://web.example/notice-board/notice/post-secret-notice")
      )
      val request = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/notice-board/notice/post-secret-notice",
        query = Record.data("page" -> "1"),
        header = Record.data("X-Trace-Id" -> "trace-1"),
        form = Record.data("title" -> "hello world"),
        body = Some(Bag.text("body payload", StandardCharsets.UTF_8)),
        context = context
      )

      When("the Web operation dispatcher crossing is invoked with the presentation path")
      val response = server._dispatch_operation(
        "notice-board",
        "notice",
        "post-secret-notice",
        request
      )
      val canonical = recorder.requests.lastOption.getOrElse(fail("recorded request is missing"))

      Then("only the first segment becomes the dotted canonical ComponentId and the remote dispatcher receives that path")
      response.code shouldBe 200
      canonical.path.asString shouldBe "/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice"
      canonical.method shouldBe request.method
      canonical.query shouldBe request.query
      canonical.form shouldBe request.form
      canonical.header shouldBe request.header
      canonical.body.map(_.asStringUnsafe()) shouldBe request.body.map(_.asStringUnsafe())
      canonical.context shouldBe request.context
      driver.calls should contain (
        RecordingRestDriver.Call(
          method = "POST",
          path = "http://app.example/base/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice?page=1",
          body = Some("body payload"),
          headers = Map("X-Trace-Id" -> "trace-1")
        )
      )
    }

    "resolve a stable Web alias while retaining canonical Form API identity" in {
      Given("a stable admitted Web alias and a canonical component identity")
      val subsystem = _form_type_fixture_subsystem()
      val componentalias = "notice-board"
      val canonicalcomponentpath = "org-goldenport-cncf-test-notice-board"

      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        componentalias,
        "notice",
        "post_secret_notice"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      When("the Web alias is resolved to its form definition")
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))

      Then("the alias resolves without replacing canonical Component or Form API identity")
      json.hcursor.downField("selector").as[String].toOption shouldBe Some(s"${canonicalcomponentpath}.notice.post-secret-notice")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some(s"/form-api/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some(s"/form/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(0).downField("path").as[String].toOption shouldBe Some(s"/form/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some(s"/form-api/${canonicalcomponentpath}/notice/post-secret-notice")
    }

    "dispatch Form API POST to the canonical REST operation request" in {
      Given("the prerequisites for dispatch Form API POST to the canonical REST operation request")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      When("dispatch Form API POST to the canonical REST operation request is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice?dryRun=true",
            "body=hello&accessToken=abc&crud.origin.href=/web/notice-board"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for dispatch Form API POST to the canonical REST operation request holds")
      response.status.code shouldBe 200
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice")
      dispatcher.forms.last.getString("body") shouldBe Some("hello")
      dispatcher.forms.last.getString("accessToken") shouldBe Some("abc")
      dispatcher.forms.last.getString("crud.origin.href") shouldBe None
    }

    "reject duplicate canonical aliases before Form API operation dispatch" in {
      Given("the prerequisites for reject duplicate canonical aliases before Form API operation dispatch")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice",
            "body=hello&accessToken=abc&access_token=def"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("reject duplicate canonical aliases before Form API operation dispatch is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for reject duplicate canonical aliases before Form API operation dispatch holds")
      response.status.code shouldBe 400
      body should include ("Duplicate property aliases after canonical naming")
      body should include ("accessToken")
      dispatcher.forms shouldBe empty
    }

    "promote x-textus-session from form payload into Form API auth headers" in {
      Given("the prerequisites for promote x-textus-session from form payload into Form API auth headers")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      When("promote x-textus-session from form payload into Form API auth headers is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice",
            "body=hello&x-textus-session=form-session"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for promote x-textus-session from form payload into Form API auth headers holds")
      response.status.code shouldBe 200
      dispatcher.headers.last.getString("x-textus-session") shouldBe Some("form-session")
    }

    "keep internal operations invisible from HTML and Form API surfaces" in {
      Given("the prerequisites for keep internal operations invisible from HTML and Form API surfaces")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Internal))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val htmlform = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "post-secret-notice",
        descriptor
      )
      When("keep internal operations invisible from HTML and Form API surfaces is exercised")
      val apiresponse = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for keep internal operations invisible from HTML and Form API surfaces holds")
      descriptor.isFormEnabled(selector) shouldBe false
      htmlform shouldBe None
      apiresponse.status.code shouldBe 404
    }

    "return structured JSON error envelope from Form API failures" in {
      Given("the prerequisites for return structured JSON error envelope from Form API failures")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("bad request from operation", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice", "body=hello"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      When("return structured JSON error envelope from Form API failures is exercised")
      val json = parse(body).getOrElse(fail(body)).hcursor

      Then("the observable contract for return structured JSON error envelope from Form API failures holds")
      response.status.code shouldBe 400
      response.contentType.map(_.mediaType) shouldBe Some(MediaType.application.json)
      json.downField("error").get[String]("message") shouldBe Right("bad request from operation")
      json.downField("error").get[Int]("status") shouldBe Right(400)
      json.downField("error").get[String]("statusText") shouldBe Right("Bad Request")
      json.downField("error").downField("codeSource").succeeded shouldBe false
      json.downField("error").downField("code").succeeded shouldBe false
      json.downField("error").downField("debug").get[String]("path") shouldBe Right("/form-api/notice-board/notice/post-secret-notice")
    }

    "return structured YAML error envelope from Form API failures when requested" in {
      Given("the prerequisites for return structured YAML error envelope from Form API failures when requested")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("bad request from operation", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )
      val request = _post_form_request(
        "/form-api/notice-board/notice/post-secret-notice",
        "body=hello"
      ).putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Accept"), "application/yaml"))

      val response = server
        ._submit_operation_form_api(
          request,
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("return structured YAML error envelope from Form API failures when requested is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for return structured YAML error envelope from Form API failures when requested holds")
      response.status.code shouldBe 400
      response.contentType.map(_.mediaType.toString).getOrElse("") should include ("application/yaml")
      body should include ("error:")
      body should include ("message: bad request from operation")
      body should include ("status: 400")
      body should include ("statusText: Bad Request")
    }

    "record minimal runtime hooks when Web dispatch crosses the operation adapter" in {
      Given("the prerequisites for record minimal runtime hooks when Web dispatch crosses the operation adapter")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )
      val beforehtml = RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total
      val beforedsl = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total
      val beforeauthorization = RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total

      When("record minimal runtime hooks when Web dispatch crosses the operation adapter is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice", "body=hello"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for record minimal runtime hooks when Web dispatch crosses the operation adapter holds")
      response.status.code shouldBe 200
      RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total shouldBe (beforehtml + 1)
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total shouldBe (beforedsl + 1)
      RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total shouldBe (beforeauthorization + 1)
    }

    "render resolved Web Descriptor summary on component admin page" in {
      Given("the prerequisites for render resolved Web Descriptor summary on component admin page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        expose = Map(s"${componentpath}.service.operation" -> WebDescriptor.Exposure.Protected),
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentpath}/dashboard", "dashboard"))
      )

      When("render resolved Web Descriptor summary on component admin page is exercised")
      val html = _renderer.renderComponentAdmin(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("component admin is missing"))

      Then("the observable contract for render resolved Web Descriptor summary on component admin page holds")
      html should include ("Web Descriptor")
      html should include ("configured")
      html should include (s"${componentpath}.service.operation")
      html should include ("protected")
      html should include ("component-dashboard")
    }

    "render system performance detail page" in {
      Given("the prerequisites for render system performance detail page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/system/dashboard", 200, 12L)
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/missing", 404, 34L)
      RuntimeDashboardMetrics.recordAuthorizationDecision(denied = true, Some("capability"))

      When("render system performance detail page is exercised")
      val html = _renderer.renderSystemPerformance(subsystem).body

      Then("the observable contract for render system performance detail page holds")
      html should include ("System Performance")
      html should include ("/web/assets/bootstrap.min.css")
      html should not include ("cdn.jsdelivr")
      html should include ("nav nav-pills")
      html should include ("card admin-card")
      html should include ("row g-3")
      html should include ("table-responsive")
      html should include ("admin-action-row")
      html should include ("id=\"html-requests\"")
      html should include ("id=\"recent-errors\"")
      html should include ("id=\"authorization\"")
      html should include ("id=\"jobs\"")
      html should include ("HTML request")
      html should include ("Latency")
      html should include ("Recent requests")
      html should include ("Recent errors")
      html should include ("HTTP 4xx/5xx entries shown as Dashboard recent failures")
      html should include ("ActionCall")
      html should include ("DSL Chokepoints")
      html should include ("Authorization")
      html should include ("Diagnostic")
      html should include ("capability")
      html should include ("Jobs")
      html should include ("Assembly warnings")
      html should include ("/web/system/admin/assembly/warnings")
      html should include ("/web/system/admin/assembly/report")
      html should include ("/form/admin/execution/history")
      html should include ("/form/admin/execution/calltree")
      html should include ("/web/system/dashboard")
      html should include ("/missing")
      html should include ("34 ms")
      html should include ("/web/system/dashboard")
      html should include ("/web/system/admin")
      html should include ("/web/system/admin/observability")
      html should include ("/web/system/admin/observability/metrics")
      html should include ("/web/system/admin/observability/diagnostics/authorization/capability")
      html should include ("/man/system")
      html should include ("/web/console")
    }

    "render structured observability metrics page" in {
      Given("the prerequisites for render structured observability metrics page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/test", 200, 15L)
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/missing", 404, 25L)
      RuntimeDashboardMetrics.recordActionCall(error = false, Some(7L))
      RuntimeDashboardMetrics.recordBlobOperation(
        operation = "blob.content.get",
        error = true,
        diagnosticKey = Some("ob05_blob"),
        kind = Some("content"),
        sourceMode = Some("managed"),
        backend = Some("local-file")
      )
      RuntimeDashboardMetrics.recordDiagnosticPayloadExternalization("result", "stored", "local-file")
      subsystem.entityAccessMetrics.record(
        "entity.search",
        Record.dataAuto(
          "entity" -> "notice",
          "source" -> "datastore",
          "outcome" -> "success"
        )
      )

      val html = _renderer.renderSystemAdminObservabilityMetrics(subsystem).body
      val home = _renderer.renderSystemAdminObservability(subsystem).body
      When("render structured observability metrics page is exercised")
      val performance = _renderer.renderSystemPerformance(subsystem).body

      Then("the observable contract for render structured observability metrics page holds")
      html should include ("Observability Metrics")
      html should include ("web.request")
      html should include ("action.execution")
      html should include ("blob.operation")
      html should include ("diagnostic-payload.externalization")
      html should include ("entity-access")
      html should include ("payload_kind=result")
      html should include ("destination=local-file")
      html should include ("/web/system/admin/observability/diagnostics/blob/ob05_blob")
      html should include ("Raw metrics snapshot")
      home should include ("/web/system/admin/observability/metrics")
      performance should include ("/web/system/admin/observability/metrics")
    }

    "render structured observability drill-down pages" in {
      Given("the prerequisites for render structured observability drill-down pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val previous = Record.dataAuto(
        "diagnosticKey" -> "storage_missing",
        "taxonomy" -> "resource.not-found",
        "taxonomyCategory" -> "resource",
        "taxonomySymptom" -> "not-found",
        "webStatus" -> 404,
        "statusText" -> "Not Found",
        "detailcode" -> 1060401L
      )
      val diagnostic = Record.dataAuto(
        "diagnosticKey" -> "ob04_payload_missing",
        "taxonomy" -> "state.invalid",
        "taxonomyCategory" -> "state",
        "taxonomySymptom" -> "invalid",
        "causeKind" -> "inconsistency",
        "interpretation" -> "system-failure",
        "userAction" -> "escalation",
        "responsibility" -> "system-admin",
        "webStatus" -> 500,
        "statusText" -> "Internal Server Error",
        "detailcode" -> 1080501L,
        "appCode" -> 9001L,
        "appStatus" -> "blob.payload.missing",
        "previous" -> Vector(previous),
        "result" -> Record.dataAuto(
          "kind" -> "record",
          "inline" -> false,
          "size_bytes" -> 4096,
          "payload_href" -> "/web/system/admin/observability/payloads/payload-1.json"
        )
      )
      RuntimeDashboardMetrics.recordBlobOperation(
        operation = "blob.content.get",
        error = true,
        diagnosticKey = Some("ob04_payload_missing"),
        diagnosticRecord = Some(diagnostic),
        kind = Some("content"),
        sourceMode = Some("managed"),
        backend = Some("local-file")
      )

      val performance = _renderer.renderSystemPerformance(subsystem).body
      val home = _renderer.renderSystemAdminObservability(subsystem).body
      val diagnostics = _renderer.renderSystemAdminObservabilityDiagnostics().body
      When("render structured observability drill-down pages is exercised")
      val detail = _renderer
        .renderSystemAdminObservabilityDiagnostic("blob", "ob04_payload_missing")
        .map(_.body)
        .getOrElse(fail("diagnostic detail is missing"))

      Then("the observable contract for render structured observability drill-down pages holds")
      performance should include ("/web/system/admin/observability/diagnostics/blob/ob04_payload_missing")
      home should include ("System Observability")
      home should include ("Diagnostic Payload Externalization")
      diagnostics should include ("Observability Diagnostics")
      diagnostics should include ("ob04_payload_missing")
      detail should include ("Structured fields")
      detail should include ("state.invalid")
      detail should include ("Internal Server Error")
      detail should include ("1080501")
      detail should include ("blob.payload.missing")
      detail should include ("Source-error trace")
      detail should include ("storage_missing")
      detail should include ("/web/system/admin/observability/payloads/payload-1.json")
      detail should include ("Raw diagnostic record")
      _renderer.renderSystemAdminObservabilityDiagnostic("missing", "ob04_payload_missing") shouldBe None
      _renderer.renderSystemAdminObservabilityDiagnostic("blob", "missing") shouldBe None
    }

    "render document and console entry pages without inline operation execution" in {
      Given("the prerequisites for render document and console entry pages without inline operation execution")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      val manual = _renderer.render(subsystem, "document").map(_.body).getOrElse(fail("documents page is missing"))
      When("render document and console entry pages without inline operation execution is exercised")
      val console = _renderer.render(subsystem, "console").map(_.body).getOrElse(fail("console is missing"))

      Then("the observable contract for render document and console entry pages without inline operation execution holds")
      manual should include ("System Documents")
      manual should include ("/web/system/dashboard")
      manual should include ("/web/console")
      manual should include ("Generated Help")
      manual should include ("Component documents")
      console should include ("System Console")
      console should include ("/web/system/dashboard")
      console should include ("/man/system")
      console should include ("/form/")
      console should include ("Console links to operation forms")
      console should include ("does not execute operations inline")
      console should not include ("<form method=\"post\"")
    }

    "keep system documents and console available while filtering component app entries by WebDescriptor apps" in {
      Given("the prerequisites for keep system documents and console available while filtering component app entries by WebDescriptor apps")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("document", "/web/document", "document"))
      )

      val manual = _renderer.render(subsystem, "document", webDescriptor = descriptor)
      val console = _renderer.render(subsystem, "console", webDescriptor = descriptor)
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      When("keep system documents and console available while filtering component app entries by WebDescriptor apps is exercised")
      val componentforms = _renderer.render(subsystem, componentpath, webDescriptor = descriptor)

      Then("the observable contract for keep system documents and console available while filtering component app entries by WebDescriptor apps holds")
      manual.map(_.body).getOrElse(fail("documents page is missing")) should include ("System Documents")
      console.map(_.body).getOrElse(fail("console is missing")) should include ("System Console")
      componentforms shouldBe None
    }

    "allow component dashboard app entries by descriptor path" in {
      Given("the prerequisites for allow component dashboard app entries by descriptor path")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentpath}/dashboard", "dashboard"))
      )

      When("allow component dashboard app entries by descriptor path is exercised")
      val page = _renderer.render(subsystem, componentpath, Vector("dashboard"), descriptor)

      Then("the observable contract for allow component dashboard app entries by descriptor path holds")
      page.map(_.body).getOrElse(fail("dashboard is missing")) should include (s"${component.name} Dashboard")
    }

    }

    "provide HTML operation form and typed-update contracts" which {
    "render component HTML form operation index" in {
      Given("the prerequisites for render component HTML form operation index")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))

      When("render component HTML form operation index is exercised")
      val html = _renderer.renderFormIndex(subsystem, component.name).map(_.body).getOrElse(fail("form index is missing"))

      Then("the observable contract for render component HTML form operation index holds")
      html should include (s"${component.name} Forms")
      html should include ("/web/assets/bootstrap.min.css")
      html should include ("card admin-card")
      html should include ("list-group")
      html should include ("row g-3")
      html should include ("nav nav-pills")
      html should include (s"/web/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}/dashboard")
      html should include (s"/form/${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)}")
    }

    "render component HTML operation form" in {
      Given("the prerequisites for render component HTML operation form")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)

      When("render component HTML operation form is exercised")
      val html = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render component HTML operation form holds")
      html should include ("<form method=\"post\"")
      html should include ("data-textus-page=\"static-form-operation\"")
      html should include ("data-textus-section=\"operation-form\"")
      html should include ("data-textus-section=\"form-errors\"")
      html should include ("data-textus-section=\"form-controls\"")
      html should include ("data-textus-section=\"form-actions\"")
      html should include ("data-textus-ux-profile=\"bootstrap\"")
      html should include (s"""data-textus-form="${componentpath}.${servicepath}.${operationpath}"""")
      html should include ("data-textus-field=\"fields\"")
      html should include ("data-textus-action=\"submit\"")
      html should include ("data-textus-action=\"operations\"")
      html should include ("card admin-card")
      html should include ("row g-3")
      html should include ("admin-action-row")
      html should include ("name=\"fields\"")
      html should include ("class=\"form-control\"")
      html should include (s"/form/${componentpath}/${servicepath}/${operationpath}")
      html should not include ("cdn.jsdelivr")
    }

    "render operation form UX profile metadata" in {
      Given("the prerequisites for render operation form UX profile metadata")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val selector = WebDescriptor.formSelector(component.name, service.name, operation.name)
      val globaldescriptor = WebDescriptor(
        profile = Some(WebUxProfile.Compact),
        profileRaw = Some("compact"),
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val appdescriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        apps = Vector(WebDescriptor.App(componentpath, profile = Some(WebUxProfile.Material), profileRaw = Some("material")))
      )
      val formdescriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(profile = Some(WebUxProfile.Admin), profileRaw = Some("admin")))
      )

      val globalhtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = globaldescriptor).map(_.body).getOrElse(fail("operation form is missing"))
      val apphtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = appdescriptor).map(_.body).getOrElse(fail("operation form is missing"))
      When("render operation form UX profile metadata is exercised")
      val formhtml = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, webdescriptor = formdescriptor).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render operation form UX profile metadata holds")
      globalhtml should include ("data-textus-ux-profile=\"compact\"")
      apphtml should include ("data-textus-ux-profile=\"material\"")
      formhtml should include ("data-textus-ux-profile=\"admin\"")
    }

    "append development debug panel to operation form error redisplay" in {
      Given("the prerequisites for append development debug panel to operation form error redisplay")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))

      When("append development debug panel to operation form error redisplay is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        component.name,
        service.name,
        operation.name,
        values = Map(
          "error.status" -> "500",
          "error.body" -> "openlibrary.org",
          "error.diagnostic.trace.id" -> "test-runtime-trace-1",
          "error.diagnostic.id" -> "test-runtime-correlation-1",
          "error.diagnostic.failure" -> "openlibrary.org",
          "error.diagnostic.providers" -> "provider:openbd.book.isbn.lookup,provider:openlibrary.book.isbn.lookup",
          "error.diagnostic.calltree.json" -> RecordEncoder.json(Record.data(
            "calltree" -> Vector(
              Record.data(
                "label" -> "action:TextusKnowledgeEditor.BookEditor.seedBook",
                "kind" -> "action",
                "flow" -> Vector(
                  Record.data(
                    "label" -> "provider:openlibrary.book.isbn.lookup",
                    "kind" -> "provider"
                  )
                )
              )
            )
          )),
          "error.diagnostic.calltree.href" -> "/rest/v1/admin/execution/calltree",
          "error.diagnostic.history.href" -> "/rest/v1/admin/execution/history",
          "textus.debug.executionPanel" -> "true"
        ),
        operationMode = OperationMode.Develop,
        showExecutionDebugPanel = true
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for append development debug panel to operation form error redisplay holds")
      html should include ("Form submission failed.")
      html should include ("textus-execution-debug-panel")
      html should not include ("error.diagnostic.trace.id")
      html should not include ("error.diagnostic.providers")
      html should include ("Execution path")
      html should include ("test-runtime-trace-1")
      html should include ("test-runtime-correlation-1")
      html should include ("provider:openlibrary.book.isbn.lookup")
      html should include ("data-textus-calltree")
      html should include ("action:TextusKnowledgeEditor.BookEditor.seedBook")
      html should include ("openlibrary.org")
    }

    "ignore external debug panel flags on operation form input pages" in {
      Given("the prerequisites for ignore external debug panel flags on operation form input pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))

      When("ignore external debug panel flags on operation form input pages is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        component.name,
        service.name,
        operation.name,
        values = Map("textus.debug.executionPanel" -> "true"),
        operationMode = OperationMode.Develop
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for ignore external debug panel flags on operation form input pages holds")
      html should not include ("textus-execution-debug-panel")
    }

    "apply app-scoped assets to the component HTML form index" in {
      Given("the prerequisites for apply app-scoped assets to the component HTML form index")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentpath,
          assets = WebDescriptor.Assets(
            css = Vector("/web/component/assets/forms.css"),
            js = Vector("/web/component/assets/forms.js")
          )
        )),
        form = Map(
          s"${componentpath}.service.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/operation.css"),
              js = Vector("/web/component/assets/operation.js")
            )
          )
        )
      )

      When("apply app-scoped assets to the component HTML form index is exercised")
      val html = _renderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))

      Then("the observable contract for apply app-scoped assets to the component HTML form index holds")
      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should not include ("/web/component/assets/operation.css")
      html should not include ("/web/component/assets/operation.js")
    }

    "apply app and form scoped assets to operation input forms" in {
      Given("the prerequisites for apply app and form scoped assets to operation input forms")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App(
          componentpath,
          assets = WebDescriptor.Assets(
            css = Vector("/web/component/assets/forms.css"),
            js = Vector("/web/component/assets/forms.js")
          )
        )),
        form = Map(
          selector -> WebDescriptor.Form(
            enabled = Some(true),
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/operation.css"),
              js = Vector("/web/component/assets/operation.js")
            )
          ),
          s"${componentpath}.other.operation" -> WebDescriptor.Form(
            assets = WebDescriptor.Assets(
              css = Vector("/web/component/assets/other.css"),
              js = Vector("/web/component/assets/other.js")
            )
          )
        )
      )

      When("apply app and form scoped assets to operation input forms is exercised")
      val html = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for apply app and form scoped assets to operation input forms holds")
      html should include ("/web/component/assets/forms.css")
      html should include ("/web/component/assets/forms.js")
      html should include ("/web/component/assets/operation.css")
      html should include ("/web/component/assets/operation.js")
      html should not include ("/web/component/assets/other.css")
      html should not include ("/web/component/assets/other.js")
    }

    "filter HTML form operations by WebDescriptor form controls" in {
      Given("the prerequisites for filter HTML form operations by WebDescriptor form controls")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val path = s"/form/${componentpath}/${servicepath}/${operationpath}"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Public),
        form = Map(selector -> WebDescriptor.Form(enabled = Some(false)))
      )

      val index = _renderer.renderFormIndex(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("form index is missing"))
      When("filter HTML form operations by WebDescriptor form controls is exercised")
      val form = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      Then("the observable contract for filter HTML form operations by WebDescriptor form controls holds")
      index should not include (path)
      form shouldBe None
    }

    "allow exposed HTML form operations when no explicit form control exists" in {
      Given("the prerequisites for allow exposed HTML form operations when no explicit form control exists")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val service = component.protocol.services.services.headOption.getOrElse(fail("service is missing"))
      val operation = service.operations.operations.toVector.headOption.getOrElse(fail("operation is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val servicepath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service.name)
      val operationpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation.name)
      val selector = Vector(componentpath, servicepath, operationpath).mkString(".")
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )

      When("allow exposed HTML form operations when no explicit form control exists is exercised")
      val form = _renderer.renderOperationForm(subsystem, component.name, service.name, operation.name, descriptor)

      Then("the observable contract for allow exposed HTML form operations when no explicit form control exists holds")
      form.map(_.body).getOrElse(fail("operation form is missing")) should include (s"/form/${componentpath}/${servicepath}/${operationpath}")
    }

    "render HTML operation form with query-provided initial fields" in {
      Given("the prerequisites for render HTML operation form with query-provided initial fields")
      val subsystem = _aggregate_fixture_subsystem()

      When("render HTML operation form with query-provided initial fields is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice_aggregate",
        "approve_notice_aggregate",
        values = Map(
          "id" -> "notice_1",
          "crud.origin.href" -> "/web/notice-board/admin/aggregates/notice-aggregate?page=2",
          "paging.page" -> "2",
          "search.keyword" -> "notice"
        )
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render HTML operation form with query-provided initial fields holds")
      html should include ("name=\"id\"")
      html should include ("type=\"text\"")
      html should include ("required")
      html should include ("value=\"notice_1\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board/admin/aggregates/notice-aggregate?page=2\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include ("type=\"hidden\" name=\"search.keyword\" value=\"notice\"")
      html should include ("Additional fields")
      html should not include ("crud.origin.href=")
      html should not include ("search.keyword=")
    }

    "render descriptor-defined select and hidden controls for operation parameters" in {
      Given("the prerequisites for render descriptor-defined select and hidden controls for operation parameters")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "accessToken" -> WebDescriptor.FormControl(hidden = true),
            "body" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("hello", "world"),
              placeholder = Some("Descriptor body placeholder."),
              help = Some("Descriptor body help.")
            )
          )
        ))
      )

      When("render descriptor-defined select and hidden controls for operation parameters is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice",
        "post_secret_notice",
        descriptor,
        values = Map("body" -> "hello", "accessToken" -> "abc")
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for render descriptor-defined select and hidden controls for operation parameters holds")
      html should include ("<select")
      html should include ("<option value=\"hello\" selected>")
      html should include ("Notice body")
      html should include ("Descriptor body help.")
      html should include ("name=\"body\"")
      html should include ("type=\"hidden\"")
      html should include ("name=\"accessToken\"")
    }

    "serve operation form definition API from the same resolved Web schema" in {
      Given("the prerequisites for serve operation form definition API from the same resolved Web schema")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "accessToken" -> WebDescriptor.FormControl(hidden = true),
            "body" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("hello", "world"),
              placeholder = Some("Descriptor body placeholder."),
              help = Some("Descriptor body help.")
            )
          )
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("form definition JSON is invalid"))
      When("serve operation form definition API from the same resolved Web schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve operation form definition API from the same resolved Web schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.notice.post-secret-notice")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("operation")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("POST")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form-api/org-goldenport-cncf-test-notice-board/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(2).downField("path").as[String].toOption shouldBe Some("/form-api/org-goldenport-cncf-test-notice-board/notice/post-secret-notice/validate")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("body")
      fields.downN(0).downField("label").as[String].toOption shouldBe Some("Notice body")
      fields.downN(0).downField("type").as[String].toOption shouldBe Some("select")
      fields.downN(0).downField("required").as[Boolean].toOption shouldBe Some(true)
      fields.downN(0).downField("values").as[Vector[String]].toOption shouldBe Some(Vector("hello", "world"))
      fields.downN(0).downField("placeholder").as[String].toOption shouldBe Some("Descriptor body placeholder.")
      fields.downN(0).downField("help").as[String].toOption shouldBe Some("Descriptor body help.")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("accessToken")
      fields.downN(1).downField("hidden").as[Boolean].toOption shouldBe Some(true)
    }

    "typed update carriers" which {
      "project typed update commands into form definitions and generated controls" in {
      Given("the prerequisites for project typed update commands into form definitions and generated controls")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "update-notice",
            kind = "COMMAND",
            inputType = "Notice",
            outputType = "Unit",
            inputValueKind = "ENTITY_UPDATE",
            parameters = Vector(
              CmlOperationField("tags", "string", "*", update = Some(CmlOperationUpdateField("*", nullAllowed = false))),
              CmlOperationField("nickname", "string", "?", update = Some(CmlOperationUpdateField("?", nullAllowed = true)))
            )
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = "notice",
              operations = spec.OperationDefinitionGroup(
                operations = NonEmptyVector.of(NoopOperation("update-notice"))
              )
            )
          )
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val response = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/update-notice"),
          "notice-board",
          "notice",
          "update-notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields")
      When("project typed update commands into form definitions and generated controls is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "update-notice"
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for project typed update commands into form definitions and generated controls holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("tags")
      fields.downN(0).downField("updateCommands").as[Vector[String]].toOption shouldBe Some(Vector("clear"))
      fields.downN(0).downField("updateValueCarriers").as[Vector[String]].toOption shouldBe Some(Vector("value", "value_or_clear"))
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("nickname")
      fields.downN(1).downField("updateCommands").as[Vector[String]].toOption shouldBe Some(Vector("null"))
      fields.downN(1).downField("updateValueCarriers").as[Vector[String]].toOption shouldBe Some(Vector("value", "value_or_null"))
      html should include ("name=\"tags__update_command\" value=\"clear\"")
      html should include ("name=\"nickname__update_command\" value=\"null\"")
      html should not include "name=\"tags__update_command\" value=\"null\""
      html should not include "name=\"nickname__update_command\" value=\"clear\""
      }

      "normalize URL-encoded and multipart typed update commands before Web dispatch" in {
      Given("an entity update operation exposed through Form API")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "update-notice",
            kind = "COMMAND",
            inputType = "Notice",
            outputType = "Unit",
            inputValueKind = "ENTITY_UPDATE",
            parameters = Vector(
              CmlOperationField("tags", "string", "*", update = Some(CmlOperationUpdateField("*", nullAllowed = false))),
              CmlOperationField("nickname", "string", "?", update = Some(CmlOperationUpdateField("?", nullAllowed = true)))
            )
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
          name = "notice",
          operations = spec.OperationDefinitionGroup(NonEmptyVector.of(NoopOperation("update-notice")))
        )))
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val dispatcher = new RecordingWebOperationDispatcher(new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("updated", StandardCharsets.UTF_8)
        )
      ))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem), operationDispatcherOption = Some(dispatcher))

      When("URL-encoded and multipart forms submit adaptive clear and an explicit empty value")
      val urlencoded = server.routes(null).orNotFound.run(
        _post_form_request(
          "/form-api/notice-board/notice/update-notice",
          "tags=&tags__value_or_clear=&nickname__value="
        )
      ).unsafeRunSync()
      val multipart = server.routes(null).orNotFound.run(
        _post_multipart_request(
          "/form-api/notice-board/notice/update-notice",
          Vector("tags" -> "", "tags__value_or_clear" -> "", "nickname__value" -> ""),
          Vector.empty
        )
      ).unsafeRunSync()

      Then("both transport adapters omit the ordinary blank and preserve explicit carriers")
      urlencoded.status.code shouldBe 200
      multipart.status.code shouldBe 200
      dispatcher.forms should have size 2
      dispatcher.forms.foreach { form =>
        form.getAny("tags") shouldBe None
        form.getString("tags__value_or_clear") shouldBe Some("")
        form.getString("nickname__value") shouldBe Some("")
      }
      }

      "deliver explicit empty, adaptive clear, and adaptive null values to ActionCall" in {
        Given("executable entity update operations using all accepted typed value carriers")
        val component = new org.goldenport.cncf.component.Component() {
          override def operationDefinitions: Vector[CmlOperationDefinition] =
            Vector(
              CmlOperationDefinition(
                name = "update-nickname",
                kind = "COMMAND",
                inputType = "Notice",
                outputType = "Unit",
                inputValueKind = "ENTITY_UPDATE",
                parameters = Vector(
                  CmlOperationField("nickname", "string", "?", update = Some(CmlOperationUpdateField("?", nullAllowed = true)))
                )
              ),
              CmlOperationDefinition(
                name = "clear-tags",
                kind = "COMMAND",
                inputType = "Notice",
                outputType = "Unit",
                inputValueKind = "ENTITY_UPDATE",
                parameters = Vector(
                  CmlOperationField("tags", "string", "*", update = Some(CmlOperationUpdateField("*", nullAllowed = false)))
                )
              )
            )
        }
        val protocol = Protocol(
          services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
              InspectingAggregateOperation("update-nickname", "nickname", "nickname"),
              InspectingAggregateOperation("clear-tags", "tags", "tags")
            ))
          ))),
          handler = ProtocolHandler(
            ingresses = IngressCollection(Vector(RestIngress())),
            egresses = EgressCollection(Vector(RestEgress())),
            projections = ProjectionCollection()
          )
        )
        _initialize_component("notice_board", component, protocol)
        val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

        When("Form API submits explicit empty, adaptive clear, and adaptive null carriers")
        val explicit = server.routes(null).orNotFound.run(
          _post_form_request(
            "/form-api/notice-board/notice/update-nickname",
            "nickname__value="
          )
        ).unsafeRunSync()
        val clear = server.routes(null).orNotFound.run(
          _post_form_request(
            "/form-api/notice-board/notice/clear-tags",
            "tags__value_or_clear="
          )
        ).unsafeRunSync()
        val nullvalue = server.routes(null).orNotFound.run(
          _post_form_request(
            "/form-api/notice-board/notice/update-nickname",
            "nickname__value_or_null="
          )
        ).unsafeRunSync()
        val explicitbody = explicit.as[String].unsafeRunSync()
        val clearbody = clear.as[String].unsafeRunSync()
        val nullbody = nullvalue.as[String].unsafeRunSync()

        Then("ComponentLogic binds each carrier to a present ActionCall argument with typed semantics")
        withClue(explicitbody) { explicit.status.code shouldBe 200 }
        withClue(clearbody) { clear.status.code shouldBe 200 }
        withClue(nullbody) { nullvalue.status.code shouldBe 200 }
        explicitbody should include ("nickname:present:")
        clearbody should include ("tags:present:Vector()")
        nullbody should include ("nickname:present:SetNull")
      }

      "return structured HTTP 400 for incompatible typed update commands" in {
      Given("an executable entity update operation using the shared request boundary")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "update-notice",
            kind = "COMMAND",
            inputType = "Notice",
            outputType = "Unit",
            inputValueKind = "ENTITY_UPDATE",
            parameters = Vector(
              CmlOperationField("nickname", "string", "?", update = Some(CmlOperationUpdateField("?", nullAllowed = true)))
            )
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
          name = "notice",
          operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
            SuccessfulAggregateOperation("update-notice", "nickname", "updated")
          ))
        ))),
        handler = ProtocolHandler(
          ingresses = IngressCollection(Vector(RestIngress())),
          egresses = EgressCollection(Vector(RestEgress())),
          projections = ProjectionCollection()
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val beforeactioncalls = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
      val beforeauthorization = RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total
      val beforevalidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot.summary.cumulative.total

      When("the form submits collection clear for a nullable scalar")
      val rejected = server.routes(null).orNotFound.run(
        _post_form_request(
          "/form-api/notice-board/notice/update-notice",
          "nickname__update_command=clear"
        )
      ).unsafeRunSync()
      val rejectedjson = parse(rejected.as[String].unsafeRunSync()).getOrElse(fail("error JSON is invalid")).hcursor
      val afterformactioncalls = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
      val afterformauthorization = RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total
      val afterformvalidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot.summary.cumulative.total

      And("the canonical REST route submits the same null command grammar")
      val restaccepted = server.routes(null).orNotFound.run(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/rest/v1/notice-board/notice/update-notice")
        ).withEntity("{\"nickname__update_command\":\"null\"}")
          .withContentType(org.http4s.headers.`Content-Type`.parse("application/json").toOption.get)
      ).unsafeRunSync()
      val restacceptedbody = restaccepted.as[String].unsafeRunSync()
      val afteracceptedactioncalls = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
      val restrejected = server.routes(null).orNotFound.run(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/rest/v1/notice-board/notice/update-notice")
        ).withEntity("{\"nickname__update_command\":\"clear\"}")
          .withContentType(org.http4s.headers.`Content-Type`.parse("application/json").toOption.get)
      ).unsafeRunSync()
      val afterrejectedactioncalls = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
      val afterrejectedvalidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot.summary.cumulative.total

      Then("the normal operation boundary returns a structured client error")
      rejected.status.code shouldBe 400
      rejectedjson.downField("error").get[Int]("status") shouldBe Right(400)
      restaccepted.status.code shouldBe 200
      restacceptedbody should include ("updated:SetNull")
      restrejected.status.code shouldBe 400
      afterformactioncalls shouldBe beforeactioncalls
      afterformauthorization should be > beforeauthorization
      afterformvalidation should be > beforevalidation
      afteracceptedactioncalls should be > afterformactioncalls
      afterrejectedactioncalls shouldBe afteracceptedactioncalls
      afterrejectedvalidation should be > afterformvalidation
      }
    }

    }

    "provide binding, admin API, and authorization contracts" which {
    "render operation image binding controls and Form API metadata" in {
      Given("the prerequisites for render operation image binding controls and Form API metadata")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice",
            kind = "COMMAND",
            inputType = "RegisterNotice",
            outputType = "RegisterNoticeResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("title", "string", "1")),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("primary", "thumbnail"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("render operation image binding controls and Form API metadata is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for render operation image binding controls and Form API metadata holds")
      html should include ("enctype=\"multipart/form-data\"")
      html should include ("Image Attachments")
      html should include ("name=\"imageAttachments.0.role\"")
      html should include ("name=\"imageAttachments.0.blobId\"")
      html should include ("name=\"imageAttachments.0.file\" type=\"file\"")
      html should include ("<option value=\"primary\">")
      html should include ("<option value=\"thumbnail\">")
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsUpload").as[Boolean].toOption shouldBe Some(true)
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsExistingBlobId").as[Boolean].toOption shouldBe Some(true)
      fieldnames should contain ("imageAttachments.0.role")
      fieldnames should contain ("imageAttachments.0.blobId")
      fieldnames should contain ("imageAttachments.0.file")
    }

    "hide disallowed image binding input modes from operation forms" in {
      Given("the prerequisites for hide disallowed image binding input modes from operation forms")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "attach-notice-image",
            kind = "COMMAND",
            inputType = "AttachNoticeImage",
            outputType = "AttachNoticeImageResult",
            inputValueKind = "COMMAND_VALUE",
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = false,
              acceptsExistingBlobId = true,
              createsAttachment = true,
              roles = Vector("cover"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("attach-notice-image"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      When("hide disallowed image binding input modes from operation forms is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "attach-notice-image"
      ).map(_.body).getOrElse(fail("operation form is missing"))

      Then("the observable contract for hide disallowed image binding input modes from operation forms holds")
      html should include ("Image Attachments")
      html should include ("name=\"imageAttachments.0.blobId\"")
      html should not include ("name=\"imageAttachments.0.file\"")
      html should not include ("enctype=\"multipart/form-data\"")
    }

    "render operation association binding controls and metadata" in {
      Given("the prerequisites for render operation association binding controls and metadata")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId"),
              sortOrderParameters = Vector("tagSortOrder")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("render operation association binding controls and metadata is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for render operation association binding controls and metadata holds")
      html should include ("Associations")
      html should include ("name=\"tagId\"")
      html should include ("name=\"tagSortOrder\"")
      json.hcursor.downField("bindings").downField("associationBinding").downField("domain").as[String].toOption shouldBe Some("notice_tag")
      json.hcursor.downField("bindings").downField("associationBinding").downField("targetKind").as[String].toOption shouldBe Some("tag")
      fieldnames should contain ("tagId")
      fieldnames should contain ("tagSortOrder")
    }

    "preserve declared binding parameters during operation form validation" in {
      Given("the prerequisites for preserve declared binding parameters during operation form validation")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("tagId", "string", "1")),
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val valid = _renderer.validateOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag",
        Map("tagId" -> "tag-1")
      ).getOrElse(fail("operation validation is missing"))
      When("preserve declared binding parameters during operation form validation is exercised")
      val missing = _renderer.validateOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag",
        Map.empty
      ).getOrElse(fail("operation validation is missing"))

      Then("the observable contract for preserve declared binding parameters during operation form validation holds")
      valid.valid shouldBe true
      missing.valid shouldBe false
      missing.errors.flatMap(_.field) should contain ("tagId")
    }

    "avoid duplicate binding virtual fields for declared operation parameters" in {
      Given("the prerequisites for avoid duplicate binding virtual fields for declared operation parameters")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-notice-tag",
            kind = "COMMAND",
            inputType = "RegisterNoticeTag",
            outputType = "RegisterNoticeTagResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("tagId", "string", "1")),
            associationBinding = Some(CmlOperationAssociationBinding(
              domain = "notice_tag",
              targetKind = "tag",
              createsAssociation = true,
              roles = Vector("tag"),
              sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeEntityCreateResult,
              targetIdParameters = Vector("tagId")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-notice-tag"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "notice",
        "register-notice-tag"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("avoid duplicate binding virtual fields for declared operation parameters is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for avoid duplicate binding virtual fields for declared operation parameters holds")
      fieldnames.count(_ == "tagId") shouldBe 1
      "name=\"tagId\"".r.findAllIn(html).size shouldBe 1
      html should not include ("Tag Id Target id")
    }

    "skip image attachment controls for non-attachment image operations" in {
      Given("the prerequisites for skip image attachment controls for non-attachment image operations")
      val component = new org.goldenport.cncf.component.Component() {
        override def operationDefinitions: Vector[CmlOperationDefinition] =
          Vector(CmlOperationDefinition(
            name = "register-blob-like",
            kind = "COMMAND",
            inputType = "RegisterBlobLike",
            outputType = "RegisterBlobLikeResult",
            inputValueKind = "COMMAND_VALUE",
            parameters = Vector(CmlOperationField("payload", "blob", "1")),
            imageBinding = Some(CmlOperationImageBinding(
              acceptsUpload = true,
              createsAttachment = false,
              parameters = Vector("payload")
            ))
          ))
      }
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(spec.ServiceDefinition(
            name = "blob",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("register-blob-like"))
            )
          ))
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "blob",
        "register-blob-like"
      ).map(_.body).getOrElse(fail("operation form is missing"))
      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        "notice-board",
        "blob",
        "register-blob-like"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))
      When("skip image attachment controls for non-attachment image operations is exercised")
      val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for skip image attachment controls for non-attachment image operations holds")
      html should not include ("Image Attachments")
      html should not include ("imageAttachments.0.file")
      fieldnames should contain ("payload")
      fieldnames should not contain ("imageAttachments.0.file")
      json.hcursor.downField("bindings").downField("imageBinding").downField("acceptsUpload").as[Boolean].toOption shouldBe Some(true)
    }

    "serve admin entity form definition API from EntityRuntimeDescriptor schema" in {
      Given("the prerequisites for serve admin entity form definition API from EntityRuntimeDescriptor schema")
      val descriptor = ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = EntityCollectionId("sys", "sys", "notice"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(Schema(Vector(
              Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
              Column(
                BaseContent.Builder("body").label("Notice body").build(),
                ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
                web = WebColumn(controlType = Some("textarea"), help = Some("Notice body shown on the board."))
              )
            )))
          )
        )
      )
      val component = TestComponentFactory
        .create("notice_board", Protocol.empty)
        .withComponentDescriptors(Vector(descriptor))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      When("serve admin entity form definition API from EntityRuntimeDescriptor schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin entity form definition API from EntityRuntimeDescriptor schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("entity")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/create")
      json.hcursor.downField("actions").downN(5).downField("path").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/{id}/update")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("shortid")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("body")
      fields.downN(2).downField("label").as[String].toOption shouldBe Some("Notice body")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("textarea")
      fields.downN(2).downField("help").as[String].toOption shouldBe Some("Notice body shown on the board.")
    }

    "serve admin entity update form definition API from detail view fields" in {
      Given("the prerequisites for serve admin entity update form definition API from detail view fields")
      val subsystem = _management_console_fixture_subsystem(
        schema = _schema("id", "title", "author"),
        viewfields = Map(
          "summary" -> Vector("id", "title"),
          "detail" -> Vector("id", "title"),
          "create" -> Vector("title", "author")
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_update_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice/notice_1/update"),
          "notice-board",
          "notice",
          "notice_1"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity update form definition JSON is invalid"))
      When("serve admin entity update form definition API from detail view fields is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin entity update form definition API from detail view fields holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-entity-update")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/entities/notice/notice_1/update")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/entities/notice/notice_1/edit")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("title")
      fields.downN(2).downField("name").as[String].toOption shouldBe None
    }

    "allow anonymous admin form API by the develop anonymous admin default" in {
      Given("the prerequisites for allow anonymous admin form API by the develop anonymous admin default")
      val subsystem = _management_console_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow anonymous admin form API by the develop anonymous admin default is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow anonymous admin form API by the develop anonymous admin default holds")
      response.status.code shouldBe 200
    }

    "deny anonymous admin form API when develop anonymous admin is disabled" in {
      Given("the prerequisites for deny anonymous admin form API when develop anonymous admin is disabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      When("deny anonymous admin form API when develop anonymous admin is disabled is exercised")
      val json = parse(body).getOrElse(fail(body)).hcursor

      Then("the observable contract for deny anonymous admin form API when develop anonymous admin is disabled holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.downField("error").get[String]("message") shouldBe Right("Forbidden")
      json.downField("error").downField("debug").get[String]("path") shouldBe Right("/form-api/notice-board/admin/entities/notice")
      json.downField("error").downField("debug").get[String]("method") shouldBe Right("GET")
    }

    "include status and detail code in plain-text structured API errors" in {
      Given("the prerequisites for include status and detail code in plain-text structured API errors")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val request = _get_request("/form-api/notice-board/admin/entities/notice")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Accept"), "text/plain"))

      val response = server
        ._component_admin_entity_form_api_definition(
          request,
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      When("include status and detail code in plain-text structured API errors is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for include status and detail code in plain-text structured API errors holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.plain)
      body should include ("Forbidden")
      body should include ("status: 403")
      body should include ("statusText: Forbidden")
      body should include ("detailCode:")
    }

    "deny anonymous admin form API in production operation mode" in {
      Given("the prerequisites for deny anonymous admin form API in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny anonymous admin form API in production operation mode is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for deny anonymous admin form API in production operation mode holds")
      response.status.code shouldBe 403
    }

    "deny anonymous component admin HTML route in production operation mode" in {
      Given("the prerequisites for deny anonymous component admin HTML route in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice"))
        .unsafeRunSync()
      When("deny anonymous component admin HTML route in production operation mode is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for deny anonymous component admin HTML route in production operation mode holds")
      response.status.code shouldBe 403
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.html)
      body should include ("Request failed")
      body should include ("<strong>Status:</strong>")
      body should include ("<strong>Status text:</strong>")
      body should include ("<code>403</code>")
      body should include ("<code>Forbidden</code>")
      body should not include ("structured-error-debug")
      body should not include ("\"error\"")
    }

    "deny forged query/header admin identity in production operation mode" in {
      Given("the prerequisites for deny forged query/header admin identity in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny forged query/header admin identity in production operation mode is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_get_request("/web/notice-board/admin/entities/notice?principalId=admin-test&role=component_operator&privilege=operator"))
        .unsafeRunSync()

      Then("the observable contract for deny forged query/header admin identity in production operation mode holds")
      response.status.code shouldBe 403
    }

    "strip forged authorization fields before resolving the production admin session" in {
      Given("the prerequisites for strip forged authorization fields before resolving the production admin session")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_echoing_auth_session(
        subsystem,
        _session_summary(
          "weak-session-with-forged-query",
          "ordinary-user",
          Map(
            "role" -> "user",
            "privilege" -> "user"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("strip forged authorization fields before resolving the production admin session is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(
          _with_session(
            _get_request("/web/system/admin?role=system_admin&privilege=system"),
            "weak-session-with-forged-query"
          )
        )
        .unsafeRunSync()

      Then("the observable contract for strip forged authorization fields before resolving the production admin session holds")
      response.status.code shouldBe 403
    }

    "allow component operator session to use component admin in production when explicitly enabled" in {
      Given("the prerequisites for allow component operator session to use component admin in production when explicitly enabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "component-admin-session",
          "component-operator",
          Map(
            "role" -> "component_operator",
            "privilege" -> "operator"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow component operator session to use component admin in production when explicitly enabled is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/notice-board/admin/entities/notice"), "component-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for allow component operator session to use component admin in production when explicitly enabled holds")
      response.status.code shouldBe 200
    }

    "deny system admin role when production privilege ceiling is only user" in {
      Given("the prerequisites for deny system admin role when production privilege ceiling is only user")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "weak-system-admin-session",
          "weak-system-admin",
          Map(
            "role" -> "system_admin",
            "privilege" -> "user"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("deny system admin role when production privilege ceiling is only user is exercised")
      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "weak-system-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for deny system admin role when production privilege ceiling is only user holds")
      response.status.code shouldBe 403
    }

    "allow system admin session to use system admin in production when explicitly enabled" in {
      Given("the prerequisites for allow system admin session to use system admin in production when explicitly enabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "system-admin-session",
          "system-admin",
          Map(
            "role" -> "system_admin",
            "privilege" -> "system"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "system-admin-session"))
        .unsafeRunSync()
      val performance = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/performance"), "system-admin-session"))
        .unsafeRunSync()
      When("allow system admin session to use system admin in production when explicitly enabled is exercised")
      val assembly = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin/assembly/report"), "system-admin-session"))
        .unsafeRunSync()

      Then("the observable contract for allow system admin session to use system admin in production when explicitly enabled holds")
      response.status.code shouldBe 200
      performance.status.code shouldBe 200
      assembly.status.code shouldBe 200
    }

    "allow audit viewer session to read production admin jobs but not system admin home" in {
      Given("the prerequisites for allow audit viewer session to read production admin jobs but not system admin home")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "audit-viewer-session",
          "audit-viewer",
          Map(
            "role" -> "audit_viewer",
            "privilege" -> "system"
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val jobs = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin/jobs"), "audit-viewer-session"))
        .unsafeRunSync()
      val home = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/admin"), "audit-viewer-session"))
        .unsafeRunSync()
      When("allow audit viewer session to read production admin jobs but not system admin home is exercised")
      val performance = server
        .routes(null)
        .orNotFound
        .run(_with_session(_get_request("/web/system/performance"), "audit-viewer-session"))
        .unsafeRunSync()

      Then("the observable contract for allow audit viewer session to read production admin jobs but not system admin home holds")
      jobs.status.code shouldBe 200
      home.status.code shouldBe 403
      performance.status.code shouldBe 403
    }

    "allow authenticated admin form API when develop anonymous admin is disabled" in {
      Given("the prerequisites for allow authenticated admin form API when develop anonymous admin is disabled")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("false")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("allow authenticated admin form API when develop anonymous admin is disabled is exercised")
      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice?principalId=admin-test"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow authenticated admin form API when develop anonymous admin is disabled holds")
      response.status.code shouldBe 200
    }

    "deny anonymous admin entity create POST in production operation mode before dispatch" in {
      Given("the prerequisites for deny anonymous admin entity create POST in production operation mode before dispatch")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("deny anonymous admin entity create POST in production operation mode before dispatch is exercised")
      val response = server
        ._submit_component_admin_entity_create(
          _post_form_request(
            "/form/notice-board/admin/entities/notice/create",
            "fields=id%3Dnotice_2%0Atitle%3Dnew+notice"
          ),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for deny anonymous admin entity create POST in production operation mode before dispatch holds")
      response.status.code shouldBe 403
      dispatcher.paths should not contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "allow component operator admin entity create POST in production operation mode" in {
      Given("the prerequisites for allow component operator admin entity create POST in production operation mode")
      val subsystem = _management_console_fixture_subsystem(
        configuration = Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
        ))
      )
      _install_auth_session(
        subsystem,
        _session_summary(
          "component-admin-post-session",
          "component-operator",
          Map(
            "role" -> "component_operator",
            "privilege" -> "operator"
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("allow component operator admin entity create POST in production operation mode is exercised")
      val response = server
        ._submit_component_admin_entity_create(
          _with_session(
            _post_form_request(
              "/form/notice-board/admin/entities/notice/create",
            "fields=id%3Dnotice_2%0Atitle%3Dnew+notice%0Aauthor%3Dbob"
            ),
            "component-admin-post-session"
          ),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()

      Then("the observable contract for allow component operator admin entity create POST in production operation mode holds")
      response.status.code shouldBe 200
      dispatcher.paths should contain ("/org.goldenport.cncf.Admin/entity/create")
    }

    "serve admin entity form definition API from generated companion schema" in {
      Given("the prerequisites for serve admin entity form definition API from generated companion schema")
      val component = TestComponentFactory
        .create("generated_schema_component", Protocol.empty)
        .withComponentDescriptors(Vector(ComponentDescriptor(
          componentName = Some("generated_schema_component"),
          entityRuntimeDescriptors = Vector(EntityRuntimeDescriptor(
            entityName = "order",
            collectionId = EntityCollectionId("test", "a", "order"),
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ))
        )))
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(new ComponentFactory().bootstrap(component)))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/generated-schema-component/admin/entities/order"),
          "generated-schema-component",
          "order"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      When("serve admin entity form definition API from generated companion schema is exercised")
      val names = fields.flatMap(_.hcursor.downField("name").as[String].toOption)

      Then("the observable contract for serve admin entity form definition API from generated companion schema holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      names shouldBe Vector("id", "shortid", "name", "status")
      fields(1).hcursor.downField("system").as[Boolean].toOption shouldBe Some(true)
      fields(1).hcursor.downField("readonly").as[Boolean].toOption shouldBe Some(true)
      fields(1).hcursor.downField("required").as[Boolean].toOption shouldBe Some(false)
      fields(3).hcursor.downField("label").as[String].toOption shouldBe Some("Order status")
      fields(3).hcursor.downField("type").as[String].toOption shouldBe Some("select")
      fields(3).hcursor.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "submitted", "approved"))
      fields(3).hcursor.downField("required").as[Boolean].toOption shouldBe Some(true)
      fields(3).hcursor.downField("help").as[String].toOption shouldBe Some("CML generated status hint.")
    }

    "serve admin entity form definition API from merged Schema and WebDescriptor controls" in {
      Given("the prerequisites for serve admin entity form definition API from merged Schema and WebDescriptor controls")
      val (subsystem, descriptor) = _entity_schema_web_descriptor_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_entity_form_api_definition(
          _get_request("/form-api/notice-board/admin/entities/notice"),
          "notice-board",
          "notice"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("entity form definition JSON is invalid"))
      val fields = _json_fields(json)
      val names = _json_field_names(fields)
      val body = _json_field(fields, "body")
      When("serve admin entity form definition API from merged Schema and WebDescriptor controls is exercised")
      val status = _json_field(fields, "status")

      Then("the observable contract for serve admin entity form definition API from merged Schema and WebDescriptor controls holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.entity.notice")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("entity")
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      names shouldBe Vector("id", "body", "status")
      body.downField("label").as[String].toOption shouldBe Some("Notice body")
      body.downField("type").as[String].toOption shouldBe Some("textarea")
      body.downField("placeholder").as[String].toOption shouldBe Some("Descriptor body placeholder.")
      body.downField("help").as[String].toOption shouldBe Some("Descriptor body help.")
      status.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "published", "archived"))
      status.downField("required").as[Boolean].toOption shouldBe Some(false)
    }

    "serve admin data form definition API from inferred data fields" in {
      Given("the prerequisites for serve admin data form definition API from inferred data fields")
      val fixture = _data_fixture()
      When("the observable result for serve admin data form definition API from inferred data fields is inspected")
      locally {
        Then("the observable contract for serve admin data form definition API from inferred data fields holds")
        _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit"),
            "notice-board",
            "audit"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data form definition JSON is invalid"))
        val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/new")
        fieldnames shouldBe Vector("id", "action", "actor")
      }
      }
    }

    "serve admin data update form definition API from inferred data fields" in {
      Given("the prerequisites for serve admin data update form definition API from inferred data fields")
      val fixture = _data_fixture()
      When("the observable result for serve admin data update form definition API from inferred data fields is inspected")
      locally {
        Then("the observable contract for serve admin data update form definition API from inferred data fields holds")
        _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))

        val response = server
          ._component_admin_data_update_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit/audit_1/update"),
            "notice-board",
            "audit",
            "audit_1"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data update form definition JSON is invalid"))
        val fieldnames = json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)
          .flatMap(_.hcursor.downField("name").as[String].toOption)

        response.status.code shouldBe 200
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("surface").as[String].toOption shouldBe Some("data")
        json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-data-update")
        json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/update")
        json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/edit")
        json.hcursor.downField("actions").downN(3).downField("path").as[String].toOption shouldBe Some("/form/org-goldenport-cncf-test-notice-board/admin/data/audit/audit_1/update")
        fieldnames shouldBe Vector("id", "action", "actor")
      }
      }
    }

    "serve admin data form definition API from merged inferred data fields and WebDescriptor controls" in {
      Given("the prerequisites for serve admin data form definition API from merged inferred data fields and WebDescriptor controls")
      val fixture = _data_fixture()
      When("serve admin data form definition API from merged inferred data fields and WebDescriptor controls is exercised")
      val descriptor = _data_schema_web_descriptor(includenote = false)
      Then("the observable contract for serve admin data form definition API from merged inferred data fields and WebDescriptor controls holds")
      _with_global_runtime(fixture.runtime) {
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem, Some(descriptor)))

        val response = server
          ._component_admin_data_form_api_definition(
            _get_request("/form-api/notice-board/admin/data/audit"),
            "notice-board",
            "audit"
          )
          .unsafeRunSync()
        val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("data form definition JSON is invalid"))
        val fields = _json_fields(json)
        val names = _json_field_names(fields)
        val action = _json_field(fields, "action")
        val actor = _json_field(fields, "actor")

        response.status.code shouldBe 200
        json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.data.audit")
        json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
        names shouldBe Vector("id", "action", "actor")
        action.downField("type").as[String].toOption shouldBe Some("select")
        action.downField("values").as[Vector[String]].toOption shouldBe Some(Vector("created", "updated"))
        actor.downField("required").as[Boolean].toOption shouldBe Some(true)
        actor.downField("placeholder").as[String].toOption shouldBe Some("Descriptor actor placeholder.")
        actor.downField("help").as[String].toOption shouldBe Some("Descriptor actor help.")
      }
    }

    "serve admin view form definition API from entity schema when the view name carries the view suffix" in {
      Given("the prerequisites for serve admin view form definition API from entity schema when the view name carries the view suffix")
      val subsystem = _view_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      When("serve admin view form definition API from entity schema when the view name carries the view suffix is exercised")
      val fields = _json_fields(json)

      Then("the observable contract for serve admin view form definition API from entity schema when the view name carries the view suffix holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "shortid", "label", "note")
    }

    "serve admin view form definition API from resolved view schema" in {
      Given("the prerequisites for serve admin view form definition API from resolved view schema")
      val subsystem = _view_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "view.notice-view" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("note", WebDescriptor.FormControl(controlType = Some("textarea")))
            )
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_view_form_api_definition(
          _get_request("/form-api/notice-board/admin/views/notice-view"),
          "notice-board",
          "notice-view"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("view form definition JSON is invalid"))
      When("serve admin view form definition API from resolved view schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin view form definition API from resolved view schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.view.notice-view")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("view")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-view")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/views/notice-view")
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      json.hcursor.downField("actions").downN(0).downField("name").as[String].toOption shouldBe Some("list")
      json.hcursor.downField("actions").downN(1).downField("name").as[String].toOption shouldBe Some("detail")
      json.hcursor.downField("actions").downN(2).downField("name").as[String].toOption shouldBe None
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("label")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("note")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("textarea")
    }

    "serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix" in {
      Given("the prerequisites for serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix")
      val subsystem = _aggregate_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      When("serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix is exercised")
      val fields = _json_fields(json)

      Then("the observable contract for serve admin aggregate form definition API from entity schema when the aggregate name carries the aggregate suffix holds")
      response.status.code shouldBe 200
      json.hcursor.downField("source").as[String].toOption shouldBe Some("Schema")
      _json_field_names(fields) shouldBe Vector("id", "shortid", "label", "status")
    }

    "serve admin aggregate form definition API from resolved aggregate schema" in {
      Given("the prerequisites for serve admin aggregate form definition API from resolved aggregate schema")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        admin = Map(
          "aggregate.notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id"),
              WebDescriptor.AdminField("label"),
              WebDescriptor.AdminField("status", WebDescriptor.FormControl(controlType = Some("select"), values = Vector("draft", "published")))
            )
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val response = server
        ._component_admin_aggregate_form_api_definition(
          _get_request("/form-api/notice-board/admin/aggregates/notice-aggregate"),
          "notice-board",
          "notice-aggregate"
        )
        .unsafeRunSync()
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("aggregate form definition JSON is invalid"))
      When("serve admin aggregate form definition API from resolved aggregate schema is exercised")
      val fields = json.hcursor.downField("fields")

      Then("the observable contract for serve admin aggregate form definition API from resolved aggregate schema holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      json.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.aggregate.notice-aggregate")
      json.hcursor.downField("surface").as[String].toOption shouldBe Some("aggregate")
      json.hcursor.downField("mode").as[String].toOption shouldBe Some("admin-aggregate")
      json.hcursor.downField("method").as[String].toOption shouldBe Some("GET")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate")
      json.hcursor.downField("actions").downN(0).downField("name").as[String].toOption shouldBe Some("list")
      json.hcursor.downField("actions").downN(1).downField("name").as[String].toOption shouldBe Some("detail")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some("/web/org-goldenport-cncf-test-notice-board/admin/aggregates/notice-aggregate/{id}")
      json.hcursor.downField("actions").downN(2).downField("name").as[String].toOption shouldBe None
      json.hcursor.downField("source").as[String].toOption shouldBe Some("WebDescriptor")
      fields.downN(0).downField("name").as[String].toOption shouldBe Some("id")
      fields.downN(1).downField("name").as[String].toOption shouldBe Some("label")
      fields.downN(2).downField("name").as[String].toOption shouldBe Some("status")
      fields.downN(2).downField("type").as[String].toOption shouldBe Some("select")
      fields.downN(2).downField("values").as[Vector[String]].toOption shouldBe Some(Vector("draft", "published"))
    }

    "validate operation form API input without executing the operation" in {
      Given("the prerequisites for validate operation form API input without executing the operation")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice/validate", "accessToken=abc&extra=value"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("validate operation form API input without executing the operation is exercised")
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      Then("the observable contract for validate operation form API input without executing the operation holds")
      invalid.status.code shouldBe 200
      invalid.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.application.json)
      invalidjson.hcursor.downField("selector").as[String].toOption shouldBe Some("org-goldenport-cncf-test-notice-board.notice.post-secret-notice")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      invalidjson.hcursor.downField("errors").downN(0).downField("field").as[String].toOption shouldBe Some("body")
      invalidjson.hcursor.downField("errors").downN(0).downField("code").as[String].toOption shouldBe Some("required")
      invalidjson.hcursor.downField("warnings").downN(0).downField("field").as[String].toOption shouldBe Some("extra")

      val valid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice/validate",
            "body=hello&accessToken=abc&paging.page=2&paging.pageSize=20&crud.origin.href=/web/notice-board&csrf=token-1"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val validjson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      valid.status.code shouldBe 200
      validjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validjson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
      validjson.hcursor.downField("warnings").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "validate operation form API datatype values and multiplicity" in {
      Given("the prerequisites for validate operation form API datatype values and multiplicity")
      val component = new org.goldenport.cncf.component.Component() {}
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          Vector(
            spec.ServiceDefinition(
              name = "notice",
              operations = spec.OperationDefinitionGroup(
                operations = NonEmptyVector.of(
                  NoopOperation("validate-fields", Vector("count", "published", "publishedAt", "status", "tags"))
                )
              )
            )
          )
        )
      )
      _initialize_component("notice_board", component, protocol)
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
      val selector = "notice-board.notice.validate-fields"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          controls = Map(
            "status" -> WebDescriptor.FormControl(controlType = Some("select"), values = Vector("draft", "published")),
            "tags" -> WebDescriptor.FormControl(controlType = Some("select"), values = Vector("news", "ops"), multiple = true)
          )
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-fields/validate",
            "count=abc&published=maybe&publishedAt=not-date&status=draft,published&tags=news,bad"
          ),
          "notice-board",
          "notice",
          "validate-fields"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))
      val errors = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      val errorfields = errors.flatMap(_.hcursor.downField("field").as[String].toOption)
      When("validate operation form API datatype values and multiplicity is exercised")
      val errorcodes = errors.flatMap(_.hcursor.downField("code").as[String].toOption)

      Then("the observable contract for validate operation form API datatype values and multiplicity holds")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorfields should contain allOf ("count", "published", "publishedAt", "status", "tags")
      errorcodes should contain ("datatype")
      errorcodes should contain ("invalid-value")
      errorcodes should contain ("multiplicity")

      val valid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-fields/validate",
            "count=12&published=true&publishedAt=2026-04-16T10:15:30&status=draft&tags=news,ops"
          ),
          "notice-board",
          "notice",
          "validate-fields"
        )
        .unsafeRunSync()
      val validjson = parse(valid.as[String].unsafeRunSync()).getOrElse(fail("validation JSON is invalid"))

      validjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(true)
      validjson.hcursor.downField("errors").as[Vector[Json]].toOption shouldBe Some(Vector.empty)
    }

    "serve and validate operation form validation hints" in {
      Given("the prerequisites for serve and validate operation form validation hints")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val definition = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/validate-hints"),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val definitionjson = parse(definition.as[String].unsafeRunSync()).getOrElse(fail("hint definition JSON is invalid"))
      val codefield = definitionjson.hcursor.downField("fields").downN(0)
      When("serve and validate operation form validation hints is exercised")
      val countfield = definitionjson.hcursor.downField("fields").downN(1)

      Then("the observable contract for serve and validate operation form validation hints holds")
      codefield.downField("validation").downField("minLength").as[Int].toOption shouldBe Some(2)
      codefield.downField("validation").downField("maxLength").as[Int].toOption shouldBe Some(4)
      codefield.downField("validation").downField("pattern").as[String].toOption shouldBe Some("^[A-Z0-9]+$")
      countfield.downField("validation").downField("min").as[BigDecimal].toOption shouldBe Some(BigDecimal(0))
      countfield.downField("validation").downField("max").as[BigDecimal].toOption shouldBe Some(BigDecimal(100))

      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice",
        "validate_hints",
        descriptor
      ).map(_.body).getOrElse(fail("hint operation form is missing"))

      html should include ("minlength=\"2\"")
      html should include ("maxlength=\"4\"")
      html should include ("pattern=\"^[A-Z0-9]+$\"")

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-hints/validate",
            "code=toolong&count=101"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("hint validation JSON is invalid"))
      val errorcodes = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
        .flatMap(_.hcursor.downField("code").as[String].toOption)

      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorcodes should contain ("max-length")
      errorcodes should contain ("pattern")
      errorcodes should contain ("max")
    }

    "keep Schema validation constraints when WebDescriptor attempts to relax them" in {
      Given("the prerequisites for keep Schema validation constraints when WebDescriptor attempts to relax them")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val invalid = server
        ._validate_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/validate-hints/validate",
            "code=A&count=-1"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      val invalidjson = parse(invalid.as[String].unsafeRunSync()).getOrElse(fail("relax validation JSON is invalid"))
      val errors = invalidjson.hcursor.downField("errors").as[Vector[Json]].toOption.getOrElse(Vector.empty)
      When("keep Schema validation constraints when WebDescriptor attempts to relax them is exercised")
      val errorpairs = errors.flatMap { json =>
        for {
          field <- json.hcursor.downField("field").as[String].toOption
          code <- json.hcursor.downField("code").as[String].toOption
        } yield field -> code
      }

      Then("the observable contract for keep Schema validation constraints when WebDescriptor attempts to relax them holds")
      invalidjson.hcursor.downField("valid").as[Boolean].toOption shouldBe Some(false)
      errorpairs should contain ("code" -> "min-length")
      errorpairs should contain ("count" -> "min")
    }

    "redisplay operation form validation hint errors before HTML dispatch" in {
      Given("the prerequisites for redisplay operation form validation hint errors before HTML dispatch")
      val (subsystem, descriptor) = _validation_hints_fixture()
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("DISPATCHED", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice/validate-hints",
            "code=toolong&count=101&crud.origin.href=/web/notice-board&paging.page=3&csrf=token-1"
          ),
          "notice-board",
          "notice",
          "validate-hints"
        )
        .unsafeRunSync()
      When("redisplay operation form validation hint errors before HTML dispatch is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay operation form validation hint errors before HTML dispatch holds")
      response.status.code shouldBe 400
      html should include ("Validation failed.")
      html should include ("code must be at most 4 characters.")
      html should include ("code does not match the required pattern.")
      html should include ("count must be less than or equal to 100.")
      html should include ("value=\"toolong\"")
      html should include ("value=\"101\"")
      html should include ("type=\"hidden\" name=\"crud.origin.href\" value=\"/web/notice-board\"")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"3\"")
      html should include (s"type=\"hidden\" name=\"csrf\" value=\"${_test_csrf_token}\"")
      html should not include ("DISPATCHED")
    }

    "render aggregate operation form with admin descriptor field controls" in {
      Given("the prerequisites for render aggregate operation form with admin descriptor field controls")
      val subsystem = _aggregate_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map("notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected),
        admin = Map(
          "aggregate.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.AdminSurface(
            fields = Vector(
              WebDescriptor.AdminField("id", WebDescriptor.FormControl(hidden = true)),
              WebDescriptor.AdminField("approved", WebDescriptor.FormControl(controlType = Some("select"), values = Vector("true", "false")))
            )
          )
        )
      )

      When("render aggregate operation form with admin descriptor field controls is exercised")
      val html = _renderer.renderOperationForm(
        subsystem,
        "notice_board",
        "notice_aggregate",
        "approve_notice_aggregate",
        descriptor,
        values = Map("id" -> "notice_1", "approved" -> "true")
      ).map(_.body).getOrElse(fail("aggregate operation form is missing"))

      Then("the observable contract for render aggregate operation form with admin descriptor field controls holds")
      html should include ("type=\"hidden\"")
      html should include ("name=\"id\"")
      html should include ("value=\"notice_1\"")
      html should include ("<select")
      html should include ("name=\"approved\"")
      html should include ("<option value=\"true\" selected>true</option>")
      html should include ("<option value=\"false\">false</option>")
    }

    "redirect HTML form submissions by descriptor transition while form-api returns operation response" in {
      Given("the prerequisites for redirect HTML form submissions by descriptor transition while form-api returns operation response")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            successRedirect = Some("/web/${component}/admin/aggregates/${service}/${result.id}"),
            failureRedirect = Some("/form/${component}/${service}/${operation}")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      val redirected = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()
      When("redirect HTML form submissions by descriptor transition while form-api returns operation response is exercised")
      val api = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .unsafeRunSync()

      Then("the observable contract for redirect HTML form submissions by descriptor transition while form-api returns operation response holds")
      redirected.status.code shouldBe 303
      redirected.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some("/web/notice-board/admin/aggregates/notice-aggregate/notice_1")
      api.status.code shouldBe 200
      api.as[String].unsafeRunSync() should include ("aggregate-updated:notice_1")
    }

    "render operation form result through descriptor result template" in {
      Given("the prerequisites for render operation form result through descriptor result template")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>${operation.label} Custom Result</h2>
                |  <p>Submitted ${form.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |  <textus-property-list source="result"></textus-property-list>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through descriptor result template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through descriptor result template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Custom Result")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "render operation form result route through descriptor result template when static template is absent" in {
      Given("the prerequisites for render operation form result route through descriptor result template when static template is absent")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some(
              """<article>
                |  <h2>Descriptor Result Route</h2>
                |  <p>${form.id}</p>
                |  <p>${result.id}</p>
                |  <textus-result-view source="result.body"></textus-result-view>
                |</article>""".stripMargin
            )
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("created:notice_1", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation form result route through descriptor result template when static template is absent is exercised")
      val html = server
        ._operation_form_result(
          _get_request("/form/notice-board/notice-aggregate/approve-notice-aggregate/result?id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result route through descriptor result template when static template is absent holds")
      html should include ("Descriptor Result Route")
      html should include ("notice_1")
      html should include ("created:notice_1")
      html should not include ("Submitted Values")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "resolve Static Form template properties across camel, snake, and kebab names" in {
      Given("the prerequisites for resolve Static Form template properties across camel, snake, and kebab names")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "approve-notice",
          Map("pageContext.sessionAuthenticated" -> "true")
        ),
        200,
        "application/json",
        """{"data":{"current":{"workTitle":"源氏物語","textualWorkInformationId":"info-1","rdfUri":"https://example.test/book/1","isbn13":"9784003510179"}}}"""
      )

      When("resolve Static Form template properties across camel, snake, and kebab names is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |<p>${result.body.data.current.work_title}</p>
          |<p>${result.body.data.current.textual-work-information-id}</p>
          |<p>${result.body.data.current.rdf_uri}</p>
          |<p>${result.body.data.current.isbn-13}</p>
          |<p>${pageContext.session-authenticated}</p>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for resolve Static Form template properties across camel, snake, and kebab names holds")
      html should include ("源氏物語")
      html should include ("info-1")
      html should include ("https://example.test/book/1")
      html should include ("9784003510179")
      html should include ("true")
      html should not include ("${result.body.data.current.work_title}")
    }

    "render operation result UX profile metadata" in {
      Given("the prerequisites for render operation result UX profile metadata")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("notice-board", "notice", "approve-notice"),
        200,
        "text/plain",
        "ok",
        uxProfile = WebUxProfile.Material
      )

      When("render operation result UX profile metadata is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render operation result UX profile metadata holds")
      html should include ("data-textus-ux-profile=\"material\"")
    }

    "render static template UX profile metadata" in {
      Given("the prerequisites for render static template UX profile metadata")
      val descriptor = WebDescriptor(
        profile = Some(WebUxProfile.Bootstrap),
        profileRaw = Some("bootstrap"),
        apps = Vector(WebDescriptor.App(
          "console",
          profile = Some(WebUxProfile.Compact),
          profileRaw = Some("compact")
        )),
        pages = Map(
          "console.detail" -> WebDescriptor.PageCustomization(
            profile = Some(WebUxProfile.Material),
            profileRaw = Some("material")
          )
        )
      )

      val apphtml = _renderer.renderStaticTemplate(
        "console",
        Vector("index"),
        """<article data-textus-page="static">${textus.uxProfile}</article>""",
        webdescriptor = descriptor
      ).body
      When("render static template UX profile metadata is exercised")
      val pagehtml = _renderer.renderStaticTemplate(
        "console",
        Vector("detail"),
        """<article data-textus-page="static" data-textus-ux-profile="${page.uxProfile}">${textus.uxProfile}</article>""",
        webdescriptor = descriptor
      ).body

      Then("the observable contract for render static template UX profile metadata holds")
      apphtml should include ("compact")
      pagehtml should include ("data-textus-ux-profile=\"material\"")
      pagehtml should include (">material</article>")
    }

    }

    "provide Static Web result, session, and UX contracts" which {
    "render a schema-backed operation form inside a Static Web page" in {
      Given("an aggregate command exposed to a Static Web template")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          successRedirect = Some("/web/notice-board/detail?outcome=approved"),
          controls = Map(
            "id" -> WebDescriptor.FormControl(hidden = true),
            "approved" -> WebDescriptor.FormControl(
              controlType = Some("select"),
              values = Vector("true", "false")
            )
          )
        ))
      )
      val template =
        """<main>
          |  <textus:operation-form service="notice-aggregate"
          |                         operation="approve-notice-aggregate"
          |                         value-id="${notice.id}"
          |                         value-approved="${notice.approved}"
          |                         submit-label="${message.action.approve}"></textus:operation-form>
          |</main>""".stripMargin

      When("the framework renders the page without a browser Form API request")
      val html = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(
          values = Map(
            "notice.id" -> "notice_1",
            "notice.approved" -> "true",
            "id" -> "unrelated-page-id",
            "csrf" -> "token-1"
          ),
          messages = Map("action.approve" -> "Approve")
        ),
        descriptor
      ).body

      Then("the operation schema, values, hidden context, and canonical HTML Form ingress are present")
      html should include ("action=\"/form/org-goldenport-cncf-test-notice-board/notice-aggregate/approve-notice-aggregate\"")
      html should include ("data-textus-widget=\"textus:operation-form\"")
      html should include ("type=\"hidden\"")
      html should include ("name=\"id\"")
      html should include ("value=\"notice_1\"")
      html should include ("<option value=\"true\" selected>true</option>")
      html should include ("name=\"csrf\" value=\"token-1\"")
      html should include (">Approve</button>")
      html should not include ("/form-api/")
      html should not include ("<textus:operation-form")

      And("dynamic boolean values select a schema-backed checkbox before widget rendering")
      val checkboxdescriptor = descriptor.copy(form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "id" -> WebDescriptor.FormControl(hidden = true),
          "approved" -> WebDescriptor.FormControl(controlType = Some("checkbox"))
        )
      )))
      val checkboxhtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> "true"
        )),
        checkboxdescriptor
      ).body
      checkboxhtml should include ("type=\"checkbox\" value=\"true\" checked")

      And("multiple select prefill marks every schema-backed value")
      val multipledescriptor = descriptor.copy(form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "id" -> WebDescriptor.FormControl(hidden = true),
          "approved" -> WebDescriptor.FormControl(
            controlType = Some("select"),
            values = Vector("true", "false"),
            multiple = true
          )
        )
      )))
      val multiplehtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> "true,false"
        )),
        multipledescriptor
      ).body
      multiplehtml should include ("<input type=\"hidden\" name=\"approved\" value=\"\">")
      multiplehtml should include ("<select class=\"form-select\" id=\"field-approved\" name=\"approved\" required multiple>")
      multiplehtml should include ("<option value=\"true\" selected>true</option>")
      multiplehtml should include ("<option value=\"false\" selected>false</option>")

      And("an empty multiple-select prefill carries an explicit clear value")
      val clearmultiplehtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        template,
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map(
          "notice.id" -> "notice_1",
          "notice.approved" -> ""
        )),
        multipledescriptor
      ).body
      clearmultiplehtml should include ("<input type=\"hidden\" name=\"approved\" value=\"\">")
      clearmultiplehtml should not include ("<option value=\"true\" selected>true</option>")
      clearmultiplehtml should not include ("<option value=\"false\" selected>false</option>")

      val unboundhtml = _renderer.renderStaticTemplate(
        subsystem,
        "notice-board",
        "planning-app",
        Vector("detail"),
        """<textus:operation-form service="notice-aggregate" operation="approve-notice-aggregate"></textus:operation-form>""",
        StaticFormAppLayout.AssetCompletionOptions(),
        WebPageContext(values = Map("id" -> "unrelated-page-id")),
        descriptor
      ).body
      unboundhtml should not include ("unrelated-page-id")

      val unresolvedhtml = _renderer.renderStaticTemplate(
        "planning-app",
        Vector("detail"),
        template,
        pageContext = WebPageContext(values = Map("notice.id" -> "notice_1")),
        webdescriptor = descriptor
      ).body
      unresolvedhtml should not include ("<textus:operation-form")
      unresolvedhtml should not include ("data-textus-widget=\"textus:operation-form\"")

      And("submitting the rendered form dispatches the aggregate command and redirects with GET")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))
      val response = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1&approved=false&approved=true&csrf=token-1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      response.status.code shouldBe 303
      response.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe
        Some("/web/notice-board/detail?outcome=approved")
    }

    "render a localized one-time flash after operation-form Post Redirect Get" in {
      Given("an aggregate command with an explicit redirect message key")
      val root = Files.createTempDirectory("cncf-static-web-flash-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("detail.html"),
        """<!doctype html><html><head><title>Detail</title></head><body><textus:flash></textus:flash><main>Committed view</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
        WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("ja-JP")
      ))
      val subsystem = _aggregate_http_fixture_subsystem(
        configuration,
        Vector(WebMessageCatalog(
          "planning-app",
          java.util.Locale.JAPAN,
          Map(
            "notice.approved" -> "承認しました",
            "other.message" -> "Forged outcome"
          )
        ))
      )
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(
          selector -> WebDescriptor.Form(
            successRedirect = Some("/web/notice-board/planning-app/detail"),
            successMessageKey = Some("notice.approved")
          ),
          "other-component.notice.approve" -> WebDescriptor.Form(
            successMessageKey = Some("other.message")
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      When("the command succeeds and the browser follows the redirect")
      val redirect = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      val setcookie = redirect.headers.headers
        .find(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .getOrElse(fail("flash cookie is missing"))
      val cookie = setcookie.takeWhile(_ != ';')
      val request = _get_request("/web/notice-board/planning-app/detail")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie))
      val response = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(request)
      ).unsafeRunSync()
      val html = response.as[String].unsafeRunSync()

      Then("the redirect carries only a bounded key and the next HTML response localizes and consumes it")
      redirect.status.code shouldBe 303
      setcookie should include ("HttpOnly")
      setcookie should include ("SameSite=Lax")
      setcookie should include ("Max-Age=120")
      setcookie should not include ("承認しました")
      html should include ("承認しました")
      html should include ("alert-success")
      html should include ("data-textus-widget=\"textus:flash\"")
      html should include ("Committed view")
      html should not include ("<textus:flash")
      response.headers.headers
        .filter(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value).mkString("\n") should include ("Max-Age=0")

      And("an unconfigured cross-component key cannot select catalog text")
      val rejectedcookie = WebFlash.encode(WebFlash.Value("success", "other.message"))
        .getOrElse(fail("test flash value was not encoded"))
      val rejectedrequest = _get_request("/web/notice-board/planning-app/detail")
        .putHeaders(org.http4s.Header.Raw(
          org.typelevel.ci.CIString("Cookie"),
          s"${WebFlash.cookieName("notice-board")}=${rejectedcookie}"
        ))
      val rejected = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(rejectedrequest)
      ).unsafeRunSync().as[String].unsafeRunSync()
      rejected should not include ("other.message")
      rejected should not include ("Forged outcome")
      rejected should not include ("data-textus-widget=\"textus:flash\"")
    }

    "issue and enforce a session-associated CSRF token for Static Web operation forms" in {
      Given("a Static Web page with one aggregate command form")
      val root = Files.createTempDirectory("cncf-static-web-csrf-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("detail.html"),
        """<!doctype html><html><head><title>Detail</title></head><body><textus:operation-form component="notice-admin" service="notice-aggregate" operation="approve-notice-aggregate" value-id="notice_1"></textus:operation-form></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
      ))
      val subsystem = _aggregate_http_fixture_subsystem_with_componentlets(configuration)
      val selector = "notice-admin.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          successRedirect = Some("/web/notice-board/planning-app/detail")
        ))
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("the browser loads the page")
      val getrequest = _get_request("/web/notice-board/planning-app/detail")
      val getresponse = server._component_web_app(
        "notice-board",
        "planning-app",
        Vector("detail"),
        Some(getrequest)
      ).unsafeRunSync()
      val html = getresponse.as[String].unsafeRunSync()
      val cookiename = WebCsrf.cookieName
      val setcookie = getresponse.headers.headers
        .filter(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .find(_.startsWith(s"${cookiename}="))
        .getOrElse(fail("CSRF cookie is missing"))
      val cookie = setcookie.takeWhile(_ != ';')
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(html)
        .map(_.group(1))
        .getOrElse(fail("CSRF hidden field is missing"))

      Then("the response synchronizes a strong hidden token and HttpOnly cookie")
      cookie shouldBe s"${cookiename}=${token}"
      setcookie should include ("HttpOnly")
      setcookie should include ("SameSite=Lax")
      getresponse.headers.get(org.typelevel.ci.CIString("Cache-Control")).map(_.head.value) shouldBe
        Some("private, no-store")
      html should include ("action=\"/form/org-goldenport-cncf-test-notice-admin/notice-aggregate/approve-notice-aggregate\"")
      WebCsrf.isValid(None, token) shouldBe true

      And("only a matching browser submission reaches operation dispatch")
      val validrequest = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
      ).withEntity(s"id=notice_1&csrf=${token}")
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie))
      val validresponse = server._submit_operation_form(
        validrequest,
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      validresponse.status.code shouldBe 303
      dispatcher.forms.size shouldBe 1
      dispatcher.forms.last.getString("csrf") shouldBe None

      val missingresponse = server._submit_operation_form(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
        ).withEntity("id=notice_1"),
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      missingresponse.status.code shouldBe 403
      dispatcher.forms.size shouldBe 1

      val othertoken = WebCsrf.issue(None)
      val mismatchresponse = server._submit_operation_form(
        Request[IO](
          method = Method.POST,
          uri = Uri.unsafeFromString("/form/notice-admin/notice-aggregate/approve-notice-aggregate")
        ).withEntity(s"id=notice_1&csrf=${othertoken}")
          .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), cookie)),
        "notice-admin",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      mismatchresponse.status.code shouldBe 403
      dispatcher.forms.size shouldBe 1

      And("an authenticated token is invalid after the session changes")
      val sessiontoken = WebCsrf.issue(Some("session-one"))
      WebCsrf.verify(
        Some("session-one"),
        Some(sessiontoken),
        Some(sessiontoken)
      ) shouldBe true
      WebCsrf.verify(
        Some("session-two"),
        Some(sessiontoken),
        Some(sessiontoken)
      ) shouldBe false
    }

    "apply subject-safe cache policy to Static Web documents" in {
      Given("a read-only Static Web page in a wired multi-user Subsystem")
      val root = Files.createTempDirectory("cncf-static-web-cache-")
      val approot = root.resolve("planning-app")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("index.html"),
        "<!doctype html><html><head><title>Planning</title></head><body><main>Public planning page</main></body></html>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        approot.resolve("form.html"),
        "<!doctype html><html><body><form method=\"post\" action=\"/form/notice-board/notice-aggregate/approve-notice-aggregate\"><input name=\"title\"></form></body></html>",
        StandardCharsets.UTF_8
      )
      val multiconfiguration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
        org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")
      ))
      val multisubsystem = _with_multi_user_authentication(_aggregate_http_fixture_subsystem(multiconfiguration))
      multisubsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe
        Some(org.goldenport.cncf.subsystem.SubsystemUserMode.MultiUser)
      multisubsystem.resolvedSecurityWiring.authentication.enabledProviders.map(_.name) shouldBe
        Vector("static-web-authentication")
      val multiserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(multisubsystem))
      def _header_(response: org.http4s.Response[IO], name: String): Option[String] =
        response.headers.get(org.typelevel.ci.CIString(name)).map(_.head.value)

      When("an anonymous browser without a session loads the public document")
      val publicresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app"))
      ).unsafeRunSync()

      Then("shared storage is allowed only with revalidation and language variance")
      publicresponse.status.code shouldBe 200
      _header_(publicresponse, "Cache-Control") shouldBe Some("public, max-age=0, must-revalidate")
      _header_(publicresponse, "Vary") shouldBe
        Some("Accept-Language, Cookie, Authorization, X-Textus-Session, X-CNCF-Session")

      When("the same multi-user document is requested with a session")
      val sessionresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_with_session(_get_request("/web/notice-board/planning-app"), "subject-session"))
      ).unsafeRunSync()

      Then("the subject-associated document cannot enter a shared cache")
      sessionresponse.status.code shouldBe 200
      _header_(sessionresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(sessionresponse, "Vary") shouldBe None

      And("an authorization-bearing request also remains private without a session cookie")
      val authorizationresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app").putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("Authorization"), "Bearer subject-token")
        ))
      ).unsafeRunSync()
      authorizationresponse.status.code shouldBe 200
      _header_(authorizationresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(authorizationresponse, "Vary") shouldBe None

      And("an alternate session ingress remains private")
      val alternatesessionresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app").putHeaders(
          org.http4s.Header.Raw(org.typelevel.ci.CIString("X-CNCF-Session"), "alternate-session")
        ))
      ).unsafeRunSync()
      alternatesessionresponse.status.code shouldBe 200
      _header_(alternatesessionresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(alternatesessionresponse, "Vary") shouldBe None

      And("an anonymous form retains its CSRF cookie and stays private")
      val csrfresponse = multiserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector("form"),
        Some(_get_request("/web/notice-board/planning-app/form"))
      ).unsafeRunSync()
      csrfresponse.status.code shouldBe 200
      _header_(csrfresponse, "Cache-Control") shouldBe Some("private, no-store")
      csrfresponse.headers.get(org.typelevel.ci.CIString("Set-Cookie")) should not be empty

      And("standalone documents remain private even without an authentication session")
      val standaloneconfiguration = Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
      ))
      val standalonesubsystem = _aggregate_http_fixture_subsystem(standaloneconfiguration)
      val standaloneserver = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(standalonesubsystem))
      val standaloneresponse = standaloneserver._component_web_app(
        "notice-board",
        "planning-app",
        Vector.empty,
        Some(_get_request("/web/notice-board/planning-app"))
      ).unsafeRunSync()
      _header_(standaloneresponse, "Cache-Control") shouldBe Some("private, no-store")
      _header_(standaloneresponse, "Vary") shouldBe None
    }

    "carry a declared failure flash without exposing the operation response" in {
      Given("the prerequisites for carry a declared failure flash without exposing the operation response")
      val subsystem = _aggregate_http_fixture_subsystem()
      val selector = "notice-board.notice-aggregate.approve-notice-aggregate"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected),
        form = Map(selector -> WebDescriptor.Form(
          failureRedirect = Some("/web/notice-board/planning-app/detail"),
          failureMessageKey = Some("notice.approval-failed")
        ))
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("sensitive operation failure detail", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).unsafeRunSync()
      val setcookie = response.headers.headers
        .find(_.name.toString.equalsIgnoreCase("Set-Cookie"))
        .map(_.value)
        .getOrElse(fail("failure flash cookie is missing"))
      When("carry a declared failure flash without exposing the operation response is exercised")
      val encoded = setcookie.takeWhile(_ != ';').split("=", 2).lift(1)
        .getOrElse(fail("failure flash cookie value is missing"))

      Then("the observable contract for carry a declared failure flash without exposing the operation response holds")
      response.status.code shouldBe 303
      WebFlash.decode(encoded) shouldBe Some(WebFlash.Value("danger", "notice.approval-failed"))
      setcookie should not include ("sensitive operation failure detail")
    }

    "resolve legacy result.body.data paths against unwrapped JSON response bodies" in {
      Given("the prerequisites for resolve legacy result.body.data paths against unwrapped JSON response bodies")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "book-editor",
          "book",
          "get-book"
        ),
        200,
        "application/json",
        """{"current":{"title":"源氏物語"},"counts":{"information_count":2},"information":[{"title":"Book A"}]}"""
      )

      When("resolve legacy result.body.data paths against unwrapped JSON response bodies is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |<p>${result.body.data.current.title}</p>
          |<textus:summary-card title="Information" source="result.body.data.counts.information_count"></textus:summary-card>
          |<textus:table source="result.body.data.information" pagination="false"></textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for resolve legacy result.body.data paths against unwrapped JSON response bodies holds")
      html should include ("源氏物語")
      html should include ("<strong class=\"display-6 text-primary\">2</strong>")
      html should include ("Book A")
      html should not include ("${result.body.data.current.title}")
    }

    "render operation form result through static success template convention before descriptor template" in {
      Given("the prerequisites for render operation form result through static success template convention before descriptor template")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__success.html",
            """<!doctype html>
              |<html>
              |<body>
              |  <h1>${operation.label} Static Success</h1>
              |  <p>Submitted ${form.id}</p>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</body>
              |</html>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some("<article><h2>Descriptor Result</h2></article>")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through static success template convention before descriptor template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through static success template convention before descriptor template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static Success")
      html should include ("Submitted notice_1")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Descriptor Result")
      html should not include ("${form.id}")
      html should not include ("<textus-result-view")
    }

    "prefer page-local static result template when textus form page is submitted" in {
      Given("the prerequisites for prefer page-local static result template when textus form page is submitted")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "new__success.html",
            """<!doctype html>
              |<html>
              |<body>
              |  <h1>New Page Success</h1>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</body>
              |</html>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            resultTemplate = Some("<article><h2>Descriptor Result</h2></article>")
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("prefer page-local static result template when textus form page is submitted is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&textus.form.page=new"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for prefer page-local static result template when textus form page is submitted holds")
      html should include ("New Page Success")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Descriptor Result")
      dispatcher.forms.lastOption.flatMap(_.getString("textus.form.page")) shouldBe None

      val pagecontexthtml = server
        ._prepared_form_result_template(
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          200,
          Map("pageContext.page" -> "new")
        )
        .toOption
        .flatten
        .getOrElse(fail("page context result template is missing"))
      pagecontexthtml should include ("New Page Success")
      pagecontexthtml should not include ("Descriptor Result")
    }

    "expand result body JSON paths in static result templates" in {
      Given("the prerequisites for expand result body JSON paths in static result templates")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("blog", "blog", "get-my-post"),
        200,
        "application/json",
        """{"entity_id":"major-post-1","title":"Hello <Blog>","content":"<article><p>Body</p></article>"}"""
      )

      When("expand result body JSON paths in static result templates is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<form action="/form/blog-component/blog/save-editor-post" method="post">
          |  <input name="id" value="${result.body.entity_id}">
          |  <input name="title" value="${result.body.title}">
          |  <textarea name="content">${result.body.content}</textarea>
          |</form>""".stripMargin
      ).body

      Then("the observable contract for expand result body JSON paths in static result templates holds")
      html should include ("value=\"major-post-1\"")
      html should include ("value=\"Hello &lt;Blog&gt;\"")
      html should include ("&lt;article&gt;&lt;p&gt;Body&lt;/p&gt;&lt;/article&gt;")
    }

    "prefer exact static status result template over static success template" in {
      Given("the prerequisites for prefer exact static status result template over static success template")
      val root = Files.createTempDirectory("cncf-web-template-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("approve-notice-aggregate__success.html"),
        "<article><h2>Static Success Alias</h2></article>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("approve-notice-aggregate__200.html"),
        """<article>
          |  <h2>${operation.label} Static 200 Exact</h2>
          |  <textus-result-view source="result.body"></textus-result-view>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("prefer exact static status result template over static success template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for prefer exact static status result template over static success template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200 Exact")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Static Success Alias")
      html should not include ("<textus-result-view")
    }

    "render operation form result through static status template convention" in {
      Given("the prerequisites for render operation form result through static status template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>${operation.label} Static 200</h2>
              |  <textus-property-list source="result"></textus-property-list>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through static status template convention is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through static status template convention holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 200")
      html should include ("result.status")
      html should not include ("Submitted Values")
      html should not include ("<textus-property-list")
    }

    "render operation form result through common static status template convention" in {
      Given("the prerequisites for render operation form result through common static status template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__200.html",
            """<article>
              |  <h2>Common Static 200</h2>
              |  <p>${operation.label}</p>
              |  <textus-result-view source="result.body"></textus-result-view>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)

      When("render operation form result through common static status template convention is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation form result through common static status template convention holds")
      html should include ("Common Static 200")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate")
      html should include ("aggregate-updated:notice_1")
      html should not include ("Submitted Values")
      html should not include ("<textus-result-view")
    }

    "render form continuation through static status template convention" in {
      Given("the prerequisites for render form continuation through static status template convention")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"fetched_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" href="paging.href"></textus:table>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("continuation link is missing"))
      When("render form continuation through static status template convention is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render form continuation through static status template convention holds")
      page1 should include ("Matching notices")
      page1 should include ("Paging Notice 01")
      page1 should include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Paging Notice 21")
      page2 should include ("Page 2")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus:table")
    }

    "preserve page-local result template on form continuation" in {
      Given("the prerequisites for preserve page-local result template on form continuation")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Page Local Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"fetched_count":21}"""
      val root = Files.createTempDirectory("cncf-web-page-local-continuation-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("publicblogs__success.html"),
        """<article>
          |  <h2>Page Local Search</h2>
          |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" href="paging.href"></textus:table>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&textus.form.page=publicblogs"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("page-local continuation link is missing"))
      When("preserve page-local result template on form continuation is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for preserve page-local result template on form continuation holds")
      page1 should include ("Page Local Search")
      page1 should include ("Page Local Notice 01")
      page2 should include ("Page Local Search")
      page2 should include ("Page Local Notice 21")
      page2 should not include ("Content-Type application/json")
    }

    "render form continuation with explicit total-count paging" in {
      Given("the prerequisites for render form continuation with explicit total-count paging")
      val rows = (1 to 21).map { i =>
        f"""{"title":"Total Paging Notice $i%02d","recipient_name":"PagingBob"}"""
      }.mkString("[", ",", "]")
      val responsebody = s"""{"data":${rows},"total_count":21}"""
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "approve-notice-aggregate__200.html",
            """<article>
              |  <h2>Matching notices</h2>
              |  <textus:table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus:table>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
          Bag.text(responsebody, StandardCharsets.UTF_8)
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      val page1 = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&paging.includeTotal=true"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      val continuehref = """href="([^"]*page=2&amp;pageSize=20&amp;includeTotal=true)""".r
        .findFirstMatchIn(page1)
        .map(_.group(1).replace("&amp;", "&"))
        .getOrElse(fail("total-count continuation link is missing"))
      When("render form continuation with explicit total-count paging is exercised")
      val page2 = server
        ._operation_form_continue(
          _get_request(continuehref),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate",
          continuehref.split("/continue/")(1).takeWhile(_ != '?')
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render form continuation with explicit total-count paging holds")
      page1 should include ("Matching notices")
      page1 should include ("Total Paging Notice 01")
      page1 should include ("includeTotal=true")
      page1 should not include ("Page 1")
      page2 should include ("Matching notices")
      page2 should include ("Total Paging Notice 21")
      page2 should include ("includeTotal=true")
      page2 should not include ("Content-Type application/json")
      page2 should not include ("Submitted Values")
      page2 should not include ("<textus:table")
    }

    "render operation failure through exact static status template before error template" in {
      Given("the prerequisites for render operation failure through exact static status template before error template")
      val root = Files.createTempDirectory("cncf-web-template-")
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        root.resolve("approve-notice-aggregate__error.html"),
        "<article><h2>Static Error Alias</h2></article>",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("approve-notice-aggregate__400.html"),
        """<article>
          |  <h2>${operation.label} Static 400 Exact</h2>
          |  <p>${error.body}</p>
          |  <p>${crud.origin.href}</p>
          |  <p>form id: ${form.id}</p>
          |  <p>form page: ${form.paging.page}</p>
          |  <form method="post" action="/form/notice-board/notice-aggregate/approve-notice-aggregate">
          |    <textus:hidden-context></textus:hidden-context>
          |    <input type="hidden" name="id" value="${form.id}">
          |  </form>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid approval", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation failure through exact static status template before error template is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&paging.page=2&paging.pageSize=20&csrf=token-1"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation failure through exact static status template before error template holds")
      html should include ("notice-board.notice-aggregate.approve-notice-aggregate Static 400 Exact")
      html should include ("invalid approval")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("form id: notice_1")
      html should include ("form page: </p>")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"2\"")
      html should include (s"type=\"hidden\" name=\"csrf\" value=\"${_test_csrf_token}\"")
      html should not include ("Static Error Alias")
      html should not include ("${crud.origin.href}")
      html should not include ("<textus:hidden-context")
      html should not include ("Submitted Values")
    }

    "render operation failure through common static error template when status template is absent" in {
      Given("the prerequisites for render operation failure through common static error template when status template is absent")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__error.html",
            """<article>
              |  <h2>Common Static Error</h2>
              |  <p>${crud.origin.href}</p>
              |  <p>${form.id}</p>
              |  <p>${form.paging.page}</p>
              |  <textus-error-panel source="result"></textus-error-panel>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.InternalServerError,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("aggregate service failed", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("render operation failure through common static error template when status template is absent is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request(
            "/form/notice-board/notice-aggregate/approve-notice-aggregate",
            "id=notice_1&crud.origin.href=/web/notice-board/admin/aggregates/notice-aggregate&paging.page=2"
          ),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for render operation failure through common static error template when status template is absent holds")
      html should include ("Common Static Error")
      html should include ("/web/notice-board/admin/aggregates/notice-aggregate")
      html should include ("notice_1")
      html should include ("500")
      html should include ("aggregate service failed")
      html should include ("result.status")
      html should include ("result.body")
      html should not include ("${form.paging.page}")
      html should not include ("<textus-error-panel")
      html should not include ("Submitted Values")
    }

    "render Web HTML errors through app-specific static status template convention" in {
      Given("the prerequisites for render Web HTML errors through app-specific static status template convention")
      val root = Files.createTempDirectory("cncf-web-error-template-")
      val approot = root.resolve("notice-board")
      Files.createDirectories(approot)
      Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
      Files.writeString(
        approot.resolve("__404.html"),
        """<article>
          |  <h2>Notice Board Missing</h2>
          |  <p>${error.status}</p>
          |  <p>${error.path}</p>
          |  <p>${error.message}</p>
          |</article>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      When("render Web HTML errors through app-specific static status template convention is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render Web HTML errors through app-specific static status template convention holds")
      response.status.code shouldBe 404
      html should include ("Notice Board Missing")
      html should include ("404")
      html should include ("/web/notice-board/missing")
      html should include ("Static Form App not found")
    }

    "render Web HTML errors through global static error template convention" in {
      Given("the prerequisites for render Web HTML errors through global static error template convention")
      val subsystem = _aggregate_http_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(_web_template_fixture_root(
            "__error.html",
            """<article>
              |  <h2>Global Web Error</h2>
              |  <p>${component}</p>
              |  <textus-error-panel source="result"></textus-error-panel>
              |  <textus-property-list source="error"></textus-property-list>
              |</article>""".stripMargin
          ).resolve("web.yaml").toString)
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val response = server
        ._static_form_app("notice-board", Vector("missing"))
        .unsafeRunSync()
      When("render Web HTML errors through global static error template convention is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for render Web HTML errors through global static error template convention holds")
      response.status.code shouldBe 404
      html should include ("Global Web Error")
      html should include ("notice-board")
      html should include ("404")
      html should include ("/web/notice-board/missing")
      html should include ("result.status")
      html should include ("result.body")
      html should include ("error.path")
      html should not include ("<textus-error-panel")
    }

    "render non-production structured error debug YAML at the bottom of Web error pages" in {
      Given("the prerequisites for render non-production structured error debug YAML at the bottom of Web error pages")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Develop,
        component = Some("notice-board")
      )

      When("render non-production structured error debug YAML at the bottom of Web error pages is exercised")
      val html = _renderer.renderStructuredErrorPage(Some("notice-board"), error).body

      Then("the observable contract for render non-production structured error debug YAML at the bottom of Web error pages holds")
      html should include ("Request failed")
      html should include (detailcode.toString)
      html should include ("structured-error-debug")
      html should include ("Debug error details")
      html should include ("mode: develop")
      html should include ("path: /web/notice-board/missing")
    }

    "hide structured debug YAML in production Web error pages while keeping the Conclusion detail code" in {
      Given("the prerequisites for hide structured debug YAML in production Web error pages while keeping the Conclusion detail code")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Production,
        component = Some("notice-board")
      )

      When("hide structured debug YAML in production Web error pages while keeping the Conclusion detail code is exercised")
      val html = _renderer.renderStructuredErrorPage(Some("notice-board"), error).body

      Then("the observable contract for hide structured debug YAML in production Web error pages while keeping the Conclusion detail code holds")
      html should include (detailcode.toString)
      html should not include ("structured-error-debug")
      html should not include ("Debug error details")
      html should not include ("mode: production")
    }

    "include structured Conclusion detail code in response error records" in {
      Given("the prerequisites for include structured Conclusion detail code in response error records")
      val conclusion = _structured_conclusion()
      val detailcode = conclusion.status.detailCode.map(_.code).getOrElse(fail("detailcode is missing"))
      When("include structured Conclusion detail code in response error records is exercised")
      val error = StructuredHttpError.fromConclusion(
        conclusion,
        "/web/notice-board/missing",
        "GET",
        OperationMode.Production,
        component = Some("notice-board")
      )

      Then("the observable contract for include structured Conclusion detail code in response error records holds")
      error.publicRecord.asMap.get("detailCode") shouldBe Some(detailcode)
      error.envelopeJson should include (s""""detailCode":${detailcode}""")
      error.envelopeJson should not include ("codeSource")
      error.envelopeJson should not include ("http.404")
    }

    "redisplay the operation form with submitted values when stayOnError is enabled" in {
      Given("the prerequisites for redisplay the operation form with submitted values when stayOnError is enabled")
      val subsystem = _aggregate_http_fixture_subsystem()
      val descriptor = WebDescriptor(
        expose = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Exposure.Protected
        ),
        form = Map(
          "notice-board.notice-aggregate.approve-notice-aggregate" -> WebDescriptor.Form(
            stayOnError = true
          )
        )
      )
      val engine = new HttpExecutionEngine(subsystem, Some(descriptor))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("invalid approval", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("redisplay the operation form with submitted values when stayOnError is enabled is exercised")
      val html = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for redisplay the operation form with submitted values when stayOnError is enabled holds")
      html should include ("HTML form operation")
      html should include ("error.status")
      html should include ("400")
      html should include ("invalid approval")
      html should include ("value=\"notice_1\"")
    }

    "redisplay operation form validation errors before dispatching HTML submit" in {
      Given("the prerequisites for redisplay operation form validation errors before dispatching HTML submit")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(
        expose = Map(selector -> WebDescriptor.Exposure.Protected)
      )
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.Ok,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("DISPATCHED", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice/post-secret-notice", "accessToken=abc"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("redisplay operation form validation errors before dispatching HTML submit is exercised")
      val html = response.as[String].unsafeRunSync()

      Then("the observable contract for redisplay operation form validation errors before dispatching HTML submit holds")
      response.status.code shouldBe 400
      html should include ("HTML form operation")
      html should include ("Validation failed.")
      html should include ("data-textus-validation-summary=\"form\"")
      html should include ("data-textus-validation-message=\"error\"")
      html should include ("data-textus-validation-field=\"body\"")
      html should include ("data-textus-validation-code=\"required\"")
      html should include ("data-textus-issue-scope=\"field\"")
      html should include ("Notice body is required.")
      html should include ("is-invalid")
      html should include ("value=\"abc\"")
      html should not include ("DISPATCHED")
    }

    "merge schema-driven form fields with additional fields on submit" in {
      Given("the prerequisites for merge schema-driven form fields with additional fields on submit")
      val subsystem = _aggregate_http_fixture_subsystem()
      val engine = new HttpExecutionEngine(subsystem)
      val dispatcher = new RecordingWebOperationDispatcher(WebOperationDispatcher.Local(engine))
      val server = HttpRuntimeBindingAdmissionFixture.server(engine, operationDispatcherOption = Some(dispatcher))

      When("merge schema-driven form fields with additional fields on submit is exercised")
      val html = server._submit_operation_form(
        _post_form_request(
          "/form/notice-board/notice-aggregate/approve-notice-aggregate",
          "id=notice_1&fields=approved%3Dtrue"
        ),
        "notice-board",
        "notice-aggregate",
        "approve-notice-aggregate"
      ).flatMap(_.as[String]).unsafeRunSync()

      Then("the observable contract for merge schema-driven form fields with additional fields on submit holds")
      html should include ("aggregate-updated:notice_1")
      dispatcher.forms.lastOption.map(_.getString("approved")) shouldBe Some(Some("true"))
    }

    "render textus result widgets with paging links" in {
      Given("the prerequisites for render textus result widgets with paging links")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "1",
            "paging.total" -> "25",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/test-id?page={page}&pageSize={pageSize}"
          )
        ),
        200,
        "application/json",
        """[{"title":"Hello","author":"Taro"},{"title":"World","author":"Hanako"}]"""
      )

      When("render textus result widgets with paging links is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render textus result widgets with paging links holds")
      html should include ("<table")
      html should include ("Hanako")
      html should not include ("<td>Hello</td>")
      html should include ("result.status")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=1&amp;pageSize=1")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=3&amp;pageSize=1")
      html should not include ("<textus:table")
      html should not include ("${result.contentType}")
    }

    "render capability gated HTML controls" in {
      Given("the prerequisites for render capability gated HTML controls")
      val template =
        """<main>
          |  <a href="/edit" data-textus-capability="information:edit" data-textus-capability-mode="hide">Edit</a>
          |  <form action="/save" data-textus-capability="information:edit" data-textus-capability-mode="disable">
          |    <input name="title" value="Notice">
          |    <button type="submit">Save</button>
          |  </form>
          |</main>""".stripMargin

      val denied = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.security.capabilities" -> "information:read"))
      ).body
      When("render capability gated HTML controls is exercised")
      val allowed = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.security.capabilities" -> "information:edit"))
      ).body

      Then("the observable contract for render capability gated HTML controls holds")
      denied should not include ("href=\"/edit\"")
      denied should include ("textus-capability-disabled")
      denied should include ("data-textus-capability-state=\"denied\"")
      denied should include ("aria-disabled=\"true\"")
      denied should include ("<input name=\"title\" value=\"Notice\" disabled>")
      denied should include ("<button type=\"submit\" disabled>")
      allowed should include ("href=\"/edit\"")
      allowed should include ("<button type=\"submit\">")
      allowed should not include ("textus-capability-disabled")
    }

    "render capability controls with authenticated policy" in {
      Given("the prerequisites for render capability controls with authenticated policy")
      val template =
        """<main>
          |  <a href="/import" data-textus-capability="information:import" data-textus-capability-policy="authenticated">Import</a>
          |</main>""".stripMargin

      val anonymous = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.session.authenticated" -> "false"))
      ).body
      When("render capability controls with authenticated policy is exercised")
      val authenticated = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.session.authenticated" -> "true"))
      ).body

      Then("the observable contract for render capability controls with authenticated policy holds")
      anonymous should not include ("Import")
      authenticated should include ("Import")
    }

    "render capability controls around nested same-name elements" in {
      Given("the prerequisites for render capability controls around nested same-name elements")
      val template =
        """<main>
          |  <div data-textus-capability="information:edit" data-textus-capability-mode="hide">
          |    <div class="inner">Secret</div>
          |    <p>Tail</p>
          |  </div>
          |  <p>Visible</p>
          |</main>""".stripMargin

      When("render capability controls around nested same-name elements is exercised")
      val html = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.security.capabilities" -> "information:read"))
      ).body

      Then("the observable contract for render capability controls around nested same-name elements holds")
      html should not include ("Secret")
      html should not include ("Tail")
      html should include ("Visible")
    }

    "render conditional HTML controls from page properties" in {
      Given("the prerequisites for render conditional HTML controls from page properties")
      val template =
        """<main>
          |  <section data-textus-render-if-any="noticeKind,noticeStatus">
          |    <h2>Activity notice</h2>
          |    <p>${noticeKind}</p>
          |  </section>
          |  <section data-textus-render-if-all="noticeKind,noticeStatus">
          |    <h2>Complete notice</h2>
          |  </section>
          |  <p>Visible</p>
          |</main>""".stripMargin

      val empty = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template
      ).body
      val partial = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("noticeKind" -> "import"))
      ).body
      When("render conditional HTML controls from page properties is exercised")
      val complete = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("noticeKind" -> "import", "noticeStatus" -> "seeded"))
      ).body

      Then("the observable contract for render conditional HTML controls from page properties holds")
      empty should not include ("Activity notice")
      empty should include ("Visible")
      partial should include ("Activity notice")
      partial should not include ("Complete notice")
      partial should include ("import")
      complete should include ("Activity notice")
      complete should include ("Complete notice")
    }

    "render capability message only when access is missing" in {
      Given("the prerequisites for render capability message only when access is missing")
      val template =
        """<main>
          |  <textus:capability-message capability="information:publish" policy="authenticated" login="true" login-href="/login">Log in to publish.</textus:capability-message>
          |</main>""".stripMargin

      val anonymous = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.session.authenticated" -> "false"))
      ).body
      When("render capability message only when access is missing is exercised")
      val authenticated = _renderer.renderStaticTemplate(
        "notice-board",
        Vector("detail"),
        template,
        pageContext = WebPageContext(Map("pageContext.session.authenticated" -> "true"))
      ).body

      Then("the observable contract for render capability message only when access is missing holds")
      anonymous should include ("Log in to publish.")
      anonymous should include ("href=\"/login\"")
      anonymous should include ("data-textus-widget=\"textus:capability-message\"")
      anonymous should include ("data-textus-capability-message=\"denied\"")
      anonymous should include ("data-textus-capability-required=\"information:publish\"")
      authenticated should not include ("Log in to publish.")
      authenticated should not include ("textus:capability-message")
    }

    "render form result properties from operation response and submitted values" in {
      Given("the prerequisites for render form result properties from operation response and submitted values")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "body" -> "hello",
            "recipient" -> "taro"
          )
        ),
        201,
        "application/json",
        """{"id":"notice_1","outcome":"created","message":"created","actions":[{"name":"detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      )

      When("render form result properties from operation response and submitted values is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render form result properties from operation response and submitted values holds")
      html should include ("notice-board.notice.post-notice Result")
      html should include ("result.id")
      html should include ("notice_1")
      html should include ("result.outcome")
      html should include ("created")
      html should include ("result.message")
      html should include ("result.action.primary.href")
      html should include ("/web/notice-board/admin/entities/notice/notice_1")
      html should include ("form.body")
      html should include ("hello")
      html should include ("form.recipient")
      html should include ("taro")
      html should include ("card admin-card")
      html should include ("admin-action-row")
      html should include ("btn btn-primary")
      html should not include ("${operation.label}")
    }

    "expose execution CallTree metadata as result template values" in {
      Given("the prerequisites for expose execution CallTree metadata as result template values")
      val metadata = RuntimeContext.ExecutionMetadata(
        traceId = Some("trace-1"),
        executionId = Some("execution-1"),
        failure = Some("openlibrary.org"),
        inlineCallTree = Some(Record.data(
          "calltree" -> Vector(
            Record.data(
              "label" -> "action:notice.post-notice",
              "kind" -> "action",
              "flow" -> Vector(
                Record.data(
                  "label" -> "provider:openbd.book.isbn.lookup",
                  "kind" -> "provider"
                ),
                Record.data(
                  "label" -> "provider:openbd.parse",
                  "kind" -> "provider-step"
                )
              )
            )
          )
        ))
      )

      When("expose execution CallTree metadata as result template values is exercised")
      val values = FormResultMetadata.executionTemplateValues(metadata)

      Then("the observable contract for expose execution CallTree metadata as result template values holds")
      values("result.execution.trace.id") shouldBe "trace-1"
      values("result.execution.id") shouldBe "execution-1"
      values("result.execution.failure") shouldBe "openlibrary.org"
      values("result.execution.calltree.captured") shouldBe "true"
      values("result.execution.calltree.href") shouldBe "/rest/v1/admin/execution/calltree?executionId=execution-1&traceId=trace-1"
      values("result.execution.history.href") shouldBe "/rest/v1/admin/execution/history?executionId=execution-1&traceId=trace-1"
      values("result.execution.providers") shouldBe "provider:openbd.book.isbn.lookup"
    }

    "render textus action link from operation result actions" in {
      Given("the prerequisites for render textus action link from operation result actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        201,
        "application/json",
        """{"outcome":"created","actions":[{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"},{"name":"approve","label":"Approve","href":"/form/notice-board/notice/approve","method":"POST"}]}"""
      )

      When("render textus action link from operation result actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.approve" class="btn btn-warning"></textus-action-link>
          |  <textus:action-link source="result.action.missing"></textus:action-link>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render textus action link from operation result actions holds")
      html should include ("""<a class="btn btn-primary" href="/web/notice-board/admin/entities/notice/notice_1">Open detail</a>""")
      html should include ("""<form method="post" action="/form/notice-board/notice/approve" class="d-inline">""")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"1\"")
      html should include ("""<button type="submit" class="btn btn-warning">Approve</button>""")
      html should not include ("<textus-action-link")
      html should not include ("<textus:action-link")
      html should not include ("result.action.missing")
    }

    "render action widgets with hidden page context for post actions" in {
      Given("the prerequisites for render action widgets with hidden page context for post actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "crud.origin.href" -> "/web/notice-board/admin/entities/notice?page=2",
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "csrf" -> "token-1",
            "recipientName" -> "bob"
          )
        ),
        201,
        "application/json",
        """{"actions":[{"name":"approve","label":"Approve","href":"/form/notice-board/notice/approve","method":"POST"},{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"}]}"""
      )

      When("render action widgets with hidden page context for post actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.approve" class="btn btn-warning"></textus:action-link>
          |  <textus:action-form source="result.action.approve" class="btn btn-danger" label="Approve again"></textus:action-form>
          |  <textus-action-form source="result.action.detail" class="btn btn-outline-primary" method="POST" context="false"></textus-action-form>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render action widgets with hidden page context for post actions holds")
      html should include ("""<form method="post" action="/form/notice-board/notice/approve" class="d-inline"><input type="hidden" name="crud.origin.href" value="/web/notice-board/admin/entities/notice?page=2">""")
      html should include ("""<input type="hidden" name="paging.page" value="2">""")
      html should include ("""<input type="hidden" name="paging.pageSize" value="20">""")
      html should include ("""<input type="hidden" name="csrf" value="token-1">""")
      html should include ("""<button type="submit" class="btn btn-warning">Approve</button>""")
      html should include ("""<button type="submit" class="btn btn-danger">Approve again</button>""")
      html should include ("""<form method="post" action="/web/notice-board/admin/entities/notice/notice_1" class="d-inline"><button type="submit" class="btn btn-outline-primary">Open detail</button></form>""")
      html should not include ("""name="recipientName"""")
      html should not include ("<textus:action-link")
      html should not include ("<textus:action-form")
      html should not include ("<textus-action-form")
    }

    "render confirm-action widgets with Bootstrap modal and no-JS fallback" in {
      Given("the prerequisites for render confirm-action widgets with Bootstrap modal and no-JS fallback")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "crud.origin.href" -> "/web/notice-board/admin/entities/notice?page=2",
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "csrf" -> "token-1"
          )
        ),
        201,
        "application/json",
        """{"actions":[{"name":"detail","label":"Open detail","href":"/web/notice-board/admin/entities/notice/notice_1","method":"GET"},{"name":"delete","label":"Delete","href":"/form/notice-board/notice/delete","method":"POST"}]}"""
      )

      When("render confirm-action widgets with Bootstrap modal and no-JS fallback is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:confirm-action source="result.action.detail" title="Open notice" message="Open the notice detail?" label="Open" confirm-label="Open detail" cancel-label="Stay" variant="unknown" class="btn btn-outline-primary" id="open-confirm"></textus:confirm-action>
          |  <textus-confirm-action source="result.action.delete" title="Delete notice" message="This cannot be undone." confirm-label="Delete now" variant="error"></textus-confirm-action>
          |  <textus:confirm-action source="result.action.missing"></textus:confirm-action>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render confirm-action widgets with Bootstrap modal and no-JS fallback holds")
      html should include ("""class="btn btn-outline-primary" data-bs-toggle="modal" data-bs-target="#open-confirm">Open</button>""")
      html should include ("""class="modal fade" id="open-confirm"""")
      html should include ("""class="modal-header border-secondary"""")
      html should include ("""id="open-confirm-label">Open notice</h2>""")
      html should include ("Open the notice detail?")
      html should include ("""<a class="btn btn-outline-primary" href="/web/notice-board/admin/entities/notice/notice_1">Open detail</a>""")
      html should include ("""<noscript><a class="btn btn-outline-primary" href="/web/notice-board/admin/entities/notice/notice_1">Open detail</a></noscript>""")
      html should include ("""class="btn btn-outline-danger" data-bs-toggle="modal" data-bs-target="#textus-confirm-action-2">Delete</button>""")
      html should include ("""class="modal fade" id="textus-confirm-action-2"""")
      html should include ("""class="modal-header border-danger"""")
      html should include ("""method="post" action="/form/notice-board/notice/delete" class="d-inline"><input type="hidden" name="crud.origin.href" value="/web/notice-board/admin/entities/notice?page=2">""")
      html should include ("""<input type="hidden" name="paging.page" value="2">""")
      html should include ("""<input type="hidden" name="csrf" value="token-1">""")
      html should include ("""<button type="submit" class="btn btn-outline-danger">Delete now</button>""")
      html should include ("""<noscript><form method="post" action="/form/notice-board/notice/delete" class="d-inline">""")
      html should not include ("<textus:confirm-action")
      html should not include ("<textus-confirm-action")
      html should not include ("result.action.missing")
    }

    "render confirm-action widgets without hidden context when disabled" in {
      Given("the prerequisites for render confirm-action widgets without hidden context when disabled")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "paging.page" -> "2",
            "csrf" -> "token-1"
          )
        ),
        201,
        "application/json",
        """{"actions":[{"name":"delete","label":"Delete","href":"/form/notice-board/notice/delete","method":"POST"}]}"""
      )

      When("render confirm-action widgets without hidden context when disabled is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:confirm-action source="result.action.delete" context="false"></textus:confirm-action></article>"""
      ).body

      Then("the observable contract for render confirm-action widgets without hidden context when disabled holds")
      html should include ("""method="post" action="/form/notice-board/notice/delete" class="d-inline"><button type="submit" class="btn btn-outline-danger">Delete</button></form>""")
      html should not include ("""name="paging.page"""")
      html should not include ("""name="csrf"""")
      html should not include ("<textus:confirm-action")
    }

    "render textus hidden context inputs from page context without operation values" in {
      Given("the prerequisites for render textus hidden context inputs from page context without operation values")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "recipientName" -> "bob",
            "crud.origin.href" -> "/web/notice-board/admin/entities/notice?page=2",
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "search.recipientName" -> "bob",
            "return.href" -> "/form/notice-board/notice/search-notices",
            "csrf" -> "token-1",
            "version" -> "7",
            "ui.tab" -> "summary",
            "empty.context" -> ""
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("render textus hidden context inputs from page context without operation values is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<form method="post" action="/form/notice-board/notice/search-notices">
          |  <textus:hidden-context keys="ui.tab,empty.context,missing"></textus:hidden-context>
          |  <textus-hidden-context></textus-hidden-context>
          |</form>""".stripMargin
      ).body

      Then("the observable contract for render textus hidden context inputs from page context without operation values holds")
      html should include ("""<input type="hidden" name="crud.origin.href" value="/web/notice-board/admin/entities/notice?page=2">""")
      html should include ("""<input type="hidden" name="paging.page" value="2">""")
      html should include ("""<input type="hidden" name="paging.pageSize" value="20">""")
      html should include ("""<input type="hidden" name="search.recipientName" value="bob">""")
      html should include ("""<input type="hidden" name="return.href" value="/form/notice-board/notice/search-notices">""")
      html should include ("""<input type="hidden" name="csrf" value="token-1">""")
      html should include ("""<input type="hidden" name="version" value="7">""")
      html should include ("""<input type="hidden" name="ui.tab" value="summary">""")
      html should not include ("""name="recipientName"""")
      html should not include ("""name="empty.context"""")
      html should not include ("<textus:hidden-context")
      html should not include ("<textus-hidden-context")
    }

    "render await action link from asynchronous command job result" in {
      Given("the prerequisites for render await action link from asynchronous command job result")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "text/plain",
        "cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa"
      )

      When("render await action link from asynchronous command job result is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <p>${result.job.id}</p>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.await" class="btn btn-outline-primary"></textus-action-link>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render await action link from asynchronous command job result holds")
      html should include ("cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa")
      html should include ("""<form method="post" action="/form/notice-board/notice/post-notice/jobs/cncf-job-job-1776566553930-2NnWI1ze2dLoQU4t6hALAa/await" class="d-inline">""")
      html should include ("type=\"hidden\" name=\"paging.page\" value=\"1\"")
      html should include ("""<button type="submit" class="btn btn-primary">Check result</button>""")
      html should include ("""<button type="submit" class="btn btn-outline-primary">Check result</button>""")
      html should not include ("<textus:action-link")
      html should not include ("<textus-action-link")
    }

    "preserve componentlet alias in framework generated await action links" in {
      Given("the prerequisites for preserve componentlet alias in framework generated await action links")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-admin",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"jobId":"cncf-job-job-1","jobStatus":"accepted"}"""
      )

      When("preserve componentlet alias in framework generated await action links is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:job-actions actions="await"></textus:job-actions></article>"""
      ).body

      Then("the observable contract for preserve componentlet alias in framework generated await action links holds")
      html should include ("/form/notice-admin/notice/post-notice/jobs/cncf-job-job-1/await")
      html should not include ("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await")
    }

    "render job ticket and job actions for application job result UX" in {
      Given("the prerequisites for render job ticket and job actions for application job result UX")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"jobId":"cncf-job-job-1","jobStatus":"running","message":"Queued"}"""
      )

      When("render job ticket and job actions for application job result UX is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:job-ticket></textus:job-ticket>
          |  <textus-job-actions actions="await"></textus-job-actions>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render job ticket and job actions for application job result UX holds")
      html should include ("textus-job-ticket")
      html should include ("cncf-job-job-1")
      html should include ("running")
      html should include ("Queued")
      html should include ("textus-job-actions")
      _count_occurrences(html, "/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await") shouldBe 2
      html should not include ("<textus:job-ticket")
      html should not include ("<textus-job-actions")
    }

    "render application job panel with local and system job actions" in {
      Given("the prerequisites for render application job panel with local and system job actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"jobId":"cncf-job-job-1","jobStatus":"accepted","message":"Queued"}"""
      )

      When("render application job panel with local and system job actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:job-panel title="Notice command" actions="await"></textus:job-panel>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render application job panel with local and system job actions holds")
      html should include ("textus-job-panel")
      html should include ("Notice command")
      html should include ("Queued")
      html should include ("textus-job-ticket")
      html should include ("/web/notice-board/jobs/cncf-job-job-1")
      html should include ("/web/notice-board/jobs")
      html should include ("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await")
      html should include ("/web/system/jobs/cncf-job-job-1")
      html should not include ("<textus:job-panel")
    }

    "append standard application job panel when a result template omits job widgets" in {
      Given("the prerequisites for append standard application job panel when a result template omits job widgets")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"jobId":"cncf-job-job-1","jobStatus":"accepted","message":"Queued"}"""
      )

      When("append standard application job panel when a result template omits job widgets is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><h2>Custom result</h2></article>"""
      ).body

      Then("the observable contract for append standard application job panel when a result template omits job widgets holds")
      html should include ("Custom result")
      html should include ("textus-job-panel")
      html should include ("/web/notice-board/jobs/cncf-job-job-1")
      html should include ("/web/notice-board/jobs")
      html should include ("/form/notice-board/notice/post-notice/jobs/cncf-job-job-1/await")
    }

    "render development execution debug panel with inline calltree" in {
      Given("the prerequisites for render development execution debug panel with inline calltree")
      val calltree = Record.data(
        "calltree" -> Vector(
          Record.data(
            "label" -> "notice.post-notice",
            "kind" -> "action",
            "attributes" -> Record.data("started_at_nanos" -> "1", "ended_at_nanos" -> "6", "duration_millis" -> "5", "component" -> "NoticeBoard", "service" -> "Notice", "operation" -> "postNotice", "request" -> "id=notice-1", "response_type" -> "RecordResponse", "response" -> """{"kind":"record","field_count":2,"size_bytes":72,"inline":false,"payload_href":"/web/system/admin/execution/payloads/notice-1"}""", "outcome" -> "success"),
            "enter_attributes" -> Record.data("started_at_nanos" -> "1", "request" -> "id=notice-1"),
            "leave_attributes" -> Record.data("ended_at_nanos" -> "6", "duration_millis" -> "5", "response_type" -> "RecordResponse", "response" -> """{"kind":"record","field_count":2,"size_bytes":72,"inline":false,"payload_href":"/web/system/admin/execution/payloads/notice-1"}""", "outcome" -> "success"),
            "observations" -> Vector.empty,
            "children" -> Vector(
              Record.data(
                "label" -> "uow:entitystore:search:direct",
                "kind" -> "uow",
                "display_label" -> "UoW EntityStore direct search",
                "attributes" -> Record.data("started_at_nanos" -> "2", "ended_at_nanos" -> "5", "duration_millis" -> "3", "real_io" -> "true", "cache_layer" -> "entity-store", "result" -> """{"kind":"search-result","record_count":3,"size_bytes":2048,"inline":false}"""),
                "observations" -> Vector(
                  Record.data("label" -> "metrics:entity.search.start", "kind" -> "metric", "display_label" -> "Entity search", "sampled_at_nanos" -> "4", "outcome" -> "start", "query" -> Record.data("condition" -> Record.data("recipientUserId" -> "user-1")))
                ),
                "children" -> Vector(
                  Record.data("label" -> "metrics:entity.load.start", "kind" -> "metric", "sampled_at_nanos" -> "4", "outcome" -> "start", "entity" -> "notice")
                )
              )
            )
          )
        )
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"data":{"id":"notice-1"},"debug":{"calltree":{"nodes":[{"label":"raw-calltree-json-only"}]}}}""",
        executionMetadata = RuntimeContext.ExecutionMetadata(
          debugJobId = Some("cncf-job-job-1"),
          inlineCallTree = Some(calltree),
          sagaId = Some("saga-1"),
          executionJobId = Some("cncf-job-job-1"),
          executionTaskId = Some("cncf-task-task-1")
        ),
        operationMode = OperationMode.Develop
      )

      When("render development execution debug panel with inline calltree is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><h2>Custom result</h2></article>"""
      ).body

      Then("the observable contract for render development execution debug panel with inline calltree holds")
      html should include ("textus-execution-debug-panel")
      html should include ("Development execution diagnostics")
      html should include ("bg-success-subtle")
      html should include ("<dt class=\"col-sm-3\">Saga</dt><dd class=\"col-sm-9\"><code>saga-1</code></dd>")
      html should include ("<dt class=\"col-sm-3\">Job</dt><dd class=\"col-sm-9\"><code>cncf-job-job-1</code></dd>")
      html should include ("<dt class=\"col-sm-3\">Task</dt><dd class=\"col-sm-9\"><code>cncf-task-task-1</code></dd>")
      html should include ("notice.post-notice")
      html should include ("textus-calltree-tree")
      html should include ("data-textus-calltree")
      html should include ("data-calltree-node")
      html should include ("data-calltree-row")
      html should include ("data-calltree-step")
      html should include ("data-calltree-children")
      html should not include ("data-calltree-enter")
      html should not include ("data-calltree-leave")
      html should not include ("data-calltree-parent")
      html should include ("data-calltree-label")
      html should include ("data-calltree-kind")
      html should include ("/web/assets/textus-calltree.js")
      html should not include ("Raw CallTree JSON")
      html should include ("border-start")
      html should include ("UoW EntityStore direct search")
      html should include ("id=notice-1")
      html should include ("[shown in CallTree panel]")
      html should not include ("raw-calltree-json-only")
      html should include ("response_type")
      html should include ("RecordResponse")
      html should include ("records=3")
      html should include ("Show result")
      html should include ("fields=2")
      html should include ("Show response")
      html should include ("Open external response")
      html should include ("Entity search")
      html should include ("metrics:entity.load.start")
      html should include ("data-calltree-observation")
      html should include ("Step observations (2)")
      html should include ("<details class=\"mt-2\" data-calltree-observations>")
      html should include ("recipientUserId")
      html should not include ("data-calltree-mark")
      html should include ("data-calltree-highlight=\"real_io\"")
      html should include ("data-calltree-real-io=\"true\"")
      html should not include ("""data-calltree-attribute-key="real_io"""")
      html should include ("cache_layer=entity-store")
      html should not include (">UoW</span>")
      html should include ("/web/system/admin/jobs/cncf-job-job-1")
      html.indexOf ("""data-calltree-attribute-key="component">component""") should be < html.indexOf ("""data-calltree-attribute-key="service">service""")
      html.indexOf ("""data-calltree-attribute-key="service">service""") should be < html.indexOf ("""data-calltree-attribute-key="operation">operation""")
      html.indexOf ("""data-calltree-label>notice.post-notice""") should be < html.indexOf ("""data-calltree-label>UoW EntityStore direct search""")
      html.indexOf ("""data-calltree-label>UoW EntityStore direct search""") should be < html.indexOf ("""data-calltree-observation-label>Entity search""")
    }

    "append development execution debug panel to full HTML result templates" in {
      Given("the prerequisites for append development execution debug panel to full HTML result templates")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"id":"notice-1"}""",
        executionMetadata = RuntimeContext.ExecutionMetadata(
          inlineCallTree = Some(Record.data("name" -> "notice.post-notice"))
        ),
        operationMode = OperationMode.Develop
      )

      When("append development execution debug panel to full HTML result templates is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html><html><head><title>Result</title></head><body><main>Custom result</main></body></html>"""
      ).body

      Then("the observable contract for append development execution debug panel to full HTML result templates holds")
      html should include ("Custom result")
      html should include ("textus-execution-debug-panel")
      html should include ("Development execution diagnostics")
      html should include ("bg-success-subtle")
      html should include ("notice.post-notice")
      html should include ("/web/assets/textus-calltree.js")
      _count_occurrences(html, "/web/assets/textus-calltree.js") shouldBe 1
    }

    "render development execution debug panel even when calltree metadata is absent" in {
      Given("the prerequisites for render development execution debug panel even when calltree metadata is absent")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "textus.debug.executionPanel" -> "true",
            "password" -> "plain-secret",
            "accessSessionId" -> "session-secret"
          )
        ),
        200,
        "application/json",
        "id=notice-1 accessSessionId=session-secret password=plain-secret credentialValue=credential-secret",
        operationMode = OperationMode.Develop,
        fieldConfidentiality = Map("credentialValue" -> org.goldenport.schema.DataConfidentiality.Secret)
      )

      When("render development execution debug panel even when calltree metadata is absent is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><h2>Custom result</h2></article>"""
      ).body

      Then("the observable contract for render development execution debug panel even when calltree metadata is absent holds")
      html should include ("textus-execution-debug-panel")
      html should include ("Operation arguments")
      html should include ("bg-success-subtle")
      html should include ("CallTree was not captured for this response.")
      html should not include ("/web/assets/textus-calltree.js")
      html should include ("[redacted]")
      html should not include ("plain-secret")
      html should not include ("session-secret")
      html should not include ("credential-secret")
      html should include ("id=notice-1")
    }

    "hide development execution debug panel in production mode" in {
      Given("the prerequisites for hide development execution debug panel in production mode")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"id":"notice-1"}""",
        executionMetadata = RuntimeContext.ExecutionMetadata(
          inlineCallTree = Some(Record.data("name" -> "notice.post-notice"))
        ),
        operationMode = OperationMode.Production
      )

      When("hide development execution debug panel in production mode is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><h2>Custom result</h2></article>"""
      ).body

      Then("the observable contract for hide development execution debug panel in production mode holds")
      html should not include ("textus-execution-debug-panel")
      html should not include ("/web/assets/textus-calltree.js")
    }

    "ship progressive CallTree enhancement asset for development diagnostics" in {
      Given("the prerequisites for ship progressive CallTree enhancement asset for development diagnostics")
      val js = StaticFormAppAssets.textusCalltreeJs

      When("the observable result for ship progressive CallTree enhancement asset for development diagnostics is inspected")
      locally {
        Then("the observable contract for ship progressive CallTree enhancement asset for development diagnostics holds")
        js should include ("data-calltree-enhanced")
        js should include ("Expand all")
        js should include ("Collapse all")
        js should include ("data-calltree-search")
        js should include ("data-calltree-clear")
        js should include ("data-calltree-show-io")
        js should include ("data-calltree-show-real-io")
        js should include ("data-calltree-toggle")
        js should include ("setupNodeExpansion")
        js should include ("refreshExpansion")
        js should include ("directChildContainer")
        js should include ("data-calltree-long-attribute")
        js should include ("compactDurationAttributes")
        js should include ("duration_millis")
        js should include ("duration_micros")
        js should include ("duration_nanos")
        js should include ("data-calltree-pair")
        js should include ("textus-calltree-row-highlight")
        js should include ("bindPairHighlight")
        js should include ("openAncestors")
        js should include ("enhancePayloadAttributes")
        js should include ("Show \" + key")
        js should include ("Open external \" + key")
        js should include ("window.TextusCallTree")
        js should include ("enhanceAll")
      }
    }

    "ship form API diagnostics CallTree extraction asset" in {
      Given("the prerequisites for ship form API diagnostics CallTree extraction asset")
      val js = StaticFormAppAssets.textusFormDebugJs

      When("the observable result for ship form API diagnostics CallTree extraction asset is inspected")
      locally {
        Then("the observable contract for ship form API diagnostics CallTree extraction asset holds")
        js should include ("extractCallTree")
        js should include ("[shown in CallTree panel]")
        js should include ("data-textus-calltree")
        js should include ("isCallTreeObservation")
        js should include ("callTreeObservations")
        js should include ("data-calltree-children")
        js should include ("Step observations")
        js should include ("const attributeOrder = { component: 0, service: 1, operation: 2 }")
        js should include ("callTreePayloadHtml")
        js should include ("Show ' + escapeHtml(key)")
        js should include ("Open external ' + escapeHtml(key)")
        js should include ("function debugRecordKey")
        js should include ("function shouldReplaceRecord")
        js should include ("data-debug-event-key")
        js should include ("""kind === "page-render"""")
        js should include ("function ensureEventSlot")
        js should include ("data-debug-slot")
        js should include ("Operation origin slot")
        js should not include ("UoW</span>")
        js should include ("window.TextusCallTree.enhanceAll")
      }
    }

    "render application user job list and detail pages" in {
      Given("the prerequisites for render application user job list and detail pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val action = RendererJobAction(GRequest.of(
        component = "notice-board",
        service = "notice",
        operation = "post-notice"
      ))
      val task = ActionTask(ActionId.generate(), action, ActionEngine.create(), None)
      val jobid = subsystem.jobEngine.submit(
        List(task),
        ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.test(), enabled = true),
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Sync,
          executionNotes = Vector("application job")
        )
      ).toOption.getOrElse(fail("job submission failed"))
      subsystem.jobEngine.annotateJob(jobid, Map("web.app" -> "notice-board"))
      val model = subsystem.jobEngine.query(jobid).getOrElse(fail("job read model missing"))

      val list = _renderer.renderApplicationJobs("notice-board", Vector(model)).body
      When("render application user job list and detail pages is exercised")
      val detail = _renderer.renderApplicationJob("notice-board", model).body

      Then("the observable contract for render application user job list and detail pages holds")
      list should include ("My jobs")
      list should include (jobid.value)
      list should include (s"/web/notice-board/jobs/${jobid.value}")
      detail should include ("Application job result")
      detail should include ("Response")
      detail should include ("CallTree")
      detail should include ("/web/notice-board/jobs")
    }

    "render system job ticket page with fixed system await link" in {
      Given("the prerequisites for render system job ticket page with fixed system await link")
      val html = _renderer.renderSystemJobTicket("cncf-job-job-1").body

      When("the observable result for render system job ticket page with fixed system await link is inspected")
      locally {
        Then("the observable contract for render system job ticket page with fixed system await link holds")
        html should include ("textus-job-ticket")
        html should include ("cncf-job-job-1")
        html should include ("/web/system/jobs/cncf-job-job-1/await")
        html should include ("Check result")
        html should not include ("<textus:job-ticket")
      }
    }

    "render detail action link from command result id" in {
      Given("the prerequisites for render detail action link from command result id")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1"}"""
      )

      When("render detail action link from command result id is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-link source="result.action.primary" class="btn btn-primary"></textus:action-link>
          |  <textus-action-link source="result.action.detail" class="btn btn-outline-primary"></textus-action-link>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render detail action link from command result id holds")
      html should include ("""<a class="btn btn-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should include ("""<a class="btn btn-outline-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should not include ("<textus:action-link")
      html should not include ("<textus-action-link")
    }

    "preserve componentlet alias in framework generated detail action links" in {
      Given("the prerequisites for preserve componentlet alias in framework generated detail action links")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-admin",
          "notice",
          "post-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1"}"""
      )

      When("preserve componentlet alias in framework generated detail action links is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:action-link source="result.action.detail" class="btn btn-outline-primary"></textus:action-link></article>"""
      ).body

      Then("the observable contract for preserve componentlet alias in framework generated detail action links holds")
      html should include ("""href="/form/notice-admin/notice/get-notice/result?id=notice_1"""")
      html should not include ("""href="/form/notice-board/notice/get-notice/result?id=notice_1"""")
    }

    "render action-group widgets from operation result actions" in {
      Given("the prerequisites for render action-group widgets from operation result actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "paging.page" -> "1",
            "result.action.await.href" -> "/form/notice-board/notice/post-notice/jobs/job-1/await",
            "result.action.await.label" -> "Wait",
            "result.action.await.method" -> "POST",
            "result.action.detail.href" -> "/form/notice-board/notice/get-notice/result?id=notice_1",
            "result.action.detail.label" -> "Open detail",
            "result.action.detail.method" -> "GET"
          )
        ),
        200,
        "application/json",
        """{}"""
      )

      When("render action-group widgets from operation result actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-group actions="await,detail"></textus:action-group>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render action-group widgets from operation result actions holds")
      html should include ("textus-action-group")
      html should include ("""<form method="post" action="/form/notice-board/notice/post-notice/jobs/job-1/await" class="d-inline">""")
      html should include ("""<input type="hidden" name="paging.page" value="1">""")
      html should include ("""<button type="submit" class="btn btn-primary">Wait</button>""")
      html should include ("""<a class="btn btn-outline-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should not include ("<textus:action-group")
    }

    "render action-group widgets directly from JSON action arrays" in {
      Given("the prerequisites for render action-group widgets directly from JSON action arrays")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map("paging.page" -> "1")
        ),
        200,
        "application/json",
        """{"actions":[{"name":"approve","label":"Approve","href":"/form/notice-board/notice/approve","method":"POST"},{"name":"detail","label":"Open detail","href":"/form/notice-board/notice/get-notice/result?id=notice_1","method":"GET"}]}"""
      )

      When("render action-group widgets directly from JSON action arrays is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-group source="result.body.actions"></textus:action-group>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render action-group widgets directly from JSON action arrays holds")
      html should include ("textus-action-group")
      html should include ("""<form method="post" action="/form/notice-board/notice/approve" class="d-inline">""")
      html should include ("""<button type="submit" class="btn btn-outline-primary">Approve</button>""")
      html should include ("""<a class="btn btn-outline-primary" href="/form/notice-board/notice/get-notice/result?id=notice_1">Open detail</a>""")
      html should not include ("<textus:action-group")
    }

    "let JSON action metadata override framework generated action defaults" in {
      Given("the prerequisites for let JSON action metadata override framework generated action defaults")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        202,
        "application/json",
        """{"jobId":"job-1","actions":[{"name":"await","label":"Wait now","href":"/custom/jobs/job-1/await","method":"POST"}]}"""
      )

      When("let JSON action metadata override framework generated action defaults is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-group actions="await"></textus:action-group>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for let JSON action metadata override framework generated action defaults holds")
      html should include ("action=\"/custom/jobs/job-1/await\"")
      html should include ("""<button type="submit" class="btn btn-primary">Wait now</button>""")
      html should not include ("action=\"/form/notice-board/notice/post-notice/jobs/job-1/await\"")
    }

    "render return action for detail pages from return href context" in {
      Given("the prerequisites for render return action for detail pages from return href context")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "get-notice",
          Map(
            "return.href" -> "/form/notice-board/notice/search-notices"
          )
        ),
        200,
        "application/json",
        """{"id":"notice_1","title":"Phase12"}"""
      )

      When("render return action for detail pages from return href context is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-group actions="return"></textus:action-group>
          |  <form><textus:hidden-context></textus:hidden-context></form>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render return action for detail pages from return href context holds")
      html should include ("""<a class="btn btn-outline-primary" href="/form/notice-board/notice/search-notices">Back</a>""")
      html should include ("""<input type="hidden" name="return.href" value="/form/notice-board/notice/search-notices">""")
      html should not include ("<textus:action-group")
      html should not include ("<textus:hidden-context")
    }

    "render textus table without total count" in {
      Given("the prerequisites for render textus table without total count")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "10",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/test-id?page={page}&pageSize={pageSize}"
          )
        ),
        200,
        "application/json",
        """[{"title":"Hello"}]"""
      )

      When("render textus table without total count is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render textus table without total count holds")
      html should include ("Page 2")
      html should include ("Previous")
      html should include ("Next")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=1&amp;pageSize=10")
      html should include ("/form/notice-board/notice/search-notices/continue/test-id?page=3&amp;pageSize=10")
    }

    "render textus table from operation response body fields" in {
      Given("the prerequisites for render textus table from operation response body fields")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.pageSize" -> "1"
          )
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello","sender_name":"alice"},{"subject":"World","sender_name":"bob"}],"total_count":2}"""
      )

      When("render textus table from operation response body fields is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <p>${result.totalCount}</p>
          |  <p>${paging.total}</p>
          |  <textus:table source="result.body.data"></textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render textus table from operation response body fields holds")
      html should include ("<table")
      html should include ("subject")
      html should include ("sender_name")
      html should include ("Hello")
      html should not include ("bob")
      html should include ("<p>2</p>")
      html should not include ("result.totalCount")
      html should not include ("paging.total")
      html should include ("/form/notice-board/notice/search-notices/result?page=2&amp;pageSize=1")
      html should not include ("Page 1")
      html should not include ("<textus:table")
    }

    "render textus table download links independent of display columns" in {
      Given("the prerequisites for render textus table download links independent of display columns")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "recipient" -> "Alice",
            "result.body" -> """{"debug":"must-not-leak"}""",
            "paging.href" -> "/form/notice-board/notice/search-notices/result",
            "form.pageContext.app" -> "notice-board",
            "component" -> "notice-board",
            "textus.debug.executionPanel" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello","sender_name":"alice","body":"full text"}]}"""
      )

      When("render textus table download links independent of display columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.body.data" columns="subject:Subject" download-source="result.body.data" download-formats="csv,json,xlsx" download-name="notices"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table download links independent of display columns holds")
      html should include ("textus-table-download")
      html should include ("textus.download.source=result.body.data")
      html should include ("textus.download.format=csv")
      html should include ("textus.download.format=json")
      html should include ("textus.download.format=xlsx")
      html should include ("textus.download.filename=notices.csv")
      html should include ("textus.download.filename=notices.xlsx")
      html should include ("recipient=Alice")
      html should not include ("result.body=")
      html should not include ("paging.href=")
      html should not include ("form.pageContext.app=")
      html should not include ("component=notice-board")
      html should not include ("textus.debug.executionPanel=")
      html should include ("Subject")
      html should not include ("full text</td>")
    }

    "render textus table without pagination when disabled" in {
      Given("the prerequisites for render textus table without pagination when disabled")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.pageSize" -> "1"
          )
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello","sender_name":"alice"},{"subject":"World","sender_name":"bob"}],"total_count":2}"""
      )

      When("render textus table without pagination when disabled is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:table source="result.body.data" pagination="false"></textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render textus table without pagination when disabled holds")
      html should include ("<table")
      html should include ("Hello")
      html should not include ("bob")
      html should not include ("Result pages")
      html should not include ("Previous")
      html should not include ("Next")
      html should not include ("<textus:table")
    }

    "render textus table with clickable rows" in {
      Given("the prerequisites for render textus table with clickable rows")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice-1","subject":"Hello"}]}"""
      )

      When("render textus table with clickable rows is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:table
          |    source="result.body.data"
          |    detail-href="/web/notices/detail"
          |    detail-param-id="{id}"
          |    row-link="true">
          |  </textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render textus table with clickable rows holds")
      html should include ("textus-clickable-row")
      html should include ("data-textus-row-href=\"/web/notices/detail?id=notice-1\"")
      html should include ("role=\"link\"")
      html should include ("tabindex=\"0\"")
      html should include ("href=\"/web/notices/detail?id=notice-1\"")
      html should not include ("<textus:table")
    }

    "render textus table from result body shorthand" in {
      Given("the prerequisites for render textus table from result body shorthand")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"subject":"Hello"}]}"""
      )

      When("render textus table from result body shorthand is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table from result body shorthand holds")
      html should include ("<td>Hello</td>")
      html should not include ("<textus:table")
    }

    "render standalone textus pagination from shared paging metadata" in {
      Given("the prerequisites for render standalone textus pagination from shared paging metadata")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "2",
            "paging.pageSize" -> "20",
            "paging.total" -> "45",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/result-1?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("render standalone textus pagination from shared paging metadata is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:pagination></textus:pagination>
          |  <textus-pagination page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href" has-next="paging.hasNext"></textus-pagination>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render standalone textus pagination from shared paging metadata holds")
      html should include ("""<nav aria-label="Result pages">""")
      html should include ("""page=1&amp;pageSize=20""")
      html should include ("""page=3&amp;pageSize=20""")
      html should include ("""<li class="page-item active"><a class="page-link" href="/form/notice-board/notice/search-notices/continue/result-1?page=2&amp;pageSize=20">2</a></li>""")
      html should not include ("<textus:pagination")
      html should not include ("<textus-pagination")
    }

    "render standalone textus pagination without total count" in {
      Given("the prerequisites for render standalone textus pagination without total count")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.href" -> "/form/notice-board/notice/search-notices/result?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "false"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("render standalone textus pagination without total count is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:pagination></textus:pagination></article>"""
      ).body

      Then("the observable contract for render standalone textus pagination without total count holds")
      html should include ("Page 1")
      html should include ("""<li class="page-item disabled"><a class="page-link" href="/form/notice-board/notice/search-notices/result?page=2&amp;pageSize=20">Next</a></li>""")
      html should not include ("<textus:pagination")
    }

    "preserve componentlet alias in default paging href" in {
      Given("the prerequisites for preserve componentlet alias in default paging href")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-admin",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.hasNext" -> "false"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("preserve componentlet alias in default paging href is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:pagination></textus:pagination></article>"""
      ).body

      Then("the observable contract for preserve componentlet alias in default paging href holds")
      html should include ("/form/notice-admin/notice/search-notices/result?page=2&amp;pageSize=20")
      html should not include ("/form/notice-board/notice/search-notices/result?page=2&amp;pageSize=20")
    }

    "preserve form result context in default paging href" in {
      Given("the prerequisites for preserve form result context in default paging href")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "textus-user-notification",
          "notification",
          "search-my-notifications",
          Map(
            "textus.form.page" -> "notifications",
            "limit" -> "100",
            "unreadOnly" -> "true",
            "password" -> "secret-password",
            "x-textus-session" -> "session-1",
            "textus.debug.executionPanel" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":{"total_count":0,"offset":0,"limit":100,"fetched_count":0}}"""
      )

      When("preserve form result context in default paging href is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:card-list source="result.body" title="title"></textus:card-list></article>"""
      ).body

      Then("the observable contract for preserve form result context in default paging href holds")
      html should include ("textus.form.page=notifications")
      html should include ("limit=100")
      html should include ("unreadOnly=true")
      html should include ("page=2&amp;pageSize=100")
      html should include ("No records")
      html should include ("""<li class="page-item disabled"><a class="page-link" href="/form/textus-user-notification/notification/search-my-notifications/result?limit=100&amp;textus.form.page=notifications&amp;unreadOnly=true&amp;page=2&amp;pageSize=100">Next</a></li>""")
      html should not include ("textus.debug.executionPanel")
      html should not include ("secret-password")
      html should not include ("x-textus-session=session-1")
    }

    "render multiline card-list attributes used by application templates" in {
      Given("a result record and a card-list whose attributes span multiple lines")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("art-scene", "timeline", "list-timeline"),
        200,
        "application/json",
        """{"data":[{"facility_name":"Museum A","exhibition_count":2,"exhibition_summary":"A, B"}]}"""
      )

      When("the Static Form result page is rendered")
      val html = _renderer.renderFormResult(
        properties,
        """<section>
          |  <textus:card-list
          |    source="result.body.data"
          |    title="facility_name"
          |    columns="exhibition_count:Count,exhibition_summary:Exhibitions"
          |    cols="1">
          |  </textus:card-list>
          |</section>""".stripMargin
      ).body

      Then("the widget is expanded into a server-rendered record card")
      html should include ("textus-record-card")
      html should include ("Museum A")
      html should include ("Exhibitions")
      html should not include ("<textus:card-list")
    }

    "render multiline line-list attributes used by application templates" in {
      Given("a result record and a line-list whose attributes span multiple lines")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("art-scene", "timeline", "list-timeline"),
        200,
        "application/json",
        """{"data":[{"facility_name":"Museum A","exhibition_count":2,"exhibition_summary":"A, B"}]}"""
      )

      When("the Static Form result page is rendered")
      val html = _renderer.renderFormResult(
        properties,
        """<section>
          |  <textus:line-list
          |    source="result.body.data"
          |    title="facility_name"
          |    columns="exhibition_count:Count,exhibition_summary:Exhibitions">
          |  </textus:line-list>
          |</section>""".stripMargin
      ).body

      Then("the widget is expanded into a server-rendered line item")
      html should include ("data-textus-widget=\"textus:line-list\"")
      html should include ("Museum A")
      html should include ("Exhibitions")
      html should not include ("<textus:line-list")
    }

    "render textus record card with CML summary columns" in {
      Given("the prerequisites for render textus record card with CML summary columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "get-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body", "notice", "summary") -> columns)
      )

      When("render textus record card with CML summary columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:record-card source="result.body" entity="notice" view="summary"></textus:record-card></article>"""
      ).body

      Then("the observable contract for render textus record card with CML summary columns holds")
      html should include ("class=\"card h-100 textus-record-card\"")
      html should include ("<h3 class=\"h5 card-title\">Phase12</h3>")
      html should include ("<dt class=\"col-sm-4\">Title</dt><dd class=\"col-sm-8\">Phase12</dd>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should not include ("Static form validation")
      html should not include ("<textus:record-card")
    }

    "render textus description list with CML detail columns" in {
      Given("the prerequisites for render textus description list with CML detail columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("content", "Content"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "get-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1","title":"Phase12","content":"Static form detail","recipient_name":"Bob","internal":"hidden"}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body", "notice", "detail") -> columns)
      )

      When("render textus description list with CML detail columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:description-list source="result.body" entity="notice" view="detail"></textus:description-list></article>"""
      ).body

      Then("the observable contract for render textus description list with CML detail columns holds")
      html should include ("textus-description-list")
      html should include ("<dt class=\"col-sm-4\">Title</dt><dd class=\"col-sm-8\">Phase12</dd>")
      html should include ("<dt class=\"col-sm-4\">Content</dt><dd class=\"col-sm-8\">Static form detail</dd>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should not include ("internal")
      html should not include ("<textus:description-list")
    }

    "render textus card list with shared paging metadata" in {
      Given("the prerequisites for render textus card list with shared paging metadata")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "1",
            "paging.href" -> "/form/notice-board/notice/search-notices/continue/result-1?page={page}&pageSize={pageSize}",
            "paging.hasNext" -> "true"
          )
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"},{"id":"notice_2","title":"Hidden","content":"Second","recipient_name":"Alice"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body.data", "notice", "summary") -> columns)
      )

      When("render textus card list with shared paging metadata is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:card-list source="result.body" entity="notice" view="summary"></textus:card-list></article>"""
      ).body

      Then("the observable contract for render textus card list with shared paging metadata holds")
      html should include ("row row-cols-1 row-cols-md-2 g-3 mt-3")
      html should include ("<h3 class=\"h5 card-title\">Phase12</h3>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should include ("Page 1")
      html should include ("""page=2&amp;pageSize=1""")
      html should not include ("Hidden")
      html should not include ("Static form validation")
      html should not include ("<textus:card-list")
    }

    "render textus card list with explicit responsive layout attributes" in {
      Given("the prerequisites for render textus card list with explicit responsive layout attributes")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.hasNext" -> "false"
          )
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"hidden","recipient_name":"Bob"}]}"""
      )

      When("render textus card list with explicit responsive layout attributes is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:card-list source="result.body" columns="title,recipient_name" cols="1" md="3" lg="4"></textus:card-list></article>"""
      ).body

      Then("the observable contract for render textus card list with explicit responsive layout attributes holds")
      html should include ("row row-cols-1 row-cols-md-3 row-cols-lg-4 g-3 mt-3")
      html should include ("<dt class=\"col-sm-4\">title</dt><dd class=\"col-sm-8\">Phase12</dd>")
      html should include ("<dt class=\"col-sm-4\">recipient_name</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should include ("Page 1")
      html should not include ("hidden")
      html should not include ("<textus:card-list")
    }

    "render textus line-list from result rows" in {
      Given("the prerequisites for render textus line-list from result rows")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","summary":"Static form validation","recipient_name":"Bob","state":"stable"},{"id":"notice_2","title":"Second","summary":"Follow up","recipient_name":"Alice","state":"editing"}]}"""
      )

      When("render textus line-list from result rows is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:line-list source="result.body.data" title="title" subtitle="summary" columns="recipient_name:Recipient,state:State" badge="state" detail-href="/notice/{id}" detail-label="Open" click-row="true"></textus:line-list></article>"""
      ).body

      Then("the observable contract for render textus line-list from result rows holds")
      html should include ("textus-line-list")
      html should include ("data-textus-widget=\"textus:line-list\"")
      html should include ("textus-line-list-item")
      html should include ("<strong>Phase12</strong>")
      html should include ("<p class=\"text-secondary mb-1\">Static form validation</p>")
      html should include ("<dt class=\"col-sm-3\">Recipient</dt><dd class=\"col-sm-9\">Bob</dd>")
      html should include ("<span class=\"badge text-bg-success\">stable</span>")
      html should include ("data-textus-row-href=\"/notice/notice_1\"")
      html should include ("href=\"/notice/notice_1\"")
      html should not include ("<textus:line-list")

      val emptyproperties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )
      val emptyhtml = _renderer.renderFormResult(
        emptyproperties,
        """<article><textus:line-list source="result.body.data" empty="No notices"></textus:line-list></article>"""
      ).body

      emptyhtml should include ("data-textus-widget=\"textus:line-list\"")
      emptyhtml should include ("No notices")
      emptyhtml should not include ("<textus:line-list")
    }

    "render textus editable-line-list from result row template" in {
      Given("the prerequisites for render textus editable-line-list from result row template")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "edit-notices"
        ),
        200,
        "application/json",
        """{"data":{"rows":[{"id":"row_1","label":"First","enabled":true,"mergeOptions":[{"value":"","label":"Keep as entry","selected":false},{"value":"row_2","label":"Second","selected":true}]},{"id":"row_2","label":"Second","enabled":false,"mergeOptions":[{"value":"","label":"Keep as entry","selected":true}]}],"rowsJson":"[{\"id\":\"json_1\",\"label\":\"JSON source\",\"enabled\":true,\"mergeOptions\":[{\"value\":\"\",\"label\":\"Keep as entry\",\"selected\":true}]}]"}}"""
      )

      When("render textus editable-line-list from result row template is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<table><tbody><textus:editable-line-list name="noticeRows" source="result.body.data.rows" key="id" empty="No rows" colspan="3">
          |<tr><td><input name="noticeLabel_${row.id}" value="${row.label}" data-textus-field="label"><div class="small text-secondary" data-textus-row-issue data-textus-issue-scope="row">${row.issueMessage}</div></td><td><input type="checkbox" name="noticeEnabled_${row.id}" value="true" ${row.enabled:checked}></td><td><select name="noticeMerge_${row.id}" data-textus-options="row.mergeOptions"></select></td></tr>
          |</textus:editable-line-list></tbody></table>""".stripMargin
      ).body

      Then("the observable contract for render textus editable-line-list from result row template holds")
      html should include ("data-textus-widget=\"textus:editable-line-list\"")
      html should include ("data-textus-list=\"noticeRows\"")
      html should include ("data-textus-row=\"row_1\"")
      html should include ("data-textus-row-issue")
      html should include ("data-textus-issue-scope=\"row\"")
      html should include ("name=\"noticeLabel_row_1\"")
      html should include ("value=\"First\"")
      html should include ("name=\"noticeEnabled_row_1\" value=\"true\"  checked")
      html should include ("<option value=\"row_2\" selected>Second</option>")
      html should not include ("<textus:editable-line-list")
      html should not include ("data-textus-options")

      val jsonhtml = _renderer.renderFormResult(
        properties,
        """<table><tbody><textus:editable-line-list name="jsonRows" source="result.body.data.rowsJson" key="id"><tr><td>${row.label}</td></tr></textus:editable-line-list></tbody></table>"""
      ).body
      jsonhtml should include ("JSON source")
      jsonhtml should include ("data-textus-row=\"json_1\"")

      val fallbackhtml = _renderer.renderFormResult(
        properties,
        """<table><tbody><textus:editable-line-list name="newRows" source="result.body.data.missingRows" key="id" add="true" new-rows="1"><tr><td><input name="newLabel___new_index__" value="${row.label}"></td></tr></textus:editable-line-list></tbody></table>"""
      ).body
      fallbackhtml should include ("data-textus-action=\"add-row\"")
      fallbackhtml should include ("data-textus-template=\"newRows\"")
      fallbackhtml should include ("data-textus-add-row=\"newRows\"")
      fallbackhtml should include ("data-textus-row=\"new_1\"")
      fallbackhtml should include ("name=\"newLabel_new_1\"")

      val emptyhtml = _renderer.renderFormResult(
        properties,
        """<table><tbody><textus:editable-line-list name="emptyRows" source="result.body.data.missingRows" key="id" empty="No rows" colspan="3"><tr><td>${row.label}</td></tr></textus:editable-line-list></tbody></table>"""
      ).body
      emptyhtml should include ("data-textus-widget=\"textus:editable-line-list\"")
      emptyhtml should include ("data-textus-empty-state=\"true\"")
      emptyhtml should include ("No rows")
    }

    "render table and card detail actions from record fields" in {
      Given("the prerequisites for render table and card detail actions from record fields")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body.data", "notice", "summary") -> columns)
      )

      When("render table and card detail actions from record fields is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:table source="result.body" entity="notice" view="summary" detail-href="/form/notice-board/notice/get-notice/result?id={id}" detail-param-return.href="/form/notice-board/notice/search-notices?recipientName=Bob Smith" detail-label="Read"></textus:table>
          |  <textus:card-list source="result.body" entity="notice" view="summary" detail-href="/form/notice-board/notice/get-notice/result?id={id}" detail-param-return.href="/form/notice-board/notice/search-notices?recipientName=Bob Smith" detail-label="Open notice"></textus:card-list>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render table and card detail actions from record fields holds")
      html should include ("<th>Actions</th>")
      html should include ("href=\"/form/notice-board/notice/get-notice/result?id=notice_1&amp;return.href=%2Fform%2Fnotice-board%2Fnotice%2Fsearch-notices%3FrecipientName%3DBob%20Smith\"")
      html should include ("""Read</a>""")
      html should include ("""Open notice</a>""")
      html should not include ("Static form validation")
      html should not include ("<textus:table")
      html should not include ("<textus:card-list")
    }

    "render summary card and feedback widgets" in {
      Given("the prerequisites for render summary card and feedback widgets")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "result.count" -> "42",
            "result.message" -> "Notices loaded",
            "error.message" -> "Search failed"
          )
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12"}]}"""
      )

      When("render summary card and feedback widgets is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:summary-card title="Matches" value="${result.count}" subtitle="result.message" variant="success"></textus:summary-card>
          |  <textus-alert title="Status" message="result.message" variant="info"></textus-alert>
          |  <textus:alert source="error.message" variant="error"></textus:alert>
          |  <textus:alert message="result.message" variant="unknown"></textus:alert>
          |  <textus:empty-state source="result.body" message="No notices"></textus:empty-state>
          |  <textus-empty-state source="result.missing" message="No missing records" action-label="Create notice" action-href="/form/notice-board/notice/post-notice"></textus-empty-state>
          |  <textus:status-badge value="mystery"></textus:status-badge>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render summary card and feedback widgets holds")
      html should include ("class=\"card h-100 textus-summary-card border-success\"")
      html should include ("data-textus-widget=\"textus:summary-card\"")
      html should include ("data-textus-widget=\"textus:alert\"")
      html should include ("data-textus-widget=\"textus:empty-state\"")
      html should include ("data-textus-empty-state=\"true\"")
      html should include ("data-textus-widget=\"textus:status-badge\"")
      html should include ("<strong class=\"display-6 text-success\">42</strong>")
      html should include ("Notices loaded")
      html should include ("class=\"alert alert-info textus-alert\"")
      html should include ("class=\"alert alert-danger textus-alert\"")
      html should include ("class=\"alert alert-secondary textus-alert\"")
      html should include ("Search failed")
      html should include ("No missing records")
      html should include ("""<a class="btn btn-sm btn-primary" href="/form/notice-board/notice/post-notice">Create notice</a>""")
      html should include ("""<span class="badge text-bg-secondary textus-status-badge" data-textus-widget="textus:status-badge">mystery</span>""")
      html should not include ("No notices")
      html should not include ("<textus:summary-card")
      html should not include ("<textus-alert")
      html should not include ("<textus:empty-state")
    }

    "render namespace and HTML-compatible widget notation through the same contract" in {
      Given("the prerequisites for render namespace and HTML-compatible widget notation through the same contract")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "paging.page" -> "1",
            "paging.pageSize" -> "20",
            "paging.href" -> "/form/notice-board/notice/search-notices/result?page={page}&pageSize={pageSize}",
            "result.count" -> "1",
            "result.message" -> "ready"
          )
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      When("render namespace and HTML-compatible widget notation through the same contract is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus-record-card source="result.body" columns="title,recipient_name"></textus-record-card>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |  <textus:card title="Widget Card" subtitle="result.message"><p>${result.count} record</p></textus:card>
          |  <textus-summary-card title="Matches" value="result.count"></textus-summary-card>
          |  <textus:alert message="result.message"></textus:alert>
          |  <textus:status-badge value="accepted"></textus:status-badge>
          |  <textus-pagination></textus-pagination>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render namespace and HTML-compatible widget notation through the same contract holds")
      html should include ("textus-record-card")
      html should include ("row row-cols-1 row-cols-md-2")
      html should include ("textus-card")
      html should include ("Widget Card")
      html should include ("textus-summary-card")
      html should include ("textus-alert")
      html should include ("textus-status-badge")
      html should include ("Result pages")
      html should not include ("<textus-record-card")
      html should not include ("<textus:card-list")
      html should not include ("<textus:card")
      html should not include ("<textus-summary-card")
      html should not include ("<textus:alert")
      html should not include ("<textus:status-badge")
      html should not include ("<textus-pagination")
    }

    "render html field widgets for trusted HTML fragments" in {
      Given("the prerequisites for render html field widgets for trusted HTML fragments")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties("blog", "blog", "get-post"),
        200,
        "application/json",
        """{"title":"Blog","content":"<article><p>Hello <strong>HTML</strong></p></article>"}"""
      )

      When("render html field widgets for trusted HTML fragments is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:html-field source="result.body" field="content" class="article-body"></textus:html-field>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render html field widgets for trusted HTML fragments holds")
      html should include ("class=\"article-body\"")
      html should include ("<article><p>Hello <strong>HTML</strong></p></article>")
      html should not include ("<textus:html-field")
    }

    "render nav-list widgets in button and list-group styles" in {
      Given("the prerequisites for render nav-list widgets in button and list-group styles")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "nav.search.href" -> "/form/notice-board/notice/search-notices",
            "nav.post.href" -> "/form/notice-board/notice/post-notice"
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("render nav-list widgets in button and list-group styles is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:nav-list items="Search:nav.search.href:btn btn-primary|Post:nav.post.href"></textus:nav-list>
          |  <textus-nav-list style="list" items="Search again:nav.search.href|Post notice:nav.post.href"></textus-nav-list>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render nav-list widgets in button and list-group styles holds")
      html should include ("textus-nav-list")
      html should include ("""class="btn btn-primary" href="/form/notice-board/notice/search-notices">Search</a>""")
      html should include ("""class="btn btn-outline-secondary" href="/form/notice-board/notice/post-notice">Post</a>""")
      html should include ("list-group")
      html should include ("list-group-item list-group-item-action")
      html should not include ("<textus:nav-list")
      html should not include ("<textus-nav-list")
    }

    "render nav-list widgets from JSON source links and actions" in {
      Given("the prerequisites for render nav-list widgets from JSON source links and actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices",
          Map(
            "nav.items" ->
              """[
                |{"label":"Search","href":"/form/notice-board/notice/search-notices","class":"btn btn-primary","method":"GET"},
                |{"label":"Refresh","href":"/form/notice-board/notice/search-notices","class":"btn btn-warning","method":"POST"}
                |]""".stripMargin
          )
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("render nav-list widgets from JSON source links and actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:nav-list source="nav.items"></textus:nav-list>
          |  <textus-nav-list style="list" source="nav.items"></textus-nav-list>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render nav-list widgets from JSON source links and actions holds")
      html should include ("""class="btn btn-primary" href="/form/notice-board/notice/search-notices">Search</a>""")
      html should include ("""method="post" action="/form/notice-board/notice/search-notices"""")
      html should include ("""<button type="submit" class="btn btn-warning">Refresh</button>""")
      html should include ("list-group-item list-group-item-action btn btn-primary")
      html should include ("list-group-item list-group-item-action btn btn-warning")
      html should not include ("<textus:nav-list")
      html should not include ("<textus-nav-list")
    }

    "parse widget attributes with single quotes and URL colons" in {
      Given("the prerequisites for parse widget attributes with single quotes and URL colons")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[]}"""
      )

      When("parse widget attributes with single quotes and URL colons is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:nav-list items='Docs:https://example.org:8443/docs:btn btn-primary|Forms:/form/notice-board'></textus:nav-list>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for parse widget attributes with single quotes and URL colons holds")
      html should include ("""class="btn btn-primary" href="https://example.org:8443/docs">Docs</a>""")
      html should include ("""class="btn btn-outline-secondary" href="/form/notice-board">Forms</a>""")
      html should not include ("<textus:nav-list")
    }

    "render action-card widgets from operation result actions" in {
      Given("the prerequisites for render action-card widgets from operation result actions")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice",
          Map(
            "result.action.await.href" -> "/form/notice-board/notice/post-notice/jobs/job-1/await",
            "result.action.await.label" -> "Wait for result",
            "result.action.await.method" -> "POST",
            "result.message" -> "Job accepted"
          )
        ),
        202,
        "application/json",
        """{"jobId":"job-1"}"""
      )

      When("render action-card widgets from operation result actions is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <textus:action-card source="result.action.await" title="Command result" description="result.message"></textus:action-card>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for render action-card widgets from operation result actions holds")
      html should include ("textus-action-card")
      html should include ("Command result")
      html should include ("Job accepted")
      html should include ("method=\"post\"")
      html should include ("/form/notice-board/notice/post-notice/jobs/job-1/await")
      html should not include ("<textus:action-card")
    }

    "complete local widget assets for HTML document templates that use Textus widgets" in {
      Given("the prerequisites for complete local widget assets for HTML document templates that use Textus widgets")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      When("complete local widget assets for HTML document templates that use Textus widgets is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head>
          |  <meta charset="utf-8">
          |  <title>Search result</title>
          |</head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for complete local widget assets for HTML document templates that use Textus widgets holds")
      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/bootstrap.bundle.min.js")
      html should include ("/web/assets/textus-widgets.css")
      html should include ("/web/assets/textus-widgets.js")
      html should include ("row row-cols-1 row-cols-md-2")
      html should not include ("<textus:card-list")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("</head>")
      html.indexOf("/web/assets/textus-widgets.css") should be < html.indexOf("</head>")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("/web/assets/textus-widgets.css")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("</body>")
      html.indexOf("/web/assets/textus-widgets.js") should be < html.indexOf("</body>")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("/web/assets/textus-widgets.js")
    }

    "not duplicate existing local widget assets in HTML document templates" in {
      Given("the prerequisites for not duplicate existing local widget assets in HTML document templates")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}"""
      )

      When("not duplicate existing local widget assets in HTML document templates is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head>
          |  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
          |  <link href="/web/assets/textus-widgets.css" rel="stylesheet">
          |</head>
          |<body>
          |  <textus:table source="result.body"></textus:table>
          |  <script src="/web/assets/bootstrap.bundle.min.js"></script>
          |  <script src="/web/assets/textus-widgets.js"></script>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for not duplicate existing local widget assets in HTML document templates holds")
      _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
      _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
      html should not include ("<textus:table")
    }

    "leave HTML document templates without Textus widgets unchanged by asset completion" in {
      Given("the prerequisites for leave HTML document templates without Textus widgets unchanged by asset completion")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "plain"
        ),
        200,
        "text/plain",
        "ok"
      )
      val template =
        """<!doctype html>
          |<html lang="en">
          |<head><title>Plain</title></head>
          |<body><p>No widgets</p></body>
          |</html>""".stripMargin

      When("leave HTML document templates without Textus widgets unchanged by asset completion is exercised")
      val html = _renderer.renderFormResult(properties, template).body

      Then("the observable contract for leave HTML document templates without Textus widgets unchanged by asset completion holds")
      html shouldBe template
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "honor descriptor-disabled result asset auto-completion" in {
      Given("the prerequisites for honor descriptor-disabled result asset auto-completion")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(autoComplete = false)
      )

      When("honor descriptor-disabled result asset auto-completion is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for honor descriptor-disabled result asset auto-completion holds")
      html should include ("row row-cols-1 row-cols-md-2")
      html should not include ("<textus:card-list")
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "insert descriptor-declared result assets without duplicating framework assets" in {
      Given("the prerequisites for insert descriptor-declared result assets without duplicating framework assets")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector(
            "/web/assets/bootstrap.min.css",
            "/web/assets/textus-widgets.css"
          ),
          declaredJs = Vector(
            "/web/assets/bootstrap.bundle.min.js",
            "/web/assets/textus-widgets.js"
          )
        )
      )

      When("insert descriptor-declared result assets without duplicating framework assets is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:table source="result.body"></textus:table>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for insert descriptor-declared result assets without duplicating framework assets holds")
      html should include ("<table")
      html should not include ("<textus:table")
      _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
      _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
      _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
    }

    "insert bootstrap-material profile assets after Bootstrap and Textus CSS" in {
      Given("the prerequisites for insert bootstrap-material profile assets after Bootstrap and Textus CSS")
      val html = StaticFormAppLayout.completeWidgetAssets(
        """<!doctype html>
          |<html lang="en">
          |<head><title>Material assets</title></head>
          |<body>
          |  <textus:table source="result.body"></textus:table>
          |</body>
          |</html>""".stripMargin,
        StaticFormAppLayout.AssetCompletionOptions(
          requiresBootstrap = true,
          requiresTextusWidgets = true,
          uxProfile = WebUxProfile.BootstrapMaterial
        )
      )

      When("the observable result for insert bootstrap-material profile assets after Bootstrap and Textus CSS is inspected")
      locally {
        Then("the observable contract for insert bootstrap-material profile assets after Bootstrap and Textus CSS holds")
        html should include ("/web/assets/bootstrap.min.css")
        html should include ("/web/assets/textus-widgets.css")
        html should include ("/web/assets/textus-bootstrap-material.css")
        html should include ("/web/assets/textus-material-icons.css")
        html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("/web/assets/textus-widgets.css")
        html.indexOf("/web/assets/textus-widgets.css") should be < html.indexOf("/web/assets/textus-bootstrap-material.css")
        html.indexOf("/web/assets/textus-bootstrap-material.css") should be < html.indexOf("/web/assets/textus-material-icons.css")
        _count_occurrences(html, "/web/assets/textus-bootstrap-material.css") shouldBe 1
        _count_occurrences(html, "/web/assets/textus-material-icons.css") shouldBe 1
      }
    }

    "insert descriptor app assets after framework assets in full HTML result pages" in {
      Given("the prerequisites for insert descriptor app assets after framework assets in full HTML result pages")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      When("insert descriptor app assets after framework assets in full HTML result pages is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:card-list source="result.body" columns="title,recipient_name"></textus:card-list>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for insert descriptor app assets after framework assets in full HTML result pages holds")
      html should include ("row row-cols-1 row-cols-md-2")
      html.indexOf("/web/assets/bootstrap.min.css") should be < html.indexOf("/web/assets/textus-widgets.css")
      html.indexOf("/web/assets/textus-widgets.css") should be < html.indexOf("/web/notice-board/notice-board/assets/app.css")
      html.indexOf("/web/assets/bootstrap.bundle.min.js") should be < html.indexOf("/web/assets/textus-widgets.js")
      html.indexOf("/web/assets/textus-widgets.js") should be < html.indexOf("/web/notice-board/notice-board/assets/app.js")
    }

    "insert descriptor app assets even when framework auto-completion is disabled" in {
      Given("the prerequisites for insert descriptor app assets even when framework auto-completion is disabled")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          autoComplete = false,
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      When("insert descriptor app assets even when framework auto-completion is disabled is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<!doctype html>
          |<html lang="en">
          |<head><title>Search result</title></head>
          |<body>
          |  <textus:table source="result.body"></textus:table>
          |</body>
          |</html>""".stripMargin
      ).body

      Then("the observable contract for insert descriptor app assets even when framework auto-completion is disabled holds")
      html should include ("<table")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/notice-board/notice-board/assets/app.js")
      html should not include ("/web/assets/bootstrap.min.css")
      html should not include ("/web/assets/bootstrap.bundle.min.js")
      html should not include ("/web/assets/textus-widgets.css")
      html should not include ("/web/assets/textus-widgets.js")
    }

    "insert descriptor app assets into fragment result pages" in {
      Given("the prerequisites for insert descriptor app assets into fragment result pages")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"title":"Phase12","recipient_name":"Bob"}]}""",
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector("/web/notice-board/notice-board/assets/app.css"),
          declaredJs = Vector("/web/notice-board/notice-board/assets/app.js")
        )
      )

      When("insert descriptor app assets into fragment result pages is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article>
          |  <h2>Fragment result</h2>
          |  <textus:table source="result.body"></textus:table>
          |</article>""".stripMargin
      ).body

      Then("the observable contract for insert descriptor app assets into fragment result pages holds")
      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/textus-widgets.css")
      html should include ("/web/notice-board/notice-board/assets/app.css")
      html should include ("/web/assets/bootstrap.bundle.min.js")
      html should include ("/web/assets/textus-widgets.js")
      html should include ("/web/notice-board/notice-board/assets/app.js")
      html should not include ("<textus:table")
    }

    "insert descriptor-declared widget assets once during completion" in {
      Given("the prerequisites for insert descriptor-declared widget assets once during completion")
      val html = StaticFormAppLayout.completeWidgetAssets(
        """<!doctype html>
          |<html lang="en">
          |<head><title>Declared assets</title></head>
          |<body><main class="card">Body</main></body>
          |</html>""".stripMargin,
        StaticFormAppLayout.AssetCompletionOptions(
          requiresBootstrap = true,
          requiresTextusWidgets = true,
          declaredCss = Vector(
            "/web/assets/bootstrap.min.css",
            "/web/assets/textus-widgets.css"
          ),
          declaredJs = Vector(
            "/web/assets/bootstrap.bundle.min.js",
            "/web/assets/textus-widgets.js"
          )
        )
      )

      When("the observable result for insert descriptor-declared widget assets once during completion is inspected")
      locally {
        Then("the observable contract for insert descriptor-declared widget assets once during completion holds")
        _count_occurrences(html, "/web/assets/bootstrap.min.css") shouldBe 1
        _count_occurrences(html, "/web/assets/bootstrap.bundle.min.js") shouldBe 1
        _count_occurrences(html, "/web/assets/textus-widgets.css") shouldBe 1
        _count_occurrences(html, "/web/assets/textus-widgets.js") shouldBe 1
      }
    }

    "insert descriptor-declared favicon once during completion" in {
      Given("the prerequisites for insert descriptor-declared favicon once during completion")
      val html = StaticFormAppLayout.completeDeclaredAssets(
        """<!doctype html>
          |<html lang="en">
          |<head><title>Declared favicon</title></head>
          |<body><main>Body</main></body>
          |</html>""".stripMargin,
        StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = Vector("/web/assets/site.css"),
          favicon = Some("/web/assets/favicon.svg")
        )
      )
      When("insert descriptor-declared favicon once during completion is exercised")
      val existing = StaticFormAppLayout.completeDeclaredAssets(
        html,
        StaticFormAppLayout.AssetCompletionOptions(
          favicon = Some("/web/assets/other.ico")
        )
      )

      Then("the observable contract for insert descriptor-declared favicon once during completion holds")
      html should include ("""<link rel="icon" href="/web/assets/favicon.svg">""")
      html should include ("""<link href="/web/assets/site.css" rel="stylesheet">""")
      _count_occurrences(html, """rel="icon"""") shouldBe 1
      _count_occurrences(existing, """rel="icon"""") shouldBe 1
      existing should not include ("/web/assets/other.ico")
    }

    "load packaged Textus widget assets" in {
      Given("the packaged Static Form asset bundle")
      val assets = StaticFormAppAssets

      When("the Textus widget stylesheet and script are loaded")
      val css = assets.textusWidgetsCss
      val javascript = assets.textusWidgetsJs

      Then("the packaged assets contain the widget runtime contract")
      css should include ("textus-widget")
      css should include ("textus-clickable-row")
      javascript should include ("textusWidgets")
      javascript should include ("data-textus-row-href")
      javascript should include ("data-textus-add-row")
    }

    "render textus table from result body object data" in {
      Given("the prerequisites for render textus table from result body object data")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"query":{"condition":{"recipient_name":"Bob"},"include_total":false},"data":[{"title":"Phase12","content":"Static form validation","recipient_name":"Bob"}],"fetched_count":1}"""
      )

      When("render textus table from result body object data is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.body"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table from result body object data holds")
      html should include ("<table")
      html should include ("<th>title</th>")
      html should include ("<th>content</th>")
      html should include ("<td>Phase12</td>")
      html should include ("<td>Static form validation</td>")
      html should include ("Page 1")
      html should not include ("<textus:table")
    }

    "render textus table with explicit columns" in {
      Given("the prerequisites for render textus table with explicit columns")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","rights":{"owner":{"read":true}}}]}"""
      )

      When("render textus table with explicit columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data" columns="sender_name,subject"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table with explicit columns holds")
      html should include ("<th>sender_name</th><th>subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("rights")
      html should not include ("<textus:table")
    }

    "render textus description list with explicit columns" in {
      Given("the prerequisites for render textus description list with explicit columns")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "get-notice"
        ),
        200,
        "application/json",
        """{"id":"notice_1","title":"Phase12","content":"Static form detail","recipient_name":"Bob"}"""
      )

      When("render textus description list with explicit columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:description-list source="result.body" columns="title:Title,recipient_name:Recipient"></textus:description-list></article>"""
      ).body

      Then("the observable contract for render textus description list with explicit columns holds")
      html should include ("textus-description-list")
      html should include ("<dt class=\"col-sm-4\">Title</dt><dd class=\"col-sm-8\">Phase12</dd>")
      html should include ("<dt class=\"col-sm-4\">Recipient</dt><dd class=\"col-sm-8\">Bob</dd>")
      html should not include ("Static form detail")
      html should not include ("notice_1")
      html should not include ("<textus:description-list")
    }

    "render textus table from result body with CML summary columns" in {
      Given("the prerequisites for render textus table from result body with CML summary columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("title", "Title"),
        StaticFormAppRenderer.TableColumn("recipient_name", "Recipient")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","title":"Phase12","content":"Static form validation","recipient_name":"Bob"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.body.data", "notice", "summary") -> columns)
      )

      When("render textus table from result body with CML summary columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.body" entity="notice" view="summary"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table from result body with CML summary columns holds")
      html should include ("<th>Title</th><th>Recipient</th>")
      html should include ("<td>Phase12</td>")
      html should include ("<td>Bob</td>")
      html should not include ("notice_1")
      html should not include ("Static form validation")
      html should not include ("<textus:table")
    }

    "render textus table with resolved CML table columns" in {
      Given("the prerequisites for render textus table with resolved CML table columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender"),
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","rights":{"owner":{"read":true}}}]}""",
        Map("result.data" -> columns)
      )

      When("render textus table with resolved CML table columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table with resolved CML table columns holds")
      html should include ("<th>Sender</th><th>Subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("rights")
      html should not include ("<textus:table")
    }

    "render textus table with explicit entity and view columns" in {
      Given("the prerequisites for render textus table with explicit entity and view columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender"),
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice","body":"hidden"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.data", "notice", "summary") -> columns)
      )

      When("render textus table with explicit entity and view columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data" entity="notice" view="summary"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table with explicit entity and view columns holds")
      html should include ("<th>Sender</th><th>Subject</th>")
      html should include ("<td>alice</td>")
      html should include ("<td>Hello</td>")
      html should not include ("notice_1")
      html should not include ("hidden")
      html should not include ("<textus:table")
    }

    "render textus table with descriptor default view columns" in {
      Given("the prerequisites for render textus table with descriptor default view columns")
      val columns = Vector(
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice"}]}""",
        Map(StaticFormAppRenderer.tableColumnKey("result.data", "notice", "card") -> columns),
        defaultTableView = "card"
      )

      When("render textus table with descriptor default view columns is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data" entity="notice"></textus:table></article>"""
      ).body

      Then("the observable contract for render textus table with descriptor default view columns holds")
      html should include ("<th>Subject</th>")
      html should include ("<td>Hello</td>")
      html should not include ("alice")
      html should not include ("<textus:table")
    }

    "let textus table view attribute override descriptor default view" in {
      Given("the prerequisites for let textus table view attribute override descriptor default view")
      val summarycolumns = Vector(
        StaticFormAppRenderer.TableColumn("sender_name", "Sender")
      )
      val cardcolumns = Vector(
        StaticFormAppRenderer.TableColumn("subject", "Subject")
      )
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "search-notices"
        ),
        200,
        "application/json",
        """{"data":[{"id":"notice_1","subject":"Hello","sender_name":"alice"}]}""",
        Map(
          StaticFormAppRenderer.tableColumnKey("result.data", "notice", "summary") -> summarycolumns,
          StaticFormAppRenderer.tableColumnKey("result.data", "notice", "card") -> cardcolumns
        ),
        defaultTableView = "card"
      )

      When("let textus table view attribute override descriptor default view is exercised")
      val html = _renderer.renderFormResult(
        properties,
        """<article><textus:table source="result.data" entity="notice" view="summary"></textus:table></article>"""
      ).body

      Then("the observable contract for let textus table view attribute override descriptor default view holds")
      html should include ("<th>Sender</th>")
      html should include ("<td>alice</td>")
      html should not include ("Hello")
      html should not include ("<textus:table")
    }

    "render form result properties with error panel" in {
      Given("the prerequisites for render form result properties with error panel")
      val properties = StaticFormAppRenderer.FormResultProperties(
        StaticFormAppRenderer.FormPageProperties(
          "notice-board",
          "notice",
          "post-notice"
        ),
        500,
        "text/plain",
        "boom"
      )

      When("render form result properties with error panel is exercised")
      val html = _renderer.renderFormResult(properties).body

      Then("the observable contract for render form result properties with error panel holds")
      html should include ("error.status")
      html should include ("500")
      html should include ("boom")
      html should include ("result.ok")
      html should include ("false")
      html should not include ("<textus-error-panel")
    }

    "provide local Bootstrap 5 assets" in {
      Given("the packaged Static Form asset bundle")
      val assets = StaticFormAppAssets

      When("the local Bootstrap version, stylesheet, and bundle are loaded")
      val version = assets.bootstrapVersion
      val css = assets.bootstrapCss
      val javascript = assets.bootstrapBundleJs

      Then("the complete Bootstrap 5.3.3 contract is available without a CDN")
      version shouldBe "5.3.3"
      css should include ("Bootstrap")
      css should include ("v5.3.3")
      css should include (".card")
      css should include (".row")
      css should include (".form-control")
      css should include (".table-responsive")
      css should not include ("cdn.jsdelivr")
      javascript should include ("Bootstrap")
      javascript should include ("v5.3.3")
      javascript should include ("bootstrap=e()")
      javascript should include ("Dropdown")
      javascript should not include ("cdn.jsdelivr")
    }

    "render descriptor-declared component admin pages on component admin home" in {
      Given("the prerequisites for render descriptor-declared component admin pages on component admin home")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        profile = Some(WebUxProfile.Material),
        profileRaw = Some("material"),
        adminPages = Vector(WebDescriptor.AdminPage(
          name = "notifications",
          label = "Notification Admin",
          href = s"/web/${componentpath}/admin/notifications",
          description = "Manage notification records.",
          permission = Some("admin.entity.read"),
          component = Some(componentpath)
        ))
      )

      When("render descriptor-declared component admin pages on component admin home is exercised")
      val html = _renderer.renderComponentAdmin(subsystem, componentpath, descriptor).map(_.body).getOrElse(fail("component admin is missing"))

      Then("the observable contract for render descriptor-declared component admin pages on component admin home holds")
      html should include ("Component Admin Pages")
      html should include ("Notification Admin")
      html should include (s"""href="/web/${componentpath}/admin/notifications"""")
      html should include ("Manage notification records.")
      html should include ("admin.entity.read")
      html should include ("list-group")
      html should include ("data-textus-ux-profile=\"material\"")
    }

    "render Application Admin separately from System Admin diagnostics" in {
      Given("the prerequisites for render Application Admin separately from System Admin diagnostics")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        adminPages = Vector(
          WebDescriptor.AdminPage(
            name = "notifications",
            label = "Notification Admin",
            href = s"/web/${componentpath}/admin/notifications",
            description = "Manage notification records.",
            permission = Some("admin.entity.read"),
            component = Some(componentpath),
            audience = WebDescriptor.AdminAudience.Application,
            audienceRaw = Some("application")
          ),
          WebDescriptor.AdminPage(
            name = "runtime-probe",
            label = "Runtime Probe",
            href = s"/web/${componentpath}/admin/runtime-probe",
            description = "Inspect runtime-only probe state.",
            permission = Some("admin.system.read"),
            component = Some(componentpath),
            audience = WebDescriptor.AdminAudience.System,
            audienceRaw = Some("system")
          )
        )
      )

      val apphtml = _renderer.renderApplicationAdmin(subsystem, descriptor).body
      When("render Application Admin separately from System Admin diagnostics is exercised")
      val systemhtml = _renderer.renderSystemAdmin(subsystem, descriptor).body

      Then("the observable contract for render Application Admin separately from System Admin diagnostics holds")
      apphtml should include ("Application Admin")
      apphtml should include ("Notification Admin")
      apphtml should include (s"""/web/${componentpath}/admin""")
      apphtml should include ("System admin")
      apphtml should not include ("Runtime Probe")
      apphtml should not include ("Runtime Configuration")
      apphtml should not include ("Assembly report")
      apphtml should not include ("Execution history")
      systemhtml should include ("""href="/web/admin"""")
      systemhtml should include ("Application admin")
      systemhtml should include ("Runtime Configuration")
      systemhtml should include ("Assembly report")
      systemhtml should include ("Runtime Probe")
    }

    "provide Bootstrap 5 by default in the Static Form App layout" in {
      Given("the prerequisites for provide Bootstrap 5 by default in the Static Form App layout")
      val html = StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
        title = "Sample Form App",
        subtitle = "Sample",
        body = """<form><input class="form-control"></form>"""
      ))

      When("the observable result for provide Bootstrap 5 by default in the Static Form App layout is inspected")
      locally {
        Then("the observable contract for provide Bootstrap 5 by default in the Static Form App layout holds")
        html should include ("/web/assets/bootstrap.min.css")
        html should include ("/web/assets/bootstrap.bundle.min.js")
        html should include ("<form><input class=\"form-control\"></form>")
        html should not include ("cdn.jsdelivr")
      }
    }

    "keep WEB-10 built-in pages offline-ready and responsive" in {
      Given("the prerequisites for keep WEB-10 built-in pages offline-ready and responsive")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      When("keep WEB-10 built-in pages offline-ready and responsive is exercised")
      val pages = Vector(
        _renderer.renderSubsystemDashboard(subsystem).body,
        _renderer.renderSystemAdmin(subsystem).body,
        _renderer.renderSystemPerformance(subsystem).body,
        _renderer.renderSystemManual(subsystem).body,
        _renderer.renderComponentManual(subsystem, componentpath).map(_.body).getOrElse(fail("component manual is missing")),
        _renderer.renderComponentAdmin(subsystem, componentpath).map(_.body).getOrElse(fail("component admin is missing"))
      )

      Then("the observable contract for keep WEB-10 built-in pages offline-ready and responsive holds")
      pages.foreach { html =>
        html should include ("""<meta name="viewport" content="width=device-width, initial-scale=1">""")
        html should include ("/web/assets/bootstrap.min.css")
        html should include ("/web/assets/bootstrap.bundle.min.js")
        html should not include ("cdn.jsdelivr")
      }
      pages.mkString("\n") should include ("table-responsive")
      pages.mkString("\n") should include ("d-flex flex-wrap")
      pages.mkString("\n") should include ("card")
      pages.mkString("\n") should include ("shadow-sm")
    }
    }
  }

  private def _count_occurrences(
    text: String,
    needle: String
  ): Int =
    if (needle.isEmpty)
      0
    else
      text.sliding(needle.length).count(_ == needle)

  private def _dashboard_state_json(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    componentname: Option[String]
  ): Json =
    _renderer.renderDashboardState(subsystem, componentname) match {
      case Some(page) =>
        parse(page.body).fold(
          err => fail(s"dashboard state is not valid JSON: ${err.getMessage}"),
          identity
        )
      case None =>
        fail(s"dashboard state not found: ${componentname.getOrElse("system")}")
    }

  private def _post_form_request(
    path: String,
    body: String,
    csrfvalue: Option[String] = Some(_test_csrf_token),
    csrfcookie: Option[String] = None
  ): Request[IO] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(path)
    )
    if (!path.startsWith("/form/") || path.startsWith("/form-api/"))
      request.withEntity(body)
    else {
      csrfvalue match {
        case Some(token) =>
          val fields = body.split("&", -1).toVector.filterNot(_.startsWith("csrf="))
          val effectivebody = (fields :+ s"csrf=${token}").filter(_.nonEmpty).mkString("&")
          val cookie = csrfcookie.getOrElse(token)
          request.withEntity(effectivebody).putHeaders(org.http4s.Header.Raw(
            org.typelevel.ci.CIString("Cookie"),
            s"${WebCsrf.cookieName}=${cookie}"
          ))
        case None =>
          request.withEntity(body)
      }
    }
  }

  private def _post_multipart_request(
    path: String,
    fields: Vector[(String, String)],
    files: Vector[(String, String, String, Array[Byte])]
  ): Request[IO] = {
    val boundary = s"----cncf-test-${java.util.UUID.randomUUID().toString.replace("-", "")}"
    val effectivefields =
      if (path.startsWith("/form/") && !path.startsWith("/form-api/"))
        fields.filterNot(_._1 == "csrf") :+ ("csrf" -> _test_csrf_token)
      else
        fields
    val fieldparts = effectivefields.map { case (name, value) =>
      s"--${boundary}\r\nContent-Disposition: form-data; name=\"${name}\"\r\n\r\n${value}\r\n".getBytes(StandardCharsets.UTF_8)
    }
    val fileparts = files.map { case (name, filename, contentType, bytes) =>
      val header =
        s"--${boundary}\r\nContent-Disposition: form-data; name=\"${name}\"; filename=\"${filename}\"\r\nContent-Type: ${contentType}\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8)
      header ++ bytes ++ "\r\n".getBytes(StandardCharsets.UTF_8)
    }
    val trailer = s"--${boundary}--\r\n".getBytes(StandardCharsets.UTF_8)
    val body = (fieldparts ++ fileparts).foldLeft(Array.emptyByteArray)(_ ++ _) ++ trailer
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(path),
      body = fs2.Stream.emits(body).covary[IO]
    ).putHeaders(
      org.http4s.headers.`Content-Type`.parse(s"multipart/form-data; boundary=${boundary}").toOption.get
    )
    if (path.startsWith("/form/") && !path.startsWith("/form-api/"))
      request.putHeaders(org.http4s.Header.Raw(
        org.typelevel.ci.CIString("Cookie"),
        s"${WebCsrf.cookieName}=${_test_csrf_token}"
      ))
    else
      request
  }

  private def _get_request(
    path: String
  ): Request[IO] =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(path)
    )

  private def _head_request(
    path: String
  ): Request[IO] =
    Request[IO](
      method = Method.HEAD,
      uri = Uri.unsafeFromString(path)
    )

  private def _with_session(
    req: Request[IO],
    sessionid: String
  ): Request[IO] =
    req.putHeaders(
      org.http4s.Header.Raw(org.typelevel.ci.CIString("X-Textus-Session"), sessionid)
    )

  private def _install_auth_session(
    subsystem: Subsystem,
    summary: AuthComponent.SessionSummary
  ): Unit =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH).foreach { component =>
      component.withPort(Component.Port.of(new AuthComponent.AuthService {
        def login(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          Consequence.success(summary)

        def logout(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.LogoutSummary] =
          Consequence.success(AuthComponent.LogoutSummary(loggedOut = true, request.sessionId))

        def currentSession(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          if (request.sessionId.contains(summary.sessionId.getOrElse("")))
            Consequence.success(summary)
          else
            Consequence.success(_anonymous_session_summary)
      }))
    }

  private def _install_echoing_auth_session(
    subsystem: Subsystem,
    summary: AuthComponent.SessionSummary
  ): Unit =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH).foreach { component =>
      component.withPort(Component.Port.of(new AuthComponent.AuthService {
        def login(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          Consequence.success(summary.copy(attributes = summary.attributes ++ request.attributes))

        def logout(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.LogoutSummary] =
          Consequence.success(AuthComponent.LogoutSummary(loggedOut = true, request.sessionId))

        def currentSession(
          request: AuthenticationRequest
        )(using ExecutionContext): Consequence[AuthComponent.SessionSummary] =
          if (request.sessionId.contains(summary.sessionId.getOrElse("")))
            Consequence.success(summary.copy(attributes = summary.attributes ++ request.attributes))
          else
            Consequence.success(_anonymous_session_summary)
      }))
    }

  private def _session_summary(
    sessionid: String,
    principalid: String,
    attributes: Map[String, String]
  ): AuthComponent.SessionSummary =
    AuthComponent.SessionSummary(
      sessionId = Some(sessionid),
      principalId = Some(principalid),
      subjectKind = "User",
      securityLevel = attributes.getOrElse("privilege", "user"),
      capabilities = Vector.empty,
      authenticated = true,
      attributes = attributes
    )

  private def _anonymous_session_summary: AuthComponent.SessionSummary =
    AuthComponent.SessionSummary(
      sessionId = None,
      principalId = Some("anonymous"),
      subjectKind = "Anonymous",
      securityLevel = "anonymous",
      capabilities = Vector.empty,
      authenticated = false,
      attributes = Map.empty
    )

  private def _structured_conclusion(): Conclusion =
    Conclusion.simple("coded failure")

  private final case class DataFixture(
    subsystem: Subsystem,
    runtime: GlobalRuntimeContext,
    datastorespace: DataStoreSpace
  )

  private def _data_fixture(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Supported
  ): DataFixture = {
    val datastorespace = _data_store_space(totalcountcapability)
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId("audit")
    val _ = datastorespace.inject(cid, Record.create(Vector(
      "id" -> "audit_1",
      "action" -> "created",
      "actor" -> "alice"
    )))
    val _ = datastorespace.inject(cid, Record.create(Vector(
      "id" -> "audit_existing",
      "action" -> "updated",
      "actor" -> "bob"
    )))
    val runtime = GlobalRuntimeContext.create(
      "data-admin-test",
      RuntimeConfig.default.copy(dataStoreSpace = datastorespace),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
      org.goldenport.cncf.context.ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val component = TestComponentFactory.create("notice_board", Protocol.empty)
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    DataFixture(subsystem, runtime, datastorespace)
  }

  private def _data_store_space(
    totalcountcapability: TotalCountCapability
  ): DataStoreSpace =
    totalcountcapability match {
      case TotalCountCapability.Supported =>
        DataStoreSpace.default()
      case other =>
        new DataStoreSpace().addDataStore(
          new TotalCountCapabilityDataStore(DataStore.inMemorySearchable(), other)
        )
    }

  private final class TotalCountCapabilityDataStore(
    delegate: SearchableDataStore,
    capability: TotalCountCapability
  ) extends SearchableDataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean =
      delegate.isAccept(cid)

    def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)

    def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)

    def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)

    def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)

    def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)

    def search(
      collection: DataStore.CollectionId,
      directive: QueryDirective
    ): Consequence[SearchResult] =
      delegate.search(collection, directive)

    override def totalCountCapability(collection: DataStore.CollectionId): TotalCountCapability =
      capability

    def prepare(tx: TransactionContext): PrepareResult =
      delegate.prepare(tx)

    def commit(tx: TransactionContext): Unit =
      delegate.commit(tx)

    def abort(tx: TransactionContext): Unit =
      delegate.abort(tx)
  }

  private def _with_global_runtime[A](
    runtime: GlobalRuntimeContext
  )(body: => A): A = {
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(runtime)
    try {
      body
    } finally {
      GlobalRuntimeContext.current = previous
    }
  }

  private def _load_data_record(
    space: DataStoreSpace,
    collection: String,
    id: String
  ): Record = {
    given org.goldenport.cncf.context.ExecutionContext = org.goldenport.cncf.context.ExecutionContext.create()
    val cid = DataStore.CollectionId(collection)
    (for {
      ds <- space.dataStore(cid)
      entry <- DataStore.EntryId.parse(id)
      record <- ds.load(cid, entry).map(_.getOrElse(Record.empty))
    } yield record).toOption.getOrElse(Record.empty)
  }

  private def _json_fields(
    json: Json
  ): Vector[Json] =
    json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)

  private def _json_field_names(
    fields: Vector[Json]
  ): Vector[String] =
    fields.flatMap(_.hcursor.downField("name").as[String].toOption)

  private def _json_field(
    fields: Vector[Json],
    name: String
  ): HCursor =
    fields.
      find(_.hcursor.downField("name").as[String].toOption.contains(name)).
      map(_.hcursor).
      getOrElse(fail(s"$name field is missing"))

  private def _admin_record_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Record = {
    val response = _admin_response(subsystem, service, operation, args*)
    response.toOption.collect {
      case OperationResponse.RecordResponse(record) => record
    }.getOrElse(fail(s"admin.${service}.${operation} did not return RecordResponse: ${response}"))
  }

  private def _tag_record_response(
    subsystem: Subsystem,
    operation: String,
    args: (String, String)*
  ): Record = {
    val request = GRequest.of(
      component = BuiltinComponentIdentity.TAG.name,
      service = "tag",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList
    )
    val response = subsystem.executeOperationResponse(request)
    response.toOption.collect {
      case OperationResponse.RecordResponse(record) => record
    }.getOrElse(fail(s"tag.tag.${operation} did not return RecordResponse: ${response}"))
  }

  private def _page_body(
    consequence: Consequence[StaticFormAppRenderer.Page],
    label: String
  ): String =
    consequence match {
      case Consequence.Success(page) => page.body
      case Consequence.Failure(conclusion) => fail(s"$label is missing: ${conclusion.show}")
    }

  private def _admin_response(
    subsystem: Subsystem,
    service: String,
    operation: String,
    args: (String, String)*
  ): Consequence[OperationResponse] = {
    val request = GRequest.of(
      component = BuiltinComponentIdentity.ADMIN.name,
      service = service,
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList
    )
    subsystem.executeOperationResponse(request)
  }

  private def _view_fixture_subsystem(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Unsupported,
    backingentityname: String = "notice",
    ambiguousbacking: Boolean = false
  ): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "notice_view",
            entityName = backingentityname,
            viewNames = Vector("default"),
            queries = Vector(ViewQueryDefinition("recent", Some("notice.updatedAt desc")))
          )
        )
    }
    _initialize_component("notice_board", component)
    given EntityPersistent[NoticeEntity] = _notice_persistent
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = NoticeEntity.collectionid,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "note"))
          )
        )
      )
    ))
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(Vector(NoticeEntity(_notice_entity_id_from_shortid("notice_1"), "notice", "view")))
    )
    if (ambiguousbacking)
      component.entitySpace.registerEntity(
        "notice",
        _notice_collection(Vector.empty, EntityCollectionId("sample", "other", "notice"))
      )
    val collection = new ViewCollection[String](
      new ViewBuilder[String] {
        def build(id: EntityId): Consequence[String] =
          Consequence.success(s"notice detail ${id.parts.entropy}")
      }
    )
    val browser = Browser.from(
      collection,
      _ => Consequence.success(Vector("notice summary", "notice next")),
      countfn = if (totalcountcapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
      totalCountCapabilityValue = totalcountcapability
    )
    component.viewSpace.register("notice_view", collection, browser)
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private def _aggregate_fixture_subsystem(
    totalcountcapability: TotalCountCapability = TotalCountCapability.Unsupported,
    backingentityname: String = "notice",
    ambiguousbacking: Boolean = false
  ): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = backingentityname,
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_protocol())
    given EntityPersistent[NoticeEntity] = _notice_persistent
    component.withComponentDescriptors(Vector(
      ComponentDescriptor(
        componentName = Some("notice_board"),
        entityRuntimeDescriptors = Vector(
          EntityRuntimeDescriptor(
            entityName = "notice",
            collectionId = NoticeEntity.collectionid,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100,
            schema = Some(_schema("id", "label", "status"))
          )
        )
      )
    ))
    val aggregate = NoticeAggregate(_notice_entity_id_from_shortid("notice_1"), "notice aggregate")
    val nextaggregate = NoticeAggregate(_notice_entity_id_from_shortid("notice_2"), "notice next")
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(Vector(NoticeEntity(aggregate.id, "notice aggregate", "aggregate")))
    )
    if (ambiguousbacking)
      component.entitySpace.registerEntity(
        "notice",
        _notice_collection(Vector.empty, EntityCollectionId("sample", "other", "notice"))
      )
    component.aggregateSpace.register(
      "notice_aggregate",
      new AggregateCollection[NoticeAggregate](
        new AggregateBuilder[NoticeAggregate] {
          def build(id: EntityId): Consequence[NoticeAggregate] =
            Consequence.success(aggregate)
        },
        q => Consequence.success(org.goldenport.cncf.directive.Query.sliceValues(Vector(aggregate, nextaggregate), q.offset, q.limit)),
        countfn = if (totalcountcapability.supportsTotalCount) Some(_ => Consequence.success(2)) else None,
        totalCountCapabilityValue = totalcountcapability
      )
    )
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private def _aggregate_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("read-notice-aggregate"),
                NoopOperation("create-notice-aggregate"),
                NoopOperation("approve-notice-aggregate", Vector("id"))
              )
            )
          )
        )
      )
    )

  private def _form_type_fixture_subsystem(): Subsystem = {
    val component = new org.goldenport.cncf.component.Component() {}
    _initialize_component("notice_board", component, _form_type_protocol())
    HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
  }

  private def _validation_hints_fixture(): (Subsystem, WebDescriptor) = {
    val component = new org.goldenport.cncf.component.Component() {}
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("validate-hints", Vector("code", "count"))
              )
            )
          )
        )
      )
    )
    _initialize_component("notice_board", component, protocol)
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    val selector = "notice-board.notice.validate-hints"
    val descriptor = WebDescriptor(
      expose = Map(selector -> WebDescriptor.Exposure.Protected),
      form = Map(selector -> WebDescriptor.Form(
        controls = Map(
          "code" -> WebDescriptor.FormControl(
            validation = WebValidationHints(minLength = Some(1), maxLength = Some(4))
          ),
          "count" -> WebDescriptor.FormControl(
            validation = WebValidationHints(min = Some(BigDecimal(-10)), max = Some(BigDecimal(200)))
          )
        )
      ))
    )
    subsystem -> descriptor
  }

  private def _form_type_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                NoopOperation("post-secret-notice", Vector("body", "accessToken"))
              )
            )
          )
        )
      )
    )

  private def _aggregate_http_fixture_subsystem(
    configuration: Configuration = Configuration.empty,
    messagecatalogs: Vector[WebMessageCatalog] = Vector.empty
  ): Subsystem = {
    var ownersubsystem: Option[Subsystem] = None
    val component = new org.goldenport.cncf.component.Component() {
      override def subsystem: Option[Subsystem] = ownersubsystem
      override def webMessageCatalogs: Vector[WebMessageCatalog] = messagecatalogs

      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "notice_aggregate",
            entityName = "notice",
            members = Vector(AggregateMemberDefinition("notice", "notice")),
            creates = Vector(AggregateCreateDefinition("create-notice-aggregate")),
            commands = Vector(AggregateCommandDefinition("approve-notice-aggregate"))
          )
        )
    }
    _initialize_component("notice_board", component, _aggregate_http_protocol())
    val subsystem = new Subsystem(
      name = "sample-web",
      configuration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    )
    ownersubsystem = Some(subsystem)
    subsystem.add(Vector(component))
    HttpRuntimeBindingAdmissionFixture.admit(subsystem)
    subsystem
  }

  private def _with_multi_user_authentication(subsystem: Subsystem): Subsystem = {
    val ownersubsystem = subsystem
    val provider = new Component {
      override val core: Component.Core = Component.Core.create(
        "org.goldenport.cncf.test.StaticWebAuthentication",
        ComponentId("org.goldenport.cncf.test.StaticWebAuthentication"),
        ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.StaticWebAuthentication")),
        Protocol.empty
      )
      override def subsystem: Option[Subsystem] = Some(ownersubsystem)
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(new AuthenticationProvider {
        override val name: String = "static-web-authentication"
        override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
          Consequence.success(
            request.sessionId
              .map(id => AuthenticationResult(PrincipalId(s"static-web-$id")))
              .orElse(request.accessToken.map(id => AuthenticationResult(PrincipalId(s"static-web-$id"))))
          )
        override def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
          authenticate(request)
      })
    }
    provider.withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = "StaticWebAuthentication",
      version = "0.0.0",
      component = Some("static-web-authentication")
    ))
    subsystem.add(provider)
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = Path.of("build.sbt").toAbsolutePath,
      subsystemName = "sample-web",
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(
        GenericSubsystemAuthenticationBinding(
          convention = Some("disabled"),
          fallbackPrivilege = Some("disabled"),
          providers = Vector(GenericSubsystemAuthenticationProviderBinding(
            name = "static-web-authentication",
            component = "static-web-authentication",
            enabled = Some(true)
          ))
        )
      )))
    ))
    subsystem
  }

  private def _aggregate_http_fixture_subsystem_with_componentlet_metadata_only(
    configuration: Configuration = Configuration.empty
  ): Subsystem = {
    val subsystem = _aggregate_http_fixture_subsystem(configuration)
    subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).foreach { component =>
      val componentlets = Vector(
        ComponentletDescriptor(
          name = "notice-admin",
          kind = Some("componentlet"),
          archiveScope = Some("car-bundled")
        )
      )
      component.withComponentDescriptors(
        if (component.componentDescriptors.nonEmpty)
          component.componentDescriptors.map(_.copy(componentlets = componentlets))
        else
          Vector(ComponentDescriptor(
            name = Some(component.name),
            componentName = Some(component.name),
            componentlets = componentlets
          ))
      )
    }
    subsystem
  }

  private def _aggregate_http_fixture_subsystem_with_componentlets(
    configuration: Configuration = Configuration.empty
  ): Subsystem = {
    val subsystem = _aggregate_http_fixture_subsystem_with_componentlet_metadata_only(configuration)
    val component = new org.goldenport.cncf.component.Component() {}
    _initialize_component_with_id("notice-admin", "notice_admin", component, _aggregate_http_protocol())
    subsystem.add(Vector(component))
  }

  private def _web_template_fixture_root(
    filename: String,
    content: String
  ): java.nio.file.Path = {
    val root = Files.createTempDirectory("cncf-web-template-")
    Files.writeString(root.resolve("web.yaml"), "form: {}\n", StandardCharsets.UTF_8)
    Files.writeString(root.resolve(filename), content, StandardCharsets.UTF_8)
    root
  }

  private def _web_archive_fixture(
    filename: String,
    entries: Vector[(String, String)]
  ): Path = {
    val root = Files.createTempDirectory("cncf-web-archive-")
    val path = root.resolve(filename)
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach {
        case (name, content) =>
          out.putNextEntry(new ZipEntry(name))
          out.write(content.getBytes(StandardCharsets.UTF_8))
          out.closeEntry()
      }
    } finally {
      out.close()
    }
    path
  }

  private def _aggregate_http_protocol(): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice-aggregate",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                SuccessfulAggregateOperation("create-notice-aggregate", "title", "aggregate-created"),
                SuccessfulAggregateOperation("approve-notice-aggregate", "id", "aggregate-updated")
              )
            )
          )
        )
      ),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector(RestIngress())),
        egresses = EgressCollection(Vector(RestEgress())),
        projections = ProjectionCollection()
      )
    )

  private def _initialize_component(
    name: String,
    component: org.goldenport.cncf.component.Component,
    protocol: Protocol = Protocol.empty
  ): org.goldenport.cncf.component.Component = {
    _initialize_component_with_id(name, name, component, protocol)
  }

  private def _initialize_component_with_id(
    name: String,
    componentidname: String,
    component: org.goldenport.cncf.component.Component,
    protocol: Protocol = Protocol.empty,
    origin: org.goldenport.cncf.component.ComponentOrigin = org.goldenport.cncf.component.ComponentOrigin.Builtin
  ): org.goldenport.cncf.component.Component = {
    val componentid = TestComponentFactory.componentId(name)
    val instanceid = org.goldenport.cncf.component.ComponentInstanceId(componentid, componentidname)
    val factory = new org.goldenport.cncf.component.Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: org.goldenport.cncf.component.ComponentCreate): org.goldenport.cncf.component.Component =
        component

      override protected def create_Core(
        params: org.goldenport.cncf.component.ComponentCreate,
        comp: org.goldenport.cncf.component.Component
      ): org.goldenport.cncf.component.Component.Core =
        org.goldenport.cncf.component.Component.Core.create(componentid.name, componentid, instanceid, protocol, this)
    }
    val core = org.goldenport.cncf.component.Component.Core.create(componentid.name, componentid, instanceid, protocol, factory)
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        TestComponentFactory.emptySubsystem("test"),
        core,
        origin
      )
    )
  }

  private def _management_console_fixture_subsystem(
    configuration: Configuration = Configuration.empty,
    schema: Schema = _schema("id", "title", "author"),
    viewfields: Map[String, Vector[String]] = Map.empty,
    relationships: Vector[CmlEntityRelationshipDefinition] = Vector.empty
  ): Subsystem = {
    val resolvedconfiguration = ResolvedConfiguration(configuration, ConfigurationTrace.empty)
    val runtimeconfig = RuntimeConfig.default.copy(
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = EntityStoreSpace.create(resolvedconfiguration)
    )
    val runtime = GlobalRuntimeContext.create(
      "static-form-app-renderer-spec",
      runtimeconfig,
      resolvedconfiguration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    given EntityPersistent[NoticeEntity] = _notice_persistent
    val cid = NoticeEntity.collectionid
    val descriptor = ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = cid,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(schema),
          revisionModelKind = Some(EntityRevisionModelKind.NonSimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Detached)
        )
      )
    )
    val component =
      if (viewfields.isEmpty && relationships.isEmpty) {
        TestComponentFactory.create("notice_board", Protocol.empty)
      } else {
        val c = new org.goldenport.cncf.component.Component() {
          override def viewDefinitions: Vector[ViewDefinition] =
            Vector(
              ViewDefinition(
                name = "notice_view",
                entityName = "notice",
                viewNames = viewfields.keys.toVector,
                viewFields = viewfields
              )
            )
          override def relationshipDefinitions: Vector[CmlEntityRelationshipDefinition] =
            relationships
        }
        _initialize_component("notice_board", c, Protocol.empty)
      }
    component.withComponentDescriptors(Vector(descriptor))
    val notices = Vector(
      NoticeEntity(
        _new_notice_entity_id(),
        "board update",
        "alice"
      ),
      NoticeEntity(
        _new_notice_entity_id(),
        "board followup",
        "bob"
      )
    )
    component.entitySpace.registerEntity(
      "notice",
      _notice_collection(notices)
    )
    val subsystem = HttpRuntimeBindingAdmissionFixture.defaultWithScope(
      runtime,
      Some(org.goldenport.cncf.cli.RunMode.Server),
      resolvedconfiguration
    ).add(Vector(component))
    given ExecutionContext =
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
        .getOrElse(fail("admin component is missing"))
        .logic.executionContext()
    EntityRevisionSpecSupport.registerRevisionBinding(
      summon[ExecutionContext],
      cid,
      _notice_persistent,
      EntityRevisionRepresentation.Detached
    )
    val noticecollection =
      component.entitySpace.entity[NoticeEntity]("notice")
    notices.foreach { notice =>
      val authorization =
        org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("notice"),
          collectionName = Some(cid.name),
          targetId = Some(notice.id),
          accessKind = "update",
          accessMode = org.goldenport.cncf.security.EntityAccessMode.System
        )
      new org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter(
        new org.goldenport.cncf.unitofwork.UnitOfWork(summon[ExecutionContext])
      ).interpret(
        org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreSaveUnversioned(
          notice,
          org.goldenport.cncf.entity.EntityUnversionedMutationPurpose.SeedImport,
          _notice_persistent,
          Some(authorization)
        )
      ).getOrElse(fail(s"notice fixture seed failed: ${notice.id.print}"))
      noticecollection.put(notice)
    }
    subsystem
  }

  private def _embedded_revision_fixture_subsystem(): Subsystem = {
    val resolvedconfiguration =
      ResolvedConfiguration(
        Configuration.empty,
        ConfigurationTrace.empty
      )
    val runtimeconfig = RuntimeConfig.default.copy(
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace =
        EntityStoreSpace.create(resolvedconfiguration)
    )
    val runtime = GlobalRuntimeContext.create(
      "embedded-revision-admin-spec",
      runtimeconfig,
      resolvedconfiguration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val component =
      TestComponentFactory.create(
        "embedded_notice_board",
        Protocol.empty
      )
    val persistent = _embedded_notice_persistent
    val cid = EmbeddedNoticeEntity.collectionid
    val descriptor = ComponentDescriptor(
      componentName = Some("embedded_notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = cid,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy =
            PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(_schema("id", "title")),
          revisionModelKind =
            Some(EntityRevisionModelKind.SimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Embedded)
        )
      )
    )
    component.withComponentDescriptors(Vector(descriptor))
    given EntityPersistent[EmbeddedNoticeEntity] = persistent
    val store = new EntityRealm[EmbeddedNoticeEntity](
      entityName = "notice",
      loader = EntityLoader[EmbeddedNoticeEntity](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memory = new PartitionedMemoryRealm[EmbeddedNoticeEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    component.entitySpace.registerEntity(
      "notice",
      new EntityCollection(
        EntityDescriptor(
          collectionId = cid,
          plan = EntityRuntimePlan(
            entityName = "notice",
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = None,
            partitionStrategy =
              PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 4,
            maxEntitiesPerPartition = 100
          ),
          persistent = persistent,
          revisionBinding = Some(
            EntityRevisionBinding(
              EntityRevisionRepresentation.Embedded
            )
          )
        ),
        EntityStorage(store, Some(memory))
      )
    )
    HttpRuntimeBindingAdmissionFixture
      .defaultWithScope(
        runtime,
        Some(org.goldenport.cncf.cli.RunMode.Server),
        resolvedconfiguration
      )
      .add(Vector(component))
  }

  private def _entity_schema_web_descriptor_fixture(): (Subsystem, WebDescriptor) = {
    val descriptor = ComponentDescriptor(
      componentName = Some("notice_board"),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "notice",
          collectionId = EntityCollectionId("sys", "sys", "notice"),
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 100,
          schema = Some(Schema(Vector(
            Column(BaseContent.simple("id"), ValueDomain(datatype = XString, multiplicity = Multiplicity.One)),
            Column(
              BaseContent.Builder("body").label("Notice body").build(),
              ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
              web = WebColumn(
                controlType = Some("textarea"),
                placeholder = Some("Schema body placeholder."),
                help = Some("Schema body help."),
                required = Some(true)
              )
            ),
            Column(
              BaseContent.Builder("status").label("Publication status").build(),
              ValueDomain(datatype = XString, multiplicity = Multiplicity.One),
              web = WebColumn(
                controlType = Some("select"),
                values = Vector("draft", "published"),
                required = Some(true)
              )
            )
          )))
        )
      )
    )
    val component = TestComponentFactory
      .create("notice_board", Protocol.empty)
      .withComponentDescriptors(Vector(descriptor))
    val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server")).add(Vector(component))
    val webdescriptor = WebDescriptor(admin = Map(
      "notice-board.entity.notice" -> WebDescriptor.AdminSurface(fields = Vector(
        WebDescriptor.AdminField("id"),
        WebDescriptor.AdminField(
          "body",
          WebDescriptor.FormControl(
            placeholder = Some("Descriptor body placeholder."),
            help = Some("Descriptor body help.")
          )
        ),
        WebDescriptor.AdminField(
          "status",
          WebDescriptor.FormControl(
            values = Vector("draft", "published", "archived"),
            required = Some(false)
          )
        )
      ))
    ))
    subsystem -> webdescriptor
  }

  private def _data_schema_web_descriptor(
    includenote: Boolean = true
  ): WebDescriptor = {
    val fields = Vector(
      WebDescriptor.AdminField("id"),
      WebDescriptor.AdminField(
        "action",
        WebDescriptor.FormControl(
          controlType = Some("select"),
          values = Vector("created", "updated")
        )
      ),
      WebDescriptor.AdminField(
        "actor",
        WebDescriptor.FormControl(
          required = Some(true),
          placeholder = Some("Descriptor actor placeholder."),
          help = Some("Descriptor actor help.")
        )
      )
    ) ++ (
      if (includenote)
        Vector(WebDescriptor.AdminField("note", WebDescriptor.FormControl(controlType = Some("textarea"))))
      else
        Vector.empty
    )
    WebDescriptor(
      admin = Map(
        "data.audit" -> WebDescriptor.AdminSurface(fields = fields)
      )
    )
  }

  private def _notice_fixture_component(
    subsystem: Subsystem
  ): Component =
    subsystem.findComponent(ComponentId("org.goldenport.cncf.test.NoticeBoard")).getOrElse(fail("notice fixture component is missing"))

  private def _schema(names: String*): Schema =
    Schema(names.toVector.map { name =>
      Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One))
    })

  private def _schema(fields: Vector[(String, WebColumn)]): Schema =
    Schema(fields.toVector.map {
      case (name, web) =>
        Column(BaseContent.simple(name), ValueDomain(datatype = XString, multiplicity = Multiplicity.One), web = web)
    })

  private def _notice_collection(
    entities: Vector[NoticeEntity],
    collectionid: EntityCollectionId = NoticeEntity.collectionid
  )(using EntityPersistent[NoticeEntity]): EntityCollection[NoticeEntity] = {
    val store = new EntityRealm[NoticeEntity](
      entityName = "notice",
      loader = EntityLoader[NoticeEntity](id => entities.find(_.id == id)),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memory = new PartitionedMemoryRealm[NoticeEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = collectionid,
      plan = EntityRuntimePlan(
        entityName = "notice",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 100
      ),
      persistent = summon[EntityPersistent[NoticeEntity]],
      revisionBinding = Some(
        EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      )
    )
    val collection = new EntityCollection[NoticeEntity](
      descriptor = descriptor,
      storage = EntityStorage(store, Some(memory))
    )
    entities.foreach(collection.put)
    collection
  }

  private def _notice_persistent: EntityPersistent[NoticeEntity] =
    new EntityPersistent[NoticeEntity] {
      def id(e: NoticeEntity): EntityId = e.id
      def toRecord(e: NoticeEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[NoticeEntity] =
        Consequence.success(
          NoticeEntity(
            _notice_entity_id(r.getAny("id")),
            r.getString("title").getOrElse(""),
            r.getString("author").getOrElse("")
          )
        )
    }

  private def _embedded_notice_persistent
      : EntityPersistent[EmbeddedNoticeEntity] =
    new EntityPersistent[EmbeddedNoticeEntity] {
      def id(e: EmbeddedNoticeEntity): EntityId =
        e.id

      def toRecord(e: EmbeddedNoticeEntity): Record =
        Record.dataAuto(
          "id" -> e.id,
          "revision" -> e.revision.value,
          "title" -> e.title
        )

      def fromRecord(
        r: Record
      ): Consequence[EmbeddedNoticeEntity] =
        for {
          idoption <- r.getAsC[EntityId]("id")
          id <- Consequence.fromOption(
            idoption,
            "id is required"
          )
          revisionvalue <- Consequence.fromOption(
            r.getLong("revision"),
            "revision is required"
          )
          revision <- EntityRevision.createC(revisionvalue)
          title <- Consequence.fromOption(
            r.getString("title"),
            "title is required"
          )
        } yield EmbeddedNoticeEntity(id, revision, title)
    }

  private def _notice_entity_id(value: Option[Any]): EntityId =
    value match {
      case Some(id: EntityId) => id
      case Some(text: String) =>
        EntityId.parse(text).toOption.getOrElse(_new_notice_entity_id())
      case Some(other) =>
        EntityId.parse(other.toString).toOption.getOrElse(_new_notice_entity_id())
      case None =>
        _new_notice_entity_id()
    }

  private def _new_notice_entity_id(): EntityId = {
    val collection = NoticeEntity.collectionid
    val generated = EntityId(collection.major, collection.minor, collection)
    EntityId.parse(generated.value).getOrElse(fail("notice entity id generation failed"))
  }

  private def _notice_entity_id_from_shortid(shortid: String): EntityId = {
    val collection = NoticeEntity.collectionid
    val generated = EntityId(
      collection.major,
      collection.minor,
      collection,
      timestamp = Some(java.time.Instant.EPOCH),
      entropy = Some(shortid)
    )
    EntityId.parse(generated.value).getOrElse(fail("notice entity id generation failed"))
  }

  private def _load_notice_store_record(
    subsystem: Subsystem,
    id: EntityId
  ): Record = {
    given ExecutionContext = _notice_fixture_component(subsystem).logic.executionContext()
    val collectionid = DataStore.CollectionId.EntityStore(id.collection)
    val entryid = DataStore.EntryId(id)
    val loaded = for {
      ds <- summon[ExecutionContext].dataStoreSpace.dataStore(collectionid)
      record <- ds.load(collectionid, entryid)
    } yield record
    loaded.toOption.flatten.getOrElse(fail(s"notice store record is missing: ${id.print}"))
  }

  private def _notice_entity_version(
      subsystem: Subsystem,
      id: String
  ): String = {
    val collection =
      _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice")
    val entityid =
      collection.resolveEntityId(id).getOrElse(fail(s"notice entity id is missing: ${id}"))
    val binding = collection.descriptor.revisionBinding
      .getOrElse(fail("notice revision binding is missing"))
    _success(binding.revision(_load_notice_store_record(subsystem, entityid)))
      .value
      .toString
  }

  private def _blob_request(
    operation: String,
    properties: Property*
  ): GRequest =
    _blob_request(operation, Nil, properties.toList)

  private def _blob_request(
    operation: String,
    arguments: List[Argument],
    properties: List[Property]
  ): GRequest =
    GRequest.of(
      component = BuiltinComponentIdentity.BLOB.name,
      service = "blob",
      operation = operation,
      arguments = arguments,
      properties = properties
    )

  private def _blob_record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected Blob record response but got $other")
    }

  private def _register_external_blob(
    subsystem: Subsystem,
    filename: String,
    url: String
  ): String = {
    val blob = _blob_record(_success(subsystem.executeOperationResponse(_blob_request(
      "register_blob",
      Property("sourceMode", "external_url", None),
      Property("kind", "image", None),
      Property("filename", filename, None),
      Property("contentType", ContentType.IMAGE_PNG.header, None),
      Property("externalUrl", url, None)
    ))))
    blob.getString("id").getOrElse(fail("Blob id is missing"))
  }

  private def _create_legacy_external_blob(
    subsystem: Subsystem,
    filename: String,
    url: String
  ): String = {
    val component = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).getOrElse(fail("Blob component is missing"))
    given ExecutionContext = component.logic.executionContext()
    val id = EntityId(BlobRepository.CollectionId.major, BlobRepository.CollectionId.minor, BlobRepository.CollectionId)
    val created = _success(BlobRepository.entityStore().create(
      BlobCreate(
        id = id,
        kind = BlobKind.Image,
        sourceMode = BlobSourceMode.ExternalUrl,
        filename = Some(filename),
        contentType = Some(ContentType.IMAGE_PNG),
        byteSize = None,
        digest = None,
        storageRef = None,
        externalUrl = Some(url),
        accessUrl = BlobAccessUrl(
          displayUrl = url,
          downloadUrl = url,
          urlSource = BlobAccessUrlSource.Backend
        )
      )
    ))
    created.id.value
  }

  private def _blob_records(
    record: Record
  ): Vector[Record] =
    record.getAny("images").collect {
      case xs: Seq[?] => xs.collect { case r: Record => r }.toVector
    }.getOrElse(Vector.empty)

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(v) => v
      case Consequence.Failure(c) => fail(s"unexpected failure: $c")
    }
}

private final case class RendererJobAction(
  request: GRequest
) extends QueryAction() {
  override def createCall(core: ActionCall.Core): ActionCall =
    RendererJobActionCall(core)
}

private final case class RendererJobActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("renderer-job-ok"))
}

private final case class NoticeEntity(
  id: EntityId,
  title: String,
  author: String
) {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "title" -> title,
      "author" -> author
    )
}

private object NoticeEntity {
  val collectionid: EntityCollectionId =
    EntityCollectionId("sample", "web", "notice")
}

private final case class EmbeddedNoticeEntity(
  id: EntityId,
  revision: EntityRevision,
  title: String
)

private object EmbeddedNoticeEntity {
  val collectionid: EntityCollectionId =
    EntityCollectionId("sample", "web", "embedded_notice")
}

private final case class NoticeAggregate(id: EntityId, summary: String)

private final case class NoopOperation(
  opname: String,
  parameters: Vector[String] = Vector.empty
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(
        parameters = parameters.map { name =>
          val content =
            if (name == "body") BaseContent.Builder("body").label("Notice body").build()
            else BaseContent.simple(name)
          val web =
            if (name == "body") WebColumn(help = Some("Body parameter."))
            else if (name == "code") WebColumn(validation = WebValidationHints(minLength = Some(2), maxLength = Some(8), pattern = Some("^[A-Z0-9]+$")))
            else if (name == "count") WebColumn(validation = WebValidationHints(min = Some(BigDecimal(0)), max = Some(BigDecimal(100))))
            else WebColumn.empty
          spec.ParameterDefinition(
            content = content,
            kind = spec.ParameterDefinition.Kind.Argument,
            domain = _noop_parameter_domain(name),
            web = web
          )
        }.toList
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.notImplemented("not used")

  private def _noop_parameter_domain(
    name: String
  ): ValueDomain =
    name match {
      case "count" => ValueDomain(datatype = XInt, multiplicity = Multiplicity.One)
      case "published" => ValueDomain(datatype = XBoolean, multiplicity = Multiplicity.One)
      case "publishedAt" => ValueDomain(datatype = XDateTime, multiplicity = Multiplicity.One)
      case _ => ValueDomain(datatype = XString, multiplicity = Multiplicity.One)
    }
}

private final case class SuccessfulAggregateOperation(
  opname: String,
  argumentname: String,
  resultprefix: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple(argumentname),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.success(SuccessfulAggregateAction(OperationRequest.Core(req), argumentname, resultprefix))
}

private final case class SuccessfulAggregateAction(
  core: OperationRequest.Core,
  argumentname: String,
  resultprefix: String
) extends QueryAction with OperationRequest.Core.Holder {
  override def createCall(core: ActionCall.Core): ActionCall =
    SuccessfulAggregateActionCall(core, argumentname, resultprefix)
}

private final case class SuccessfulAggregateActionCall(
  core: ActionCall.Core,
  argumentname: String,
  resultprefix: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    val value = core.action.arguments.find(_.name == argumentname).map(_.value).getOrElse("")
    Consequence.success(OperationResponse.Scalar(s"${resultprefix}:${value}"))
  }
}

private final case class InspectingAggregateOperation(
  opname: String,
  argumentname: String,
  resultprefix: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple(argumentname),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: GRequest): Consequence[OperationRequest] =
    Consequence.success(InspectingAggregateAction(OperationRequest.Core(req), argumentname, resultprefix))
}

private final case class InspectingAggregateAction(
  core: OperationRequest.Core,
  argumentname: String,
  resultprefix: String
) extends QueryAction with OperationRequest.Core.Holder {
  override def createCall(core: ActionCall.Core): ActionCall =
    InspectingAggregateActionCall(core, argumentname, resultprefix)
}

private final case class InspectingAggregateActionCall(
  core: ActionCall.Core,
  argumentname: String,
  resultprefix: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    val value = core.action.arguments.find(_.name == argumentname).map(x => s"present:${x.value}").getOrElse("absent")
    Consequence.success(OperationResponse.Scalar(s"${resultprefix}:${value}"))
  }
}

private final class RecordingWebOperationDispatcher(
  delegate: WebOperationDispatcher
) extends WebOperationDispatcher {
  private val _requests = ListBuffer.empty[HttpRequest]
  private val _paths = ListBuffer.empty[String]
  private val _forms = ListBuffer.empty[Record]
  private val _headers = ListBuffer.empty[Record]

  def targetName: String = "recording"

  def paths: Vector[String] = _paths.synchronized {
    _paths.toVector
  }

  def requests: Vector[HttpRequest] = _requests.synchronized {
    _requests.toVector
  }

  def forms: Vector[Record] = _forms.synchronized {
    _forms.toVector
  }

  def headers: Vector[Record] = _headers.synchronized {
    _headers.toVector
  }

  def dispatch(request: HttpRequest): HttpResponse = {
    _requests.synchronized {
      _requests += request
    }
    _paths.synchronized {
      _paths += request.path.asString
    }
    _forms.synchronized {
      _forms += request.form
    }
    _headers.synchronized {
      _headers += request.header
    }
    delegate.dispatch(request)
  }
}

private final class StaticWebOperationDispatcher(
  response: HttpResponse
) extends WebOperationDispatcher {
  def targetName: String = "static"

  def dispatch(request: HttpRequest): HttpResponse = {
    val _ = request
    response
  }
}

private final class RecordingRestDriver extends HttpDriver {
  import RecordingRestDriver.Call

  private val _calls = ListBuffer.empty[Call]
  private val _response =
    HttpResponse.Text(
      HttpStatus.Ok,
      ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
      Bag.text("ok", StandardCharsets.UTF_8)
    )

  def calls: Vector[Call] = _calls.synchronized {
    _calls.toVector
  }

  def get(
    path: String,
    headers: Map[String, String],
    properties: Vector[org.goldenport.protocol.Property] = Vector.empty
  ): HttpResponse = {
    _record(Call("GET", path, None, headers))
    _response
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[org.goldenport.protocol.Property] = Vector.empty
  ): HttpResponse = {
    _record(Call("POST", path, body, headers))
    _response
  }

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[org.goldenport.protocol.Property] = Vector.empty
  ): HttpResponse = {
    _record(Call("PUT", path, body, headers))
    _response
  }

  private def _record(call: Call): Unit = _calls.synchronized {
    _calls += call
  }
}

private object RecordingRestDriver {
  final case class Call(
    method: String,
    path: String,
    body: Option[String],
    headers: Map[String, String]
  )
}

private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value: A = initial

  def get: A = synchronized {
    _value
  }

  def set(a: A): Unit = synchronized {
    _value = a
  }

  override def getAndSet(a: A): A = synchronized {
    val prev = _value
    _value = a
    prev
  }

  def access: (A, A => Boolean) = synchronized {
    val snapshot = _value
    val setter: A => Boolean = (next: A) => synchronized {
      if (_value == snapshot) {
        _value = next
        true
      } else {
        false
      }
    }
    (snapshot, setter)
  }

  override def tryUpdate(f: A => A): Boolean = synchronized {
    _value = f(_value)
    true
  }

  override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
    val (next, out) = f(_value)
    _value = next
    Some(out)
  }

  def update(f: A => A): Unit = synchronized {
    _value = f(_value)
  }

  def modify[B](f: A => (A, B)): B = synchronized {
    val (next, out) = f(_value)
    _value = next
    out
  }

  override def modifyState[B](state: State[A, B]): B = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    out
  }

  override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    Some(out)
  }
}
