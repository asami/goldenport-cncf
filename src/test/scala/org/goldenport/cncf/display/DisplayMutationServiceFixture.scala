package org.goldenport.cncf.display

import cats.~>
import cats.data.State
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Clock, Instant, ZoneOffset}
import java.security.MessageDigest
import logicalui.DisplayMutationServiceDomain
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.*
import org.goldenport.cncf.config.{OperationMode, ResolvedParameters, RuntimeConfig}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateDefinition, ContextualAggregateBuilder, ContextualAggregateQuery}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ContextualViewBuilder, ViewCollection}
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.cncf.mcp.{McpJsonRpcAdapter, McpJsonRpcOutcome}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.*
import org.goldenport.cncf.spi.*
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.*
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.http4s.{Header, Headers, Method, Request as HttpRequest, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.typelevel.ci.CIString

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
/** Real ordinary component assembly; no fixture-owned resource map or result writer. */
private[cncf] object DisplayMutationServiceFixture {
  import DisplayMutationServiceDomain.{Root, PatchShape, CreateShape, BodyMode}
  val representations: Vector[EntityRevisionRepresentation] = Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
  val fixedInstant: Instant = Instant.parse("2026-10-15T09:10:11.123456789Z")
  val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
  val observability: ObservabilityContext = ObservabilityContext(TraceId("fixture", "displaynormal"), None, Some(CorrelationId("fixture", "displaynormal")))
  val body: String = "Preserved body 日本 Ω\n" * 400
  val resourceType: String = "normal-update-reference"
  val screenId: String = "capture-detail"
  val domainFailure: Consequence.Failure[Nothing] = DisplayMutationServiceDomain.domainFailure
  val denied: Consequence.Failure[Nothing] = Consequence.securityPermissionDenied("normal-update-factory-denied")
  val projectionFailure: Consequence.Failure[Nothing] = Consequence.operationInvalid("normal-update-projection-rejected")
  enum Target { case Entity, Aggregate, Alias }
  enum Reload { case Current, Older, Newer, Failure, NullConsequence, NullSuccess, Throw, WrongId, RowFailure, RowNull, RowNullSuccess, RowThrow }
  enum CaptureFault { case None, Projection, Coherence }
  enum DeleteRejectionBoundary { case BeforeCapture, CanonicalMissingTarget }
  enum Commit { case Normal, Noop, Reject, Abort, Throw, Cleanup, Dispose }
  enum Registration {
    case Normal, Empty, NullProvider, ThrowProvider, Duplicate, ForeignComponent, ForeignResource,
      WrongScreen, DefinitionRevision, Producer, Source, ProvenanceRevision, ListBranch, UnregisteredBrowser,
      WrongOwner, WrongFamily, WrongCollection, Unmanaged, DuplicateStorage, ManagedStorage, AliasedTarget, UnselectedSeed
  }
  final case class Trace(event: String, context: ExecutionContext, uow: UnitOfWork)
  final case class Rows(selected: Option[Record], unrelated: Option[Record], body: Option[Record], unrelatedbody: Option[Record])
  final case class ViewState(selected: Root, unrelated: Root)
  final case class Resident(selected: Option[Root], unrelated: Option[Root], memoryselected: Option[Root], memoryunrelated: Option[Root], views: ViewState, builds: Int)
  final case class Expected(rows: Rows, physical: Map[(String, String), StoredTable], resident: Resident)
  final case class Attempt(result: Consequence[OperationResponse], execution: Execution)
  final case class LifecycleSetup(supplied: Boolean = true, bodyMode: BodyMode = BodyMode.Overflow,
    logical: Boolean = true, removed: Set[EntityMutationProviderFeature] = Set.empty,
    capabilities: DisplayMutation.Capabilities = DisplayMutationServiceDomain.lifecycleCapabilities, mixed: Boolean = false,
    withList: Boolean = false)
  enum LifecycleView {
    case Present(root: Root)
    case Missing(conclusion: Conclusion)
  }
  final case class LifecycleResident(selected: Option[Root], unrelated: Option[Root], memoryselected: Option[Root],
    memoryunrelated: Option[Root], selectedview: LifecycleView, unrelatedview: LifecycleView, builds: Int)
  final case class LifecycleSnapshot(rows: Rows, physical: Map[(String, String), StoredTable], resident: LifecycleResident)
  final case class LifecycleAdapterResult(value: Option[io.circe.Json], failure: Option[Conclusion], wire: Option[io.circe.Json], status: Int,
    execution: Option[Execution] = None)
  trait UpdateApi
  val spiContract: SpiContract[UpdateApi] = SpiContract("display-normal-update", classOf[UpdateApi])
  private val _sequence = new java.util.concurrent.atomic.AtomicInteger()

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.display)
  }
  def revision(value: Long): EntityRevision = success(EntityRevision.createC(value))
  def json(result: Consequence[OperationResponse]): io.circe.Json = success(result) match {
    case OperationResponse.Json(value) => value
    case _ => throw new IllegalStateException("ordinary update requires JSON")
  }
  def conclusion(result: Consequence[?]): Conclusion = result match {
    case Consequence.Failure(value) => value
    case _ => throw new IllegalStateException("expected structured rejection")
  }
  def reasons(result: Consequence[?]): Vector[String] = conclusion(result).observation.cause.descriptor.facets.collect {
    case Descriptor.Facet.Reason(value) => value
  }
  def field(name: String, value: DisplayValue): DisplayMutation.FieldInput =
    DisplayMutation.FieldInput(DisplayMutation.FieldLocator("main", name), value)
  val lifecycleFields: Vector[DisplayMutation.FieldInput] = Vector(field("title", DisplayValue.Text("Created 日本 Ω")),
    field("description", DisplayValue.Text("description λ")), field("number", DisplayValue.Number(BigDecimal("123.4500"))),
    field("flag", DisplayValue.Boolean(false)), field("instant", DisplayValue.Timestamp(DisplayMutationServiceDomain.instant)),
    field("nullable", DisplayValue.Missing))
  val lifecycleRootColumnsBase: Vector[String] = Vector("id", "title", "description", "number", "flag", "instant", "nullable",
    "stage", "business", "audit", "content", "short_id", "name", "created_at", "created_by", "updated_at", "updated_by",
    "post_status", "aliveness", "owner_id", "group_id", "privilege_id", "permission", "trace_id", "correlation_id",
    "content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref", "deleted_at", "deletedAt", "deleted_by")
  val lifecycleBodyColumns: Vector[String] = Vector("id", "source_entity_id", "field", "content", "content_byte_size", "content_digest", "content_charset")
  val lifecyclePermission: String = "{\"owner\":{\"read\":true,\"write\":true,\"execute\":false},\"group\":{\"read\":false,\"write\":false,\"execute\":false},\"other\":{\"read\":false,\"write\":false,\"execute\":false}}"
  def lifecycleRootColumns(representation: EntityRevisionRepresentation): Vector[String] = lifecycleRootColumnsBase :+ representation.storageFieldName
  def quote(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
  def missingConclusion(id: EntityId): Conclusion = conclusion(Consequence.successOrEntityNotFound(Option.empty[Root])(id))
  def authorizationMissingConclusion(id: EntityId): Conclusion = conclusion(Consequence.entityNotFound(s"SimpleEntity not found: ${id.print}"))
  private def _field(name: String, value: Any): Field = Field(name, Field.Value.Single(value))
  private def _record(values: (String, Any)*): Record = Record(values.toVector.map { case (name, value) => _field(name, value) })
  private def _permission_record: Record = _record("owner" -> _record("read" -> true, "write" -> true, "execute" -> false),
    "group" -> _record("read" -> false, "write" -> false, "execute" -> false),
    "other" -> _record("read" -> false, "write" -> false, "execute" -> false))
  private def _digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
    .iterator.map(value => f"${value & 0xff}%02x").mkString
  private def _stored_text(text: String): String = "utf16:" + text.iterator.map(character => f"${character.toInt}%04x").mkString
  private def _physical_value(value: Any): Any = value match {
    case identity: EntityId => identity.value
    case instant: Instant => instant.toString
    case PostStatus.Published => "published"
    case Aliveness.Alive => "alive"
    case other => other
  }
  private def _logical_cells(cells: Map[String, Any], columns: Vector[(String, String)], session: Session): Record = {
    val names = columns.map(_._1)
    val revisionkey = session.component.representation.storageFieldName
    val order = if (session.backend == "postgresql") Vector("id") ++ names.filter(_ == revisionkey) ++
      names.filterNot(name => name == "id" || name == revisionkey) else names
    Record(order.filter(name => cells(name) != null).map { name =>
      _field(name, if (name == "permission") _permission_record else cells(name))
    })
  }
  private def _expected_root(session: Session, stored: Record, bodyrecord: Option[Record], hydrated: Boolean,
    version: Long): Root = {
    val revisionkey = session.component.representation.storageFieldName
    val fields = if (hydrated && bodyrecord.nonEmpty)
      stored.fields.filterNot(_.key == "content") ++ bodyrecord.get.fields.filter(_.key == "content") else stored.fields
    val decoded = fields.filterNot(field => session.component.representation == EntityRevisionRepresentation.Detached && field.key == revisionkey)
      .map { field => (field.key, field.value.single) match {
        case ("title" | "description" | "nullable", text: String) if text.startsWith("utf16:") =>
          field.copy(value = Field.Value.Single(text.drop(6).grouped(4).map(unit => Integer.parseInt(unit, 16).toChar).mkString))
        case _ => field
      } }
    val root = Root(session.component.id, Record(decoded.filterNot(_.key == "id") :+ _field("id", session.component.id)))
    if (hydrated) root.copy(record = Record(root.record.fields.filterNot(_.key == revisionkey) :+ _field(revisionkey, version))) else root
  }

  def expectedLifecycleCreate(session: Session, before: LifecycleSnapshot, fields: Vector[DisplayMutation.FieldInput],
    fault: CaptureFault = CaptureFault.None, principal: String = "owner"): LifecycleSnapshot = {
    val identity = session.component.id
    val setup = session.component.lifecycleSetup.get
    val inputs = fields.map(input => input.location.fieldId -> input.value).toMap
    def _value_(name: String, fallback: Any): Option[Any] = inputs.get(name) match {
      case Some(DisplayValue.Text(text)) => Some(_stored_text(text))
      case Some(DisplayValue.Number(number)) => Some(number.bigDecimal.toString)
      case Some(DisplayValue.Boolean(flag)) => Some(if (flag) "true" else "false")
      case Some(DisplayValue.Timestamp(instant)) => Some(instant.toString)
      case Some(DisplayValue.Missing) => None
      case None => Option(fallback)
    }
    val domain = Vector("title" -> "", "description" -> _stored_text("server description"), "number" -> "42.1250",
      "flag" -> "false", "instant" -> DisplayMutationServiceDomain.instant.toString, "nullable" -> null)
      .flatMap { case (name, fallback) => _value_(name, fallback).map(name -> _) }.toMap ++
      Map[String, Any]("stage" -> "draft", "business" -> "server business", "audit" -> "server audit")
    val content = if (setup.bodyMode == BodyMode.Inline) body.take(24) else body
    val defaults = Map[String, Any]("id" -> identity.value, "short_id" -> identity.parts.entropy, "name" -> principal,
      "created_at" -> fixedInstant, "created_by" -> principal, "updated_at" -> fixedInstant, "updated_by" -> principal,
      "post_status" -> PostStatus.Published, "aliveness" -> Aliveness.default, "owner_id" -> principal, "group_id" -> principal,
      "privilege_id" -> principal, "permission" -> lifecyclePermission, "trace_id" -> observability.traceId.value,
      "correlation_id" -> observability.correlationId.get.value)
    val contentfield: Map[String, Any] = setup.bodyMode match {
      case BodyMode.Absent => Map.empty
      case BodyMode.SetNull => Map("content" -> Update.SetNull)
      case _ => Map("content" -> content)
    }
    val declared = (domain ++ contentfield ++ defaults).toVector.map { case (name, value) => _field(name, value) } :+
      _field(session.component.representation.storageFieldName, 1L)
    val metadata = Vector(_field("content_byte_size", content.getBytes(StandardCharsets.UTF_8).length),
      _field("content_digest", _digest(content)), _field("content_charset", "UTF-8"))
    val memoryroot = Record(setup.bodyMode match {
      case BodyMode.Absent => declared
      case BodyMode.SetNull => declared.filterNot(_.key == "content")
      case BodyMode.Inline => declared ++ metadata :+ _field("content_storage", "inline")
      case BodyMode.Overflow => declared.filterNot(_.key == "content") ++ metadata ++
        Vector(_field("content_storage", "overflow"), _field("content_ref", identity.value + ":content"))
    })
    val memorybody = if (setup.bodyMode == BodyMode.Overflow) Some(_record("id" -> (identity.value + ":content"),
      "source_entity_id" -> identity.value, "field" -> "content", "content" -> content,
      "content_byte_size" -> content.getBytes(StandardCharsets.UTF_8).length, "content_digest" -> _digest(content), "content_charset" -> "UTF-8")) else None
    var raw = memoryroot
    var rawbody = memorybody
    val physical = if (session.backend == "memory") before.physical else before.physical.map { case (key, table) =>
      val record = if (key._2 == DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName) Some(memoryroot)
        else if (key._2 == "cncf_content_body_overflow") memorybody else None
      record match {
        case None => key -> table
        case Some(value) =>
          val cells = table.columns.map { case (name, _) => name -> value.getAny(name).map(_physical_value).orNull }.toMap
          val logical = _logical_cells(cells, table.columns, session)
          if (key._2 == "cncf_content_body_overflow") rawbody = Some(logical) else raw = logical
          key -> table.copy(rows = (table.rows :+ cells).sortBy(_("id").toString))
      }
    }
    val resident = _expected_root(session, raw, rawbody, false, 1L)
    val view = _expected_root(session, raw, rawbody, true, 1L)
    val selected = if (fault == CaptureFault.Coherence) before.resident.selected else if (fault == CaptureFault.Projection) None else Some(resident)
    val memory = if (fault == CaptureFault.Coherence) before.resident.memoryselected else if (fault == CaptureFault.Projection) None else Some(resident)
    LifecycleSnapshot(before.rows.copy(selected = Some(raw), body = rawbody), physical, before.resident.copy(
      selected = selected, memoryselected = memory, selectedview = LifecycleView.Present(view), builds = before.resident.builds + 2))
  }

  def expectedLifecycleDelete(session: Session, before: LifecycleSnapshot, version: Long, logical: Boolean,
    fault: CaptureFault = CaptureFault.None): LifecycleSnapshot = {
    val revisionkey = session.component.representation.storageFieldName
    val updates = Vector[(String, Any)]("post_status" -> 3, "aliveness" -> 3, "updated_at" -> fixedInstant,
      "updated_by" -> "owner", "trace_id" -> observability.traceId.value, "correlation_id" -> observability.correlationId.get.value,
      "deleted_at" -> fixedInstant, "deleted_by" -> "owner")
    var raw = if (logical) Some(Record(before.rows.selected.get.fields.filterNot(field =>
      updates.exists(_._1 == field.key) || field.key == revisionkey) ++ updates.map { case (name, value) => _field(name, value) } :+
      _field(revisionkey, version + 1L))) else None
    val physical = if (session.backend == "memory") before.physical else before.physical.map { case (key, table) =>
      val rows = if (key._2 == DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName) table.rows.flatMap { row =>
        if (row("id") != session.component.id.value) Some(row)
        else if (!logical) None else {
          val cells = row ++ updates.map { case (name, value) => name ->
            (if (name == "post_status" || name == "aliveness") "3" else _physical_value(value)) } + (revisionkey -> (version + 1L))
          raw = Some(_logical_cells(cells, table.columns, session)); Some(cells)
        }
      } else if (key._2 == "cncf_content_body_overflow" && !logical)
        table.rows.filterNot(_("id") == session.component.id.value + ":content") else table.rows
      key -> table.copy(rows = rows)
    }
    LifecycleSnapshot(before.rows.copy(selected = raw, body = if (logical) before.rows.body else None), physical,
      before.resident.copy(selected = if (fault == CaptureFault.Coherence) before.resident.selected else None,
        memoryselected = if (fault == CaptureFault.Coherence) before.resident.memoryselected else None,
        selectedview = LifecycleView.Missing(missingConclusion(session.component.id)), builds = before.resident.builds + 2))
  }

  /** Canonical missing-target reconciliation evicts only the selected resident keys. */
  def expectedLifecycleDeleteRejection(before: LifecycleSnapshot,
    boundary: DeleteRejectionBoundary): LifecycleSnapshot = boundary match {
    case DeleteRejectionBoundary.BeforeCapture => before
    case DeleteRejectionBoundary.CanonicalMissingTarget => before.copy(resident = before.resident.copy(
      selected = None, memoryselected = None))
  }

  def lifecycleAdapter(session: Session, operation: DisplayMutation.Operation, payload: String, adapter: String,
    observed: Long = 1L, authenticated: Boolean = true): LifecycleAdapterResult = {
    val name = operation.toString.toLowerCase(java.util.Locale.ROOT)
    def _response_(value: Consequence[OperationResponse], active: Execution): LifecycleAdapterResult = value match {
      case Consequence.Success(OperationResponse.Json(json)) => LifecycleAdapterResult(Some(json), None, None, 200, Some(active))
      case Consequence.Failure(error) => LifecycleAdapterResult(None, Some(error), None, 400, Some(active))
      case _ => throw new IllegalStateException("Lifecycle adapter requires ordinary JSON response")
    }
    adapter match {
      case "http" =>
        val response = session.http(payload.getBytes(StandardCharsets.UTF_8),
          if (operation == DisplayMutation.Operation.Create) Vector.empty else Vector("If-Match" -> EntityRevisionTransport.entityTag(revision(observed))),
          method = if (operation == DisplayMutation.Operation.Create) Method.POST else Method.DELETE, operation = name,
          authenticated = authenticated)
        LifecycleAdapterResult(if (response.status == 200) Some(response.data) else None, None, Some(response.json), response.status)
      case "cli" =>
        val args = Vector("display", name, "--resourceType=" + resourceType, "--screenId=" + screenId, "--payload=" + payload) ++
          (if (operation == DisplayMutation.Operation.Create) Vector.empty else Vector("--" + EntityRevisionTransport.observedRevisionPropertyName + "=" + observed))
        val request = org.goldenport.protocol.handler.ingress.ArgsIngress().encode(session.component.protocol.services, args.toArray).TAKE
          .copy(component = Some(session.component.name))
        val active = session.execution(authenticated = authenticated)
        _response_(session.execute(request, active).result, active)
      case "mcp" =>
        val arguments = io.circe.Json.fromFields(Vector("resourceType" -> io.circe.Json.fromString(resourceType),
          "screenId" -> io.circe.Json.fromString(screenId), "payload" -> io.circe.Json.fromString(payload),
          "cncf.security.privilege" -> io.circe.Json.fromString("user"), "principalId" -> io.circe.Json.fromString("owner"),
          "cncf.security.authenticated" -> io.circe.Json.fromString(authenticated.toString),
          "cncf.context.traceId" -> io.circe.Json.fromString(observability.traceId.print),
          "cncf.context.correlationId" -> io.circe.Json.fromString(observability.correlationId.get.print)) ++
          (if (operation == DisplayMutation.Operation.Create) Vector.empty else Vector(EntityRevisionTransport.observedRevisionPropertyName -> io.circe.Json.fromString(observed.toString))))
        val response = lifecycleMcp(session, name, arguments)
        val failed = response.hcursor.downField("result").get[Boolean]("isError").toOption.contains(true) || response.hcursor.downField("error").focus.nonEmpty
        val value = if (failed) None else {
          val text = response.hcursor.downField("result").downField("content").downN(0).get[String]("text").toOption.get
          val parsed = io.circe.parser.parse(text).toOption.get
          Some(parsed.hcursor.downField("data").focus.getOrElse(parsed))
        }
        LifecycleAdapterResult(value, None, Some(response), if (failed) 400 else 200)
      case "spi" =>
        val active = session.execution(authenticated = authenticated)
        given ExecutionContext = active.context
        val args = Vector[(String, Any)]("resourceType" -> resourceType, "screenId" -> screenId, "payload" -> payload) ++
          (if (operation == DisplayMutation.Operation.Create) Vector.empty else Vector(EntityRevisionTransport.observedRevisionPropertyName -> observed.toString))
        session.subsystem.spiInvoker.invoke(spiContract, SpiOperationSelector(name, Some("display")), Record.dataAuto(args*),
          ComponentSelector(component = Some(session.component.name))) match {
          case Consequence.Success(value) => LifecycleAdapterResult(Some(io.circe.parser.parse(value.toJsonString).toOption.get), None, None, 200, Some(active))
          case Consequence.Failure(error) => LifecycleAdapterResult(None, Some(error), None, 400, Some(active))
        }
      case _ => throw new IllegalArgumentException("Unknown lifecycle adapter")
    }
  }

  def lifecycleMcp(session: Session, operation: String, arguments: io.circe.Json): io.circe.Json = {
    val request = io.circe.Json.obj("jsonrpc" -> io.circe.Json.fromString("2.0"), "id" -> io.circe.Json.fromString(operation),
      "method" -> io.circe.Json.fromString("tools/call"), "params" -> io.circe.Json.obj(
        "name" -> io.circe.Json.fromString(session.component.name + ".display." + operation), "arguments" -> arguments))
    new McpJsonRpcAdapter(session.subsystem).handle(request.noSpaces,
      Some(org.goldenport.cncf.mcp.McpProtocolRevision.PREFERRED.print)).responseBody.get
  }

  final class Log {
    private var _events = Vector.empty[Trace]
    private var _conversions = Vector.empty[String]
    private var _validations = 0
    private var _commands = 0
    private var _authorizations = Vector.empty[(UnitOfWorkAuthorization, UnitOfWork)]
    private var _after_ack = false
    private var _projection_failed = false
    private var _id_calls = Vector.empty[(EntityCollectionId, String)]
    private var _queries = Vector.empty[(Query[?], ExecutionContext)]
    var rejectDelete: Boolean = false
    var reload: Reload = Reload.Current
    var captureFault: CaptureFault = CaptureFault.None
    var availability: Consequence[DisplayMutation.Availability] = Consequence.success(DisplayMutation.Availability.enabled)
    var availabilityMode: Reload = Reload.Current
    var denyFactory: Boolean = false
    var rejectTransition: Boolean = false
    def events: Vector[Trace] = _events
    def conversions: Vector[String] = _conversions
    def validations: Int = _validations
    def commands: Int = _commands
    def authorizations: Vector[(UnitOfWorkAuthorization, UnitOfWork)] = _authorizations
    def afterAck: Boolean = _after_ack
    def idCalls: Vector[(EntityCollectionId, String)] = _id_calls
    def queries: Vector[(Query[?], ExecutionContext)] = _queries
    def query(value: Query[?], context: ExecutionContext): Unit = { _queries :+= value -> context; record("query", context) }
    def generated(collection: EntityCollectionId, purpose: String): Unit = { _id_calls :+= collection -> purpose }
    def rejectCaptureProjection(): Boolean = {
      if (_after_ack && captureFault == CaptureFault.Projection && !_projection_failed) { _projection_failed = true; true }
      else false
    }
    def available(context: ExecutionContext): Consequence[DisplayMutation.Availability] = {
      record("availability", context)
      availabilityMode match {
        case Reload.Failure => projectionFailure
        case Reload.NullConsequence => null
        case Reload.NullSuccess => Consequence.success(null)
        case Reload.Throw => throw new IllegalStateException("private availability credential")
        case _ => availability
      }
    }
    def record(event: String, context: ExecutionContext): Unit = { _events :+= Trace(event, context, context.unitOfWork) }
    def convert(name: String): Unit = { _conversions :+= name }
    def validate(): Unit = { _validations += 1 }
    def command(): Unit = { _commands += 1 }
    def authorize(value: UnitOfWorkAuthorization, uow: UnitOfWork): Unit = { _authorizations :+= value -> uow; record("authorize", uow.executionContext) }
    def acknowledged(context: ExecutionContext): Unit = { _after_ack = true; record("capture", context) }
    def reset(): Unit = {
      _events = Vector.empty; _conversions = Vector.empty; _validations = 0; _commands = 0
      _authorizations = Vector.empty; _after_ack = false; _projection_failed = false
      _id_calls = Vector.empty
      _queries = Vector.empty
    }
  }

  final class Probe(log: Log, lifecycle: Boolean = false) extends EntityPersistent[Root] {
    private val _canonical = DisplayMutationServiceDomain.persistent
    def id(entity: Root): EntityId = _canonical.id(entity)
    def toRecord(entity: Root): Record = _canonical.toRecord(entity)
    override def toStoreRecord(entity: Root): Record = _canonical.toStoreRecord(entity)
    def fromRecord(record: Record): Consequence[Root] = _canonical.fromRecord(record)
    override def fromStoreRecord(record: Record): Consequence[Root] =
      if (lifecycle && log.rejectCaptureProjection()) projectionFailure else _canonical.fromStoreRecord(record)
    override private[cncf] def admitStoreRecord(record: Record): Consequence[Record] =
      _canonical.admitStoreRecord(record)
    override private[cncf] def decodeAdmittedStoreRecord(record: Record): Consequence[Root] =
      _canonical.decodeAdmittedStoreRecord(record)
  }

  final class HydrationFaultStore(underlying: DataStore, log: Log, selectedId: EntityId)
      extends DataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    private val _provider = underlying match {
      case provider: EntityVersionedMutationDataStore => provider
      case _ => throw new IllegalStateException("normal update requires a versioned mutation provider")
    }
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      _provider.entityMutationProviderCapabilities
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] =
      underlying match {
        case provider: EntityCreateDataStore => provider.createEntity(plan)
        case _ => super.createEntity(plan)
      }
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      underlying.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      if (collection.collectionName == "cncf_content_body_overflow" &&
          id == DataStore.StringEntryId(selectedId.value + ":content") && log.rejectCaptureProjection()) projectionFailure
      else underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      underlying.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      underlying.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] =
      underlying.delete(collection, id)
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = underlying.prepare(tx)
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = underlying.commit(tx)
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = underlying.abort(tx)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      _provider.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      _provider.compareAndSetEntity(plan)
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] =
      _provider.mutateVersionedEntity(plan)
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] =
      _provider.compareAndDeleteEntity(plan)
  }

  /** Lifecycle-only observer: all results and effects belong to the actual selected provider. */
  final class LifecycleRecordingStore(val underlying: DataStore, log: Log, selectedId: EntityId,
    removed: Set[EntityMutationProviderFeature]) extends DataStore with EntityVersionedMutationDataStore with EntityCreateDataStore
      with SearchableDataStore {
    private val _delegate = new HydrationFaultStore(underlying, log, selectedId)
    private var _creates = Vector.empty[EntityCreatePlan]
    private var _deletes = Vector.empty[EntityCompareAndDeletePlan]
    private var _created = Vector.empty[EntityCreateResult]
    private var _deleted = Vector.empty[EntityCompareAndDeleteResult]
    private var _writes = 0
    private var _armed = false
    private var _lifecycle = Vector.empty[String]
    private var _searches = Vector.empty[(DataStore.CollectionId, QueryDirective)]
    private var _counts = Vector.empty[(DataStore.CollectionId, QueryDirective)]
    private var _updates = Vector.empty[EntityVersionedMutationPlan]
    def updates: Vector[EntityVersionedMutationPlan] = _updates
    def searches: Vector[(DataStore.CollectionId, QueryDirective)] = _searches
    def counts: Vector[(DataStore.CollectionId, QueryDirective)] = _counts
    override def totalCountCapability(collection: DataStore.CollectionId): TotalCountCapability = underlying match {
      case searchable: SearchableDataStore => searchable.totalCountCapability(collection)
      case _ => TotalCountCapability.Unsupported
    }
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[org.goldenport.cncf.datastore.SearchResult] = {
      _searches :+= collection -> directive
      underlying match {
        case searchable: SearchableDataStore => searchable.search(collection, directive)
        case _ => Consequence.dataStoreUnavailable(s"datastore is not searchable: ${collection.print}")
      }
    }
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] = {
      _counts :+= collection -> directive
      underlying match {
        case searchable: SearchableDataStore => searchable.count(collection, directive)
        case _ => Consequence.dataStoreUnavailable(s"datastore is not countable: ${collection.print}")
      }
    }
    def creates: Vector[EntityCreatePlan] = _creates
    def deletes: Vector[EntityCompareAndDeletePlan] = _deletes
    def created: Vector[EntityCreateResult] = _created
    def deleted: Vector[EntityCompareAndDeleteResult] = _deleted
    def writes: Int = _writes
    def lifecycle: Vector[String] = _lifecycle
    def reset(armed: Boolean = true): Unit = {
      _creates = Vector.empty; _deletes = Vector.empty; _created = Vector.empty; _deleted = Vector.empty
      _writes = 0; _lifecycle = Vector.empty; _armed = armed; _searches = Vector.empty; _counts = Vector.empty; _updates = Vector.empty
    }
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = {
      val actual = _delegate.entityMutationProviderCapabilities
      actual.copy(features = actual.features -- removed)
    }
    def create(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.create(cid, id, record)
    }
    def load(cid: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      _delegate.load(cid, id)
    def save(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.save(cid, id, record)
    }
    def update(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.update(cid, id, record)
    }
    def delete(cid: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.delete(cid, id)
    }
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = { _lifecycle :+= "prepare"; underlying.prepare(tx) }
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _lifecycle :+= "commit"; underlying.commit(tx) }
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _lifecycle :+= "abort"; underlying.abort(tx) }
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      _creates :+= plan
      _delegate.createEntity(plan).map { result =>
        _created :+= result
        if (_armed) log.acknowledged(ctx)
        result
      }
    }
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] = {
      _deletes :+= plan
      _delegate.compareAndDeleteEntity(plan).map { result =>
        _deleted :+= result
        if (_armed && result.isInstanceOf[EntityCompareAndDeleteResult.Deleted]) log.acknowledged(ctx)
        result
      }
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      _delegate.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      _delegate.compareAndSetEntity(plan)
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      _updates :+= plan
      _delegate.mutateVersionedEntity(plan)
    }
  }

  /** Wrap only the provider returned by normal ComponentDataStore selection. */
  final class ObservedSpace(log: Log, context: () => ExecutionContext, fallback: DataStore, selectedId: EntityId,
    lifecycle: Option[LifecycleSetup] = None) extends DataStoreSpace {
    private var _observing = false
    private var _providers = Vector.empty[(DataStore, EntityVersionedMutationPortFixture.RecordingStore)]
    private var _lifecycle_providers = Vector.empty[(DataStore, LifecycleRecordingStore)]
    useDataStore(fallback)
    def observing(value: Boolean): Unit = { _observing = value }
    def observers: Vector[EntityVersionedMutationPortFixture.RecordingStore] = _providers.map(_._2)
    def lifecycleObservers: Vector[LifecycleRecordingStore] = _lifecycle_providers.map(_._2)
    override def dataStore(cid: DataStore.CollectionId): Consequence[DataStore] = super.dataStore(cid).map { actual =>
      if (!_observing) actual
      else if (lifecycle.nonEmpty) _lifecycle_providers.find(pair => pair._1 eq actual).map(_._2).getOrElse {
        val observer = new LifecycleRecordingStore(actual, log, selectedId, lifecycle.get.removed)
        observer.reset()
        _lifecycle_providers :+= actual -> observer
        observer
      }
      else _providers.find(pair => pair._1 eq actual).map(_._2).getOrElse {
        val observer = new EntityVersionedMutationPortFixture.RecordingStore(new HydrationFaultStore(actual, log, selectedId))
        observer.reset(() => log.acknowledged(context()))
        _providers :+= actual -> observer
        observer
      }
    }
  }

  final class NormalComponent(val token: String, val target: Target, val representation: EntityRevisionRepresentation,
    val log: Log, registration: Registration = Registration.Normal, val lifecycleSetup: Option[LifecycleSetup] = None) extends Component()
      with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider
      with DisplayMutationServiceBindingProvider with SpiProviderComponent {
    val rootName: String = token
    val aliasName: String = token + "alias"
    val collectionId: EntityCollectionId = EntityCollectionId("sys", "sys", token)
    val id: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "selected", collectionId, entropy = "same_selected")
    val unrelatedId: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "unrelated", collectionId, entropy = "same_unrelated")
    val probe = new Probe(log, lifecycleSetup.nonEmpty)
    val realm = new EntityRealm[Root](rootName, EntityLoader[Root](_ => None), new IdRef(EntityRealmState[Root](Map.empty)))(using probe) {
      override def remove(id: EntityId): Boolean = {
        if (log.afterAck && log.captureFault == CaptureFault.Coherence) throw new IllegalStateException("private coherence credential")
        super.remove(id)
      }
    }
    val memory = new PartitionedMemoryRealm[Root](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id)
    val collection = new EntityCollection(EntityDescriptor(collectionId, EntityRuntimePlan[Root](rootName,
      EntityMemoryPolicy.LoadToMemory, None, partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 4, maxEntitiesPerPartition = 16, concurrencyPolicy = EntityConcurrencyPolicy.Optimistic), probe,
      if (registration == Registration.Unmanaged) None else Some(EntityRevisionBinding(representation))), EntityStorage(realm, Some(memory)))
    entitySpace.registerEntity(rootName, collection)
    override def aggregateDefinitions: Vector[AggregateDefinition] = Vector(AggregateDefinition(rootName, rootName), AggregateDefinition(aliasName, rootName))
    override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
      CmlOperationDefinition(name = "changeFields", kind = "command",
        inputType = "Root", outputType = "Root", inputValueKind = "record", access = Some(CmlOperationAccess("authenticated_only"))),
      CmlOperationDefinition(name = "update", kind = "command",
        inputType = "Root", outputType = "Root", inputValueKind = "record", access = Some(CmlOperationAccess("authenticated_only")))) ++
      (if (lifecycleSetup.nonEmpty) Vector("create", "delete", "createRoot").map(name =>
        CmlOperationDefinition(name = name, kind = "command", inputType = "Root", outputType = "Root",
          inputValueKind = "record", access = Some(CmlOperationAccess("authenticated_only")))) else Vector.empty)
    private val _aggregates = new AggregateCollection(new ContextualAggregateBuilder[Root] {
      def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
        log.record("aggregate", ctx)
        ctx.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(id, probe))
          .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
      }
    }, queryfn = new ContextualAggregateQuery[Root] {
      def query_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Vector[Root]] = {
        if (!lifecycleSetup.exists(_.withList)) Consequence.notImplemented("AggregateCollection.query is not supported")
        else _list_search(q).flatMap { roots =>
          roots.foldLeft(Consequence.success(Vector.empty[Root])) { (result, root) =>
            result.flatMap { values =>
              aggregateSpace.collection[Root](ownerName).resolve_with_context(root.id).flatMap { resolved =>
                loadC(root.id).map { current =>
                  values :+ resolved.copy(record = resolved.record.upsertSingle(representation.storageFieldName,
                    current.record.getAny(representation.storageFieldName).get))
                }
              }
            }
          }
        }
      }
    })
    aggregateSpace.register(rootName, _aggregates)
    aggregateSpace.register(aliasName, _aggregates)
    private var _view_builds = 0
    def viewBuilds: Int = _view_builds
    val view = new ViewCollection(new ContextualViewBuilder[Root] {
      def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
        _view_builds += 1
        loadC(id)(using ctx)
      }
    })
    val browser: Browser[Root] = new Browser[Root] {
      def find(id: EntityId): Consequence[Root] = Consequence.operationInvalid("context-required")
      def query(query: Query[?]): Consequence[Vector[Root]] = Consequence.operationInvalid("context-required")
      override def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
        log.record("find", ctx)
        log.reload match {
          case Reload.Failure => projectionFailure
          case Reload.NullConsequence => null
          case Reload.NullSuccess => Consequence.success(null)
          case Reload.Throw => throw new IllegalStateException("private Browser credential")
          case mode => loadC(if (mode == Reload.WrongId) unrelatedId else id).map { root =>
            if (mode == Reload.Older || mode == Reload.Newer) {
              val version = EntityRevisionBinding(representation).revision(root.record).TAKE
              root.copy(record = root.record.upsertSingle(representation.storageFieldName, if (mode == Reload.Older) math.max(1L, version.value - 1L) else version.value + 1L))
            } else root
          }
        }
      }
      override def query_with_context(query: Query[?])(using ctx: ExecutionContext): Consequence[Vector[Root]] = {
        if (!lifecycleSetup.exists(_.withList)) {
          log.record("query", ctx); loadC(id).flatMap(selected => loadC(unrelatedId).map(other => Vector(selected, other)))
        } else {
          log.query(query, ctx)
          if (log.reload == Reload.Failure) projectionFailure
          else {
            val found = if (target == Target.Entity) _list_search(query).flatMap { roots =>
              roots.foldLeft(Consequence.success(Vector.empty[Root])) { (result, root) =>
                result.flatMap(values => loadC(root.id).map(values :+ _))
              }
            } else aggregateSpace.query_with_context[Root](ownerName, query)
            found.map(_.sortBy(_.id.value))
          }
        }
      }
    }
    viewSpace.register(rootName, view, browser)
    private def _list_search(query: Query[?])(using ctx: ExecutionContext): Consequence[Vector[Root]] = {
      val authorization = UnitOfWorkAuthorization(resourceFamily = "domain", resourceType = Some(rootName),
        collectionName = Some(collectionId.name), accessKind = "search/list", sourceComponentName = Some(name),
        targetComponentName = Some(name), entityNames = Vector(rootName), accessMode = EntityAccessMode.UserPermission,
        visibilityScope = Some(EntityVisibilityScope.Owner))
      new UnitOfWorkInterpreter(ctx.unitOfWork).interpret(UnitOfWorkOp.EntityStoreSearchDirect(
        EntityQuery[Root](collectionId, query, EntitySearchScope.Store, Some(EntityVisibilityScope.Owner)), probe,
        Some(authorization))).map(_.data)
    }
    def loadC(identity: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
      if (representation == EntityRevisionRepresentation.Embedded)
        ctx.entityStoreSpace.loadSnapshot(identity, probe).flatMap(value => Consequence.successOrEntityNotFound(value)(identity))
          .map(value => value.entity.copy(record = value.entity.record.upsertSingle(representation.storageFieldName, value.revision.value)))
      else ctx.entityStoreSpace.loadDetached(identity, probe).flatMap(value => Consequence.successOrEntityNotFound(value)(identity))
        .map(value => value.entity.copy(record = value.entity.record.upsertSingle(representation.storageFieldName, value.revision.value)))
    }
    def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition] = DisplayMutationServiceDomain.definitions.flatMap { definition =>
      val selected = definition.copy(provenance = definition.provenance.copy(sourceId = "typed-normal-component-" + token))
      Vector(selected) ++ (if (lifecycleSetup.exists(_.mixed)) Vector(selected.copy(screenId = "legacy-detail")) else Vector.empty) ++
        (if (lifecycleSetup.exists(_.withList)) Vector(DisplayMutationServiceListFixture.listDefinition(selected)) else Vector.empty)
    }
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      val row = DisplayMutationServiceDomain.row { root =>
        log.reload match {
          case Reload.RowFailure => projectionFailure
          case Reload.RowNull => null
          case Reload.RowNullSuccess => Consequence.success(null)
          case Reload.RowThrow => throw new IllegalStateException("private row credential")
          case _ => EntityRevisionBinding(representation).revision(root.record).map(DisplayModel.Resource(root.id, _))
        }
      }
      val selected = if (registration == Registration.UnregisteredBrowser) new Browser[Root] {
        def find(id: EntityId): Consequence[Root] = projectionFailure
        def query(query: Query[?]): Consequence[Vector[Root]] = projectionFailure
      } else browser
      Vector(DisplayRuntime.detail(screenId, "ess.reference", selected, row)) ++
        (if (lifecycleSetup.exists(_.mixed)) Vector(DisplayRuntime.detail("legacy-detail", "ess.reference", selected, row)) else Vector.empty) ++
        (if (lifecycleSetup.exists(_.withList)) Vector(DisplayRuntime.list(DisplayMutationServiceListFixture.listScreenId,
          "ess.reference", selected, row, Vector.empty)) else Vector.empty)
    }
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = Vector(DisplayService.ResourceBinding(resourceType, screenId)) ++
      (if (lifecycleSetup.exists(_.mixed)) Vector(DisplayService.ResourceBinding(resourceType, "legacy-detail")) else Vector.empty) ++
      (if (lifecycleSetup.exists(_.withList)) Vector(DisplayService.ResourceBinding(resourceType, DisplayMutationServiceListFixture.listScreenId)) else Vector.empty)
    def legacyBinding: DisplayMutationBinding.Binding[PatchShape] = DisplayMutationServiceDomain.binding(
      DisplayProtocol.Source.createC(componentId, resourceType).TAKE, ownerName, target != Target.Entity,
      log.convert, () => log.validate(), GeneratedLogicalUiAbi.admitC(generatedLogicalUiDefinitions).TAKE, "legacy-detail")
    def inputBinding: DisplayMutationBinding.Binding[PatchShape] = {
      val source = DisplayProtocol.Source.createC(if (registration == Registration.ForeignComponent) ComponentId("org.goldenport.cncf.test.Foreign") else componentId,
        if (registration == Registration.ForeignResource) "foreign-resource" else resourceType).TAKE
      val definitions = generatedLogicalUiDefinitions.map { definition => registration match {
        case Registration.DefinitionRevision => definition.copy(definitionRevision = "foreign-definition")
        case Registration.Producer => definition.copy(provenance = definition.provenance.copy(producerId = "foreign-producer"))
        case Registration.Source => definition.copy(provenance = definition.provenance.copy(sourceId = "foreign-source"))
        case Registration.ProvenanceRevision => definition.copy(provenance = definition.provenance.copy(revision = "foreign-revision"))
        case Registration.WrongScreen => definition.copy(screenId = "foreign-screen")
        case _ => definition
      } }
      DisplayMutationServiceDomain.binding(source, ownerName, target != Target.Entity, log.convert, () => log.validate(),
        GeneratedLogicalUiAbi.admitC(definitions).TAKE, if (registration == Registration.WrongScreen) "foreign-screen" else screenId,
        target => if (registration == Registration.WrongFamily) DisplayMutationBinding.UpdateTarget.Aggregate(ownerName, "title")
          else if (registration == Registration.WrongOwner) DisplayMutationBinding.UpdateTarget.Entity("foreign-owner", "title") else target,
        aliasDescription = registration == Registration.AliasedTarget,
        capabilities = if (lifecycleSetup.nonEmpty) lifecycleSetup.get.capabilities else
          DisplayMutation.Capabilities(DisplayMutation.Support.Supported, DisplayMutation.Support.Unsupported,
            DisplayMutation.Support.Unsupported, DisplayMutation.Support.Supported, DisplayMutation.Support.Unsupported))
    }
    def createSeed: CreateShape = DisplayMutationServiceDomain.createSeed(
      if (lifecycleSetup.exists(_.supplied)) Some(id) else None, lifecycleSetup.map(_.bodyMode).getOrElse(BodyMode.Overflow), body)
    def createBinding: DisplayMutationBinding.Binding[CreateShape] = DisplayMutationServiceDomain.createBinding(
      inputBinding.selection.source, ownerName, target != Target.Entity, log.convert, () => log.validate(),
      GeneratedLogicalUiAbi.admitC(generatedLogicalUiDefinitions).TAKE,
      capabilities = lifecycleSetup.map(_.capabilities).getOrElse(DisplayMutationServiceDomain.lifecycleCapabilities))
    def ownerName: String = if (target == Target.Alias) aliasName else rootName
    def displayMutationBindings(component: Component): Vector[DisplayMutationRuntime.Binding] = {
      registration match {
        case Registration.NullProvider => return null
        case Registration.ThrowProvider => throw new IllegalStateException("private provider credential")
        case Registration.Empty => return Vector.empty
        case Registration.ListBranch =>
          val definition = domain.logicalui.DomainLogicalUiDefinitions.definitions.head
          DisplayMutationBinding.admitC[PatchShape](DisplayProtocol.Source.createC(componentId, resourceType).TAKE,
            GeneratedLogicalUiAbi.admitC(Vector(definition, domain.logicalui.DomainLogicalUiDefinitions.definitions(1))).TAKE,
            definition.screenId, "normal-update-1", inputBinding.capabilities, Vector.empty, Set.empty, Consequence.success).TAKE
        case _ => ()
      }
      val canonical = DisplayMutationServiceDomain.patchPersistent(collectionId)
      val persistent = new EntityPersistentUpdate[PatchShape] {
        def collection(patch: PatchShape): EntityCollectionId = if (registration == Registration.WrongCollection) EntityCollectionId("sys", "sys", "foreign") else canonical.collection(patch)
        def toRecord(patch: PatchShape): Record = canonical.toRecord(patch)
        override def toStoreRecord(patch: PatchShape): Record = canonical.toStoreRecord(patch)
        def fromRecord(record: Record): Consequence[PatchShape] = canonical.fromRecord(record)
        override def storeFieldName(logicalName: String): String = registration match {
          case Registration.DuplicateStorage => "title"
          case Registration.ManagedStorage => "updated_at"
          case _ => canonical.storeFieldName(logicalName)
        }
      }
      val binding = inputBinding
      val seed = if (registration == Registration.UnselectedSeed) DisplayMutationServiceDomain.seed.copy(description = Update.set("private seed")) else DisplayMutationServiceDomain.seed
      val registered = if (lifecycleSetup.nonEmpty) {
        val create = createBinding
        def _validate_delete_(root: Root): Consequence[Unit] = {
          log.validate()
          if (log.rejectDelete) domainFailure else Consequence.unit
        }
        if (target == Target.Entity) DisplayMutationRuntime.entityLifecycleC(binding, ownerName, collection,
          DisplayMutationServiceDomain.fieldIds, seed, persistent, create, createSeed,
          DisplayMutationServiceDomain.createPersistent(collectionId), _validate_delete_, log.available).TAKE
        else DisplayMutationRuntime.aggregateLifecycleC[Root, CreateShape, PatchShape](binding, ownerName, "changeFields",
          DisplayMutationServiceDomain.fieldIds, seed, persistent,
          (root, patch) => { log.command(); DisplayMutationServiceDomain.command(root, patch) }, create, createSeed,
          DisplayMutationServiceDomain.createPersistent(collectionId), "createRoot",
          input => { log.command(); Consequence.success(input) }, _validate_delete_, log.available).TAKE
      } else if (target == Target.Entity) DisplayMutationServiceDomain.entityRegistration(binding, ownerName, collection,
        seed, persistent, context => {
          log.available(context)
        }).TAKE
      else DisplayMutationServiceDomain.aggregateRegistration(binding, ownerName,
        seed, persistent,
        (root, patch) => { log.command(); DisplayMutationServiceDomain.command(root, patch) }, context => {
          log.available(context)
        }).TAKE
      if (registration == Registration.Duplicate) Vector(registered, registered)
      else if (lifecycleSetup.exists(_.mixed)) {
        val legacy = if (target == Target.Entity) DisplayMutationServiceDomain.entityRegistration(legacyBinding, ownerName, collection,
          DisplayMutationServiceDomain.seed, canonical, log.available).TAKE
        else DisplayMutationServiceDomain.aggregateRegistration(legacyBinding, ownerName, DisplayMutationServiceDomain.seed,
          canonical, (root, patch) => { log.command(); DisplayMutationServiceDomain.command(root, patch) }, log.available).TAKE
        Vector(registered, legacy)
      } else Vector(registered)
    }
    def spiProviders: Vector[SpiProvider[?]] = Vector(new SpiProvider[UpdateApi] with SpiOperationProvider {
      def spiOperations(contract: String): Vector[SpiOperationSelector] =
        if (contract == spiContract.name) (Vector("update") ++ (if (lifecycleSetup.nonEmpty) Vector("create", "delete") else Vector.empty))
          .map(name => SpiOperationSelector(name, Some("display"))) else Vector.empty
      def supports(contract: SpiContract[UpdateApi], selection: SpiSelection)(using ExecutionContext): Boolean = contract.name == spiContract.name
      def provide(contract: SpiContract[UpdateApi], selection: SpiSelection)(using ExecutionContext): Consequence[UpdateApi] = Consequence.success(new UpdateApi {})
    })
  }

  final class Execution(session: Session, val mode: Commit = Commit.Normal, principal: String = "owner", historical: Boolean = false,
    authenticated: Boolean = true) {
    val space = new ObservedSpace(session.log, () => context, session.provider, session.component.id, session.component.lifecycleSetup)
    private val _commit_store = new CommitStore(mode)
    private val _entities = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "normal-update-root", None, observability)
    private val _component_scope = Component.Context("normal-update-component", _root_scope, session.component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "normal-update", parent = Some(_component_scope),
        observabilityContext = _root_scope.observabilityContext, httpDriverOption = None,
        datastore = Some(new DataStoreContext(space)), entitystore = Some(new EntityStoreContext(_entities)),
        entityspace = Some(new EntitySpaceContext(session.component.entitySpace))),
      unitofworksupplier = () => uow, unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(uow).interpret(operation)
      }, commitaction = active => {
        session.log.record("commit", context)
        mode match {
          case Commit.Noop => ()
          case Commit.Abort => success(active.abort()); ()
          case Commit.Throw => throw new IllegalStateException("current commit rejected")
          case Commit.Dispose => success(active.dispose()); ()
          case _ => active.commit(); ()
        }
      }, abortaction = active => { session.log.record("abort", context); success(active.abort()); () },
      disposeaction = _ => (), token = "normal-update", operationMode = OperationMode.Test,
      transitionValidationHook = new TransitionValidationHook {
        private def _admit: Consequence[Unit] = if (session.log.rejectTransition) projectionFailure else Consequence.unit
        def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _admit
        def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = _admit
        def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] = _admit
      })
    lazy val context: ExecutionContext = {
      val initial = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val selected = initial.copy(core = initial.core.copy(clock = clock), cncfCore = initial.cncfCore.copy(observability = observability, security = SecurityContext(new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = if (authenticated) Map("access_token" -> "normal-fixture") else Map.empty
      }, Set.empty, SecurityLevel("test"))))
      if (session.component.lifecycleSetup.isEmpty) selected
      else ExecutionContext.withIdGenerationContext(selected, new IdGenerationContext {
        val namespace: IdGenerationContext.IdNamespace = IdGenerationContext.IdNamespace("normal", "selected")
        def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
          require(collection == session.component.collectionId && purpose == "entity-create")
          session.log.generated(collection, purpose)
          session.component.id
        }
        def opaqueId(purpose: String): String = "lifecycle-" + purpose
      })
    }
    lazy val uow: UnitOfWork = new UnitOfWork(context, org.goldenport.cncf.event.EventEngine.noop(_commit_store))
    def bind(): Unit = {
      success(session.subsystem.bindManagedApplicationDataStoreC(space,
        ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(session.subsystem.configuration)),
        ComponentDataStore.Request.forComponent(session.component.componentId)))
    }
    def prepare(): Unit = {
      bind()
      if (historical) success(uow.commit())
      _commit_store.arm()
      if (mode == Commit.Cleanup) uow.stagePostCommitC(projectionFailure)
      space.observing(true)
    }
    def attempts: Int = space.observers.map(_.attempts).sum
    def lifecycleObservers: Vector[LifecycleRecordingStore] = space.lifecycleObservers
  }
  /** Actual EventEngine commit participant rejects inside UnitOfWork.commit. */
  final class CommitStore(mode: Commit) extends DataStore {
    private val _delegate = DataStore.noop()
    private var _armed = false
    def arm(): Unit = { _armed = true }
    def isAccept(cid: DataStore.CollectionId): Boolean = _delegate.isAccept(cid)
    def create(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ExecutionContext): Consequence[Unit] = _delegate.create(cid, id, record)
    def load(cid: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext): Consequence[Option[Record]] = _delegate.load(cid, id)
    def save(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ExecutionContext): Consequence[Unit] = _delegate.save(cid, id, record)
    def update(cid: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ExecutionContext): Consequence[Unit] = _delegate.update(cid, id, record)
    def delete(cid: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext): Consequence[Unit] = _delegate.delete(cid, id)
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = _delegate.prepare(tx)
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit =
      if (_armed && mode == Commit.Reject) throw new IllegalStateException("actual EventEngine participant rejected commit") else _delegate.commit(tx)
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = _delegate.abort(tx)
  }

  final class Session(val subsystem: Subsystem, val component: NormalComponent,
    val physical: () => Map[(String, String), StoredTable], val backend: String,
    private val _directory: Option[Path], private val _owns_subsystem: Boolean,
    val ingressSpace: Option[ObservedSpace] = None,
    private val _connection: Option[() => java.sql.Connection] = None) {
    val log: Log = component.log
    val provider: DataStore = success(component.scopeContext.dataStoreSpace.dataStore(DataStore.CollectionId.EntityStore(component.collectionId))) match {
      case observed: LifecycleRecordingStore => observed.underlying
      case actual => actual
    }
    private val _seed = new Execution(this)
    private def _with_seed[A](body: => A): A = success(subsystem._with_managed_datastore_lease_c { _ =>
      Consequence { _seed.bind(); body }
    })
    private def _setup_store(collection: DataStore.CollectionId): DataStore = success(_seed.space.dataStore(collection))
    private def _setup_delete_if_present(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext): Unit = {
      val store = _setup_store(collection)
      success(store.load(collection, id)) match {
        case Some(_) => success(store.delete(collection, id))
        case None => ()
      }
    }
    def execution(mode: Commit = Commit.Normal, principal: String = "owner", historical: Boolean = false,
      authenticated: Boolean = true): Execution = {
      val active = new Execution(this, mode, principal, historical, authenticated); active.prepare(); active
    }
    def request(changes: Vector[DisplayMutation.FieldInput], observed: Long = 1L): Request = rawRequest(payload(changes), observed)
    def rawRequest(payload: String, observed: Long = 1L): Request = Request.of(
      component = component.name, service = "display", operation = "update", properties = List(
        Property("resourceType", resourceType, None), Property("screenId", screenId, None),
        Property("payload", payload, None), Property(EntityRevisionTransport.observedRevisionPropertyName, observed.toString, None)))
    def payload(changes: Vector[DisplayMutation.FieldInput]): String = DisplayMutationCodec.encodeRequestC(
      DisplayMutation.Request.Update(component.inputBinding.selection, component.id, changes)).TAKE
    def duplicateFieldPayload(change: DisplayMutation.FieldInput): String = {
      val original = payload(Vector(change))
      val start = original.indexOf("\"changes\":[") + "\"changes\":[".length
      val end = original.lastIndexOf(']')
      original.substring(0, end) + "," + original.substring(start, end) + original.substring(end)
    }
    def readRequest: Request = Request.of(component = component.name, service = "display", operation = "read", properties = List(
      Property("resourceType", resourceType, None), Property("screenId", screenId, None), Property("resourceId", component.id.value, None)))
    def descriptorRequest: Request = readRequest.copy(operation = "describeMutation", properties = readRequest.properties.filterNot(_.name == "resourceId"))
    def execute(request: Request, active: Execution = execution()): Attempt =
      Attempt(subsystem.executeOperationResponseInContext(request, active.context), active)
    def read(): Attempt = execute(readRequest)
    def descriptor(): Attempt = execute(descriptorRequest)
    def lifecycleRequest(operation: DisplayMutation.Operation, fields: Vector[DisplayMutation.FieldInput] = lifecycleFields,
      observed: Long = 1L): Request = lifecycleRawRequest(operation, lifecyclePayload(operation, fields), observed)
    def lifecycleRawRequest(operation: DisplayMutation.Operation, payload: String, observed: Long = 1L): Request = Request.of(
      component = component.name, service = "display", operation = operation.toString.toLowerCase(java.util.Locale.ROOT),
      properties = List(Property("resourceType", resourceType, None), Property("screenId", screenId, None),
        Property("payload", payload, None)) ++ (if (operation == DisplayMutation.Operation.Create) Nil
          else List(Property(EntityRevisionTransport.observedRevisionPropertyName, observed.toString, None))))
    def lifecyclePayload(operation: DisplayMutation.Operation, fields: Vector[DisplayMutation.FieldInput] = lifecycleFields): String = {
      val request = operation match {
        case DisplayMutation.Operation.Create => DisplayMutation.Request.Create(component.inputBinding.selection, fields)
        case DisplayMutation.Operation.Update => DisplayMutation.Request.Update(component.inputBinding.selection, component.id, fields)
        case DisplayMutation.Operation.Delete => DisplayMutation.Request.Delete(component.inputBinding.selection, component.id)
      }
      DisplayMutationCodec.encodeRequestC(request).TAKE
    }
    def lifecycleObservers: Vector[LifecycleRecordingStore] = ingressSpace.toVector.flatMap(_.lifecycleObservers)

    def prepareLifecycle(create: Boolean, version: Long = 1L, logical: Boolean = component.lifecycleSetup.forall(_.logical)): Unit = {
      require(component.lifecycleSetup.nonEmpty)
      log.captureFault = CaptureFault.None; log.reload = Reload.Current; log.rejectDelete = false
      log.denyFactory = false; log.rejectTransition = false; log.availabilityMode = Reload.Current
      log.availability = Consequence.success(DisplayMutation.Availability.enabled)
      ingressSpace.foreach(_.observing(false))
      lifecycleObservers.foreach(_.reset(false))
      _with_seed {
        given ExecutionContext = _seed.context
        _seed.space.observing(false)
        val root = DataStore.CollectionId.EntityStore(component.collectionId)
        Vector(component.id, component.unrelatedId).foreach { identity =>
          _setup_delete_if_present(root, DataStore.EntryId(identity))
          _setup_delete_if_present(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(identity.value + ":content"))
          component.realm.remove(identity); component.memory.remove(identity)
        }
      }
      seed(initialRevision = version)
      _with_seed {
        given ExecutionContext = _seed.context
        val root = DataStore.CollectionId.EntityStore(component.collectionId)
        if (create) {
          // Keep the real warmed Views/residents stale while proving durable absence.
          _setup_delete_if_present(root, DataStore.EntryId(component.id))
          _setup_delete_if_present(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(component.id.value + ":content"))
          require(success(_setup_store(root).load(root, DataStore.EntryId(component.id))).isEmpty)
        } else if (!logical) {
          val store = _setup_store(root)
          val stored = success(store.load(root, DataStore.EntryId(component.id))).get
          success(store.delete(root, DataStore.EntryId(component.id)))
          success(store.create(root, DataStore.EntryId(component.id), Record(stored.fields.filterNot(_.key == "aliveness"))))
          component.viewSpace.invalidateAll()
          success(component.view.resolve_with_context(component.id)); success(component.view.resolve_with_context(component.unrelatedId))
        }
      }
      log.reset()
      lifecycleObservers.foreach(_.reset())
      ingressSpace.foreach(_.observing(true))
    }

    def lifecycleSnapshot(): LifecycleSnapshot = _with_seed {
      def _view_(identity: EntityId): LifecycleView = component.view.resolve_with_context(identity)(using _seed.context) match {
        case Consequence.Success(root) => LifecycleView.Present(root)
        case Consequence.Failure(found) =>
          val missing = missingConclusion(identity)
          if (found.observation.taxonomy == missing.observation.taxonomy) LifecycleView.Missing(found)
          else throw new IllegalStateException("Unexpected lifecycle View failure: " + found.display)
      }
      val selected = _view_(component.id)
      val unrelated = _view_(component.unrelatedId)
      LifecycleSnapshot(rows(), physical(), LifecycleResident(component.realm.get(component.id), component.realm.get(component.unrelatedId),
        component.memory.get(component.id), component.memory.get(component.unrelatedId), selected, unrelated, component.viewBuilds))
    }

    def prepareLifecycleSchema(): Unit = _connection.foreach { open =>
      val connection = open()
      try Vector(DataStore.CollectionId.EntityStore(component.collectionId).collectionName -> lifecycleRootColumns(component.representation),
        "cncf_content_body_overflow" -> lifecycleBodyColumns).foreach { case (name, columns) =>
        val definitions = columns.map(column => quote(column) + " " +
          (if (column == component.representation.storageFieldName || column == "content_byte_size") {
            if (backend == "postgresql") "BIGINT" else "INTEGER"
          } else "TEXT") +
          (if (column == "id") " PRIMARY KEY" else ""))
        val statement = connection.createStatement()
        try statement.execute("CREATE TABLE IF NOT EXISTS " + quote(name) + " (" + definitions.mkString(",") + ")")
        finally statement.close()
      } finally connection.close()
    }
    def setupLifecycleRoot(transform: Record => Record): Unit = _with_seed {
      given ExecutionContext = _seed.context
      val collection = DataStore.CollectionId.EntityStore(component.collectionId)
      val store = _setup_store(collection)
      val old = success(store.load(collection, DataStore.EntryId(component.id))).get
      success(store.delete(collection, DataStore.EntryId(component.id)))
      success(store.create(collection, DataStore.EntryId(component.id), transform(old)))
      log.reset()
    }
    /** Trusted setup and cleanup only, under the existing managed lease and exact C4C witness allowlist. */
    def setupListWitness(root: Root, principal: String, version: Long): Unit = _with_seed {
      require(component.lifecycleSetup.exists(_.withList))
      require(root.id.collection == component.collectionId && DisplayMutationServiceListFixture.witnessIds(component).contains(root.id))
      val active = new Execution(this, principal = principal)
      active.bind()
      given ExecutionContext = active.context
      val collection = DataStore.CollectionId.EntityStore(component.collectionId)
      _setup_delete_if_present(collection, DataStore.EntryId(root.id))
      _setup_delete_if_present(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(root.id.value + ":content"))
      success(active.context.entityStoreSpace.create(UnitOfWorkOp.EntityStoreCreate(root, EntityPersistentCreate.fromPersistent(component.probe))))
      val store = success(active.space.dataStore(collection))
      val stored = success(store.load(collection, DataStore.EntryId(root.id))).get
      success(store.save(collection, DataStore.EntryId(root.id), stored.upsertSingle(component.representation.storageFieldName, version)))
    }
    def listWitnessRows(identity: EntityId): (Option[Record], Option[Record]) = _with_seed {
      require(DisplayMutationServiceListFixture.witnessIds(component).contains(identity))
      given ExecutionContext = _seed.context
      val collection = DataStore.CollectionId.EntityStore(component.collectionId)
      (success(_setup_store(collection).load(collection, DataStore.EntryId(identity))),
        success(_setup_store(DataStore.CollectionId("cncf_content_body_overflow")).load(DataStore.CollectionId("cncf_content_body_overflow"),
          DataStore.StringEntryId(identity.value + ":content"))))
    }
    def closeListWitnesses(): Unit = _with_seed {
      given ExecutionContext = _seed.context
      _seed.space.observing(false)
      DisplayMutationServiceListFixture.witnessIds(component).foreach { identity =>
        _setup_delete_if_present(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(identity))
        _setup_delete_if_present(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(identity.value + ":content"))
      }
    }
    def seed(title: String = "Before", initialRevision: Long = 1L): Unit = _with_seed {
      given ExecutionContext = _seed.context
      _seed.space.observing(false)
      Vector(component.id -> title, component.unrelatedId -> "Unrelated").foreach { case (id, label) =>
        val source = Root(id, Record.dataAuto("id" -> id, "title" -> label, "description" -> "retained 日本 Ω",
          "number" -> BigDecimal("123.4500"), "flag" -> true, "instant" -> DisplayMutationServiceDomain.instant,
          "nullable" -> "present", "stage" -> "draft", "business" -> "unrelated business", "audit" -> "retained audit", "content" -> body))
        val create = EntityPersistentCreate.fromPersistent(component.probe)
        val existing = success(_seed.context.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(id, component.probe)))
        if (existing.isEmpty) success(_seed.context.entityStoreSpace.create(UnitOfWorkOp.EntityStoreCreate(source, create)))
        else {
          val store = success(_seed.space.dataStore(DataStore.CollectionId.EntityStore(component.collectionId)))
          val old = success(store.load(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(id))).get
          val replaced = old.fields.map { field =>
            if (field.key == "id" || field.key == "content") field
            else source.record.fields.find(_.key == field.key).map(value => field.copy(value = value.value)).getOrElse(field)
          }
          val reset = if (replaced.exists(_.key == "nullable")) Record(replaced)
            else Record(replaced :+ source.record.fields.find(_.key == "nullable").get)
          success(store.save(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(id),
            DisplayMutationServiceDomain.storageRecord(reset).upsertSingle(component.representation.storageFieldName, 1L)))
        }
        if (initialRevision != 1L && id == component.id) {
          val store = success(_seed.space.dataStore(DataStore.CollectionId.EntityStore(component.collectionId)))
          val old = success(store.load(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(id))).get
          success(store.save(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(id), old.upsertSingle(component.representation.storageFieldName, initialRevision)))
        }
        component.collection.putScoped(success(_seed.context.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(id, component.probe))).get)
      }
      component.viewSpace.invalidateAll()
      success(component.view.resolve_with_context(component.id))
      success(component.view.resolve_with_context(component.unrelatedId))
      log.reset()
    }
    def rows(): Rows = _with_seed {
      given ExecutionContext = _seed.context
      val store = success(_seed.space.dataStore(DataStore.CollectionId.EntityStore(component.collectionId)))
      def _load_(identity: EntityId): Option[Record] = success(store.load(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(identity)))
      def _body_(identity: EntityId): Option[Record] = success(store.load(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(identity.value + ":content")))
      Rows(_load_(component.id), _load_(component.unrelatedId), _body_(component.id), _body_(component.unrelatedId))
    }
    def resident: Resident = _with_seed {
      val views = ViewState(success(component.view.resolve_with_context(component.id)(using _seed.context)),
        success(component.view.resolve_with_context(component.unrelatedId)(using _seed.context)))
      Resident(component.realm.get(component.id), component.realm.get(component.unrelatedId), component.memory.get(component.id), component.memory.get(component.unrelatedId), views, component.viewBuilds)
    }
    def expected(before: Rows, tables: Map[(String, String), StoredTable], changes: Vector[DisplayMutation.FieldInput], applied: Boolean,
      version: Long, captureFault: CaptureFault = CaptureFault.None): Expected = {
      val previousresident = resident
      val stored = before.selected.get
      val registrations = component.inputBinding._field_registrations
      val targets = changes.map { input =>
        val registration = registrations.find(_.location == input.location).get
        val key = registration.target match {
          case DisplayMutationBinding.UpdateTarget.Entity(_, fieldid) => fieldid
          case DisplayMutationBinding.UpdateTarget.Aggregate(_, fieldid) => fieldid
        }
        key -> input.value
      }
      def _stored_value_(value: DisplayValue): Option[Any] = value match {
        case DisplayValue.Text(text) => Some("utf16:" + text.iterator.map(character => f"${character.toInt}%04x").mkString)
        case DisplayValue.Number(number) => Some(number.bigDecimal.toString)
        case DisplayValue.Boolean(flag) => Some(if (flag) "true" else "false")
        case DisplayValue.Timestamp(instant) => Some(instant.toString)
        case DisplayValue.Missing => None
      }
      // Forward target metadata fixes aliases; request order is not domain declaration order.
      val patch = DisplayMutationServiceDomain.fieldIds.flatMap(key => targets.find(_._1 == key))
        .map { case (key, value) => key -> _stored_value_(value) }
      val selectedkeys = patch.map(_._1).toSet
      val atkey = stored.fields.map(_.key).find(key => key == "updated_at" || key == "updatedAt").get
      val bykey = stored.fields.map(_.key).find(key => key == "updated_by" || key == "updatedBy").get
      val revisionkey = component.representation.storageFieldName
      val timestamp: Any = if (backend == "memory") fixedInstant else fixedInstant.toString
      val replacements = patch ++ Vector[(String, Option[Any])](revisionkey -> Some(version), atkey -> Some(timestamp), bykey -> Some("owner"))
      def _replace_field_(field: Field): Option[Field] = replacements.find(_._1 == field.key) match {
        case Some((_, value)) => value.map(actual => field.copy(value = Field.Value.Single(actual)))
        case None => Some(field)
      }
      def _ordered_fields_(keys: Vector[String]): Vector[Field] =
        keys.flatMap(key => stored.fields.filter(_.key == key).flatMap(_replace_field_))
      val patchfields = patch.flatMap { case (key, value) => value.map { actual =>
        stored.fields.find(_.key == key).map(field => field.copy(value = Field.Value.Single(actual)))
          .getOrElse(Field(key, Field.Value.Single(actual)))
      } }
      val revisionfield = stored.fields.find(_.key == revisionkey).get.copy(value = Field.Value.Single(version))
      val bodykeys = Vector("content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref")
      val record = if (!applied) stored else if (backend != "memory") {
        // Pre-action columns fix SQL readback order, including restored logical fields.
        val expectedfields = stored.fields.flatMap(_replace_field_) ++
          patchfields.filterNot(field => stored.fields.exists(_.key == field.key))
        val collectionname = DataStore.CollectionId.EntityStore(component.collectionId).collectionName
        val names = tables.find { case (key, _) => key._2 == collectionname }.get._2.columns.map(_._1)
        val order = if (backend == "postgresql") Vector("id") ++ names.filter(_ == revisionkey) ++
          names.filterNot(name => name == "id" || name == revisionkey) else names
        Record(order.flatMap(key => expectedfields.find(_.key == key)))
      } else if (component.target == Target.Entity) {
        // Guarded memory patches precede retained managed fields and the untouched domain tail.
        val tracekeys = Vector("trace_id", "traceId", "correlation_id", "correlationId")
          .filter(key => stored.fields.exists(_.key == key))
        val prefixkeys = Vector(atkey, bykey) ++ tracekeys
        val retainedkeys = Vector("id", "short_id", "name", "created_at", "created_by", "post_status", "aliveness",
          "tenant_id", "organization_id", "publish_at", "public_at", "published_by", "owner_id", "group_id", "privilege_id", "permission")
        val excluded = prefixkeys.toSet ++ retainedkeys.toSet ++ selectedkeys + revisionkey
        Record(_ordered_fields_(prefixkeys) ++ patchfields ++ _ordered_fields_(retainedkeys) ++
          stored.fields.filterNot(field => excluded.contains(field.key)) :+ revisionfield)
      } else {
        // Memory Aggregate full-save moves selected domain fields before unchanged overflow metadata.
        val prefixkeys = Vector("id", "short_id", "name", "created_at", "created_by", atkey, bykey, "post_status", "aliveness",
          "owner_id", "group_id", "privilege_id", "permission", "trace_id", "correlation_id")
        val excluded = prefixkeys.toSet ++ bodykeys.toSet ++ selectedkeys + revisionkey
        Record(_ordered_fields_(prefixkeys) ++ stored.fields.filterNot(field => excluded.contains(field.key)) ++
          patchfields ++ _ordered_fields_(bodykeys) :+ revisionfield)
      }
      val physicalchanges = patch.map { case (key, value) => key -> value.getOrElse(null) }.toMap ++
        Map(revisionkey -> version, "updated_at" -> fixedInstant.toString, "updated_by" -> "owner")
      val wantedtables = if (!applied) tables else tables.map { case (key, table) =>
        key -> table.copy(rows = table.rows.map { row =>
          if (key._2 == DataStore.CollectionId.EntityStore(component.collectionId).collectionName && row.get("id").contains(DataStore.EntryId(component.id).print)) row ++ physicalchanges else row
        })
      }
      // Hydration and decoding use only the expected stored fields and the pre-action body.
      val hydrated = before.body.map(bodyrecord => record.fields.filterNot(_.key == "content") ++ bodyrecord.fields.filter(_.key == "content"))
        .getOrElse(record.fields)
      val decoded = hydrated.filterNot(field => component.representation == EntityRevisionRepresentation.Detached && field.key == revisionkey)
        .map { field => (field.key, field.value.single) match {
          case ("title" | "description" | "nullable", text: String) if text.startsWith("utf16:") =>
            val value = text.drop(6).grouped(4).map(unit => Integer.parseInt(unit, 16).toChar).mkString
            field.copy(value = Field.Value.Single(value))
          case _ => field
        } }
      // The domain decoder appends typed identity; the View appends its explicit revision carrier.
      val root = Root(component.id, Record(decoded.filterNot(_.key == "id") :+ Field("id", Field.Value.Single(component.id))))
      def _expected_view_(): Root = {
        val fields = root.record.fields.filterNot(_.key == revisionkey) :+ Field(revisionkey, Field.Value.Single(version))
        root.copy(record = Record(fields))
      }
      val nextselected = if (captureFault == CaptureFault.Coherence) previousresident.selected
        else if (captureFault == CaptureFault.Projection) None else previousresident.selected.map(_ => root)
      val nextmemory = if (captureFault == CaptureFault.Coherence) previousresident.memoryselected
        else if (captureFault == CaptureFault.Projection) None else previousresident.memoryselected.map(_ => root)
      val nextviews = ViewState(_expected_view_(), previousresident.views.unrelated)
      Expected(before.copy(selected = Some(record)), wantedtables, previousresident.copy(selected = nextselected, memoryselected = nextmemory,
        views = nextviews, builds = previousresident.builds + 2))
    }
    def http(payload: Array[Byte], headers: Vector[(String, String)] = Vector("If-Match" -> "\"revision-1\""),
      method: Method = Method.PUT, operation: String = "update", extra: Vector[(String, String)] = Vector.empty,
      contentType: String = "application/json; charset=UTF-8", authenticated: Boolean = true,
      selectedScreenId: String = screenId): logicalui.DisplayServiceReadFixture.HttpResult = {
      val uri = Uri.unsafeFromString(s"/rest/v1/${component.name}/display/$operation.json").withQueryParams(
        Map("resourceType" -> resourceType, "screenId" -> selectedScreenId, "textus.output.shape" -> "envelope",
          "cncf.security.privilege" -> "user", "cncf.security.principalId" -> "owner", "cncf.security.authenticated" -> authenticated.toString,
          "cncf.context.traceId" -> observability.traceId.print,
          "cncf.context.correlationId" -> observability.correlationId.get.print))
      val selected = if (extra.isEmpty) uri else Uri.unsafeFromString(uri.renderString + extra.map { case (key, value) =>
        "&" + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
      }.mkString)
      val emptyread = method == Method.GET && (operation == "query" || operation == "read") && payload.isEmpty
      val selectedheaders = if (emptyread) headers else Vector("Content-Type" -> contentType) ++ headers
      val rawrequest = HttpRequest[IO](method, selected, headers = Headers(selectedheaders.map {
        case (name, value) => Header.Raw(CIString(name), value)
      }))
      val request = if (emptyread) rawrequest else rawrequest.withBodyStream(fs2.Stream.emits(payload).covary[IO])
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val response = DisplayServiceHttpRouteFixture.app(server).run(request).unsafeRunSync()
      val bytes = response.body.compile.toVector.unsafeRunSync()
      logicalui.DisplayServiceReadFixture.HttpResult(response.status.code,
        response.headers.headers.find(_.name == CIString("Content-Type")).map(_.value.takeWhile(_ != ';')),
        new String(bytes.toArray, StandardCharsets.UTF_8), bytes)
    }
    def close(): Unit = {
      try {
        if (component.lifecycleSetup.nonEmpty) {
          log.captureFault = CaptureFault.None
          ingressSpace.foreach(_.observing(false))
          _with_seed {
            given ExecutionContext = _seed.context
            _seed.space.observing(false)
            Vector(component.id, component.unrelatedId).foreach { identity =>
              _setup_delete_if_present(DataStore.CollectionId.EntityStore(component.collectionId), DataStore.EntryId(identity))
              _setup_delete_if_present(DataStore.CollectionId("cncf_content_body_overflow"), DataStore.StringEntryId(identity.value + ":content"))
            }
          }
        }
      } finally {
        try if (_owns_subsystem) Subsystem.shutdownOwned(subsystem)
        finally _directory.foreach(_remove_sqlite_directory)
      }
    }
  }

  def open(target: Target = Target.Entity, representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Embedded,
    database: Option[PostgresqlPersistenceFixture.Database] = None, registration: Registration = Registration.Normal,
    backend: String = "memory", sharedSession: Option[Session] = None, lifecycle: Option[LifecycleSetup] = None): Session = {
    val token = "normalupdate" + _sequence.incrementAndGet()
    val log = new Log
    val selectedid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "selected", EntityCollectionId("sys", "sys", token), entropy = "same_selected")
    val ingressspace = lifecycle.map(setup => new ObservedSpace(log,
      () => throw new IllegalStateException("Lifecycle observer uses the actual provider execution context"),
      DataStore.inMemorySearchable(), selectedid, Some(setup)))
    val directory = if (backend == "sqlite" && database.isEmpty && sharedSession.isEmpty) {
      val root = Files.createDirectories(Path.of("target", "display-normal-update"))
      Some(Files.createTempDirectory(root, token + "-"))
    } else None
    val sqlitepath = directory.map(_.resolve("application.db"))
    val baseconfiguration = database.map(_.configuration("textus.datastore",
      "textus.datastore.kind" -> "postgresql",
      "textus.component.datastores.application.policy" -> "external-required")).getOrElse {
      if (sqlitepath.nonEmpty) {
        PostgresqlPersistenceFixture.configuration(Vector("textus.component.datastores.application.policy" -> "external-required",
          "textus.datastore.kind" -> "sqlite", "textus.datastore.sqlite.path" -> sqlitepath.get.toString))
      } else ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    }
    val configuration = ResolvedConfiguration(Configuration(baseconfiguration.configuration.values ++ Map(
      "textus.operation-mode" -> ConfigurationValue.StringValue("test"),
      "textus.execution.profile" -> ConfigurationValue.StringValue("controlled"),
      "textus.execution.key" -> ConfigurationValue.StringValue(token),
      "textus.execution.random.seed" -> ConfigurationValue.StringValue(token),
      "textus.execution.time.mode" -> ConfigurationValue.StringValue("manual"),
      "textus.execution.time.start-at" -> ConfigurationValue.StringValue(fixedInstant.toString))), baseconfiguration.trace)
    val subsystem = try sharedSession.map(_.subsystem).getOrElse {
      val config = RuntimeConfig.default.copy(
        executionProfile = success(ExecutionProfileResolver.resolve(configuration, OperationMode.Test)),
        operationMode = OperationMode.Test,
        dataStoreSpace = ingressspace.getOrElse(DataStoreSpace.default()),
        entityStoreSpace = new EntityStoreSpace().addEntityStore(EntityStore.standard()))
      val global = GlobalRuntimeContext.create(token, config, configuration, observability, AliasResolver.empty)
      val child = ScopeContext(ScopeKind.Subsystem, token, Some(global), observability)
      val created = new Subsystem(name = token, scopeContext = Some(child), configuration = configuration,
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command)
      Subsystem.withStartupCleanup(created) { RuntimeBindingAdmissionFixture.admit(created) }
    } catch {
      case scala.util.control.NonFatal(error) =>
        directory.foreach(_remove_sqlite_directory)
        throw error
    }
    try {
      val component = new NormalComponent(token, target, representation, log, registration, lifecycle)
      initialize(component, subsystem)
      success(new ComponentFactory().bootstrapC(component))
      component.withMcpReadyServices(Set("display"))
      subsystem.add(Vector(component))
      given ExecutionContext = ExecutionContext.create()
      val resolution = SpiResolver.resolveAssembly(subsystem.components.toVector, Vector.empty).TAKE
      subsystem.withComponentApiResolver(resolution.componentApiResolver)
      val physical = sharedSession.map(_.physical).orElse(database.map(value => () => value.snapshotAll())).getOrElse {
        sqlitepath.map(path => () => org.goldenport.cncf.action.AggregateObservedSnapshotFixture.sqlitePhysical(path))
          .getOrElse(() => Map.empty[(String, String), StoredTable])
      }
      val result = new Session(subsystem, component, physical, if (database.nonEmpty) "postgresql" else backend,
        directory, sharedSession.isEmpty, ingressspace,
        database.map(value => () => value.source().getConnection()).orElse(sqlitepath.map(path =>
          () => java.sql.DriverManager.getConnection("jdbc:sqlite:" + path.toString))))
      if (lifecycle.nonEmpty) { result.prepareLifecycleSchema(); result.prepareLifecycle(false) }
      else result.seed()
      result
    } catch {
      case scala.util.control.NonFatal(error) =>
        try if (sharedSession.isEmpty) Subsystem.shutdownOwned(subsystem)
        finally directory.foreach(_remove_sqlite_directory)
        throw error
    }
  }

  private def _remove_sqlite_directory(directory: Path): Unit = {
    Vector("application.db-wal", "application.db-shm", "application.db-journal", "application.db").foreach { name =>
      Files.deleteIfExists(directory.resolve(name))
    }
    Files.deleteIfExists(directory)
    ()
  }

  def initialize(component: NormalComponent, subsystem: Subsystem): Unit = {
    val factory = new Component.Factory {
      protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
      protected def create_Component(params: ComponentCreate): Component = new Component() {}
      override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
        component.log.authorize(authorization, uow)
        if (component.log.denyFactory) Some(denied) else None
      }
    }
    logicalui.DisplayServiceReadFixture.initialize(component, subsystem, "NormalUpdate" + component.token,
      Protocol(handler = ProtocolHandler.default), Some(factory))
    ()
  }

  trait Vocabulary extends Matchers with GivenWhenThen { self: org.scalatest.Informing =>
    private def _without_source_positions(value: Conclusion): Conclusion = {
      val observation = value.observation
      val cause = observation.cause
      val descriptor = cause.descriptor
      value.copy(observation = observation.copy(cause = cause.copy(descriptor = descriptor.copy(
        facets = descriptor.facets.filterNot {
          case Descriptor.Facet.SrcPos(_) => true
          case _ => false
        }))))
    }

    def checkLifecycleFailure(found: Conclusion, expected: Conclusion): Unit = {
      found.observation.cause.descriptor.facets.collect {
        case value: Descriptor.Facet.SrcPos => value
      } should not be empty
      found.status shouldBe expected.status
      _without_source_positions(found).isMatch(_without_source_positions(expected)) shouldBe true
    }

    def checkLifecycleDeadTarget(session: Session, boundary: DeleteRejectionBoundary): Unit = {
      Given(s"a reset revision-seven root with warmed selected/unrelated Views and independently chosen $boundary rejection")
      session.prepareLifecycle(false, 7L)
      val warm = session.lifecycleSnapshot()
      val deletedat = Instant.parse("2026-10-14T01:02:03Z")
      session.setupLifecycleRoot { stored =>
        val dead = Record(stored.fields.filterNot(field => Set("aliveness", "deleted_at", "deletedAt").contains(field.key)))
          .upsertSingle("aliveness", "dead")
        if (boundary == DeleteRejectionBoundary.BeforeCapture) dead.upsertSingle("deleted_at", deletedat) else dead
      }
      val before = session.lifecycleSnapshot()
      val wanted = expectedLifecycleDeleteRejection(before, boundary)
      val taxonomy = org.goldenport.observation.Taxonomy.Symptom.NotFound
      val missing = missingConclusion(session.component.id)
      val expectedreason = if (boundary == DeleteRejectionBoundary.CanonicalMissingTarget)
        Vector("entity-mutation-target-not-found") else Vector.empty[String]
      val expectedpolicy = if (boundary == DeleteRejectionBoundary.CanonicalMissingTarget)
        Vector("entity.versioned-mutation") else Vector.empty[String]
      before.rows.selected.get.getString("aliveness") shouldBe Some("dead")
      before.rows.selected.get.getAny("deletedAt") shouldBe None
      before.rows.selected.get.getAny("deleted_at").nonEmpty shouldBe (boundary == DeleteRejectionBoundary.BeforeCapture)
      before.rows.body.map(_.fields) shouldBe warm.rows.body.map(_.fields)
      before.resident shouldBe warm.resident
      before.resident.selectedview shouldBe a[LifecycleView.Present]
      before.resident.unrelatedview shouldBe a[LifecycleView.Present]
      val active = session.execution()
      When("the normal authorized lifecycle request delegates the existing canonical observed-delete policy without a Display dead gate")
      val attempt = session.execute(session.lifecycleRequest(DisplayMutation.Operation.Delete, observed = 7L), active)
      Then("the independently selected failure branch has no receipt or ACK and preserves every durable/native/body/unrelated/View dimension")
      val found = conclusion(attempt.result)
      found.observation.taxonomy.symptom shouldBe taxonomy
      found.status.webCode.code shouldBe 404
      reasons(attempt.result).filter(_ == "entity-mutation-target-not-found") shouldBe expectedreason
      found.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Policy(value) if value == "entity.versioned-mutation" => value
      } shouldBe expectedpolicy
      if (boundary == DeleteRejectionBoundary.BeforeCapture) {
        checkLifecycleFailure(found, missing)
      }
      active.lifecycleObservers.flatMap(_.creates) shouldBe Vector.empty
      active.lifecycleObservers.flatMap(_.deletes) shouldBe Vector.empty
      active.lifecycleObservers.flatMap(_.created) shouldBe Vector.empty
      active.lifecycleObservers.flatMap(_.deleted) shouldBe Vector.empty
      active.lifecycleObservers.map(_.writes).sum shouldBe 0
      session.log.validations shouldBe (if (boundary == DeleteRejectionBoundary.CanonicalMissingTarget) 1 else 0)
      session.log.conversions shouldBe Vector.empty
      session.log.commands shouldBe 0
      session.log.idCalls shouldBe Vector.empty
      session.log.events.count(_.event == "availability") shouldBe (if (boundary == DeleteRejectionBoundary.CanonicalMissingTarget) 1 else 0)
      session.log.events.count(_.event == "capture") shouldBe 0
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.authorizations should not be empty
      session.log.authorizations.foreach { case (authorization, uow) =>
        (uow eq active.uow) shouldBe true
        authorization.resourceFamily shouldBe (if (session.component.target == Target.Entity) "domain" else "aggregate")
        authorization.resourceType shouldBe Some(session.component.ownerName)
        authorization.targetId shouldBe Some(session.component.id)
        authorization.accessKind shouldBe "delete"
        authorization.access shouldBe Some(CmlOperationAccess("authenticated_only"))
        authorization.sourceComponentName shouldBe Some(session.component.name)
        authorization.targetComponentName shouldBe Some(session.component.name)
      }
      checkLifecycleState(session, wanted)
    }

    def checkLifecycleAdapter(session: Session, value: LifecycleAdapterResult, create: Boolean,
      version: Long, wanted: LifecycleSnapshot): Unit = {
      value.status shouldBe 200
      value.failure shouldBe None
      checkLifecycleJson(value.value.get, session, create, version)
      val active = session.log.authorizations.head._2
      active.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      active.lastCommitResult shouldBe Some(Consequence.unit)
      session.log.authorizations.foreach { case (authorization, uow) =>
        (uow eq active) shouldBe true
        authorization.resourceFamily shouldBe (if (session.component.target == Target.Entity) "domain" else "aggregate")
        authorization.resourceType shouldBe Some(session.component.ownerName)
        authorization.targetId shouldBe (if (create) None else Some(session.component.id))
        authorization.accessKind shouldBe (if (create && session.component.target != Target.Entity) "create:createRoot" else if (create) "create" else "delete")
        authorization.access shouldBe Some(CmlOperationAccess("authenticated_only"))
        authorization.sourceComponentName shouldBe Some(session.component.name)
        authorization.targetComponentName shouldBe Some(session.component.name)
      }
      session.log.events.foreach { event =>
        (event.uow eq active) shouldBe true
        event.context.clock.instant() shouldBe fixedInstant
        event.context.security.principal.id.value shouldBe "owner"
        event.context.observability shouldBe observability
      }
      session.log.validations shouldBe 1
      session.log.commands shouldBe (if (create && session.component.target != Target.Entity) 1 else 0)
      session.log.events.count(_.event == "find") shouldBe (if (create) 1 else 0)
      session.log.idCalls shouldBe Vector.empty
      val observers = value.execution.toVector.flatMap(_.lifecycleObservers) ++ session.lifecycleObservers
      observers.flatMap(_.creates) should have size (if (create) 1 else 0)
      observers.flatMap(_.deletes) should have size (if (create) 0 else 1)
      observers.flatMap(_.created) should have size (if (create) 1 else 0)
      observers.flatMap(_.deleted) should have size (if (create) 0 else 1)
      observers.map(_.writes).sum shouldBe 0
      checkLifecycleState(session, wanted)
    }

    def checkLifecycleState(session: Session, wanted: LifecycleSnapshot): Unit = {
      When("independent canonical readers resolve full durable, physical, resident and selected/unrelated semantic View state")
      val actual = session.lifecycleSnapshot()
      Then("every ordered logical field, physical column/type/row/NULL and resident/View value equals the oracle declared before the action")
      checkRows(actual.rows, wanted.rows)
      actual.physical shouldBe wanted.physical
      actual.resident.selected.map(_.record.fields) shouldBe wanted.resident.selected.map(_.record.fields)
      actual.resident.unrelated.map(_.record.fields) shouldBe wanted.resident.unrelated.map(_.record.fields)
      actual.resident.memoryselected.map(_.record.fields) shouldBe wanted.resident.memoryselected.map(_.record.fields)
      actual.resident.memoryunrelated.map(_.record.fields) shouldBe wanted.resident.memoryunrelated.map(_.record.fields)
      def _view_(actual: LifecycleView, expected: LifecycleView): Unit = (actual, expected) match {
        case (LifecycleView.Present(found), LifecycleView.Present(value)) =>
          found.id shouldBe value.id; found.record.fields shouldBe value.record.fields
        case (LifecycleView.Missing(found), LifecycleView.Missing(value)) =>
          checkLifecycleFailure(found, value)
        case _ => fail("Present and known entity-not-found View states must correspond exactly")
      }
      _view_(actual.resident.selectedview, wanted.resident.selectedview)
      _view_(actual.resident.unrelatedview, wanted.resident.unrelatedview)
      actual.resident.builds shouldBe wanted.resident.builds
    }
    def checkLifecycleJson(value: io.circe.Json, session: Session, create: Boolean, version: Long,
      projection: String = "ready"): Unit = {
      value.hcursor.get[String]("outcome") shouldBe Right(if (create) "created" else "deleted")
      value.hcursor.downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
      value.hcursor.downField("resource").get[Long]("revision") shouldBe Right(version)
      value.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
      if (create) value.hcursor.downField("projection").get[String]("type") shouldBe Right(projection)
      else {
        value.hcursor.downField("projection").get[String]("type") shouldBe Right("reload-list")
      }
    }
    def checkLifecycleResult(attempt: Attempt, session: Session, create: Boolean, version: Long,
      projection: String = "ready", principal: String = "owner"): Unit = {
      checkLifecycleJson(json(attempt.result), session, create, version, projection)
      attempt.execution.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      attempt.execution.uow.lastCommitResult shouldBe Some(Consequence.unit)
      val observers = attempt.execution.lifecycleObservers
      observers.map(_.creates.size).sum shouldBe (if (create) 1 else 0)
      observers.map(_.deletes.size).sum shouldBe (if (create) 0 else 1)
      observers.map(_.writes).sum shouldBe 0
      if (create) {
        val result = observers.flatMap(_.created).head
        result.record.fields shouldBe session.rows().selected.get.fields
      } else observers.flatMap(_.deleted) shouldBe Vector(EntityCompareAndDeleteResult.Deleted(revision(version)))
      session.log.events.count(_.event == "commit") shouldBe 1
      session.log.events.foreach(event => (event.uow eq attempt.execution.uow) shouldBe true)
      val targetid = if (create) None else Some(session.component.id)
      val access = if (create && session.component.target != Target.Entity) "create:createRoot" else if (create) "create" else "delete"
      session.log.authorizations should not be empty
      session.log.authorizations.foreach { case (authorization, uow) =>
        (uow eq attempt.execution.uow) shouldBe true
        authorization.targetId shouldBe targetid
        authorization.accessKind shouldBe access
        authorization.resourceFamily shouldBe (if (session.component.target == Target.Entity) "domain" else "aggregate")
        authorization.resourceType shouldBe Some(session.component.ownerName)
        authorization.sourceComponentName shouldBe Some(session.component.name)
        authorization.targetComponentName shouldBe Some(session.component.name)
        authorization.access shouldBe Some(CmlOperationAccess("authenticated_only"))
      }
      session.log.events.filter(_.event == "find").foreach { event =>
        event.context.clock.instant() shouldBe fixedInstant
        event.context.security.principal.id.value shouldBe principal
        event.context.observability shouldBe observability
      }
    }
    def checkLifecyclePreserved(session: Session, before: LifecycleSnapshot, attempt: Attempt,
      converted: Boolean = false): Unit = {
      attempt.result shouldBe a[Consequence.Failure[?]]
      attempt.execution.lifecycleObservers.map(_.creates.size).sum shouldBe 0
      attempt.execution.lifecycleObservers.map(_.deletes.size).sum shouldBe 0
      attempt.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
      session.log.events.count(_.event == "find") shouldBe 0
      if (!converted) session.log.conversions shouldBe Vector.empty
      checkLifecycleState(session, before)
    }
    def checkLifecycleForeignPrincipal(session: Session, create: Boolean): Unit = {
      Given("an Entity route with revision-seven owner state or absent create target and an authenticated foreign principal")
      session.component.target shouldBe Target.Entity
      session.prepareLifecycle(create, 7L)
      val before = session.lifecycleSnapshot()
      val operation = if (create) DisplayMutation.Operation.Create else DisplayMutation.Operation.Delete
      val active = session.execution(principal = "foreign")
      val wanted = if (create) expectedLifecycleCreate(session, before, lifecycleFields, principal = "foreign") else before
      val denied = conclusion(Consequence.securityPermissionDenied(
        "Permission is insufficient for delete.", Cause.Kind.Permission, Seq(
          Descriptor.Facet.Reason("other"), Descriptor.Facet.Permission("other"),
          Descriptor.Facet.Parameter.argument("access-kind"), Descriptor.Facet.Value("delete"), Descriptor.Facet.Id("foreign"))))
      When("the authenticated foreign principal requests ordinary type-level creation or deletion of the existing owner instance")
      val attempt = session.execute(session.lifecycleRequest(operation, observed = 7L), active)
      Then("type-level create retains exact foreign defaults and contextual Detail while instance delete preserves the complete owner state")
      if (create) {
        checkLifecycleResult(attempt, session, true, 1L, principal = "foreign")
        session.log.conversions shouldBe DisplayMutationServiceDomain.fieldIds.filter(name => lifecycleFields.exists(_.location.fieldId == name))
        session.log.validations shouldBe 1
        session.log.events.count(_.event == "capture") shouldBe 1
        session.log.events.count(_.event == "find") shouldBe 1
        checkLifecycleState(session, wanted)
      } else {
        checkLifecycleFailure(conclusion(attempt.result), denied)
        checkLifecyclePreserved(session, wanted, attempt)
        session.log.validations shouldBe 0
      }
      session.log.commands shouldBe 0
      session.log.idCalls shouldBe Vector.empty
    }
    def checkCompleteState(session: Session, wanted: Expected): Unit = {
      When("independent canonical readers observe the complete selected/unrelated root, body, physical, resident and semantic View state")
      val rows = session.rows()
      val tables = session.physical()
      val resident = session.resident
      Then("every ordered field, physical column/type/row/NULL and resident/View value equals its pre-action declared oracle")
      checkRows(rows, wanted.rows)
      tables shouldBe wanted.physical
      resident shouldBe wanted.resident
    }
    def checkRows(actual: Rows, wanted: Rows): Unit = {
      Vector(actual.selected -> wanted.selected, actual.unrelated -> wanted.unrelated,
        actual.body -> wanted.body, actual.unrelatedbody -> wanted.unrelatedbody).foreach { case (left, right) =>
        left.map(_.fields) shouldBe right.map(_.fields)
      }
    }
    def checkResult(attempt: Attempt, session: Session, outcome: String, version: Long, projection: String = "ready",
      pendingReason: Option[String] = None): Unit = {
      val value = json(attempt.result)
      value.hcursor.get[String]("outcome") shouldBe Right(outcome)
      value.hcursor.downField("resource").get[Long]("revision") shouldBe Right(version)
      value.hcursor.downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
      value.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
      value.hcursor.downField("projection").get[String]("type") shouldBe Right(projection)
      pendingReason.foreach(reason => value.hcursor.downField("projection").get[String]("reason") shouldBe Right(reason))
      attempt.execution.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      attempt.execution.uow.lastCommitResult shouldBe Some(Consequence.unit)
      attempt.execution.attempts shouldBe 1
      val events = session.log.events
      events.filter(_.event == "commit") should have size 1
      events.foreach(event => (event.uow eq attempt.execution.uow) shouldBe true)
      session.log.authorizations.foreach { case (_, uow) => (uow eq attempt.execution.uow) shouldBe true }
    }
    def checkPreserved(session: Session, before: Rows, tables: Map[(String, String), StoredTable], resident: Resident): Unit = {
      checkRows(session.rows(), before)
      session.physical() shouldBe tables
      session.resident shouldBe resident
      session.log.conversions shouldBe Vector.empty
      session.log.validations shouldBe 0
      session.log.commands shouldBe 0
      session.log.events.count(_.event == "find") shouldBe 0
    }
  }

  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value = initial
    def get: A = _value
    def set(value: A): Unit = { _value = value }
    override def getAndSet(value: A): A = { val previous = _value; _value = value; previous }
    def access: (A, A => Boolean) = { val previous = _value; previous -> ((next: A) => if (_value == previous) { _value = next; true } else false) }
    override def tryUpdate(f: A => A): Boolean = { update(f); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = Some(modify(f))
    def update(f: A => A): Unit = { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = { val (next, result) = f(_value); _value = next; result }
    override def modifyState[B](state: State[A, B]): B = { val (next, result) = state.run(_value).value; _value = next; result }
    override def tryModifyState[B](state: State[A, B]): Option[B] = Some(modifyState(state))
  }
}
