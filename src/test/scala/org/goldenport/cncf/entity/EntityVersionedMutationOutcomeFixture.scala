package org.goldenport.cncf.entity

import java.nio.file.{Files, Paths}
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.{Fixture, MemoryStore, Root}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedMutationResult, PostgresqlPersistenceFixture}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Shrink}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] object EntityVersionedMutationOutcomeFixture {
  val representations: Vector[EntityRevisionRepresentation] = AggregateObservedSnapshotFixture.representations
  val backends: Vector[String] = Vector("memory", "sqlite")
  enum Variant { case TypedSnapshot, TypedDetached, RecordSnapshot, RecordDetached }
  enum ProjectionMode { case Good, StructuredFailure, Throw, NullConsequence, NullEntity, ForeignId, WrongRevision }
  enum Mode { case Current, Changed, StaleCurrent, StaleChanged, FailureCurrent, FailureChanged }
  final case class Rows(selected: Option[Record], body: Option[Record], unrelated: Option[Record], unrelatedbody: Option[Record])
  final case class Sample(title: String, retained: String, revision: Long, mode: Mode,
    representation: EntityRevisionRepresentation)
  val structuredFailure: Consequence.Failure[Nothing] = Consequence.operationInvalid(
    "owned-domain-projection", Cause.Kind.Inconsistency,
    Vector(Descriptor.Facet.Reason("owned-domain-projection-rejected")))

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
  } yield Sample(title, retained, revision, mode, representation)
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

  def variants(representation: EntityRevisionRepresentation): Vector[Variant] = representation match {
    case EntityRevisionRepresentation.Embedded => Vector(Variant.TypedSnapshot, Variant.RecordSnapshot)
    case EntityRevisionRepresentation.Detached => Vector(Variant.TypedDetached, Variant.RecordDetached)
  }
  def typedVariant(representation: EntityRevisionRepresentation): Variant = variants(representation).head
  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(_) => throw new IllegalStateException("owned outcome fixture setup failed")
  }
  def revision(value: Long): EntityRevision = success(EntityRevision.createC(value))
  def conclusion(result: Consequence[?]): Conclusion = result match {
    case Consequence.Failure(value) => value
    case _ => throw new IllegalStateException("owned outcome fixture expected structured failure")
  }
  def reasons(result: Consequence[?]): Vector[String] = result match {
    case Consequence.Failure(value) => value.observation.cause.descriptor.facets.collect { case Descriptor.Facet.Reason(text) => text }.toVector
    case _ => Vector.empty
  }
  def raw(result: EntityVersionedMutationResult): Record = result match {
    case EntityVersionedMutationResult.Applied(record) => record
    case EntityVersionedMutationResult.NoOp(record) => record
    case _ => throw new IllegalStateException("owned outcome fixture expected positive provider result")
  }
  def isApplied(result: EntityVersionedMutationResult): Boolean = result match {
    case EntityVersionedMutationResult.Applied(_) => true
    case _ => false
  }
  def isNoOp(result: EntityVersionedMutationResult): Boolean = result match {
    case EntityVersionedMutationResult.NoOp(_) => true
    case _ => false
  }
  def bodyStorage(record: Record): Option[String] =
    record.getString("content_storage").orElse(record.getString("contentStorage"))
  def bodyReference(record: Record): Option[String] =
    record.getString("content_ref").orElse(record.getString("contentRef"))

  final class Probe(setup: Fixture) extends EntityPersistent[Root] {
    private var _mode: ProjectionMode = ProjectionMode.Good
    private var _admissions = 0
    private var _decodes = 0
    def admissions: Int = _admissions
    def decodes: Int = _decodes
    def arm(mode: ProjectionMode): Unit = { _mode = mode; _admissions = 0; _decodes = 0 }
    def id(entity: Root): EntityId = setup.persistent.id(entity)
    def toRecord(entity: Root): Record = setup.persistent.toRecord(entity)
    override def toStoreRecord(entity: Root): Record = setup.persistent.toStoreRecord(entity)
    override private[cncf] def admitStoreRecord(r: Record): Consequence[Record] = {
      _admissions += 1
      _mode match {
        case ProjectionMode.StructuredFailure => structuredFailure
        case ProjectionMode.Throw => throw new IllegalStateException("private projection secret")
        case ProjectionMode.NullConsequence => null
        case ProjectionMode.WrongRevision =>
          val actual = success(EntityRevisionBinding(setup.representation).revision(r))
          Consequence.success(r.upsertSingle(setup.revisionField, if (actual.value == 1L) 2L else 1L))
        case _ => Consequence.success(r)
      }
    }
    def fromRecord(record: Record): Consequence[Root] = {
      _decodes += 1
      _mode match {
        case ProjectionMode.NullEntity => Consequence.success(null)
        case ProjectionMode.ForeignId => setup.persistent.fromRecord(record).map(_.copy(id = setup.unrelatedId))
        case _ => setup.persistent.fromRecord(record)
      }
    }
  }

  final class Session(val setup: Fixture) {
    val binding: EntityRevisionBinding = EntityRevisionBinding(setup.representation)
    val store: StandardEntityStore = EntityStore.standard().asInstanceOf[StandardEntityStore]
    val probe: Probe = new Probe(setup)
    private var _provider_attempts = 0
    def providerAttempts: Int = _provider_attempts
    def seed(title: String = "Before", retained: String = "retained 日本 Ω", initialRevision: Long = 1L): Unit = {
      setup.seed(title, retained, initialRevision)
      probe.arm(ProjectionMode.Good)
      _provider_attempts = 0
    }
    def rows(reader: DataStore): Rows = {
      given ExecutionContext = setup.context
      def _load_(collection: DataStore.CollectionId, entry: DataStore.EntryId): Option[Record] =
        success(reader.load(collection, entry))
      Rows(_load_(setup.dataCollection, DataStore.EntryId(setup.id)),
        _load_(AggregateObservedSnapshotFixture.overflowCollection, setup.overflowEntry(setup.id)),
        _load_(setup.dataCollection, DataStore.EntryId(setup.unrelatedId)),
        _load_(AggregateObservedSnapshotFixture.overflowCollection, setup.overflowEntry(setup.unrelatedId)))
    }
    def mutate(title: String, observed: EntityRevision): Consequence[EntityVersionedMutationResult] = {
      given ExecutionContext = setup.context
      val previous = success(setup.provider.load(setup.dataCollection, DataStore.EntryId(setup.id))).get
      val current = success(setup.loadC()).withTitle(title)
      val candidate = store._complement_save_record(
        binding.withoutManagedRevision(setup.persistent.toStoreRecord(current)), setup.id, Some(previous))
      val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(setup.id, candidate))
      val policy = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic,
        EntityWritePolicy.WriteIfChanged, RevisionPreconditionPolicy.ObservedRequired, Some(observed))
      _provider_attempts += 1
      store._mutate_versioned(setup.dataCollection, DataStore.EntryId(setup.id),
        preparation, binding, Some(observed), policy)
    }
    def outcome(variant: Variant, result: EntityVersionedMutationResult): Consequence[EntityVersionedMutationOutcome[?]] = {
      given ExecutionContext = setup.context
      variant match {
        case Variant.TypedSnapshot => store._typed_snapshot_outcome_c(setup.id, result, binding, probe)
        case Variant.TypedDetached => store._typed_detached_outcome_c(setup.id, result, binding, probe)
        case Variant.RecordSnapshot => store._record_snapshot_outcome_c(setup.id, result, binding)
        case Variant.RecordDetached => store._record_detached_outcome_c(setup.id, result, binding)
      }
    }
    def legacy(variant: Variant, result: EntityVersionedMutationResult): Consequence[?] = {
      given ExecutionContext = setup.context
      variant match {
        case Variant.TypedSnapshot => store._typed_snapshot(setup.id, result, binding, probe)
        case Variant.TypedDetached => store._typed_detached_carrier(setup.id, result, binding, probe)
        case Variant.RecordSnapshot => store._record_snapshot(setup.id, result, binding)
        case Variant.RecordDetached => store._record_detached_carrier(setup.id, result, binding)
      }
    }
    def deleteSelectedBody(): Unit = success(setup.provider.delete(
      AggregateObservedSnapshotFixture.overflowCollection, setup.overflowEntry(setup.id))(using setup.context))
  }

  def session(token: String, representation: EntityRevisionRepresentation, writer: DataStore): Session =
    new Session(AggregateObservedSnapshotFixture.fixture(token, representation, writer))

  def withBackend[A](backend: String)(body: (DataStore, DataStore, () => Map[(String, String), StoredTable]) => A): A =
    if (backend == "memory") {
      val provider = new MemoryStore()
      body(provider, provider, () => Map.empty)
    } else {
      val directory = Paths.get("target", "entity-versioned-outcome", "work")
      Files.createDirectories(directory)
      val path = Files.createTempFile(directory, "owned-outcome-", ".sqlite")
      try {
        val writer = SqlDataStore.sqlite(path.toString)
        try {
          val reader = SqlDataStore.sqlite(path.toString)
          try body(writer, reader, () => AggregateObservedSnapshotFixture.sqlitePhysical(path))
          finally success(reader.closeC())
        } finally success(writer.closeC())
      } finally {
        Vector(path, Paths.get(path.toString + "-wal"), Paths.get(path.toString + "-shm"),
          Paths.get(path.toString + "-journal")).foreach(Files.deleteIfExists(_))
      }
    }

  def changedPhysical(before: Map[(String, String), StoredTable], setup: Fixture,
    title: String, version: Long): Map[(String, String), StoredTable] = before.map { case (key, table) =>
    val rows = if (key._2 == setup.dataCollection.collectionName) table.rows.map { row =>
      if (row.get("id").contains(DataStore.EntryId(setup.id).print))
        row.updated("title", title).updated(setup.revisionField, version)
          .updated("updated_at", AggregateObservedSnapshotFixture.instant.toString).updated("updated_by", "owner")
      else row
    } else table.rows
    key -> table.copy(rows = rows)
  }

  def withoutSelectedBodyPhysical(before: Map[(String, String), StoredTable],
    setup: Fixture): Map[(String, String), StoredTable] = before.map { case (key, table) =>
    val rows = if (key._2 == AggregateObservedSnapshotFixture.overflowCollection.collectionName)
      table.rows.filterNot(_.get("id").contains(setup.overflowEntry(setup.id).print))
    else table.rows
    key -> table.copy(rows = rows)
  }

  trait Vocabulary extends Matchers with GivenWhenThen { self: org.scalatest.Informing =>
    def checkConstructedFailure(actual: Conclusion, expected: Conclusion): Unit = {
      actual.status shouldBe expected.status
      actual.interpretation shouldBe expected.interpretation
      actual.disposition shouldBe expected.disposition
      actual.previous shouldBe expected.previous
      actual.observation.timestamp should not be null
      expected.observation.timestamp should not be null
      actual.observation.copy(timestamp = expected.observation.timestamp) shouldBe expected.observation
      expected.previous.foreach(previous => (actual.previous.get eq previous) shouldBe true)
    }
    def checkRecord(actual: Record, expected: Record): Unit = {
      actual.fields.map(_.key) shouldBe expected.fields.map(_.key)
      withClue(s"ordered Field differences=${_field_differences(actual.fields, expected.fields)}: ") {
        (actual.fields == expected.fields) shouldBe true
      }
    }
    private def _field_differences(actual: Vector[Field], expected: Vector[Field]): Vector[String] =
      (0 until math.max(actual.size, expected.size)).toVector.flatMap { index =>
        val left = actual.lift(index)
        val right = expected.lift(index)
        if (java.util.Objects.equals(left.orNull, right.orNull) && left.isDefined == right.isDefined) Vector.empty
        else {
          val leftkey = left.flatMap(Option(_)).flatMap(field => Option(field.key)).getOrElse("<absent-or-null>")
          val rightkey = right.flatMap(Option(_)).flatMap(field => Option(field.key)).getOrElse("<absent-or-null>")
          Vector(s"index=$index actual-key=$leftkey expected-key=$rightkey")
        }
      }
    def checkPhysical(actual: Map[(String, String), StoredTable], expected: Map[(String, String), StoredTable]): Unit = {
      val addresses = (actual.keySet ++ expected.keySet).toVector.sorted
      val differences = addresses.flatMap { address =>
        (actual.get(address), expected.get(address)) match {
          case (Some(left), Some(right)) =>
            val columns = if (left.columns == right.columns) Vector.empty else
              Vector(s"table=$address actual-columns=${left.columns} expected-columns=${right.columns}")
            val counts = if (left.rows.size == right.rows.size) Vector.empty else
              Vector(s"table=$address actual-row-count=${left.rows.size} expected-row-count=${right.rows.size}")
            val cells = (0 until math.max(left.rows.size, right.rows.size)).toVector.flatMap { index =>
              (left.rows.lift(index), right.rows.lift(index)) match {
                case (Some(leftrow), Some(rightrow)) =>
                  (leftrow.keySet ++ rightrow.keySet).toVector.sorted.collect {
                    case key if leftrow.contains(key) != rightrow.contains(key) ||
                        !java.util.Objects.equals(leftrow.getOrElse(key, null), rightrow.getOrElse(key, null)) =>
                      s"table=$address row-index=$index cell-key=$key"
                  }
                case (leftrow, rightrow) =>
                  Vector(s"table=$address row-index=$index actual-row-present=${leftrow.isDefined} expected-row-present=${rightrow.isDefined}")
              }
            }
            columns ++ counts ++ cells
          case (left, right) =>
            Vector(s"table=$address actual-table-present=${left.isDefined} expected-table-present=${right.isDefined}")
        }
      }
      withClue(s"physical table/column/row/cell-key differences=$differences: ") {
        (actual == expected) shouldBe true
      }
    }
    def checkRows(actual: Rows, expected: Rows): Unit = {
      Vector(actual.selected -> expected.selected, actual.body -> expected.body,
        actual.unrelated -> expected.unrelated, actual.unrelatedbody -> expected.unrelatedbody).foreach {
        case (Some(value), Some(reference)) => checkRecord(value, reference)
        case (value, reference) => value.isDefined shouldBe reference.isDefined
      }
    }
    def checkChanged(before: Rows, after: Rows, setup: Fixture, title: String, version: Long): Unit = {
      val previous = before.selected.get
      val actual = after.selected.get
      val atkeys = previous.fields.filter(field => Set("updated_at", "updatedAt").contains(field.key)).map(_.key)
      val bykeys = previous.fields.filter(field => Set("updated_by", "updatedBy").contains(field.key)).map(_.key)
      atkeys should have size 1
      bykeys should have size 1
      val atkey = atkeys.head
      val bykey = bykeys.head
      actual.fields.count(_.key == atkey) shouldBe 1
      actual.fields.count(_.key == bykey) shouldBe 1
      val changedkeys = Set("title", setup.revisionField, atkey, bykey)
      val retained = previous.fields.filterNot(field => changedkeys.contains(field.key)).sortBy(_.key)
      val actualretained = actual.fields.filterNot(field => changedkeys.contains(field.key)).sortBy(_.key)
      withClue(s"retained Field differences=${_field_differences(actualretained, retained)}: ") {
        (actualretained == retained) shouldBe true
      }
      actual.getString("title") shouldBe Some(title)
      success(EntityRevisionBinding(setup.representation).revision(actual)).value shouldBe version
      actual.getString(bykey) shouldBe Some("owner")
      actual.getAny(atkey) shouldBe Some(setup.provider match {
        case _: SqlDataStore => AggregateObservedSnapshotFixture.instant.toString
        case _ => AggregateObservedSnapshotFixture.instant
      })
      checkRows(after.copy(selected = None), before.copy(selected = None))
    }
    def checkRaw(outcome: EntityVersionedMutationOutcome[?], supplied: EntityVersionedMutationResult,
      observed: Record, setup: Fixture, applied: Boolean, version: Long): Unit = {
      outcome.id shouldBe setup.id
      outcome.applied shouldBe applied
      outcome.persisted.revision.value shouldBe version
      (outcome.persisted.record eq raw(supplied)) shouldBe true
      checkRecord(outcome.persisted.record, raw(supplied))
      checkRecord(outcome.persisted.record, observed)
      outcome.toString shouldBe "EntityVersionedMutationOutcome"
    }
    def checkProjection(value: Consequence[?], variant: Variant, rows: Rows, setup: Fixture, version: Long): Unit = {
      val stored = rows.selected.get
      val hydrated = rows.body match {
        case Some(body) => Record(stored.fields.filterNot(_.key == "content") :+
          Field("content", Field.Value.Single(body.getString("content").get)))
        case None => stored
      }
      val domain = if (setup.representation == EntityRevisionRepresentation.Detached)
        Record(hydrated.fields.filterNot(_.key == setup.revisionField)) else hydrated
      val typed = Record(domain.fields.filterNot(_.key == "id") :+ Field("id", Field.Value.Single(setup.id)))
      val projected = success(value)
      variant match {
        case Variant.TypedSnapshot =>
          val snapshot = projected.asInstanceOf[EntitySnapshot[Root]]
          snapshot.revision.value shouldBe version
          snapshot.entity.id shouldBe setup.id
          checkRecord(snapshot.entity.record, typed)
        case Variant.TypedDetached =>
          val carrier = projected.asInstanceOf[EntityRevisionCarrier[Root]]
          carrier.revision.value shouldBe version
          carrier.entity.id shouldBe setup.id
          checkRecord(carrier.entity.record, typed)
        case Variant.RecordSnapshot =>
          val snapshot = projected.asInstanceOf[EntityRecordSnapshot]
          snapshot.revision.value shouldBe version
          checkRecord(snapshot.record, domain)
        case Variant.RecordDetached =>
          val carrier = projected.asInstanceOf[EntityRevisionCarrier[Record]]
          carrier.revision.value shouldBe version
          checkRecord(carrier.entity, domain)
      }
    }
    def checkProjectionFailure(value: Consequence[?], applied: Boolean, original: Option[Conclusion] = None): Unit = {
      value.toOption.isEmpty shouldBe true
      if (applied) {
        reasons(value) should contain("committed-entity-projection-failure")
        conclusion(value).observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
        original.foreach(previous => checkConstructedFailure(conclusion(value),
          conclusion(EntityConcurrencyMetadata.committedProjectionFailure(previous))))
      } else original.foreach(previous => conclusion(value) shouldBe previous)
    }
    def checkClosedManagedReadFailure(value: Consequence[?], applied: Boolean): Unit = {
      checkProjectionFailure(value, applied)
      val actual = conclusion(value)
      val readfailure = if (applied) {
        actual.previous.isDefined shouldBe true
        actual.previous.get
      } else actual
      readfailure.previous shouldBe None
      readfailure.observation.taxonomy shouldBe Taxonomy(Taxonomy.Category.System, Taxonomy.Symptom.Corrupted)
      readfailure.getException.isDefined shouldBe true
      val error = readfailure.getException.get
      error should not be null
      error.getClass shouldBe classOf[IllegalStateException]
      error.getMessage shouldBe "managed SQL datastore resource is closed"
      checkConstructedFailure(readfailure, Conclusion.from(error))
      if (applied) checkProjectionFailure(value, true, Some(readfailure))
    }
    def checkSample(sample: Sample, current: Session, reader: DataStore,
      physical: () => Map[(String, String), StoredTable]): Boolean = {
      Given("one lawful generated root with full NULL, security, audit, body and unrelated state reset sequentially")
      current.seed(sample.title, sample.retained, sample.revision)
      val before = current.rows(reader)
      val beforephysical = physical()
      val changed = Set(Mode.Changed, Mode.StaleChanged, Mode.FailureChanged).contains(sample.mode)
      val stale = Set(Mode.StaleCurrent, Mode.StaleChanged).contains(sample.mode)
      val failure = Set(Mode.FailureCurrent, Mode.FailureChanged).contains(sample.mode)
      val title = if (changed) "changed:" + sample.title else sample.title
      val observed = revision(if (stale) sample.revision - 1L else sample.revision)
      When("one real canonical provider interprets the changed, current or stale candidate before optional projection")
      val supplied = success(current.mutate(title, observed))
      val after = current.rows(reader)
      val afterphysical = physical()
      current.probe.arm(if (failure) ProjectionMode.StructuredFailure else ProjectionMode.Good)
      val results = variants(sample.representation).map(variant => variant -> current.outcome(variant, supplied))
      Then("the total law specifies the real branch, complete storage, captured revision and independent projection")
      current.providerAttempts shouldBe 1
      if (stale) {
        supplied shouldBe EntityVersionedMutationResult.Stale(observed, revision(sample.revision))
        results.foreach { case (_, value) => checkConstructedFailure(conclusion(value), conclusion(
          EntityConcurrencyMetadata.staleMutation(observed, revision(sample.revision)))) }
        variants(sample.representation).foreach { variant =>
          checkConstructedFailure(conclusion(current.legacy(variant, supplied)), conclusion(
            EntityConcurrencyMetadata.staleMutation(observed, revision(sample.revision))))
        }
        current.probe.admissions shouldBe 0
        current.probe.decodes shouldBe 0
        checkRows(after, before)
        checkPhysical(afterphysical, beforephysical)
      } else {
        val version = if (changed) sample.revision + 1L else sample.revision
        if (changed) {
          isApplied(supplied) shouldBe true
          checkChanged(before, after, current.setup, title, version)
          checkPhysical(afterphysical, changedPhysical(beforephysical, current.setup, title, version))
        } else {
          isNoOp(supplied) shouldBe true
          checkRows(after, before)
          checkPhysical(afterphysical, beforephysical)
        }
        results.foreach { case (variant, value) =>
          val outcome = success(value)
          checkRaw(outcome, supplied, after.selected.get, current.setup, changed, version)
          if (failure && variant == typedVariant(sample.representation))
            checkProjectionFailure(outcome.projection, changed, Some(structuredFailure.conclusion))
          else checkProjection(outcome.projection, variant, after, current.setup, version)
        }
      }
      checkRows(current.rows(reader), after)
      checkPhysical(physical(), afterphysical)
      true
    }
  }
}
