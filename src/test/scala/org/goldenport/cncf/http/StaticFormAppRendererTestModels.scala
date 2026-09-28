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
