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
final class StaticFormAppRendererStaticWebSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
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
      When("keep component Web app routes separate from component form indexes is exercised")
      val canonical = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene/textus-art-scene"))).unsafeRunSync()
      val toplevel = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/textus-art-scene"))).unsafeRunSync()
      val componentroot = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/art-scene"))).unsafeRunSync()
      val defaultentry = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val defaulthead = app.run(Request[IO](Method.HEAD, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val formindex = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/form/art-scene"))).unsafeRunSync()

      // Then
      Then("the observable contract for keep component Web app routes separate from component form indexes holds")
      canonical.status.code shouldBe 200
      canonical.as[String].unsafeRunSync() should include ("ArtScene")
      defaultentry.status.code shouldBe 200
      defaultentry.as[String].unsafeRunSync() should include ("ArtScene")
      defaulthead.status.code shouldBe 200
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

    "redirect / to the Dashboard when no Web application is configured" in {
      Given("the prerequisites for redirect / to the Dashboard when no Web application is configured")
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("develop")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the no-application development routes are requested")
      val root = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))).unsafeRunSync()
      val web = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val webhead = server.routes(null).orNotFound.run(Request[IO](Method.HEAD, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val webslash = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/"))).unsafeRunSync()
      val webtarget = web.headers.get[org.http4s.headers.Location].map(_.uri.renderString)

      Then("the observable contract for redirect / to the Dashboard when no Web application is configured holds")
      root.status.code shouldBe 307
      root.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      webslash.status.code shouldBe 307
      webslash.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      web.status.code shouldBe 307
      webtarget shouldBe Some("/web/system/dashboard")
      webhead.status.code shouldBe 307
      webhead.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web/system/dashboard")
    }

    "serve a selected application at /web while retaining its explicit SAR default route" in {
      Given("a selected static Web application with an explicit SAR default route")
      val root = Files.createTempDirectory("cncf-runtime-landing-routes-")
      Files.writeString(
        root.resolve("web-descriptor.yaml"),
        """web:
          |  apps:
          |    - name: board
          |      kind: static-form
          |  routes:
          |    - path: /web/board
          |      kind: default
          |      target:
          |        component: notice-board
          |        app: board
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.createDirectories(root.resolve("board"))
      Files.writeString(root.resolve("board").resolve("index.html"), "<h1>Default Notice Board</h1>", StandardCharsets.UTF_8)
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("develop"),
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web-descriptor.yaml").toString)
        ))
      )

      When("the public and explicit routes are requested")
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val app = server.routes(null).orNotFound
      val web = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val explicitroute = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      val explicithead = app.run(Request[IO](Method.HEAD, Uri.unsafeFromString("/web/board"))).unsafeRunSync()
      val componentalias = app.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board"))).unsafeRunSync()
      val webhtml = web.as[String].unsafeRunSync()
      val explicithtml = explicitroute.as[String].unsafeRunSync()

      Then("the selected application is served at both public routes without an implicit component alias")
      web.status.code shouldBe 200
      webhtml should include ("Default Notice Board")
      explicitroute.status.code shouldBe 200
      explicithead.status.code shouldBe 200
      explicithtml should include ("Default Notice Board")
      componentalias.status.code shouldBe 404
    }

    "redirect /web to the Dashboard in production when no Web application is configured" in {
      Given("the prerequisites for redirect /web to the Dashboard in production when no Web application is configured")
      val subsystem = _management_console_fixture_subsystem(
        Configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the no-application production routes are requested")
      val root = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/"))).unsafeRunSync()
      val web = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()

      Then("the observable contract for redirect /web to the Dashboard in production when no Web application is configured holds")
      root.status.code shouldBe 307
      root.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web")
      web.status.code shouldBe 307
      web.headers.get[org.http4s.headers.Location].map(_.uri.renderString) shouldBe Some("/web/system/dashboard")
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

    "serve a sole Web app at /web without inferring legacy aliases" in {
      // Given
      Given("a sole component Web app with no descriptor routes")
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

      // When
      When("the default, legacy alias, asset alias, and canonical routes are requested")
      val server = HttpRuntimeBindingAdmissionFixture.server(engine)
      val alias = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board"))).unsafeRunSync()
      val default = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web"))).unsafeRunSync()
      val asset = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board/assets/app.css"))).unsafeRunSync()
      val canonical = server.routes(null).orNotFound.run(Request[IO](Method.GET, Uri.unsafeFromString("/web/notice-board/notice-board"))).unsafeRunSync()
      val defaulthtml = default.as[String].unsafeRunSync()
      val canonicalhtml = canonical.as[String].unsafeRunSync()

      // Then
      Then("the sole app uses /web while legacy aliases remain unavailable and the canonical route remains available")
      engine.webDescriptor.routes shouldBe Vector.empty
      alias.status.code shouldBe 404
      default.status.code shouldBe 200
      defaulthtml should include ("Implicit Notice Board")
      asset.status.code shouldBe 404
      canonical.status.code shouldBe 200
      canonicalhtml should include ("Implicit Notice Board")
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
  }
}
