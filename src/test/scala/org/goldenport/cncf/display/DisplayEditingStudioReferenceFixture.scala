package org.goldenport.cncf.display

import io.circe.Json
import logicalui.DisplayMutationServiceDomain
import logicalui.DisplayMutationServiceDomain.Root
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.datastore.{DataStore, PostgresqlPersistenceFixture}
import org.goldenport.cncf.entity.{EntityConcurrencyMetadata, EntityRevisionRepresentation}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.{Field, Record}
import org.scalatest.Informing
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct. 17, 2026
 * @version Oct. 19, 2026
 */
/** One development-only connected reference; every writer is an existing ordinary Operation. */
private[cncf] object DisplayEditingStudioReferenceFixture {
  import DisplayMutationServiceFixture.*
  import DisplayMutation.Operation
  import GeneratedLogicalUiAbi.*

  enum Profile { case UpdateOnly, Crud }
  enum Rejection { case CurrentNoop, StaleEqual, Readonly, InvalidTitle }
  final case class Declaration(selected: EntityId, unrelated: EntityId, profile: Profile,
    initialtitle: String, initialrevision: Long) {
    def initial: Entry = Entry(selected, initialrevision, values(initialtitle))
    def other: Entry = Entry(unrelated, 1L, values("Unrelated"))
  }
  final case class Entry(identity: EntityId, version: Long, values: Vector[DisplayValue]) {
    def resource: DisplayModel.Resource = DisplayModel.Resource(identity, revision(version))
  }
  final case class Selected(listjson: Json, detailjson: Json, resource: DisplayModel.Resource)
  final case class Updated(selected: Selected, entry: Entry, state: LifecycleSnapshot)
  final case class Sample(payload: String, description: String, initialrevision: Long, target: Target,
    representation: EntityRevisionRepresentation, profile: Profile) {
    def initialTitle: String = "Initial:" + payload
    def title: String = "Updated:" + payload
    def changes: Vector[DisplayMutation.FieldInput] = Vector(field("title", DisplayValue.Text(title)),
      field("description", DisplayValue.Text(description)))
  }
  final case class Rejected(sample: Sample, mode: Rejection)

  val listScreenId: String = "capture-list"
  val fieldIds: Vector[String] = Vector("title", "description", "number", "flag", "instant", "nullable", "stage")
  val fieldLabels: Vector[String] = Vector("Title", "Description", "Number", "Flag", "Instant", "Nullable", "Stage")
  val action: DisplayModel.Action = DisplayModel.Action("request-review", "Request review", "business:request-review",
    DisplayModel.ActionAvailability.Enabled)
  val witnessText: String = "日本 Ω\n\u0000"
  def capabilities(profile: Profile): DisplayMutation.Capabilities = {
    import DisplayMutation.Support.*
    val cud = if (profile == Profile.Crud) Supported else Unsupported
    DisplayMutation.Capabilities(Supported, Supported, cud, Supported, cud)
  }
  def lifecycle(profile: Profile): LifecycleSetup = LifecycleSetup(capabilities = capabilities(profile), withList = true)
  def provenance(profile: Profile): SourceProvenance = SourceProvenance("cncf.development.editing-studio-reference",
    "cncf.editing-studio-reference.v1:" + profile.toString, "fixture-1")
  def values(title: String): Vector[DisplayValue] = Vector(DisplayValue.Text(title), DisplayValue.Text("retained 日本 Ω"),
    DisplayValue.Number(BigDecimal("123.4500")), DisplayValue.Boolean(true),
    DisplayValue.Timestamp(DisplayMutationServiceDomain.instant), DisplayValue.Text("present"), DisplayValue.Text("draft"))
  def changed(entry: Entry, inputs: Vector[DisplayMutation.FieldInput], version: Long): Entry = {
    val replacements = inputs.map(input => input.location.fieldId -> input.value).toMap
    entry.copy(version = version, values = fieldIds.zip(entry.values).map { case (name, value) => replacements.getOrElse(name, value) })
  }
  def reviewed(entry: Entry): Entry = entry.copy(version = entry.version + 1L,
    values = entry.values.updated(6, DisplayValue.Text("review-requested")))
  def openReference(target: Target = Target.Entity,
    representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Embedded,
    profile: Profile = Profile.UpdateOnly, database: Option[PostgresqlPersistenceFixture.Database] = None): Session =
    open(target, representation, database, backend = "sqlite", lifecycle = Some(lifecycle(profile)),
      businessSetup = Some(DisplayBusinessReviewFixture.Setup()), referenceProfile = Some(profile))
  def prepare(session: Session, title: String = "Before", version: Long = 1L, create: Boolean = false): Declaration = {
    session.prepareLifecycle(create, version)
    if (!create && title != "Before") session.seed(title, version)
    Declaration(session.component.id, session.component.unrelatedId, session.component.referenceProfile.get, title, version)
  }
  def source(session: Session): DisplayProtocol.Source = DisplayProtocol.Source.createC(session.component.componentId, resourceType).TAKE
  def definitions(session: Session): AdmittedDefinitions = GeneratedLogicalUiAbi.admitC(session.component.generatedLogicalUiDefinitions).TAKE
  def queryRequest(session: Session): Request = Request.of(component = session.component.name, service = "display", operation = "query",
    properties = List(Property("resourceType", resourceType, None), Property("screenId", listScreenId, None)))
  def detailRequest(session: Session, identity: EntityId, target: DetailTarget): Request = Request.of(
    component = session.component.name, service = "display", operation = "read", properties = List(
      Property("resourceType", resourceType, None), Property("screenId", target.screenId, None), Property("resourceId", identity.value, None)))
  def updateModel(session: Session, observed: DisplayModel.Resource, fields: Vector[DisplayMutation.FieldInput]): DisplayMutation.Request.Update =
    DisplayMutation.Request.Update(session.component.inputBinding.selection, observed.entityId, fields)
  def updateRequest(session: Session, observed: DisplayModel.Resource, fields: Vector[DisplayMutation.FieldInput]): Request =
    session.rawRequest(DisplayMutationCodec.encodeRequestC(updateModel(session, observed, fields)).TAKE, observed.revision.value)
  def businessRequest(session: Session, observed: DisplayModel.Resource): Request = Request.of(
    component = session.component.name, service = "business", operation = "requestReview", properties = List(
      Property("resourceId", observed.entityId.value, None), Property("revision", observed.revision.value.toString, None)))
  def deleteRequest(session: Session, observed: DisplayModel.Resource): Request = session.lifecycleRawRequest(Operation.Delete,
    DisplayMutationCodec.encodeRequestC(DisplayMutation.Request.Delete(session.component.inputBinding.selection, observed.entityId)).TAKE,
    observed.revision.value)
  private def _fields(entry: Entry): Vector[DisplayModel.Field] = fieldIds.zip(fieldLabels).zip(entry.values).map {
    case ((name, label), value) => DisplayModel.Field(name, label,
      if (name == "title") PresentationRole.Title else PresentationRole.Content, value)
  }
  def listModel(entries: Vector[Entry]): DisplayModel.ListModel = DisplayModel.ListModel(listScreenId, "Normal list",
    entries.sortBy(_.identity.value).map(entry => DisplayModel.ListItem(entry.resource, _fields(entry), Vector.empty)),
    DetailTarget(screenId), Vector.empty, None)
  def detailModel(entry: Entry): DisplayModel.DetailModel = DisplayModel.DetailModel(screenId, "Normal update", entry.resource,
    Vector(DisplayModel.Section("main", None, _fields(entry))), Vector(action))
  def resetTrace(session: Session): Unit = {
    session.log.reset()
    session.lifecycleObservers.foreach(_.reset())
  }
  def writeAttempts(attempt: Attempt): Int = attempt.execution.lifecycleObservers.map(observer =>
    observer.creates.size + observer.deletes.size + observer.updates.size + observer.nativeUpdates.size).sum
  def staleUpdateConclusion(session: Session, observed: Long, current: Long): Conclusion =
    if (session.component.target == Target.Entity)
      conclusion(DisplayMutation.revisionConflictC(revision(observed), revision(current)))
    else conclusion(EntityConcurrencyMetadata.staleMutation(revision(observed), revision(current)))

  /** Hydrate only the independently declared expected rows/body, never a post-action source. */
  private def _expected_root(session: Session, rows: Rows, version: Long, view: Boolean): Root = {
    val revisionkey = session.component.representation.storageFieldName
    val stored = rows.selected.get
    val hydrated = rows.body.map(value => stored.fields.filterNot(_.key == "content") ++ value.fields.filter(_.key == "content"))
      .getOrElse(stored.fields)
    val fields = hydrated.filterNot(field => field.key == "id" ||
      (session.component.representation == EntityRevisionRepresentation.Detached && field.key == revisionkey)).map { field =>
      (field.key, field.value.single) match {
        case ("title" | "description" | "nullable", text: String) if text.startsWith("utf16:") =>
          field.copy(value = Field.Value.Single(text.drop(6).grouped(4).map(unit => Integer.parseInt(unit, 16).toChar).mkString))
        case _ => field
      }
    } :+ Field("id", Field.Value.Single(session.component.id))
    val complete = if (view) fields.filterNot(_.key == revisionkey) :+ Field(revisionkey, Field.Value.Single(version)) else fields
    Root(session.component.id, Record(complete))
  }
  def expectedReview(session: Session, before: LifecycleSnapshot, version: Long): LifecycleSnapshot = {
    def _present_(value: LifecycleView): Root = value match {
      case LifecycleView.Present(root) => root
      case _ => throw new IllegalStateException("Review requires the independently declared present pre-action Views")
    }
    val resident = before.resident
    val views = ViewState(_present_(resident.selectedview), _present_(resident.unrelatedview))
    val previous = DisplayBusinessReviewFixture.Baseline(before.rows, before.physical,
      Resident(resident.selected, resident.unrelated, resident.memoryselected, resident.memoryunrelated, views, resident.builds),
      Json.obj(), resident.builds)
    val wanted = DisplayBusinessReviewFixture.expected(session, previous, version)
    val root = _expected_root(session, wanted.rows, version, false)
    val view = _expected_root(session, wanted.rows, version, true)
    before.copy(rows = wanted.rows, physical = wanted.physical, resident = before.resident.copy(
      selected = Some(root), memoryselected = Some(root), selectedview = LifecycleView.Present(view), builds = before.resident.builds + 2))
  }

  /** At most four runtime owners; eviction completes cleanup before replacement. */
  final class Pool {
    private var _sessions = Map.empty[(Target, EntityRevisionRepresentation, Profile), Session]
    def liveSessionCount: Int = _sessions.size
    def session(sample: Sample): Session = {
      val key = (sample.target, sample.representation, sample.profile)
      _sessions.get(key) match {
        case Some(value) => value
        case None =>
          if (_sessions.size >= 4) {
            val (identity, retired) = _sessions.head
            _sessions = _sessions.removed(identity)
            retired.close()
          }
          val value = openReference(key._1, key._2, key._3)
          _sessions = _sessions.updated(key, value)
          value
      }
    }
    def close(): Unit = {
      val sessions = _sessions.values.toVector
      _sessions = Map.empty
      var failure: Option[Throwable] = None
      sessions.foreach { session =>
        try session.close() catch { case scala.util.control.NonFatal(error) => if (failure.isEmpty) failure = Some(error) }
      }
      failure.foreach(error => throw error)
    }
  }

  trait Vocabulary extends DisplayMutationServiceFixture.Vocabulary { self: Informing =>
    private def _check_mutation_input_failure(found: Conclusion, code: String, path: String): Unit = {
      val expected = conclusion(DisplayMutation.inputInvalidC(code, path))
      (found.observation.cause.getException, expected.observation.cause.getException) match {
        case (Some(actual: DisplayMutation.MutationException), Some(wanted: DisplayMutation.MutationException)) =>
          actual.diagnostic shouldBe wanted.diagnostic
          actual.getClass shouldBe wanted.getClass
          actual.getMessage shouldBe wanted.getMessage
        case _ => fail("Expected the canonical structured mutation diagnostic")
      }
      def _normalize_mutation_exception_(value: Conclusion): Conclusion = {
        val observation = value.observation
        val cause = observation.cause
        val descriptor = cause.descriptor
        value.copy(observation = observation.copy(cause = cause.copy(descriptor = descriptor.copy(
          facets = descriptor.facets.map {
            case Descriptor.Facet.Exception(carrier: DisplayMutation.MutationException) =>
              Descriptor.Facet.ExceptionLegacy(carrier.getClass.getName, Some(carrier.getMessage))
            case facet => facet
          }))))
      }
      checkLifecycleFailure(_normalize_mutation_exception_(found), _normalize_mutation_exception_(expected))
    }
    private def _check_fields(found: Vector[DisplayModel.Field], expected: Entry): Unit = {
      found shouldBe _fields(expected)
      found.zip(expected.values).foreach {
        case (DisplayModel.Field(_, _, _, DisplayValue.Number(actual)), DisplayValue.Number(wanted)) =>
          actual.bigDecimal.unscaledValue shouldBe wanted.bigDecimal.unscaledValue
          actual.bigDecimal.scale shouldBe wanted.bigDecimal.scale
        case _ => ()
      }
    }
    def checkProvenance(value: Json, session: Session): Display = {
      val profile = session.component.referenceProfile.get
      val found = DisplayProtocolCodec.decodeC(source(session), definitions(session), value.noSpaces).TAKE
      found.definition.provenance shouldBe provenance(profile)
      value.hcursor.downField("provenance").get[String]("producerId") shouldBe Right("cncf.development.editing-studio-reference")
      value.hcursor.downField("provenance").get[String]("sourceId") shouldBe Right("cncf.editing-studio-reference.v1:" + profile.toString)
      value.hcursor.downField("provenance").get[String]("revision") shouldBe Right("fixture-1")
      found
    }
    def checkDetail(value: Json, session: Session, entry: Entry): DisplayModel.Resource = {
      val found = checkProvenance(value, session)
      found.model shouldBe detailModel(entry)
      val model = found.model.asInstanceOf[DisplayModel.DetailModel]
      _check_fields(model.sections.flatMap(_.fields), entry)
      model.resource
    }
    def checkList(value: Json, session: Session, entries: Vector[Entry]): DisplayModel.ListModel = {
      val found = checkProvenance(value, session)
      found.model shouldBe listModel(entries)
      value.hcursor.downField("model").downField("snapshot").focus shouldBe None
      val model = found.model.asInstanceOf[DisplayModel.ListModel]
      model.items.zip(entries.sortBy(_.identity.value)).foreach { case (item, entry) => _check_fields(item.fields, entry) }
      model
    }
    def checkNoWrites(attempt: Attempt): Unit = {
      writeAttempts(attempt) shouldBe 0
      attempt.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
    }
    def checkCommitted(attempt: Attempt, session: Session, attempts: Int): Unit = {
      checkActionOwnership(attempt)
      attempt.execution.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      attempt.execution.uow.lastCommitResult shouldBe Some(Consequence.unit)
      writeAttempts(attempt) shouldBe attempts
      attempt.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
      session.log.events.count(_.event == "commit") shouldBe 1
      session.log.events.foreach(event => (event.uow eq attempt.execution.uow) shouldBe true)
      session.log.authorizations should not be empty
      session.log.authorizations.foreach { case (authorization, uow) =>
        (uow eq attempt.execution.uow) shouldBe true
        authorization.accessMode shouldBe EntityAccessMode.UserPermission
        authorization.sourceComponentName shouldBe Some(session.component.name)
        authorization.targetComponentName shouldBe Some(session.component.name)
        uow.executionContext.security.principal.id.value shouldBe "owner"
      }
    }
    def checkSemantic(state: LifecycleSnapshot, entry: Entry): Unit = state.resident.selectedview match {
      case LifecycleView.Present(root) =>
        root.id shouldBe entry.identity
        root.record.getString("title") shouldBe Some(entry.values(0).asInstanceOf[DisplayValue.Text].value)
        root.record.getString("description") shouldBe Some(entry.values(1).asInstanceOf[DisplayValue.Text].value)
        root.record.getString("stage") shouldBe Some(entry.values(6).asInstanceOf[DisplayValue.Text].value)
      case _ => fail("The selected semantic View must be present")
    }
    def query(session: Session, entries: Vector[Entry], principal: String = "owner"): (Json, DisplayModel.ListModel) = {
      Given("the independently declared List membership and complete canonical pre-read state")
      val before = session.lifecycleSnapshot()
      resetTrace(session)
      When("the client explicitly queries the ordinary capture-list screen")
      val attempt = session.execute(queryRequest(session), session.execution(principal = principal))
      val value = json(attempt.result)
      Then("the decoded ordered seven-field List has no eager Detail or actions and no writer")
      val model = checkList(value, session, entries)
      checkNoWrites(attempt)
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.queries should have size 1
      checkLifecycleState(session, DisplayMutationServiceListFixture.expectedReadObservation(before))
      value -> model
    }
    def select(session: Session, declaration: Declaration): Selected = {
      Given("independently declared canonical selected/unrelated IDs, values and managed revisions before List selection")
      val before = session.lifecycleSnapshot()
      checkSemantic(before, declaration.initial)
      val (listjson, list) = query(session, Vector(declaration.initial, declaration.other))
      val chosen = list.items.find(_.resource.entityId == declaration.selected).get
      chosen.resource shouldBe declaration.initial.resource
      val request = detailRequest(session, chosen.resource.entityId, list.detailTarget)
      resetTrace(session)
      When("the client follows the decoded List item's identity and logical DetailTarget")
      val attempt = session.execute(request)
      val value = json(attempt.result)
      Then("the selected Detail matches the independent values and supplies the next real observation")
      val resource = checkDetail(value, session, declaration.initial)
      checkNoWrites(attempt)
      session.log.events.count(_.event == "find") shouldBe 1
      session.log.queries shouldBe empty
      checkLifecycleState(session, before)
      Selected(listjson, value, resource)
    }
    def describe(session: Session, declaration: Declaration): Unit = {
      Given("the same complete pre-descriptor state and the exact profile capability declaration")
      val before = session.lifecycleSnapshot()
      resetTrace(session)
      When("ordinary describeMutation and describeBusiness expose inert registration metadata")
      val mutation = session.descriptor()
      val business = session.execute(session.descriptorRequest.copy(operation = "describeBusiness"))
      Then("six typed editable bindings, readonly stage, exact capabilities and the ordinary Business target are descriptive only")
      val descriptor = DisplayMutationCodec.decodeDescriptorC(session.component.inputBinding, json(mutation.result).noSpaces).TAKE
      descriptor.capabilities shouldBe capabilities(declaration.profile)
      descriptor.fields.map(_.location) shouldBe fieldIds.map(DisplayMutation.FieldLocator("main", _))
      descriptor.fields.take(6).map(_.editability) shouldBe Vector.fill(6)(DisplayMutation.FieldEditability.Editable)
      descriptor.fields.take(6).map(_.bindingId) shouldBe fieldIds.take(6).map(name => Some("update." + name))
      descriptor.fields.take(6).map(_.acceptedKinds) shouldBe Vector(Vector(ValueKind.Text), Vector(ValueKind.Text),
        Vector(ValueKind.Number), Vector(ValueKind.Boolean), Vector(ValueKind.Timestamp), Vector(ValueKind.Text, ValueKind.Missing))
      descriptor.fields.last.editability shouldBe DisplayMutation.FieldEditability.ReadOnly
      descriptor.fields.last.bindingId shouldBe None
      descriptor.fields.last.web.readonly shouldBe true
      val actions = json(business.result).hcursor.downField("actions").focus.get.asArray.get
      actions should have size 1
      val described = actions.head.hcursor
      described.get[String]("id") shouldBe Right("request-review")
      described.get[String]("bindingId") shouldBe Right("business:request-review")
      described.downField("operation").get[String]("componentId") shouldBe Right(session.component.componentId.name)
      described.downField("operation").get[String]("service") shouldBe Right("business")
      described.downField("operation").get[String]("operation") shouldBe Right("requestReview")
      described.downField("input").focus.get.asArray.get.map(_.hcursor.get[String]("name")) shouldBe
        Vector(Right("resourceId"), Right("revision"))
      checkNoWrites(mutation); checkNoWrites(business)
      session.log.events.count(event => event.event == "find" || event.event == "query" || event.event == "business") shouldBe 0
      checkLifecycleState(session, before)
    }
    def update(session: Session, selected: Selected, entry: Entry, inputs: Vector[DisplayMutation.FieldInput],
      applied: Boolean = true, pending: Boolean = false): Updated = {
      Given("the validated returned Detail identity/revision and independent raw typed inputs and complete next-state oracle")
      selected.resource shouldBe entry.resource
      val before = session.lifecycleSnapshot()
      val next = changed(entry, inputs, entry.version + (if (applied) 1L else 0L))
      val wanted = DisplayMutationServiceListFixture.expectedUpdate(session, before, inputs, applied, next.version)
      val model = updateModel(session, selected.resource, inputs)
      val request = updateRequest(session, selected.resource, inputs)
      resetTrace(session)
      if (pending) session.log.reload = Reload.Failure
      When("one ordinary standard update consumes that returned observation and the exact supplied typed text")
      val attempt = session.execute(request)
      val value = json(attempt.result)
      Then("the real committed receipt and one contextual read correspond to the predeclared full-state delta without replay authority")
      val result = DisplayMutationCodec.decodeResultC(session.component.inputBinding.selection, definitions(session), model,
        Some(selected.resource.revision), value.noSpaces).TAKE
      result.receipt.resource shouldBe next.resource
      result.receipt.kind shouldBe (if (applied) DisplayMutationResult.OutcomeKind.Updated else DisplayMutationResult.OutcomeKind.Unchanged)
      result.retryCommand shouldBe false
      checkCommitted(attempt, session, 1)
      session.log.conversions shouldBe DisplayMutationServiceDomain.fieldIds.filter(name => inputs.exists(_.location.fieldId == name))
      session.log.validations shouldBe 1
      session.log.commands shouldBe (if (session.component.target == Target.Entity) 0 else 1)
      session.log.events.count(_.event == "find") shouldBe 1
      session.log.queries shouldBe empty
      val (resource, detailjson) = if (pending) {
        result.projection shouldBe Some(DisplayMutationResult.ProjectionState.Pending(DisplayMutationResult.PendingReason.ProjectionUnavailable))
        session.log.reload = Reload.Current
        Given("the command has already committed and only a read may recover the projection")
        When("the client explicitly reads the current Detail after the read fault clears")
        val recovered = session.read()
        val fresh = json(recovered.result)
        Then("the recovered exact semantic values use the committed revision without a second provider mutation")
        checkNoWrites(recovered)
        writeAttempts(attempt) shouldBe 1
        checkDetail(fresh, session, next) -> fresh
      } else {
        result.projection shouldBe a[Some[?]]
        val fresh = value.hcursor.downField("projection").downField("display").focus.get
        checkDetail(fresh, session, next) -> fresh
      }
      checkLifecycleState(session, wanted)
      checkSemantic(wanted, next)
      checkDetail(selected.detailjson, session, entry)
      // The next observation is always the actual decoded Ready/recovery resource, never the expected model.
      Updated(Selected(selected.listjson, detailjson, resource), next, wanted)
    }
    def business(session: Session, selected: Selected, entry: Entry, recoverRead: Boolean = false): Updated = {
      Given("the same selected resource's validated current observation and a complete independent stage-only oracle")
      selected.resource shouldBe entry.resource
      val before = session.lifecycleSnapshot()
      val next = reviewed(entry)
      val wanted = expectedReview(session, before, next.version)
      val request = businessRequest(session, selected.resource)
      val olddetail = selected.detailjson
      resetTrace(session)
      When("the ordinary direct business.requestReview Operation receives resourceId and canonical positive revision String Properties")
      val attempt = session.execute(request)
      val response = json(attempt.result)
      Then("one canonical effect commits stage only and returns an ordinary response without Display or an implicit find/query")
      response shouldBe Json.obj("outcome" -> Json.fromString("review-requested"),
        "resourceId" -> Json.fromString(entry.identity.value), "revision" -> Json.fromLong(next.version))
      checkCommitted(attempt, session, 1)
      session.log.events.count(_.event == "business") shouldBe 1
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.queries shouldBe empty
      session.component.viewBuilds shouldBe before.resident.builds
      checkRows(session.rows(), wanted.rows)
      session.physical() shouldBe wanted.physical
      session.component.realm.get(session.component.unrelatedId) shouldBe before.resident.unrelated
      session.component.memory.get(session.component.unrelatedId) shouldBe before.resident.memoryunrelated
      if (recoverRead) {
        session.log.reload = Reload.Failure
        When("a later explicit Detail read encounters a real Browser failure after the Business commit")
        val failed = session.execute(detailRequest(session, selected.resource.entityId, DetailTarget(screenId)))
        Then("the separate read retains the exact Business response and durable state with no command replay")
        checkLifecycleFailure(conclusion(failed.result), projectionFailure.conclusion)
        checkNoWrites(failed)
        checkRows(session.rows(), wanted.rows)
        session.physical() shouldBe wanted.physical
        writeAttempts(attempt) shouldBe 1
        session.log.reload = Reload.Current
      }
      When("the client explicitly reloads Detail using the same selected identity")
      val reloaded = session.execute(detailRequest(session, selected.resource.entityId, DetailTarget(screenId)))
      val fresh = json(reloaded.result)
      Then("the fresh Display alone exposes the committed stage/revision and preserves the exact earlier title/description and immutable JSON")
      checkNoWrites(reloaded)
      session.log.events.count(_.event == "business") shouldBe 1
      session.log.events.count(_.event == "find") shouldBe (if (recoverRead) 2 else 1)
      session.log.queries shouldBe empty
      val resource = checkDetail(fresh, session, next)
      checkDetail(olddetail, session, entry)
      response shouldBe Json.obj("outcome" -> Json.fromString("review-requested"),
        "resourceId" -> Json.fromString(entry.identity.value), "revision" -> Json.fromLong(next.version))
      writeAttempts(attempt) shouldBe 1
      checkLifecycleState(session, wanted)
      checkSemantic(wanted, next)
      Updated(Selected(selected.listjson, fresh, resource), next, wanted)
    }
    def connected(session: Session, sample: Sample, nullableMissing: Boolean = false): Updated = {
      Given("the supplied lawful initial title, raw description and managed revision with independently declared selected/unrelated identities")
      val declaration = prepare(session, sample.initialTitle, sample.initialrevision)
      val selected = select(session, declaration)
      val inputs = sample.changes ++ (if (nullableMissing) Vector(field("nullable", DisplayValue.Missing)) else Vector.empty)
      val updated = update(session, selected, declaration.initial, inputs)
      query(session, Vector(updated.entry, declaration.other))
      val unchanged = update(session, updated.selected, updated.entry, inputs, applied = false)
      rejectMode(session, unchanged.selected, unchanged.entry, Rejection.StaleEqual, inputs, declaration.initialrevision)
      val result = business(session, unchanged.selected, unchanged.entry)
      query(session, Vector(result.entry, declaration.other))
      checkList(selected.listjson, session, Vector(declaration.initial, declaration.other))
      result
    }
    def rejectMode(session: Session, selected: Selected, entry: Entry, mode: Rejection,
      lawfulInputs: Vector[DisplayMutation.FieldInput], staleRevision: Long): Unit = {
      Given(s"independently selected $mode with the complete warmed pre-action state")
      val before = session.lifecycleSnapshot()
      val inputs = mode match {
        case Rejection.Readonly => Vector(field("stage", DisplayValue.Text("review-requested")))
        case Rejection.InvalidTitle => Vector(field("title", DisplayValue.Text(" \t\n")))
        case _ => lawfulInputs
      }
      val observed = if (mode == Rejection.StaleEqual) selected.resource.copy(revision = revision(staleRevision)) else selected.resource
      if (mode == Rejection.CurrentNoop) {
        When("the current equal typed standard update is interpreted")
        val unchanged = update(session, selected, entry, inputs, applied = false)
        Then("the full durable state retains its revision and exact values")
        checkRows(unchanged.state.rows, before.rows)
        unchanged.state.physical shouldBe before.physical
      } else {
        val expected = mode match {
          case Rejection.StaleEqual => staleUpdateConclusion(session, observed.revision.value, entry.version)
          case Rejection.Readonly => conclusion(DisplayMutation.inputInvalidC("FIELD_READ_ONLY", "request.fields.location"))
          case Rejection.InvalidTitle => domainFailure.conclusion
          case _ => throw new IllegalStateException("CurrentNoop has a distinct successful contract")
        }
        resetTrace(session)
        When("the ordinary update reaches its canonical stale-before-equality, readonly or title-constraint gate")
        val attempt = session.execute(updateRequest(session, observed, inputs))
        Then("the exact structured failure precedes provider mutation and preserves every pre-action state dimension")
        if (mode == Rejection.Readonly)
          _check_mutation_input_failure(conclusion(attempt.result), "FIELD_READ_ONLY", "request.fields.location")
        else checkLifecycleFailure(conclusion(attempt.result), expected)
        checkNoWrites(attempt)
        session.log.events.count(_.event == "find") shouldBe 0
        session.log.queries shouldBe empty
        checkLifecycleState(session, before)
      }
    }
    def unsupported(session: Session, declaration: Declaration): Unit = {
      describe(session, declaration)
      for (operation <- Vector(Operation.Create, Operation.Delete)) {
        Given(s"UpdateOnly's explicitly unsupported $operation and full independent baseline")
        val before = session.lifecycleSnapshot()
        resetTrace(session)
        When("the ordinary lifecycle operation receives otherwise lawful input")
        val attempt = session.execute(session.lifecycleRequest(operation, observed = declaration.initialrevision))
        Then("the existing operation-unsupported Conclusion precedes every canonical provider write or read")
        checkLifecycleFailure(conclusion(attempt.result), conclusion(DisplayMutation.operationUnsupportedC(operation)))
        checkNoWrites(attempt)
        session.log.events.count(_.event == "find") shouldBe 0
        session.log.queries shouldBe empty
        checkLifecycleState(session, before)
      }
    }
    def rejections(session: Session): Unit = {
      Given("docs/spec/display-editing-studio-reference.md R4 REF-E4: independent revision-seven values, selectors and complete state")
      val declaration = prepare(session, version = 7L)
      val selected = select(session, declaration)
      val lawful = Vector(field("title", DisplayValue.Text("Before")))
      val invalid = Vector(
        ("readonly", updateRequest(session, selected.resource, Vector(field("stage", DisplayValue.Text("review-requested")))),
          "FIELD_READ_ONLY", "request.fields.location"),
        ("unknown", updateRequest(session, selected.resource, Vector(field("unknown", DisplayValue.Text("private input")))),
          "FIELD_UNKNOWN", "request.fields.location"),
        ("kind", updateRequest(session, selected.resource, Vector(field("title", DisplayValue.Boolean(true)))),
          "VALUE_KIND_INVALID", "request.fields.value"),
        ("duplicate", session.rawRequest(session.duplicateFieldPayload(lawful.head), 7L), "FIELD_DUPLICATE", "$.changes"),
        ("blank", updateRequest(session, selected.resource, Vector(field("title", DisplayValue.Text(" \t\n")))), "", ""),
        ("stale", updateRequest(session, selected.resource.copy(revision = revision(6L)), lawful), "", ""))
      invalid.foreach { case (mode, candidate, code, path) =>
        Given(s"the independently chosen $mode rejection and complete warmed baseline")
        val before = session.lifecycleSnapshot()
        val expected = if (mode == "blank") domainFailure.conclusion
          else if (mode == "stale") staleUpdateConclusion(session, 6L, 7L)
          else conclusion(DisplayMutation.inputInvalidC(code, path))
        resetTrace(session)
        When("the ordinary update interprets the invalid typed field or stale observation")
        val attempt = session.execute(candidate)
        Then("the exact structured canonical failure preserves every logical/native/body/resident/View value without a writer")
        if (code.nonEmpty) _check_mutation_input_failure(conclusion(attempt.result), code, path)
        else checkLifecycleFailure(conclusion(attempt.result), expected)
        checkNoWrites(attempt)
        session.log.events.count(_.event == "find") shouldBe 0
        session.log.queries shouldBe empty
        checkLifecycleState(session, before)
      }
      val reads = Vector(queryRequest(session), detailRequest(session, selected.resource.entityId, DetailTarget(screenId)))
      val candidates = reads.flatMap { request =>
        Vector(
          (request.copy(properties = request.properties.map(property => if (property.name == "resourceType")
            property.copy(value = "foreign-resource") else property)), "RESOURCE_MISMATCH", "request.resourceType"),
          (request.copy(properties = request.properties.map(property => if (property.name == "screenId")
            property.copy(value = "foreign-screen") else property)), "SCREEN_UNDECLARED", "request.screenId"),
          (request.copy(properties = request.properties.map(property => if (property.name == "screenId")
            property.copy(value = if (request.operation == "query") screenId else listScreenId) else property)),
            "OPERATION_UNSUPPORTED", "request.screenId"))
      }
      candidates.foreach { case (candidate, code, path) =>
        Given("an independently invalid List/Detail selector and complete pre-request state")
        val before = session.lifecycleSnapshot()
        resetTrace(session)
        When("the ordinary service admits exact resource and screen selectors")
        val attempt = session.execute(candidate)
        Then("the exact redacted service diagnostic rejects before Browser or mutation and preserves the baseline")
        val failure = conclusion(attempt.result)
        failure.observation.taxonomy shouldBe conclusion(Consequence.argumentInvalid("expected")).observation.taxonomy
        failure.observation.cause.getException match {
          case Some(carrier: DisplayService.ServiceException) =>
            carrier.diagnostic.code shouldBe "DSP96_SERVICE_" + code
            carrier.diagnostic.path shouldBe path
          case _ => fail("Expected the canonical structured service diagnostic")
        }
        checkNoWrites(attempt)
        session.log.events.count(event => event.event == "find" || event.event == "query") shouldBe 0
        checkLifecycleState(session, before)
      }
    }
    def denials(session: Session): Unit = {
      Given("docs/spec/display-editing-studio-reference.md R5 REF-E5: an Enabled descriptor and ordinary canonical authorization")
      val declaration = prepare(session)
      val selected = select(session, declaration)
      describe(session, declaration)
      checkDetail(selected.detailjson, session, declaration.initial)
      query(session, Vector.empty, principal = "foreign")
      val modes = Vector("anonymous", "factory") ++ (if (session.component.target == Target.Entity) Vector("foreign") else Vector.empty)
      for (mode <- modes; service <- Vector("update", "business")) {
        Given(s"the same complete pre-action state before $mode $service, independently of Enabled presentation")
        val before = session.lifecycleSnapshot()
        resetTrace(session)
        session.log.denyFactory = mode == "factory"
        val active = session.execution(principal = if (mode == "foreign") "foreign" else "owner", authenticated = mode != "anonymous")
        val request = if (service == "business") businessRequest(session, selected.resource)
          else updateRequest(session, selected.resource, Vector(field("title", DisplayValue.Text("Denied change"))))
        When("the ordinary canonical command applies authentication and factory or Entity permission policy")
        val attempt = session.execute(request, active)
        Then("presentation grants no authority and rejection preserves the complete state without canonical provider writes or refresh")
        val failure = conclusion(attempt.result)
        if (mode == "factory") (failure eq denied.conclusion) shouldBe true
        else {
          val expected = if (mode == "anonymous") conclusion(Consequence.securityAuthenticationRequired("expected"))
            else conclusion(Consequence.securityPermissionDenied("expected", Cause.Kind.Permission, Vector.empty))
          failure.observation.taxonomy shouldBe expected.observation.taxonomy
          failure.status shouldBe expected.status
          if (mode == "foreign") failure.observation.cause.kind shouldBe Some(Cause.Kind.Permission)
        }
        checkNoWrites(attempt)
        session.log.events.count(_.event == "find") shouldBe 0
        session.log.queries shouldBe empty
        checkLifecycleState(session, before)
        session.log.denyFactory = false
      }
    }
    def crud(session: Session): Unit = {
      Given("the explicit Crud profile, a durably absent supplied selected identity and complete server-default/native create oracle")
      val declaration = prepare(session, create = true)
      declaration.profile shouldBe Profile.Crud
      val before = session.lifecycleSnapshot()
      val inputs = lifecycleFields.updated(0, field("title", DisplayValue.Text("Created:" + witnessText)))
        .updated(1, field("description", DisplayValue.Text(witnessText)))
      val next = Entry(declaration.selected, 1L, inputs.map(_.value) :+ DisplayValue.Text("draft"))
      val wanted = expectedLifecycleCreate(session, before, inputs)
      query(session, Vector(declaration.other))
      resetTrace(session)
      When("one ordinary create uses the reference's declared supplied identity")
      val attempt = session.execute(session.lifecycleRequest(Operation.Create, inputs))
      val value = json(attempt.result)
      Then("the real Created ID must equal the independent declared ID before any subsequent read")
      checkLifecycleResult(attempt, session, true, 1L)
      checkCommitted(attempt, session, 1)
      val created = checkDetail(value.hcursor.downField("projection").downField("display").focus.get, session, next)
      created.entityId shouldBe declaration.selected
      session.log.idCalls shouldBe empty
      checkLifecycleState(session, wanted)
      val (listjson, list) = query(session, Vector(next, declaration.other))
      val chosen = list.items.find(_.resource.entityId == declaration.selected).get
      When("the client follows the actual post-create List selection to Detail")
      val read = session.execute(detailRequest(session, chosen.resource.entityId, list.detailTarget))
      val detail = json(read.result)
      Then("the independently checked returned Detail observation drives the direct Business command")
      checkNoWrites(read)
      writeAttempts(attempt) shouldBe 1
      val observation = checkDetail(detail, session, next)
      val reviewed = business(session, Selected(listjson, detail, observation), next)
      query(session, Vector(reviewed.entry, declaration.other))
      Given("the returned current post-Business Detail observation and full independent logical-delete oracle")
      val deletebefore = session.lifecycleSnapshot()
      val deletewanted = expectedLifecycleDelete(session, deletebefore, reviewed.entry.version, logical = true)
      resetTrace(session)
      When("one observed delete consumes the actual current Detail identity/revision")
      val deleted = session.execute(deleteRequest(session, reviewed.selected.resource))
      Then("Deleted returns only inert reload-list with no implicit query or deleted Detail and one provider effect")
      checkLifecycleResult(deleted, session, false, reviewed.entry.version)
      checkCommitted(deleted, session, 1)
      session.log.events.count(_.event == "find") shouldBe 0
      session.log.queries shouldBe empty
      checkLifecycleState(session, deletewanted)
      query(session, Vector(declaration.other))
      writeAttempts(deleted) shouldBe 1
    }
    def checkNative(session: Session, state: LifecycleSnapshot): Unit = {
      session.backend shouldBe "postgresql"
      val rootname = DataStore.CollectionId.EntityStore(session.component.collectionId).collectionName
      val roots = state.physical.toVector.filter(_._1._2 == rootname)
      roots should have size 1
      roots.head._2.columns shouldBe lifecycleRootColumns(session.component.representation).map { name =>
        name -> (if (name == session.component.representation.storageFieldName || name == "content_byte_size") "int8" else "text")
      }
      roots.head._2.rows.map(_("id")) shouldBe Vector(session.component.id.value, session.component.unrelatedId.value).sorted
      val bodies = state.physical.toVector.filter(_._1._2 == "cncf_content_body_overflow")
      bodies should have size 1
      bodies.head._2.columns shouldBe lifecycleBodyColumns.map(name => name -> (if (name == "content_byte_size") "int8" else "text"))
      bodies.head._2.rows.foreach(row => row("content") shouldBe body)
    }
  }
}
