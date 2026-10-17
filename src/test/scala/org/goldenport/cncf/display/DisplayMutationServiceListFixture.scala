package org.goldenport.cncf.display

import io.circe.Json
import java.nio.charset.StandardCharsets
import logicalui.DisplayMutationServiceDomain
import logicalui.DisplayMutationServiceDomain.{Root, BodyMode}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, PostgresqlPersistenceFixture, SearchableDataStore}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.{Field, Record}
import org.http4s.Method
import org.scalatest.Informing
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** C4C declarations, trusted exact-ID setup and independent lifecycle expectations. */
private[cncf] object DisplayMutationServiceListFixture {
  import DisplayMutationServiceFixture.*
  import DisplayMutation.Operation
  import GeneratedLogicalUiAbi.*

  val listScreenId: String = "capture-list"
  final case class Entry(identity: EntityId, version: Long, values: Vector[DisplayValue])
  final case class Witness(identity: EntityId, principal: String, version: Long, title: String)
  final case class WitnessState(witness: Witness, root: Option[Record], body: Option[Record])
  final case class Baseline(state: LifecycleSnapshot, witnesses: Vector[WitnessState])

  def listDefinition(detail: Definition): Definition = {
    val screen = detail.screen.asInstanceOf[DetailDefinition]
    detail.copy(screenId = listScreenId, screen = ListDefinition("Normal list", screen.sections.flatMap(_.fields),
      DetailTarget(screenId), Vector.empty, Vector.empty))
  }
  def witnessIds(component: NormalComponent): Vector[EntityId] = Vector("owner_witness", "foreign_witness").map { entropy =>
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("normal", "witness", component.collectionId, entropy = entropy)
  }
  def witnessModels(component: NormalComponent): Vector[Witness] = {
    val identities = witnessIds(component)
    Vector(Witness(identities(0), "owner", 17L, "Third owned 日本 Ω"),
      Witness(identities(1), "foreign", 23L, "Foreign owned λ"))
  }
  def retainedValues(title: String): Vector[DisplayValue] = Vector(DisplayValue.Text(title),
    DisplayValue.Text("retained 日本 Ω"), DisplayValue.Number(BigDecimal("123.4500")), DisplayValue.Boolean(true),
    DisplayValue.Timestamp(DisplayMutationServiceDomain.instant), DisplayValue.Text("present"), DisplayValue.Text("draft"))
  def expectedWitnessRoot(state: WitnessState, representation: EntityRevisionRepresentation): Root = {
    val stored = state.root.get
    val hydrated = state.body.map(bodyrecord => stored.fields.filterNot(_.key == "content") ++
      bodyrecord.fields.filter(_.key == "content")).getOrElse(stored.fields)
    val decoded = hydrated.filterNot(field => field.key == "id" || field.key == representation.storageFieldName).map { field =>
      (field.key, field.value.single) match {
        case ("title" | "description" | "nullable", text: String) if text.startsWith("utf16:") =>
          field.copy(value = Field.Value.Single(text.drop(6).grouped(4).map(unit => Integer.parseInt(unit, 16).toChar).mkString))
        case _ => field
      }
    }
    Root(state.witness.identity, Record(decoded ++ Vector(Field("id", Field.Value.Single(state.witness.identity)),
      Field(representation.storageFieldName, Field.Value.Single(state.witness.version)))))
  }
  def createdEntry(component: NormalComponent, fields: Vector[DisplayMutation.FieldInput]): Entry = {
    val values = fields.map(value => value.location.fieldId -> value.value).toMap
    val defaults = Vector("title" -> DisplayValue.Text(""), "description" -> DisplayValue.Text("server description"),
      "number" -> DisplayValue.Number(BigDecimal("42.1250")), "flag" -> DisplayValue.Boolean(false),
      "instant" -> DisplayValue.Timestamp(DisplayMutationServiceDomain.instant), "nullable" -> DisplayValue.Missing)
    Entry(component.id, 1L, defaults.map { case (name, value) => values.getOrElse(name, value) } :+ DisplayValue.Text("draft"))
  }
  def updatedEntry(entry: Entry, fields: Vector[DisplayMutation.FieldInput], version: Long): Entry = {
    val changes = fields.map(value => value.location.fieldId -> value.value).toMap
    entry.copy(version = version, values = (DisplayMutationServiceDomain.fieldIds :+ "stage").zip(entry.values)
      .map { case (name, value) => changes.getOrElse(name, value) })
  }
  def expectedUpdate(session: Session, before: LifecycleSnapshot, fields: Vector[DisplayMutation.FieldInput],
    applied: Boolean, version: Long): LifecycleSnapshot = {
    val wanted = session.expected(before.rows, before.physical, fields, applied, version)
    val resident = wanted.resident
    LifecycleSnapshot(wanted.rows, wanted.physical, LifecycleResident(resident.selected, resident.unrelated,
      resident.memoryselected, resident.memoryunrelated, LifecycleView.Present(resident.views.selected),
      LifecycleView.Present(resident.views.unrelated), resident.builds))
  }
  def queryRequest(session: Session): Request = Request.of(component = session.component.name, service = "display", operation = "query",
    properties = List(Property("resourceType", resourceType, None), Property("screenId", listScreenId, None)))
  def semanticQuery(session: Session, query: Query[?], active: Execution): Consequence[Vector[Root]] =
    session.subsystem._with_managed_datastore_lease_c { _ =>
      Consequence(active.bind()).flatMap(_ => session.component.browser.query_with_context(query)(using active.context))
    }
  def expectedReadObservation(before: LifecycleSnapshot): LifecycleSnapshot = {
    val missing = Vector(before.resident.selectedview, before.resident.unrelatedview).count {
      case LifecycleView.Missing(_) => true
      case _ => false
    }
    before.copy(resident = before.resident.copy(builds = before.resident.builds + missing))
  }
  def source(session: Session): DisplayProtocol.Source = DisplayProtocol.Source.createC(session.component.componentId, resourceType).TAKE
  def definitions(session: Session): AdmittedDefinitions = GeneratedLogicalUiAbi.admitC(session.component.generatedLogicalUiDefinitions).TAKE
  def expectedModel(session: Session, entries: Vector[Entry]): DisplayModel.ListModel = {
    val definition = listDefinition(session.component.generatedLogicalUiDefinitions.find(_.screenId == screenId).get)
    val screen = definition.screen.asInstanceOf[ListDefinition]
    val items = entries.sortBy(_.identity.value).map { entry =>
      DisplayModel.ListItem(DisplayModel.Resource(entry.identity, revision(entry.version)), screen.itemFields.zip(entry.values).map {
        case (field, value) => DisplayModel.Field(field.id, field.label, field.role, value)
      }, Vector.empty)
    }
    DisplayModel.ListModel(listScreenId, "Normal list", items, DetailTarget(screenId), Vector.empty, None)
  }

  final class ListSession(val baseSession: Session) {
    val witnesses: Vector[Witness] = witnessModels(baseSession.component)
    private var _closed = false
    def prepare(create: Boolean, version: Long = 1L, logical: Boolean = true): Baseline = {
      baseSession.prepareLifecycle(create, version, logical)
      witnesses.foreach { witness =>
        val root = Root(witness.identity, Record.dataAuto("id" -> witness.identity, "title" -> witness.title,
          "description" -> "retained 日本 Ω", "number" -> BigDecimal("123.4500"), "flag" -> true,
          "instant" -> DisplayMutationServiceDomain.instant, "nullable" -> "present", "stage" -> "draft",
          "business" -> "witness business", "audit" -> "witness audit", "content" -> body))
        baseSession.setupListWitness(root, witness.principal, witness.version)
      }
      baseSession.log.reset()
      baseSession.lifecycleObservers.foreach(_.reset())
      baseline()
    }
    def baseline(): Baseline = Baseline(baseSession.lifecycleSnapshot(), witnesses.map { witness =>
      val (root, bodyrecord) = baseSession.listWitnessRows(witness.identity)
      WitnessState(witness, root, bodyrecord)
    })
    def entries(selected: Option[Entry], principal: String = "owner"): Vector[Entry] = {
      val normal = if (principal == "owner") selected.toVector :+
        Entry(baseSession.component.unrelatedId, 1L, retainedValues("Unrelated")) else Vector.empty
      (normal ++ witnesses.filter(_.principal == principal).map(witness =>
        Entry(witness.identity, witness.version, retainedValues(witness.title)))).sortBy(_.identity.value)
    }
    def close(): Unit = if (!_closed) {
      _closed = true
      try baseSession.closeListWitnesses() finally baseSession.close()
    }
  }
  def openList(target: Target = Target.Entity, representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Embedded,
    backend: String = "memory", supplied: Boolean = true, bodyMode: BodyMode = BodyMode.Overflow,
    database: Option[PostgresqlPersistenceFixture.Database] = None): ListSession =
    new ListSession(open(target, representation, database, backend = backend,
      lifecycle = Some(LifecycleSetup(supplied = supplied, bodyMode = bodyMode, withList = true))))

  /** Each eviction closes witnesses and the managed base runtime before opening its replacement. */
  final class Pool(backend: String, database: Option[PostgresqlPersistenceFixture.Database] = None) {
    private var _sessions = Map.empty[(Target, EntityRevisionRepresentation, Boolean, BodyMode), ListSession]
    def liveSessionCount: Int = _sessions.size
    def session(sample: DisplayMutationServiceLifecyclePropertySpec.Sample): ListSession = {
      val key = (sample.target, sample.representation, sample.supplied, sample.bodymode)
      _sessions.get(key) match {
        case Some(value) => value
        case None =>
          if (_sessions.size >= 4) {
            val (identity, retired) = _sessions.head
            _sessions = _sessions.removed(identity)
            retired.close()
          }
          val value = openList(key._1, key._2, backend, key._3, key._4, database)
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
    def checkWitnesses(session: ListSession, wanted: Vector[WitnessState]): Unit = {
      When("independent managed readers load the two exact separately owned witness roots and bodies")
      val found = wanted.map { expected =>
        val (root, bodyrecord) = session.baseSession.listWitnessRows(expected.witness.identity)
        WitnessState(expected.witness, root, bodyrecord)
      }
      Then("every ordered business audit security revision and body field retains its independently captured pre-action value")
      found.zip(wanted).foreach { case (actual, expected) =>
        actual.root.map(_.fields) shouldBe expected.root.map(_.fields)
        actual.body.map(_.fields) shouldBe expected.body.map(_.fields)
      }
    }
    def checkListJson(value: Json, session: Session, entries: Vector[Entry]): Unit = {
      val found = DisplayProtocolCodec.decodeC(source(session), definitions(session), value.noSpaces).TAKE
      found.definition shouldBe listDefinition(session.component.generatedLogicalUiDefinitions.find(_.screenId == screenId).get)
      found.model shouldBe expectedModel(session, entries)
      value.hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
      value.hcursor.get[Int]("version") shouldBe Right(1)
      value.hcursor.get[String]("componentId") shouldBe Right(session.component.name)
      value.hcursor.get[String]("resourceType") shouldBe Right(resourceType)
      value.hcursor.downField("provenance").get[String]("sourceId") shouldBe Right("typed-normal-component-" + session.component.token)
      value.hcursor.downField("provenance").get[String]("producerId") shouldBe Right("cncf.development.display-update")
      value.hcursor.downField("provenance").get[String]("revision") shouldBe Right("fixture-1")
      value.hcursor.downField("model").downField("snapshot").focus shouldBe None
      found.model.asInstanceOf[DisplayModel.ListModel].items.zip(entries.sortBy(_.identity.value)).foreach { case (actual, expected) =>
        actual.fields.map(_.id) shouldBe (DisplayMutationServiceDomain.fieldIds :+ "stage")
        actual.fields.map(_.value) shouldBe expected.values
        actual.fields.zip(expected.values).foreach {
          case (DisplayModel.Field(_, _, _, DisplayValue.Number(actual)), DisplayValue.Number(expected)) =>
            actual.bigDecimal.unscaledValue shouldBe expected.bigDecimal.unscaledValue
            actual.bigDecimal.scale shouldBe expected.bigDecimal.scale
          case _ => ()
        }
      }
    }
    def checkDetailJson(value: Json, session: Session, entry: Entry): Unit = {
      val display = DisplayProtocolCodec.decodeC(source(session), definitions(session), value.noSpaces).TAKE
      display.definition shouldBe session.component.generatedLogicalUiDefinitions.find(_.screenId == screenId).get
      val model = display.model.asInstanceOf[DisplayModel.DetailModel]
      model.resource shouldBe DisplayModel.Resource(entry.identity, revision(entry.version))
      model.sections.flatMap(_.fields).map(_.value) shouldBe entry.values
      model.actions shouldBe Vector.empty
    }
    def checkSearch(attempt: Attempt, session: ListSession, expected: Vector[Entry], principal: String = "owner"): Unit = {
      val base = session.baseSession
      checkListJson(json(attempt.result), base, expected)
      val calls = base.log.queries.filter(pair => pair._2.unitOfWork eq attempt.execution.uow)
      calls should have size 1
      calls.head._1 shouldBe Query.fromRecord(Record.empty)
      calls.head._2.security.principal.id.value shouldBe principal
      calls.head._2.observability shouldBe observability
      val authorizations = base.log.authorizations.filter(pair => pair._2 eq attempt.execution.uow)
      authorizations should have size 1
      val authorization = authorizations.head._1
      authorization.resourceFamily shouldBe "domain"
      authorization.resourceType shouldBe Some(base.component.rootName)
      authorization.collectionName shouldBe Some(base.component.collectionId.name)
      authorization.accessKind shouldBe "search/list"
      authorization.accessMode shouldBe EntityAccessMode.UserPermission
      authorization.visibilityScope shouldBe Some(EntityVisibilityScope.Owner)
      authorization.targetId shouldBe None
      authorization.sourceComponentName shouldBe Some(base.component.name)
      authorization.targetComponentName shouldBe Some(base.component.name)
      val observers = attempt.execution.lifecycleObservers
      observers.flatMap(_.searches) should have size 1
      observers.flatMap(_.searches).foreach { case (collection, directive) =>
        collection shouldBe DataStore.CollectionId.EntityStore(base.component.collectionId)
        directive.query should not be org.goldenport.cncf.datastore.Query.Empty
      }
      observers.foreach { observer =>
        val collection = DataStore.CollectionId.EntityStore(base.component.collectionId)
        observer.totalCountCapability(collection) shouldBe observer.underlying.asInstanceOf[SearchableDataStore].totalCountCapability(collection)
      }
      observers.flatMap(_.creates) shouldBe Vector.empty
      observers.flatMap(_.deletes) shouldBe Vector.empty
      observers.flatMap(_.updates) shouldBe Vector.empty
      observers.map(_.writes).sum shouldBe 0
      if (base.component.target != Target.Entity)
        base.log.events.count(event => event.event == "aggregate" && (event.uow eq attempt.execution.uow)) shouldBe expected.size
    }
    def queryAndCheck(session: ListSession, expected: Vector[Entry], principal: String = "owner"): Json = {
      Given("the independently fixed complete List membership and the current durable/native/resident/View and witness baseline")
      val before = session.baseline()
      val base = session.baseSession
      val active = base.execution(principal = principal)
      val wanted = expectedReadObservation(before.state)
      When("the ordinary installed query operation searches the selected managed provider with owner visibility on its active UnitOfWork")
      val attempt = base.execute(queryRequest(base), active)
      Then("the exact ordered typed List and carrier revisions match the model without another command or provider mutation")
      checkSearch(attempt, session, expected, principal)
      checkLifecycleState(base, wanted)
      checkWitnesses(session, before.witnesses)
      json(attempt.result)
    }
    def checkUpdate(attempt: Attempt, session: Session, version: Long, applied: Boolean, pending: Boolean = false): Unit = {
      val value = json(attempt.result)
      value.hcursor.get[String]("outcome") shouldBe Right(if (applied) "updated" else "unchanged")
      value.hcursor.downField("resource").get[String]("entityId") shouldBe Right(session.component.id.value)
      value.hcursor.downField("resource").get[Long]("revision") shouldBe Right(version)
      value.hcursor.get[Boolean]("retryCommand") shouldBe Right(false)
      value.hcursor.downField("projection").get[String]("type") shouldBe Right(if (pending) "pending" else "ready")
      attempt.execution.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      attempt.execution.uow.lastCommitResult shouldBe Some(Consequence.unit)
      attempt.execution.lifecycleObservers.flatMap(_.updates) should have size 1
      attempt.execution.lifecycleObservers.map(_.writes).sum shouldBe 0
    }

    def createAndList(session: ListSession, fields: Vector[DisplayMutation.FieldInput] = lifecycleFields): Entry = {
      Given("a durably absent selected root, arbitrary owner/foreign witnesses and independent full create and List models")
      val base = session.baseSession
      val before = session.prepare(true)
      val wanted = expectedLifecycleCreate(base, before.state, fields)
      val entry = createdEntry(base.component, fields)
      val entries = session.entries(Some(entry))
      queryAndCheck(session, session.entries(None))
      base.log.reset()
      When("one authorized supplied or generated identity create commits through the actual selected provider")
      val attempt = base.execute(base.lifecycleRequest(Operation.Create, fields))
      Then("INITIAL Created has exactly one ready contextual Detail with the full independent durable/native/body/cache oracle")
      checkLifecycleResult(attempt, base, true, 1L)
      checkDetailJson(json(attempt.result).hcursor.downField("projection").downField("display").focus.get, base, entry)
      base.log.events.count(_.event == "find") shouldBe 1
      base.log.idCalls shouldBe (if (base.component.lifecycleSetup.get.supplied) Vector.empty else Vector(base.component.collectionId -> "entity-create"))
      checkLifecycleState(base, wanted)
      checkWitnesses(session, before.witnesses)
      queryAndCheck(session, entries)
      entry
    }

    def updateDeleteSequence(session: ListSession, original: Entry, changes: Vector[DisplayMutation.FieldInput], logical: Boolean): Unit = {
      val base = session.baseSession
      Given("the prior List independently identifies the selected Detail and its managed revision")
      val before = session.baseline()
      val changed = updatedEntry(original, changes, original.version + 1L)
      val wanted = expectedUpdate(base, before.state, changes, true, changed.version)
      When("the selected ordinary Detail is explicitly read and a changed update carries its strong revision")
      val detail = base.read()
      Then("the selected Detail has all seven values and its real carrier revision")
      checkDetailJson(json(detail.result), base, original)
      base.log.reset()
      When("the normal changed update commits and refreshes its contextual Detail")
      val updated = base.execute(base.request(changes, original.version))
      Then("all selected fields and durable/native/body/resident/View state equal the pre-action updated model")
      checkUpdate(updated, base, changed.version, true)
      checkDetailJson(json(updated.result).hcursor.downField("projection").downField("display").focus.get, base, changed)
      checkLifecycleState(base, wanted)
      checkWitnesses(session, before.witnesses)
      queryAndCheck(session, session.entries(Some(changed)))

      Given("the same values and current revision plus an independently stale pre-update revision")
      val currentbefore = session.baseline()
      val noopwanted = expectedUpdate(base, currentbefore.state, changes, false, changed.version)
      base.log.reset()
      When("the current no-op update is followed by a stale request carrying those equal values")
      val noop = base.execute(base.request(changes, changed.version))
      Then("the current no-op retains the managed revision and complete state")
      checkUpdate(noop, base, changed.version, false)
      checkDetailJson(json(noop.result).hcursor.downField("projection").downField("display").focus.get, base, changed)
      checkLifecycleState(base, noopwanted)
      val stalebefore = session.baseline()
      When("the stale-before-equality request reaches normal revision admission")
      val stale = base.execute(base.request(changes, original.version))
      Then("the exact stale conflict preserves the full independent state and List membership")
      conclusion(stale.result).observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
      reasons(stale.result) should contain("stale-entity-revision")
      stale.execution.lifecycleObservers.flatMap(_.updates) shouldBe Vector.empty
      checkLifecycleState(base, stalebefore.state)
      checkWitnesses(session, stalebefore.witnesses)
      queryAndCheck(session, session.entries(Some(changed)))

      Given(s"the exact selected root prepared for the canonical logical=$logical deletion policy, with matched current revision")
      if (!logical) base.setupLifecycleRoot(record => Record(record.fields.filterNot(_.key == "aliveness")))
      val deletebefore = session.baseline()
      val deletewanted = expectedLifecycleDelete(base, deletebefore.state, changed.version, logical)
      val entries = session.entries(None)
      base.log.reset()
      When("one observed delete commits and returns its inert reload-list descriptor")
      val deleted = base.execute(base.lifecycleRequest(Operation.Delete, observed = changed.version))
      Then("the matched prior revision and independent physical/logical full state survive without automatic List or deleted Detail")
      checkLifecycleResult(deleted, base, false, changed.version)
      base.log.queries shouldBe Vector.empty
      base.log.events.count(_.event == "find") shouldBe 0
      checkLifecycleState(base, deletewanted)
      checkWitnesses(session, deletebefore.witnesses)
      queryAndCheck(session, entries)
    }

    def rejectionSequence(session: ListSession): Unit = {
      val base = session.baseSession
      Given("a revision-seven owner root and separately seeded foreign witness with complete immutable rejection oracles")
      val before = session.prepare(false, 7L)
      val selected = Entry(base.component.id, 7L, retainedValues("Before"))
      val entries = session.entries(Some(selected))
      queryAndCheck(session, session.entries(None, "foreign"), "foreign")
      queryAndCheck(session, Vector.empty, "no-owned-rows")
      for (mode <- Vector("factory", "domain", "stale", "duplicate")) {
        Given(s"the same complete root/body/native/resident/View and witness baseline before the $mode rejection")
        base.log.reset()
        val snapshot = session.baseline()
        val fields = if (mode == "domain") Vector(field("title", DisplayValue.Text(" ")))
          else Vector(field("title", DisplayValue.Text("Rejected change")))
        base.log.denyFactory = mode == "factory"
        val request = if (mode == "duplicate") base.lifecycleRequest(Operation.Create)
          else base.request(fields, if (mode == "stale") 6L else 7L)
        val duplicate = conclusion(Consequence.DataStoreDuplicate(DataStore.EntryId(base.component.id).print))
        val duplicatecause = duplicate.observation.cause
          .addFacet(Descriptor.Facet.Reason("provider-failure"))
          .addFacet(Descriptor.Facet.Policy("entity.versioned-mutation.provider"))
        val expectedduplicate = duplicate.copy(observation = duplicate.observation.copy(cause = duplicatecause))
        When("the ordinary command reaches its exact canonical factory domain revision or unique-ID rejection gate")
        val rejected = base.execute(request)
        Then("the responsible structured failure has no success receipt and leaves every selected/unrelated/witness state value unchanged")
        rejected.result shouldBe a[Consequence.Failure[?]]
        if (mode == "factory") (conclusion(rejected.result) eq denied.conclusion) shouldBe true
        if (mode == "domain") (conclusion(rejected.result) eq domainFailure.conclusion) shouldBe true
        if (mode == "stale") {
          conclusion(rejected.result).observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
          reasons(rejected.result) should contain("stale-entity-revision")
        }
        if (mode == "duplicate") checkLifecycleFailure(conclusion(rejected.result), expectedduplicate)
        checkLifecycleState(base, snapshot.state)
        checkWitnesses(session, snapshot.witnesses)
        base.log.denyFactory = false
        queryAndCheck(session, entries)
      }
      checkWitnesses(session, before.witnesses)
    }

    def pendingSequence(session: ListSession): Unit = {
      val base = session.baseSession
      Given("an absent selected root with independent full committed-create and exact List models and a postcommit read fault")
      val before = session.prepare(true)
      val createwanted = expectedLifecycleCreate(base, before.state, lifecycleFields)
      val createdentry = createdEntry(base.component, lifecycleFields)
      base.log.reload = Reload.Failure
      When("one real create commits before contextual Detail fails")
      val created = base.execute(base.lifecycleRequest(Operation.Create))
      Then("Created pending retains the durable create without retry authority")
      checkLifecycleResult(created, base, true, 1L, "pending")
      val attempts = created.execution.lifecycleObservers.flatMap(_.creates).size
      base.log.reload = Reload.Current
      checkLifecycleState(base, createwanted)
      checkWitnesses(session, before.witnesses)
      queryAndCheck(session, session.entries(Some(createdentry)))
      created.execution.lifecycleObservers.flatMap(_.creates) should have size attempts

      Given("the committed root and an independently chosen changed value with a postcommit Detail fault")
      val changes = Vector(field("title", DisplayValue.Text("Committed pending update 日本")))
      val updatebefore = session.baseline()
      val updatewanted = expectedUpdate(base, updatebefore.state, changes, true, 2L)
      val updatedentry = updatedEntry(createdentry, changes, 2L)
      base.log.reset()
      base.log.reload = Reload.Failure
      When("one real changed update commits before contextual Detail fails")
      val updated = base.execute(base.request(changes))
      Then("Updated pending retains the independently declared full committed state")
      checkUpdate(updated, base, 2L, true, pending = true)
      base.log.reload = Reload.Current
      checkLifecycleState(base, updatewanted)
      queryAndCheck(session, session.entries(Some(updatedentry)))
      updated.execution.lifecycleObservers.flatMap(_.updates) should have size 1

      Given("the current matched root and independent post-delete state before a List row projection fault")
      val deletebefore = session.baseline()
      val deletewanted = expectedLifecycleDelete(base, deletebefore.state, 2L, true)
      val entries = session.entries(None)
      base.log.reset()
      When("one observed delete commits before the client explicitly queries a failing List")
      val deleted = base.execute(base.lifecycleRequest(Operation.Delete, observed = 2L))
      Then("Deleted remains committed and requests explicit reload-list without replay")
      checkLifecycleResult(deleted, base, false, 2L)
      base.log.reload = Reload.RowFailure
      When("the explicit List reaches its normal selected row projector")
      val failed = base.execute(queryRequest(base))
      Then("the read failure cannot undo or replay the durable delete")
      failed.result shouldBe a[Consequence.Failure[?]]
      base.log.reload = Reload.Current
      checkLifecycleState(base, deletewanted)
      checkWitnesses(session, deletebefore.witnesses)
      queryAndCheck(session, entries)
      deleted.execution.lifecycleObservers.flatMap(_.deletes) should have size 1
      failed.execution.lifecycleObservers.flatMap(_.deletes) shouldBe Vector.empty
      failed.execution.lifecycleObservers.flatMap(_.updates) shouldBe Vector.empty
      failed.execution.lifecycleObservers.flatMap(_.creates) shouldBe Vector.empty
    }
  }
}
