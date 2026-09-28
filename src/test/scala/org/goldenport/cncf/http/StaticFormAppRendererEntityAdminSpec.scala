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
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererEntityAdminSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
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
      val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,entropy = storedid.entropy.getOrElse(fail("stored notice entropy is missing"))).value

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
      val canonicalid = _new_notice_entity_id(entropy = "canonical_route_id").value
      val storedid = _notice_fixture_component(subsystem).entitySpace.entity[NoticeEntity]("notice").storage.storeRealm.values.head.id
      val foreigncollection = EntityCollectionId("foreign", "route", "notice")
      val foreignid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        foreigncollection.major,
        foreigncollection.minor,
        foreigncollection,entropy = storedid.parts.entropy).value

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
      val id = _new_notice_entity_id(entropy = "create_entity")
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
      val id = _new_notice_entity_id(entropy = "multipart_image_create")
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
      val missingblobid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        BlobRepository.CollectionId.major,
        BlobRepository.CollectionId.minor,
        BlobRepository.CollectionId,entropy = missingtoken).value

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
      val missingblobid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        BlobRepository.CollectionId.major,
        BlobRepository.CollectionId.minor,
        BlobRepository.CollectionId,entropy = missingtoken).value

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
      val id = _new_notice_entity_id(entropy = "derived_alias_create")
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
      val id = _new_notice_entity_id(entropy = "static_result_guard")
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
  }
}
