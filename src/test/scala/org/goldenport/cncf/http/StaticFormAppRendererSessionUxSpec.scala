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
final class StaticFormAppRendererSessionUxSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide Static Web result, session, and UX contracts" which {
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

    "render only validated canonical component Admin page hrefs" in {
      Given("one component descriptor with an exact declared page and direct in-memory raw href, query, and foreign-component entries")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(adminPages = Vector(
        WebDescriptor.AdminPage("safe-page", "Safe Page", s"/web/${componentpath}/admin/safe-page", component = Some(componentpath)),
        WebDescriptor.AdminPage("system-safe", "System Safe", s"/web/${componentpath}/admin/system-safe", component = Some(componentpath), audience = WebDescriptor.AdminAudience.System),
        WebDescriptor.AdminPage("raw-page", "Raw Page", "javascript:alert(1)", component = Some(componentpath)),
        WebDescriptor.AdminPage("query-page", "Query Page", s"/web/${componentpath}/admin/query-page?debug=true", component = Some(componentpath)),
        WebDescriptor.AdminPage("foreign-page", "Foreign Page", "/web/other/admin/foreign-page", component = Some(componentpath))
      ))

      When("component, application, and system indexes render their descriptor-declared Admin page links")
      val componenthtml = _renderer.renderComponentAdmin(subsystem, componentpath, descriptor).map(_.body).getOrElse(fail("component admin is missing"))
      val applicationhtml = _renderer.renderApplicationAdmin(subsystem, descriptor).body
      val systemhtml = _renderer.renderSystemAdmin(subsystem, descriptor).body

      Then("only the canonical page href is emitted with the existing HTML escaping boundary")
      componenthtml should include (s"href=\"/web/${componentpath}/admin/safe-page\"")
      componenthtml should include (s"href=\"/web/${componentpath}/admin/system-safe\"")
      applicationhtml should include (s"href=\"/web/${componentpath}/admin/safe-page\"")
      applicationhtml should not include (s"/web/${componentpath}/admin/system-safe")
      systemhtml should include (s"href=\"/web/${componentpath}/admin/system-safe\"")
      Vector(componenthtml, applicationhtml, systemhtml).mkString("\n") should not include ("javascript:alert")
      Vector(componenthtml, applicationhtml, systemhtml).mkString("\n") should not include ("query-page?debug=true")
      Vector(componenthtml, applicationhtml, systemhtml).mkString("\n") should not include ("/web/other/admin/foreign-page")
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
}
