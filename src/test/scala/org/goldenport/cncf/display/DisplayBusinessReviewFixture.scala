package org.goldenport.cncf.display

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.free.Free
import cats.syntax.all.*
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import logicalui.{DisplayMutationServiceDomain, DisplayServiceReadFixture}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, FunctionalActionCall}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId}
import org.goldenport.cncf.datastore.{DataStore, PostgresqlPersistenceFixture}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.entity.{EntityPersistentUpdate, EntityRevisionRepresentation}
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.cncf.security.{AggregateAuthorization, AuthenticationProvider, AuthenticationRequest, AuthenticationResult, EntityAccessMode, SecuritySubject}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp, UnitOfWorkTermination}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, OperationDefinitionGroup, ParameterDefinition, RequestDefinition, ResponseDefinition, ServiceDefinition, ServiceDefinitionGroup}
import org.goldenport.record.{Field, Record}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.http4s.{Header, Headers, Method, Request as HttpRequest, Uri, UrlForm}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update
import org.typelevel.ci.CIString

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** Distinct development workflow, using normal canonical APIs and real transports. */
private[cncf] object DisplayBusinessReviewFixture {
  import DisplayMutationServiceFixture.{NormalComponent, Session, Target, Rows, Resident, Attempt,
    success, revision, json, representations, resourceType, screenId, observability, fixedInstant}
  import DisplayMutationServiceDomain.Root
  final case class Setup(production: Boolean = false,
    availability: DisplayModel.ActionAvailability = DisplayModel.ActionAvailability.Enabled)
  final case class ReviewPatch(stage: Update[String])
  final case class Baseline(rows: Rows, physical: Map[(String, String), StoredTable], resident: Resident,
    display: Json, builds: Int)
  final case class Expected(rows: Rows, physical: Map[(String, String), StoredTable])
  final case class AuditWindow(start: Instant, end: Instant)
  val ownerSession: String = "canonical-review-owner-session"
  val foreignSession: String = "canonical-review-foreign-session"
  def authenticationProvider(log: DisplayMutationServiceFixture.Log): AuthenticationProvider = new AuthenticationProvider {
    override val name: String = "canonical-review-auth"
    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] = {
      val result = request.sessionId match {
        case Some(`ownerSession`) => Some(AuthenticationResult(PrincipalId("owner")))
        case Some(`foreignSession`) => Some(AuthenticationResult(PrincipalId("foreign")))
        case _ => None
      }
      result.foreach(value => log.authenticatedPrincipal(value.principalId))
      Consequence.success(result)
    }
  }
  val inputNames: Vector[String] = Vector("resourceId", "revision")
  def definition(original: GeneratedLogicalUiAbi.Definition, token: String): GeneratedLogicalUiAbi.Definition =
    original.copy(provenance = GeneratedLogicalUiAbi.SourceProvenance("cncf.development.display-business",
      "canonical-review-" + token, "fixture-1"), screen = original.screen.asInstanceOf[GeneratedLogicalUiAbi.DetailDefinition].copy(
      actions = Vector(GeneratedLogicalUiAbi.ActionDefinition("request-review", "Request review", "business:request-review"))))

  def patchPersistent(collectionId: EntityCollectionId): EntityPersistentUpdate[ReviewPatch] = new EntityPersistentUpdate[ReviewPatch] {
    def collection(patch: ReviewPatch): EntityCollectionId = collectionId
    def toRecord(patch: ReviewPatch): Record = Record.dataAuto("stage" -> patch.stage)
    override def toStoreRecord(patch: ReviewPatch): Record = toRecord(patch)
    def fromRecord(record: Record): Consequence[ReviewPatch] = Consequence.operationInvalid("review-patch-is-write-only")
  }
  def protocol(component: NormalComponent): Protocol = Protocol(ServiceDefinitionGroup(Vector(
    ServiceDefinition(name = "business", operations = OperationDefinitionGroup.create(Vector(new ReviewOperation(component)))))),
    ProtocolHandler.default)

  final class ReviewOperation(private val _component: NormalComponent) extends OperationDefinition with OpenApiOperationProjection {
    def openApiHttpMethod: OpenApiHttpMethod = OpenApiHttpMethod.POST
    val specification: OperationDefinition.Specification = OperationDefinition.Specification(name = "requestReview",
      request = RequestDefinition(inputNames.map(name => ParameterDefinition(BaseContent.simple(name),
        ParameterDefinition.Kind.Property, ValueDomain(XString, Multiplicity.One))).toList),
      response = ResponseDefinition(result = List(DataType.Named("OperationResponse"))))
    def createOperationRequest(request: Request): Consequence[OperationRequest] = for {
      identitytext <- _single_string(request, "resourceId")
      identity <- EntityId.parse(identitytext)
      revisiontext <- _single_string(request, "revision")
      observed <- revisiontext.toLongOption match {
        case Some(value) if value.toString == revisiontext => EntityRevision.createC(value)
        case _ => Consequence.argumentInvalid("revision must be a canonical positive decimal Long")
      }
    } yield new ReviewAction(request, _component, identity, observed)
  }
  private def _single_string(request: Request, name: String): Consequence[String] =
    request.properties.filter(_.name == name) match {
      case property :: Nil => property.value match {
        case text: String if text.nonEmpty => Consequence.success(text)
        case _ => Consequence.argumentInvalid(name + " requires one nonempty String")
      }
      case Nil => Consequence.argumentMissing(name)
      case _ => Consequence.argumentInvalid(name + " requires one String")
    }
  private final class ReviewAction(val request: Request, private val _component: NormalComponent,
    private val _identity: EntityId, private val _observed: EntityRevision) extends CommandAction {
    def createCall(core: ActionCall.Core): ActionCall = new ReviewCall(core, _component, _identity, _observed)
  }
  private final class ReviewCall(val core: ActionCall.Core, private val _component: NormalComponent,
    private val _identity: EntityId, private val _observed: EntityRevision) extends FunctionalActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      given ExecutionContext = executionContext
      _component.log.record("business", executionContext)
      for {
        _ <- use_component_application_datastore()
        _ <- if (_component.target == Target.Entity) exec_from(Consequence.unit)
          else ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, Unit](UnitOfWorkOp.Authorize(
            AggregateAuthorization.Request(aggregateName = _component.ownerName, targetId = Some(_identity),
              accessKind = "command:requestReview", access = None, sourceComponentName = Some(_component.name),
              targetComponentName = Some(_component.name)).toUnitOfWorkAuthorization)))
        current <- if (_component.target == Target.Entity) {
          given EntityPersistentUpdate[ReviewPatch] = patchPersistent(_component.collectionId)
          val patch = ReviewPatch(Update.set("review-requested"))
          if (_component.representation == EntityRevisionRepresentation.Embedded)
            entity_update(_identity, patch, _observed).map(_.revision)
          else entity_update_detached(_identity, patch, _observed).map(_.revision)
        } else exec_from(aggregate_update_observed_snapshot_c[Root](_component.ownerName, _identity,
          "requestReview", _observed) { root =>
          _component.log.command()
          Consequence.success(root.copy(record = root.record.upsertSingle("stage", "review-requested")))
        }.map(_.revision))
      } yield OperationResponse.Json(Json.obj("outcome" -> Json.fromString("review-requested"),
        "resourceId" -> Json.fromString(_identity.value), "revision" -> Json.fromLong(current.value)))
    }
  }

  def installDescriptor(subsystem: Subsystem, component: NormalComponent): Unit = {
    subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<canonical-review-fixture>"),
      subsystemName = subsystem.name, componentBindings = Vector(GenericSubsystemComponentBinding(component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"), providers = Vector(
          GenericSubsystemAuthenticationProviderBinding(authenticationProvider(component.log).name, component.name, enabled = Some(true))))))),
      operationAuthorization = Vector("business.requestReview", "display.read", "display.describeBusiness").map(operation =>
        (component.name + "." + operation) -> OperationAuthorizationRule(requireAuthenticated = true)).toMap))
    ()
  }
  def open(target: Target = Target.Entity, representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Embedded,
    database: Option[PostgresqlPersistenceFixture.Database] = None, setup: Setup = Setup()): Session =
    DisplayMutationServiceFixture.open(target, representation, database, backend = "sqlite", businessSetup = Some(setup))
  def request(session: Session, observed: String = "1"): Request = Request.of(component = session.component.name,
    service = "business", operation = "requestReview", properties = List(
      Property("resourceId", session.component.id.value, None), Property("revision", observed, None)))
  def descriptorRequest(session: Session): Request = session.descriptorRequest.copy(operation = "describeBusiness")
  def http(session: Session, service: String = "business", operation: String = "requestReview", method: Method = Method.POST,
    sessionId: Option[String] = None, parameters: Option[Vector[(String, String)]] = None,
    extra: Vector[(String, String)] = Vector.empty): DisplayServiceReadFixture.HttpResult = {
    val selectors = parameters.getOrElse(if (service == "business")
      Vector("resourceId" -> session.component.id.value, "revision" -> "1")
      else Vector("resourceType" -> resourceType, "screenId" -> screenId) ++
        (if (operation == "read") Vector("resourceId" -> session.component.id.value) else Vector.empty))
    // Explicit ControlledTest facts are confined to the non-production fixture.
    val testfacts = if (session.component.businessSetup.exists(_.production)) Vector.empty else Vector(
      "cncf.security.privilege" -> "user", "cncf.security.principalId" -> "owner", "cncf.security.authenticated" -> "true")
    val businesspost = service == "business" && method == Method.POST
    val pairs = (if (businesspost) Vector.empty else selectors) ++ testfacts ++ Vector("textus.output.shape" -> "envelope",
      "cncf.context.traceId" -> observability.traceId.print,
      "cncf.context.correlationId" -> observability.correlationId.get.print) ++ extra
    val uri = Uri.unsafeFromString(s"/rest/v1/${session.component.name}/$service/$operation.json")
      .withMultiValueQueryParams(pairs.groupMap(_._1)(_._2))
    val headers = Headers(sessionId.toVector.map(value => Header.Raw(CIString("x-textus-session"), value)))
    val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(session.subsystem))
    val request = HttpRequest[IO](method, uri, headers = headers)
    val supplied = if (businesspost) request.withEntity(UrlForm(selectors*)) else request
    val response = DisplayServiceHttpRouteFixture.app(server).run(supplied).unsafeRunSync()
    val bytes = response.body.compile.toVector.unsafeRunSync()
    DisplayServiceReadFixture.HttpResult(response.status.code,
      response.headers.headers.find(_.name == CIString("Content-Type")).map(_.value.takeWhile(_ != ';')),
      new String(bytes.toArray, StandardCharsets.UTF_8), bytes)
  }
  def readHttp(session: Session, sessionId: Option[String] = None): DisplayServiceReadFixture.HttpResult =
    http(session, "display", "read", Method.GET, sessionId)

  def baseline(session: Session): Baseline = {
    val resident = session.resident // Warm both Views only before the Business action.
    val display = if (session.component.businessSetup.exists(_.production)) {
      val response = readHttp(session, Some(ownerSession))
      require(response.status == 200, response.text)
      response.data
    } else json(session.read().result)
    val result = Baseline(session.rows(), session.physical(), resident, display, session.component.viewBuilds)
    session.log.reset()
    session.lifecycleObservers.foreach(_.reset())
    result
  }
  /** SQL order and every unchanged value come from independent pre-action storage. */
  def expected(session: Session, before: Baseline, version: Long): Expected = {
    require(session.backend == "sqlite" || session.backend == "postgresql")
    val replacements: Map[String, Any] = Map("stage" -> "review-requested",
      session.component.representation.storageFieldName -> version,
      "updated_at" -> fixedInstant.toString, "updated_by" -> "owner",
      "trace_id" -> observability.traceId.value, "correlation_id" -> observability.correlationId.get.value)
    val record = Record(before.rows.selected.get.fields.map(field => replacements.get(field.key)
      .map(value => field.copy(value = Field.Value.Single(value))).getOrElse(field)))
    val collection = DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName
    val tables = before.physical.map { case (key, table) => key -> table.copy(rows = table.rows.map { row =>
      if (key._2 == collection && row.get("id").contains(session.component.id.value)) row ++ replacements else row
    }) }
    Expected(before.rows.copy(selected = Some(record)), tables)
  }
  def malformed(session: Session): Vector[Request] = {
    val valid = request(session)
    inputNames.map(name => valid.copy(properties = valid.properties.filterNot(_.name == name))) ++
      Vector("not-an-entity-id", "", " ").map(value => valid.copy(properties =
        List(Property("resourceId", value, None), Property("revision", "1", None)))) ++
      Vector("0", "-1", "+1", "01", "1.0", "1e0", " 1", "9223372036854775808").map(request(session, _)) ++
      inputNames.flatMap(name => Vector(valid.copy(properties = valid.properties :+ valid.properties.find(_.name == name).get),
        valid.copy(properties = valid.properties.map(property => if (property.name == name) Property(name, 1L, None) else property))))
  }

  trait Vocabulary extends DisplayMutationServiceFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkIdle(session: Session, before: Baseline): Unit = {
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.events.count(_.event == "query") shouldBe 0
      session.log.queries shouldBe empty
      session.component.viewBuilds shouldBe before.builds
    }
    def checkPreservedBusiness(session: Session, before: Baseline, stale: Boolean = false): Unit = {
      checkRows(session.rows(), before.rows)
      session.physical() shouldBe before.physical
      checkIdle(session, before)
      session.log.events.count(_.event == "capture") shouldBe 0
      session.log.conversions shouldBe empty
      session.log.validations shouldBe 0
      session.log.commands shouldBe 0
      val evicted = stale && session.component.target == Target.Entity
      session.component.realm.get(session.component.id) shouldBe (if (evicted) None else before.resident.selected)
      session.component.memory.get(session.component.id) shouldBe (if (evicted) None else before.resident.memoryselected)
      session.component.realm.get(session.component.unrelatedId) shouldBe before.resident.unrelated
      session.component.memory.get(session.component.unrelatedId) shouldBe before.resident.memoryunrelated
    }
    def checkResponse(value: Json, session: Session, version: Long): Unit = {
      value shouldBe Json.obj("outcome" -> Json.fromString("review-requested"),
        "resourceId" -> Json.fromString(session.component.id.value), "revision" -> Json.fromLong(version))
    }
    def checkAuthorization(session: Session, production: Boolean = false): Unit = {
      session.log.authorizations should not be empty
      val uow = session.log.authorizations.head._2
      uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      uow.lastCommitResult shouldBe Some(Consequence.unit)
      session.log.authorizations.foreach { case (authorization, active) =>
        (active eq uow) shouldBe true
        authorization.targetId shouldBe Some(session.component.id)
        authorization.accessMode shouldBe EntityAccessMode.UserPermission
        authorization.resourceFamily shouldBe (if (session.component.target == Target.Entity) "domain" else "aggregate")
        authorization.resourceType shouldBe Some(session.component.ownerName)
        authorization.accessKind shouldBe (if (session.component.target == Target.Entity) "update" else "command:requestReview")
        authorization.sourceComponentName shouldBe Some(session.component.name)
        authorization.targetComponentName shouldBe Some(session.component.name)
        active.executionContext.security.principal.id.value shouldBe "owner"
        SecuritySubject.from(active.executionContext.security).isAuthenticated shouldBe true
        if (!production) {
          active.executionContext.clock.instant() shouldBe fixedInstant
          active.executionContext.observability.traceId.value shouldBe observability.traceId.value
          active.executionContext.observability.correlationId.map(_.value) shouldBe observability.correlationId.map(_.value)
        }
      }
    }
    def checkState(session: Session, wanted: Expected, window: Option[AuditWindow] = None): Unit = {
      val actual = session.rows()
      def _timestamp_(value: Any): Unit = {
        val time = value match {
          case instant: Instant => instant
          case text: String => Instant.parse(text)
          case _ => fail("Expected explicit update audit time")
        }
        val bounds = window.get
        time.isBefore(bounds.start) shouldBe false
        time.isAfter(bounds.end) shouldBe false
      }
      val selected = actual.selected.get.fields
      val expectedfields = wanted.rows.selected.get.fields
      selected.map(_.key) shouldBe expectedfields.map(_.key)
      selected.zip(expectedfields).foreach { case (found, expected) =>
        if (window.nonEmpty && found.key == "updated_at") _timestamp_(found.value.single)
        else found shouldBe expected
      }
      checkRows(actual.copy(selected = wanted.rows.selected), wanted.rows)
      val physical = session.physical()
      physical.keySet shouldBe wanted.physical.keySet
      physical.foreach { case (key, table) =>
        val expectedtable = wanted.physical(key)
        table.columns shouldBe expectedtable.columns
        table.rows.size shouldBe expectedtable.rows.size
        table.rows.zip(expectedtable.rows).foreach { case (found, expected) =>
          found.keySet shouldBe expected.keySet
          found.foreach { case (name, value) =>
            if (window.nonEmpty && name == "updated_at" && found.get("id").contains(session.component.id.value) &&
                key._2 == DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName) _timestamp_(value)
            else value shouldBe expected(name)
          }
        }
      }
    }
    // Obtain no View here: the raw unrelated resident is checked by scenarios against the baseline.
    private def _stage(value: Json): String = value.hcursor.downField("model").downField("sections").downN(0)
      .downField("fields").focus.get.asArray.get.find(_.hcursor.get[String]("id") == Right("stage")).get
      .hcursor.downField("value").get[String]("text").toOption.get
    def checkDetail(value: Json, session: Session, stage: String, version: Long): Unit = {
      value.hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
      value.hcursor.downField("model").downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
      value.hcursor.downField("model").downField("resource").get[Long]("revision") shouldBe Right(version)
      _stage(value) shouldBe stage
      value.hcursor.downField("provenance").get[String]("producerId") shouldBe Right("cncf.development.display-business")
      value.hcursor.downField("provenance").get[String]("sourceId") shouldBe Right("canonical-review-" + session.component.token)
    }
    def checkCommit(attempt: Attempt, session: Session, before: Baseline, wanted: Expected, version: Long): Json = {
      val response = json(attempt.result)
      checkResponse(response, session, version)
      attempt.execution.attempts shouldBe 1
      attempt.execution.space.observers.map(_.genericWrites).sum shouldBe 0
      checkAuthorization(session)
      checkIdle(session, before)
      checkState(session, wanted)
      session.component.realm.get(session.component.unrelatedId) shouldBe before.resident.unrelated
      session.component.memory.get(session.component.unrelatedId) shouldBe before.resident.memoryunrelated
      checkDetail(before.display, session, "draft", version - 1L)
      response
    }
    def checkReload(session: Session, before: Baseline, response: Json, version: Long): Json = {
      val saved = response
      When("the client explicitly invokes the ordinary Display Detail read after the Business response")
      val fresh = json(session.read().result)
      Then("only this request returns fresh stage and revision while prior Display and Business JSON retain their values")
      checkDetail(fresh, session, "review-requested", version)
      checkDetail(before.display, session, "draft", version - 1L)
      response shouldBe saved
      session.log.events.count(_.event == "find") shouldBe 1
      session.log.events.count(_.event == "business") shouldBe 1
      fresh
    }
    def checkNative(session: Session, before: Baseline): Unit = {
      val roots = before.physical.toVector.filter(_._1._2 == DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName)
      roots should have size 1
      roots.head._2.rows.map(_("id")) shouldBe Vector(session.component.id.value, session.component.unrelatedId.value).sorted
      val bodies = before.physical.toVector.filter(_._1._2 == "cncf_content_body_overflow")
      bodies should have size 1
      Vector(session.component.id, session.component.unrelatedId).foreach { identity =>
        val row = bodies.head._2.rows.find(_.get("id").contains(identity.value + ":content")).get
        row("content") shouldBe DisplayMutationServiceFixture.body
        row("field") shouldBe "content"
      }
    }
    def checkProductionHttp(session: Session): Unit = {
      session.component.businessSetup.exists(_.production) shouldBe true
      session.subsystem.configuration.configuration.values.contains("textus.execution.profile") shouldBe false
      session.subsystem.configuration.configuration.values.contains("textus.execution.time.mode") shouldBe false
      val forged = Vector("cncf.security.authenticated" -> "true", "cncf.security.principalId" -> "owner",
        "principalId" -> "owner", "cncf.security.privilege" -> "admin", "roles" -> "admin", "owner_id" -> "owner")
      val selectors = Vector("resourceId" -> session.component.id.value, "revision" -> "1")
      val candidates = Vector(
        (None, Vector.empty[(String, String)], selectors),
        (None, forged, selectors),
        (Some("rejected-session"), forged, selectors),
        (Some(foreignSession), forged, selectors),
        (Some(ownerSession), Vector.empty[(String, String)], selectors.filterNot(_._1 == "resourceId")),
        (Some(ownerSession), Vector.empty[(String, String)], Vector("resourceId" -> session.component.id.value, "revision" -> "01")))
      candidates.foreach { case (sessionid, facts, inputs) =>
        Given("docs/spec/display-business-operation-descriptor.md BR1/BR2: production standard/system profile, real provider and complete warmed baseline")
        val before = baseline(session)
        When("actual Http4s receives absent, rejected, forged or trusted-foreign evidence, or trusted-owner malformed Business input")
        val response = http(session, sessionId = sessionid, parameters = Some(inputs), extra = facts)
        Then("ordinary authentication/input or canonical owner permission rejects with no write, publication, Browser call or View build")
        withClue(response.text) { response.status should be >= 400 }
        session.log.authenticatedPrincipals shouldBe (sessionid match {
          case Some(`foreignSession`) => Vector(PrincipalId("foreign"))
          case Some(`ownerSession`) => Vector(PrincipalId("owner"))
          case _ => Vector.empty
        })
        session.log.authorizations shouldBe empty
        checkPreservedBusiness(session, before)
        session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 0
        session.lifecycleObservers.map(_.writes).sum shouldBe 0
      }
      Given("the real trusted-owner session and an independently specified full stage-only oracle before mutation")
      val before = baseline(session)
      val wanted = expected(session, before, 2L)
      val started = Instant.now()
      When("actual production Http4s directly POSTs the ordinary Business Operation using only provider session evidence")
      val response = http(session, sessionId = Some(ownerSession))
      val finished = Instant.now()
      Then("reachable owner execution commits once, preserves all data except declared managed fields and performs no Display read")
      withClue(response.text) { response.status shouldBe 200 }
      response.contentType shouldBe Some("application/json")
      session.log.authenticatedPrincipals shouldBe Vector(PrincipalId("owner"))
      checkResponse(response.data, session, 2L)
      checkAuthorization(session, production = true)
      checkIdle(session, before)
      checkState(session, wanted, Some(AuditWindow(started, finished)))
      session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
      session.lifecycleObservers.map(_.writes).sum shouldBe 0
      session.log.commands shouldBe (if (session.component.target == Target.Entity) 0 else 1)
      session.component.realm.get(session.component.unrelatedId) shouldBe before.resident.unrelated
      session.component.memory.get(session.component.unrelatedId) shouldBe before.resident.memoryunrelated
      val businessresponse = response.data
      When("the same authenticated owner subsequently GETs display.read.json explicitly")
      val fresh = readHttp(session, Some(ownerSession))
      Then("fresh Detail corresponds to the canonical commit while the earlier Display and ordinary Business response remain separate")
      withClue(fresh.text) { fresh.status shouldBe 200 }
      session.log.authenticatedPrincipals shouldBe Vector(PrincipalId("owner"), PrincipalId("owner"))
      checkDetail(fresh.data, session, "review-requested", 2L)
      checkDetail(before.display, session, "draft", 1L)
      checkResponse(businessresponse, session, 2L)
      session.log.events.count(_.event == "find") shouldBe 1
      session.log.events.count(_.event == "business") shouldBe 1
      session.lifecycleObservers.map(observer => observer.updates.size + observer.nativeUpdates.size).sum shouldBe 1
      checkState(session, wanted, Some(AuditWindow(started, finished)))
    }
  }
}
