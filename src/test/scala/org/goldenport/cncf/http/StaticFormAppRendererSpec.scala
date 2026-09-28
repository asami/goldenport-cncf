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
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
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

    "E2 render canonical managed Information output in system admin pages" must _ic06e2 {
    "render system admin information pages without provider or raw conflict values" in {
      Given("a subsystem containing published Information and one structured conflict")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      subsystem.add(TestComponentFactory.create("information_component", Protocol.empty))
      subsystem.add(TestComponentFactory.create("isolated_information_component", Protocol.empty))
      val component = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.InformationComponent")).getOrElse(fail("information component missing"))
      val isolatedcomponent = subsystem.findComponent(ComponentId("org.goldenport.cncf.test.IsolatedInformationComponent")).getOrElse(fail("isolated information component missing"))
      given ExecutionContext = component.logic.executionContext()
      val providerpayload = "provider-secret-payload"
      val batch = _success(component.informationSpace.registerInformation(
        "paper",
        Vector(Record.data(
          "title" -> "Knowledge Import",
          "authors" -> "Alice Example",
          "venue" -> "CNCF Notes",
          "providerPayload" -> providerpayload
        ))
      ))
      val record = batch.headOption.getOrElse(fail("information record missing"))
      _success(component.informationSpace.validateInformation(record.id))
      val item = _success(component.informationSpace.confirmInformation(record.id))
      _success(component.informationSpace.publishInformation(item.id, "fuseki", Some("published")))
      val informationvalue = "editor-conflict-value"
      val rdfvalue = "provider-conflict-value"
      _success(component.informationSpace.recordConflict(
        item.id,
        "title",
        informationvalue,
        rdfvalue
      ))
      val current = component.informationSpace.getInformation(item.id).getOrElse(fail("current information missing"))
      val isolatedtitle = "Isolated component title"
      val isolatedpayload = "isolated-provider-secret-payload"
      {
        given ExecutionContext = isolatedcomponent.logic.executionContext()
        _success(isolatedcomponent.informationSpace.registerInformation(
          "paper",
          Vector(Record.data("title" -> isolatedtitle, "providerPayload" -> isolatedpayload))
        ))
      }

      When("the system Information index and component detail are rendered")
      val index = _renderer.renderSystemAdminInformation(subsystem).body
      val detail = _renderer.renderSystemAdminInformationComponent(subsystem, "information-component").map(_.body).getOrElse(fail("information component page missing"))

      Then("the pages expose managed revision and structured conflict summary without raw values")
      index should include ("System Information")
      index should include (component.displayName)
      index should include ("/web/system/admin/information/information_component")
      detail should include (s"System Information ${component.displayName}")
      detail should include ("Knowledge Import")
      detail should include ("paper")
      detail should include ("published")
      detail should include ("Revision (system-managed)")
      detail should include (current.revision.value.toString)
      detail should include ("conflict-1")
      detail should include ("title")
      detail should include (current.conflicts.head.state.label)
      detail should not include providerpayload
      detail should not include informationvalue
      detail should not include rdfvalue
      detail should not include isolatedtitle
      detail should not include isolatedpayload
      _renderer.renderSystemAdminInformationComponent(subsystem, "missing") shouldBe None
    }
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
  }
}
