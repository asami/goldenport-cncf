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
private[http] trait StaticFormAppRendererHttpSpecFixtures extends AnyWordSpec with Matchers with GivenWhenThen { self: StaticFormAppRendererSpecFixtures =>
  private[http] def _count_occurrences(
    text: String,
    needle: String
  ): Int =
    if (needle.isEmpty)
      0
    else
      text.sliding(needle.length).count(_ == needle)

  private[http] def _dashboard_state_json(
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

  private[http] def _post_form_request(
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

  private[http] def _post_multipart_request(
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

  private[http] def _get_request(
    path: String
  ): Request[IO] =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(path)
    )

  private[http] def _head_request(
    path: String
  ): Request[IO] =
    Request[IO](
      method = Method.HEAD,
      uri = Uri.unsafeFromString(path)
    )

  private[http] def _session_summary(
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

  private[http] def _with_global_runtime[A](
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

  private[http] def _json_fields(
    json: Json
  ): Vector[Json] =
    json.hcursor.downField("fields").as[Vector[Json]].toOption.getOrElse(Vector.empty)

  private[http] def _json_field_names(
    fields: Vector[Json]
  ): Vector[String] =
    fields.flatMap(_.hcursor.downField("name").as[String].toOption)

  private[http] def _json_field(
    fields: Vector[Json],
    name: String
  ): HCursor =
    fields.
      find(_.hcursor.downField("name").as[String].toOption.contains(name)).
      map(_.hcursor).
      getOrElse(fail(s"$name field is missing"))

}
