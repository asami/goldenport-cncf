package org.goldenport.cncf.entity

import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.{Fixture, Root}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.observation.Descriptor
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Shrink}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] object EntityVersionedMutationPortFixture {
  import EntityVersionedMutationOutcomeFixture.{Probe, ProjectionMode, Rows, Variant, Mode,
    success, revision, conclusion, reasons, structuredFailure, variants, typedVariant}

  val representations = EntityVersionedMutationOutcomeFixture.representations
  val backends = EntityVersionedMutationOutcomeFixture.backends
  enum Route { case Direct, Space }
  enum NativeSqlSchema { case Sqlite, Postgresql }
  private final class NullCollectionId(collectionid: EntityCollectionId)
      extends EntityId("aggregate", "selected", collectionid, Some(java.time.Instant.EPOCH), Some("selected")) {
    override val collection: EntityCollectionId = null
  }
  def nullCollectionId(collectionId: EntityCollectionId): EntityId = new NullCollectionId(collectionId)

  final case class Patch(title: String)
  final case class Sample(title: String, retained: String, revision: Long, mode: Mode, variant: Variant)

  enum Captured {
    case Versioned(result: EntityVersionedMutationResult)
    case Native(result: EntityMutationProviderResult)

    def record: Option[Record] = this match {
      case Versioned(EntityVersionedMutationResult.Applied(value)) => Some(value)
      case Versioned(EntityVersionedMutationResult.NoOp(value)) => Some(value)
      case Native(EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(value))) => Some(value)
      case Native(EntityMutationProviderResult.NoOp(EntityMutationProviderReadback.Authoritative(value))) => Some(value)
      case _ => None
    }
    def applied: Boolean = this match {
      case Versioned(_: EntityVersionedMutationResult.Applied) | Native(_: EntityMutationProviderResult.Applied) => true
      case _ => false
    }
    def stale: Option[(EntityRevision, EntityRevision)] = this match {
      case Versioned(EntityVersionedMutationResult.Stale(expected, actual)) => Some(expected -> actual)
      case Native(EntityMutationProviderResult.Stale(expected, actual)) => Some(expected -> actual)
      case _ => None
    }
  }
  enum SelectedPlan {
    case Versioned(plan: EntityVersionedMutationPlan)
    case Direct(plan: EntityDirectMutationPlan)
    case CompareAndSet(plan: EntityCompareAndSetMutationPlan)
  }

  // Every feature and operation is delegated unchanged. Observation never supplies a result.
  final class RecordingStore(val underlying: DataStore) extends DataStore with EntityVersionedMutationDataStore {
    private val _provider = underlying.asInstanceOf[EntityVersionedMutationDataStore]
    private var _attempts = 0
    private var _generic_writes = 0
    private var _captured: Option[Captured] = None
    private var _plans = Vector.empty[SelectedPlan]
    private var _after_ack: () => Unit = () => ()
    def attempts: Int = _attempts
    def genericWrites: Int = _generic_writes
    def captured: Option[Captured] = _captured
    def plans: Vector[SelectedPlan] = _plans
    def reset(afterAck: () => Unit = () => ()): Unit = {
      _attempts = 0
      _generic_writes = 0
      _captured = None
      _plans = Vector.empty
      _after_ack = afterAck
    }
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      _provider.entityMutationProviderCapabilities
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1
      underlying.create(collection, id, record)
    }
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1
      underlying.save(collection, id, record)
    }
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1
      underlying.update(collection, id, changes)
    }
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _generic_writes += 1
      underlying.delete(collection, id)
    }
    def prepare(tx: TransactionContext): PrepareResult = underlying.prepare(tx)
    def commit(tx: TransactionContext): Unit = underlying.commit(tx)
    def abort(tx: TransactionContext): Unit = underlying.abort(tx)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _attempts += 1
      _plans :+= SelectedPlan.Direct(plan)
      _provider.mutateEntityDirect(plan).map { result =>
        _observe(Captured.Native(result))
        result
      }
    }
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _attempts += 1
      _plans :+= SelectedPlan.CompareAndSet(plan)
      _provider.compareAndSetEntity(plan).map { result =>
        _observe(Captured.Native(result))
        result
      }
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      _attempts += 1
      _plans :+= SelectedPlan.Versioned(plan)
      _provider.mutateVersionedEntity(plan).map { result =>
        _observe(Captured.Versioned(result))
        result
      }
    }
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] =
      _provider.compareAndDeleteEntity(plan)
    private def _observe(result: Captured): Unit = {
      _captured = Some(result)
      if (result.record.isDefined) _after_ack()
    }
  }

  def representation(variant: Variant): EntityRevisionRepresentation = variant match {
    case Variant.TypedSnapshot | Variant.RecordSnapshot => EntityRevisionRepresentation.Embedded
    case Variant.TypedDetached | Variant.RecordDetached => EntityRevisionRepresentation.Detached
  }
  def isTyped(variant: Variant): Boolean = variant == typedVariant(representation(variant))
  def policy(observed: EntityRevision): EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.WriteIfChanged,
      RevisionPreconditionPolicy.ObservedRequired, Some(observed))

  private val _characters = Vector('a', 'Z', '0', '日', '本', '語', 'α', 'β', 'Ω')
  private val _text: Gen[String] = for {
    length <- Gen.chooseNum(1, 24)
    characters <- Gen.listOfN(length, Gen.oneOf(_characters))
  } yield characters.mkString
  val samples: Gen[Sample] = for {
    title <- _text
    retained <- _text
    revision <- Gen.chooseNum(2L, 50L)
    mode <- Gen.oneOf(Mode.values.toVector)
    representation <- Gen.oneOf(representations)
    variant <- if (mode == Mode.FailureCurrent || mode == Mode.FailureChanged)
      Gen.const(typedVariant(representation)) else Gen.oneOf(variants(representation))
  } yield Sample(title, retained, revision, mode, variant)
  implicit val sampleShrink: Shrink[Sample] = Shrink { sample =>
    def _shorter_(value: String): LazyList[String] =
      if (value.length <= 1) LazyList.empty else LazyList(value.take(math.max(1, value.length / 2)))
    val titles = _shorter_(sample.title).map(value => sample.copy(title = value))
    val retained = _shorter_(sample.retained).map(value => sample.copy(retained = value))
    val revisions = if (sample.revision > 2L) LazyList(sample.copy(revision = 2L)) else LazyList.empty
    val modes = sample.mode match {
      case Mode.StaleChanged => LazyList(sample.copy(mode = Mode.StaleCurrent), sample.copy(mode = Mode.Changed))
      case Mode.FailureChanged => LazyList(sample.copy(mode = Mode.FailureCurrent), sample.copy(mode = Mode.Changed))
      case Mode.StaleCurrent | Mode.FailureCurrent | Mode.Changed => LazyList(sample.copy(mode = Mode.Current))
      case Mode.Current => LazyList.empty
    }
    (titles #::: retained #::: revisions #::: modes).toStream
  }

  final class Session(token: String, val variant: Variant, writer: DataStore,
    managed: Boolean = true, optimistic: Boolean = true) {
    val recording = new RecordingStore(writer)
    val setup: Fixture = AggregateObservedSnapshotFixture.fixture(token, representation(variant), recording,
      managed = managed, optimistic = optimistic)
    val oracle: Fixture = setup.peer(writer)
    val probe = new Probe(setup)
    val store: EntityStore = EntityStore.standard()
    val patchPersistent: EntityPersistentUpdate[Patch] = new EntityPersistentUpdate[Patch] {
      def collection(patch: Patch): EntityCollectionId = setup.collectionId
      def toRecord(patch: Patch): Record = Record.dataAuto("title" -> patch.title)
      def fromRecord(record: Record): Consequence[Patch] =
        record.getString("title").map(value => Consequence.success(Patch(value)))
          .getOrElse(Consequence.argumentMissing("title"))
    }
    private val _rows = new EntityVersionedMutationOutcomeFixture.Session(setup)
    def rows(reader: DataStore): Rows = _rows.rows(reader)
    def seed(title: String = "Before", retained: String = "retained 日本 Ω", initialRevision: Long = 1L): Unit = {
      setup.seed(title, retained, initialRevision)
      probe.arm(ProjectionMode.Good)
      recording.reset()
    }
    def input(title: String, observed: EntityRevision): Root = {
      val current = success(setup.loadC()).withTitle(title)
      if (setup.representation == EntityRevisionRepresentation.Embedded)
        current.copy(record = current.record.upsertSingle(setup.revisionField, observed.value))
      else current
    }
    def invokeC(route: Route, entity: Root, patch: Patch, observed: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy): Consequence[EntityVersionedMutationOutcome[?]] = {
      given ExecutionContext = setup.context
      route match {
        case Route.Direct => variant match {
          case Variant.TypedSnapshot => store._save_snapshot_outcome_c(entity, observed, executionPolicy)(using probe, setup.context)
          case Variant.TypedDetached => store._save_detached_outcome_c(entity, observed, executionPolicy)(using probe, setup.context)
          case Variant.RecordSnapshot => store._update_record_snapshot_outcome_c(setup.id, patch, observed, executionPolicy)(using patchPersistent, setup.context)
          case Variant.RecordDetached => store._update_record_detached_outcome_c(setup.id, patch, observed, executionPolicy)(using patchPersistent, setup.context)
        }
        case Route.Space => variant match {
          case Variant.TypedSnapshot => setup.context.entityStoreSpace._save_snapshot_outcome_c(entity, probe, observed, executionPolicy)
          case Variant.TypedDetached => setup.context.entityStoreSpace._save_detached_outcome_c(entity, probe, observed, executionPolicy)
          case Variant.RecordSnapshot => setup.context.entityStoreSpace._update_record_snapshot_outcome_c(setup.id, patch, patchPersistent, observed, executionPolicy)
          case Variant.RecordDetached => setup.context.entityStoreSpace._update_record_detached_outcome_c(setup.id, patch, patchPersistent, observed, executionPolicy)
        }
      }
    }
    def legacyC(entity: Root, patch: Patch, observed: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy): Consequence[?] = variant match {
      case Variant.TypedSnapshot => store.save(entity, observed, executionPolicy)(using probe, setup.context)
      case Variant.TypedDetached => store.saveDetached(entity, observed, executionPolicy)(using probe, setup.context)
      case Variant.RecordSnapshot => store.updateById(setup.id, patch, observed, executionPolicy)(using patchPersistent, setup.context)
      case Variant.RecordDetached => store.updateByIdDetached(setup.id, patch, observed, executionPolicy)(using patchPersistent, setup.context)
    }
    def arm(mode: ProjectionMode = ProjectionMode.Good, afterAck: () => Unit = () => ()): Unit = {
      probe.arm(mode)
      recording.reset(afterAck)
    }
  }

  def expectedChangedPhysical(before: Map[(String, String), StoredTable], current: Session,
    title: String, version: Long): Map[(String, String), StoredTable] = {
    val changed = EntityVersionedMutationOutcomeFixture.changedPhysical(before, current.oracle, title, version)
    if (isTyped(current.variant)) changed
    else changed.map { case (key, table) =>
      val rows = if (key._2 == current.setup.dataCollection.collectionName) table.rows.map { row =>
        if (row.get("id").contains(DataStore.EntryId(current.setup.id).print)) row.map {
          case ("trace_id", _) => "trace_id" -> current.setup.context.observability.traceId.value
          case ("correlation_id", _) => "correlation_id" -> current.setup.context.observability.correlationId.get.value
          case cell => cell
        } else row
      } else table.rows
      key -> table.copy(rows = rows)
    }
  }

  trait Vocabulary extends EntityVersionedMutationOutcomeFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkConstructedSourceFailure(actual: Conclusion, expected: Conclusion, sourceFile: String): Unit = {
      Given("independently constructed producer and specification Conclusions with typed source provenance")
      val actualfacets = actual.observation.cause.descriptor.facets
      val expectedfacets = expected.observation.cause.descriptor.facets
      When("the typed source facets are resolved for separate provenance validation and complete Observation comparison")
      val actualsources = actualfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      val expectedsources = expectedfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      Then("producer/test provenance, source position, every remaining Conclusion component and previous identity are exact")
      actualsources should have size 1
      expectedsources should have size 1
      val (actualsource, actualindex) = actualsources.head
      val (expectedsource, expectedindex) = expectedsources.head
      actualindex shouldBe expectedindex
      actualsource.pos.file.endsWith(sourceFile) shouldBe true
      Set("EntityVersionedMutationPortSpec.scala", "PostgresqlEntityVersionedMutationPortAcceptanceSpec.scala")
        .exists(expectedsource.pos.file.endsWith) shouldBe true
      Vector(actualsource, expectedsource).foreach { source =>
        source.pos.line should be > 0
        source.pos.column should be > 0
      }
      actual.status shouldBe expected.status
      actual.interpretation shouldBe expected.interpretation
      actual.disposition shouldBe expected.disposition
      actual.previous shouldBe expected.previous
      expected.previous.foreach(previous => (actual.previous.get eq previous) shouldBe true)
      actual.observation.timestamp should not be null
      expected.observation.timestamp should not be null
      val normalized = actual.observation.copy(timestamp = expected.observation.timestamp,
        cause = actual.observation.cause.copy(descriptor = actual.observation.cause.descriptor.copy(
          facets = actualfacets.updated(actualindex, expectedsource))))
      normalized shouldBe expected.observation
    }
    def checkLegacyWrongRevisionProjection(value: Consequence[?], variant: Variant,
      rows: Rows, setup: Fixture, version: Long): Unit = {
      Given("the independent complete persisted rows and owned overflow body before legacy admission")
      val stored = rows.selected.get
      val hydrated = rows.body match {
        case Some(body) => Record(stored.fields.filterNot(_.key == "content") :+
          Field("content", Field.Value.Single(body.getString("content").get)))
        case None => stored
      }
      When("the prescribed WrongRevision admission follows overflow hydration")
      val admitted = hydrated.upsertSingle(setup.revisionField, version)
      Then("the unchanged projection vocabulary compares every ordered Field and the legacy wrong revision")
      checkProjection(value, variant, rows.copy(selected = Some(admitted), body = None), setup, version)
    }
    def checkCaptured(outcome: EntityVersionedMutationOutcome[?], current: Session,
      rows: Rows, applied: Boolean, version: Long): Unit = {
      val captured = current.recording.captured.get
      captured.stale shouldBe None
      captured.applied shouldBe applied
      outcome.id shouldBe current.setup.id
      outcome.applied shouldBe applied
      outcome.persisted.revision.value shouldBe version
      (outcome.persisted.record eq captured.record.get) shouldBe true
      checkRecord(outcome.persisted.record, captured.record.get)
      checkRecord(outcome.persisted.record, rows.selected.get)
      outcome.toString shouldBe "EntityVersionedMutationOutcome"
    }
    def checkCounters(current: Session, attempts: Int = 1): Unit = {
      current.recording.attempts shouldBe attempts
      current.recording.genericWrites shouldBe 0
      current.recording.plans should have size attempts
      current.recording.entityMutationProviderCapabilities shouldBe
        current.recording.underlying.asInstanceOf[EntityVersionedMutationDataStore].entityMutationProviderCapabilities
    }
    def checkNativeSqlSchemaAbsent(current: Session, before: Rows,
      beforePhysical: Map[(String, String), StoredTable]): Unit = {
      Vector(before.selected.get, before.unrelated.get).foreach { record =>
        record.fields.exists(field => Set("deletedAt", "deleted_at").contains(field.key)) shouldBe false
      }
      val tables = beforePhysical.filter { case (key, _) => key._2 == current.setup.dataCollection.collectionName }
      tables should have size 1
      val table = tables.values.head
      table.columns.exists(_._1 == "deleted_at") shouldBe false
      table.rows.foreach(row => row.contains("deleted_at") shouldBe false)
    }
    private def _prepared_native_rows(before: Rows, schema: NativeSqlSchema): Rows = before
    private def _prepared_native_physical(before: Map[(String, String), StoredTable],
      current: Session, schema: NativeSqlSchema): Map[(String, String), StoredTable] = {
      val columntype = schema match {
        case NativeSqlSchema.Sqlite => "TEXT"
        case NativeSqlSchema.Postgresql => "text"
      }
      before.map { case (key, table) =>
        val prepared = if (key._2 == current.setup.dataCollection.collectionName)
          table.copy(columns = table.columns :+ ("deleted_at" -> columntype),
            rows = table.rows.map(row => row + ("deleted_at" -> null)))
        else table
        key -> prepared
      }
    }
    private def _check_native_guard(after: Rows, afterphysical: Map[(String, String), StoredTable],
      current: Session, schema: NativeSqlSchema): Unit = {
      Vector(after.selected.get, after.unrelated.get).foreach { record =>
        val guards = record.fields.filter(field => Set("deletedAt", "deleted_at").contains(field.key))
        guards shouldBe Vector.empty
      }
      val tables = afterphysical.filter { case (key, _) => key._2 == current.setup.dataCollection.collectionName }
      tables should have size 1
      val expectedtype = schema match {
        case NativeSqlSchema.Sqlite => "TEXT"
        case NativeSqlSchema.Postgresql => "text"
      }
      tables.values.head.columns.filter(_._1 == "deleted_at") shouldBe Vector("deleted_at" -> expectedtype)
      tables.values.head.rows.foreach { row =>
        row.contains("deleted_at") shouldBe true
        java.util.Objects.isNull(row("deleted_at")) shouldBe true
      }
    }
    private def _expected_applied_record(current: Session, baseline: Record,
      title: String, version: Long): Record = {
      val sql = current.recording.underlying.isInstanceOf[SqlDataStore]
      val timestamp: Any = if (sql) AggregateObservedSnapshotFixture.instant.toString
        else AggregateObservedSnapshotFixture.instant
      val changed = baseline.fields.map { field =>
        if (field.key == "title") field.copy(value = Field.Value.Single(title))
        else if (field.key == current.setup.revisionField) field.copy(value = Field.Value.Single(version))
        else if (Set("updated_at", "updatedAt").contains(field.key)) field.copy(value = Field.Value.Single(timestamp))
        else if (Set("updated_by", "updatedBy").contains(field.key)) field.copy(value = Field.Value.Single("owner"))
        else field
      }
      if (sql) Record(changed)
      else {
        val keys = if (isTyped(current.variant)) Vector(
          "id", "short_id", "name", "created_at", "created_by", "updated_at", "updated_by",
          "post_status", "aliveness", "owner_id", "group_id", "privilege_id", "permission",
          "trace_id", "correlation_id", "business", "audit", "title", "content_byte_size",
          "content_digest", "content_charset", "content_storage", "content_ref", current.setup.revisionField
        ) else current.recording.plans.head match {
          case SelectedPlan.Versioned(_) => Vector(
            "updated_at", "updated_by", "trace_id", "correlation_id", "title", "id", "short_id",
            "name", "created_at", "created_by", "post_status", "aliveness", "owner_id", "group_id",
            "privilege_id", "permission", "business", "audit", "content_byte_size", "content_digest",
            "content_charset", "content_storage", "content_ref", current.setup.revisionField
          )
          case SelectedPlan.Direct(_) | SelectedPlan.CompareAndSet(_) => Vector(
            "id", "business", "short_id", "name", "created_at", "created_by", "post_status", "aliveness",
            "owner_id", "group_id", "privilege_id", "permission", "audit", "content_byte_size",
            "content_digest", "content_charset", "content_storage", "content_ref", "updated_at", "updated_by",
            "trace_id", "correlation_id", "title", current.setup.revisionField
          )
        }
        Record(keys.map { key =>
          changed.find(_.key == key).getOrElse(fail(s"independent Applied expectation requires Field $key"))
        })
      }
    }
    def checkState(current: Session, before: Rows, after: Rows,
      beforePhysical: Map[(String, String), StoredTable], afterPhysical: Map[(String, String), StoredTable],
      title: String, applied: Boolean, version: Long, nativeSqlSchema: Option[NativeSqlSchema] = None): Unit = {
      Given("complete pre-action rows and physical state with the selected immutable context and save/patch variant")
      nativeSqlSchema.foreach { _ =>
        applied shouldBe true
        isTyped(current.variant) shouldBe false
        current.recording.underlying shouldBe a[SqlDataStore]
        current.recording.plans should have size 1
        current.recording.plans.head match {
          case SelectedPlan.Direct(_) | SelectedPlan.CompareAndSet(_) => ()
          case _ => fail("native schema expectation requires an actual direct or CAS patch plan")
        }
        checkNativeSqlSchemaAbsent(current, before, beforePhysical)
      }
      val previous = before.selected.get
      val tracealiases = Set("trace_id", "traceId")
      val correlationaliases = Set("correlation_id", "correlationId")
      val tracekeys = previous.fields.filter(field => tracealiases.contains(field.key)).map(_.key)
      val correlationkeys = previous.fields.filter(field => correlationaliases.contains(field.key)).map(_.key)
      val trace = current.setup.context.observability.traceId.value
      val correlation = current.setup.context.observability.correlationId.get.value
      When("the independent baseline prescribes applied patch context values before comparing observed state")
      val prepared = nativeSqlSchema.map(schema => _prepared_native_rows(before, schema)).getOrElse(before)
      val preparedphysical = nativeSqlSchema.map(schema => _prepared_native_physical(beforePhysical, current, schema))
        .getOrElse(beforePhysical)
      val baseline = if (applied && !isTyped(current.variant)) {
        val fields = prepared.selected.get.fields.map { field =>
          if (tracealiases.contains(field.key)) field.copy(value = Field.Value.Single(trace))
          else if (correlationaliases.contains(field.key)) field.copy(value = Field.Value.Single(correlation))
          else field
        }
        prepared.copy(selected = Some(Record(fields)))
      } else prepared
      val expectedrecord = if (applied) Some(_expected_applied_record(current, baseline.selected.get, title, version))
        else None
      val expectedphysical = if (applied) expectedChangedPhysical(preparedphysical, current, title, version)
        else preparedphysical
      Then("the independently prescribed full ordered Applied Record, patch context, absent SQL NULL attributes, body, unrelated row and physical cells correspond")
      if (applied) {
        if (!isTyped(current.variant)) {
          val actual = after.selected.get
          tracekeys should have size 1
          correlationkeys should have size 1
          val tracekey = tracekeys.head
          val correlationkey = correlationkeys.head
          actual.fields.count(field => tracealiases.contains(field.key)) shouldBe 1
          actual.fields.count(field => correlationaliases.contains(field.key)) shouldBe 1
          actual.getString(tracekey) shouldBe Some(trace)
          actual.getString(correlationkey) shouldBe Some(correlation)
        }
        checkChanged(baseline, after, current.oracle, title, version)
        checkRecord(after.selected.get, expectedrecord.get)
      } else {
        checkRows(after, before)
      }
      checkPhysical(afterPhysical, expectedphysical)
      nativeSqlSchema.foreach(schema => _check_native_guard(after, afterPhysical, current, schema))
    }
    def checkSample(sample: Sample, current: Session, reader: DataStore,
      physical: () => Map[(String, String), StoredTable], route: Route = Route.Space): Boolean = {
      Given("one sequentially seeded lawful typed sample with full body, NULL, security, audit and unrelated state")
      current.seed(sample.title, sample.retained, sample.revision)
      val before = current.rows(reader)
      val beforephysical = physical()
      val changed = Set(Mode.Changed, Mode.StaleChanged, Mode.FailureChanged).contains(sample.mode)
      val stale = Set(Mode.StaleCurrent, Mode.StaleChanged).contains(sample.mode)
      val failure = Set(Mode.FailureCurrent, Mode.FailureChanged).contains(sample.mode)
      val title = if (changed) "changed:" + sample.title else sample.title
      val observed = revision(if (stale) sample.revision - 1L else sample.revision)
      val entity = current.input(title, observed)
      current.arm(if (failure) ProjectionMode.StructuredFailure else ProjectionMode.Good)
      When("exactly one new producer port interprets the typed save or title patch through the real provider")
      val result = current.invokeC(route, entity, Patch(title), Some(observed), policy(observed))
      val after = current.rows(reader)
      val afterphysical = physical()
      Then("the real branch, exact raw reference, complete state, projection and one-attempt law correspond")
      checkCounters(current)
      if (stale) {
        current.recording.captured.get.stale shouldBe Some(observed -> revision(sample.revision))
        checkConstructedFailure(conclusion(result), conclusion(EntityConcurrencyMetadata.staleMutation(observed, revision(sample.revision))))
        current.probe.admissions shouldBe 0
        current.probe.decodes shouldBe 0
        checkRows(after, before)
        checkPhysical(afterphysical, beforephysical)
      } else {
        val version = if (changed) sample.revision + 1L else sample.revision
        val outcome = success(result)
        checkCaptured(outcome, current, after, changed, version)
        checkState(current, before, after, beforephysical, afterphysical, title, changed, version)
        if (failure) checkProjectionFailure(outcome.projection, changed, Some(structuredFailure.conclusion))
        else checkProjection(outcome.projection, current.variant, after, current.setup, version)
        current.probe.admissions shouldBe (if (isTyped(current.variant)) 1 else 0)
        current.probe.decodes shouldBe (if (isTyped(current.variant) && !failure) 1 else 0)
      }
      checkRows(current.rows(reader), after)
      checkPhysical(physical(), afterphysical)
      true
    }
  }
}
