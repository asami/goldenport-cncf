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
final class StaticFormAppRendererTypedUpdateSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
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
}
