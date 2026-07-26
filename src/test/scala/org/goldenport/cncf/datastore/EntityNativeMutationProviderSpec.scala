package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.goldenport.cncf.unitofwork.{
  CommitRecorder,
  PrepareResult,
  TransactionContext
}
import org.simplemodeling.model.datatype.EntityRevision
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 26, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityNativeMutationProviderSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity native mutation provider" should {
    "dispatch direct and compare-and-set plans without guarded or generic reads" in {
      Given("a recording provider that declares both native capabilities")
      val store = new RecordingNativeStore
      val space = new DataStoreSpace().useDataStore(store)
      given ExecutionContext = _context(store)

      When("DataStoreSpace executes direct and compare-and-set plans")
      val direct = space.mutateEntityDirect(_direct_plan())
      val compareandset =
        space.compareAndSetEntity(
          _compare_and_set_plan(EntityRevision.INITIAL)
        )

      Then("only the corresponding native provider operations are called")
      direct shouldBe Consequence.success(_applied_omitted)
      compareandset shouldBe Consequence.success(_applied_omitted)
      store.directCount shouldBe 1
      store.compareAndSetCount shouldBe 1
      store.guardedCount shouldBe 0
      store.loadCount shouldBe 0
    }

    "reject unsupported authoritative readback before invoking the provider" in {
      Given("a recording native provider without authoritative readback capability")
      val store = new RecordingNativeStore
      val space = new DataStoreSpace().useDataStore(store)
      given ExecutionContext = _context(store)

      When("direct and compare-and-set plans require authoritative records")
      val direct =
        space.mutateEntityDirect(
          _direct_plan(
            readbackrequirement =
              EntityMutationReadbackRequirement.AuthoritativeRecord
          )
        )
      val compareandset =
        space.compareAndSetEntity(
          _compare_and_set_plan(
            EntityRevision.INITIAL,
            readbackrequirement =
              EntityMutationReadbackRequirement.AuthoritativeRecord
          )
        )

      Then("capability admission fails without publishing either mutation")
      direct shouldBe a[Consequence.Failure[?]]
      compareandset shouldBe a[Consequence.Failure[?]]
      store.directCount shouldBe 0
      store.compareAndSetCount shouldBe 0
      store.guardedCount shouldBe 0
      store.loadCount shouldBe 0
    }

    "increment revision through the direct acknowledgment and readback contracts" in {
      Given("an in-memory Entity at revision one")
      val store = DataStore.inMemorySearchable()
      given ExecutionContext = _context(store)
      val seeded =
        store.create(
          _collection,
          _entry,
          _record("before", 1L)
        )

      When("direct mutation runs once without and once with readback")
      val first =
        seeded.flatMap(_ =>
          _provider(store).mutateEntityDirect(_direct_plan("middle"))
        )
      val second =
        first.flatMap(_ =>
          _provider(store).mutateEntityDirect(
            _direct_plan(
              "after",
              EntityMutationReadbackRequirement.AuthoritativeRecord
            )
          )
        )

      Then("each write advances revision and only the requested result carries the record")
      first shouldBe Consequence.success(_applied_omitted)
      second.map(_readback_name_and_revision) shouldBe
        Consequence.success(Some("after") -> Some(3L))
      store
        .load(_collection, _entry)
        .map(_.map(record =>
          record.getString("name") -> record.getAny(_revision_field)
        )) shouldBe
        Consequence.success(Some(Some("after") -> Some(3L)))
    }

    "classify stale compare-and-set without mutation and apply a matching plan" in {
      Given("an in-memory Entity at revision two")
      val store = DataStore.inMemorySearchable()
      given ExecutionContext = _context(store)
      val seeded =
        store.create(
          _collection,
          _entry,
          _record("before", 2L)
        )

      When("one stale and one matching compare-and-set plan execute")
      val stale =
        seeded.flatMap(_ =>
          _provider(store).compareAndSetEntity(
            _compare_and_set_plan(EntityRevision.INITIAL, "stale")
          )
        )
      val applied =
        stale.flatMap(_ =>
          _provider(store).compareAndSetEntity(
            _compare_and_set_plan(
              _revision(2L),
              "after",
              EntityMutationReadbackRequirement.AuthoritativeRecord
            )
          )
        )

      Then("stale reports actual revision and the matching write advances exactly once")
      stale shouldBe Consequence.success(
        EntityMutationProviderResult.Stale(
          EntityRevision.INITIAL,
          _revision(2L)
        )
      )
      applied.map(_readback_name_and_revision) shouldBe
        Consequence.success(Some("after") -> Some(3L))
    }

    "reject revision exhaustion without changing the stored Entity" in {
      Given("two in-memory Entities at the maximum revision")
      val directstore = DataStore.inMemorySearchable()
      val casstore = DataStore.inMemorySearchable()
      given ExecutionContext = _context(directstore)
      val seeded =
        directstore
          .create(_collection, _entry, _record("direct", Long.MaxValue))
          .flatMap(_ =>
            casstore.create(
              _collection,
              _entry,
              _record("cas", Long.MaxValue)
            )
          )

      When("direct and matching compare-and-set mutations attempt to advance")
      val direct =
        seeded.flatMap(_ =>
          _provider(directstore).mutateEntityDirect(_direct_plan("changed"))
        )
      val compareandset =
        seeded.flatMap(_ =>
          _provider(casstore).compareAndSetEntity(
            _compare_and_set_plan(
              _revision(Long.MaxValue),
              "changed"
            )
          )
        )

      Then("both fail structurally and preserve their original values")
      direct shouldBe a[Consequence.Failure[?]]
      compareandset shouldBe a[Consequence.Failure[?]]
      directstore
        .load(_collection, _entry)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("direct"))
      casstore
        .load(_collection, _entry)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("cas"))
    }

    "report missing direct and compare-and-set targets structurally" in {
      Given("an in-memory collection that does not contain the requested Entity")
      val store = DataStore.inMemorySearchable()
      given ExecutionContext = _context(store)
      val seeded =
        store.create(
          _collection,
          DataStore.StringEntryId("other"),
          Record.dataAuto(
            "id" -> "other",
            "name" -> "other",
            _revision_field -> 1L
          )
        )

      When("both native operations address the missing Entity")
      val direct =
        seeded.flatMap(_ =>
          _provider(store).mutateEntityDirect(_direct_plan())
        )
      val compareandset =
        seeded.flatMap(_ =>
          _provider(store).compareAndSetEntity(
            _compare_and_set_plan(EntityRevision.INITIAL)
          )
        )

      Then("neither operation invents a record or weakens absence into stale")
      direct shouldBe a[Consequence.Failure[?]]
      compareandset shouldBe a[Consequence.Failure[?]]
      store.load(_collection, _entry) shouldBe Consequence.success(None)
    }

    "advance every admissible revision by exactly one" in {
      Given("generated positive revisions below the maximum")
      val revisions = Gen.chooseNum(1L, 1000000L)
      val property = Prop.forAll(revisions) { value =>
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        val result =
          store
            .create(_collection, _entry, _record("before", value))
            .flatMap(_ =>
              _provider(store).mutateEntityDirect(
                _direct_plan(
                  "after",
                  EntityMutationReadbackRequirement.AuthoritativeRecord
                )
              )
            )
        result.toOption.exists(
          _readback_name_and_revision(_) ==
            (Some("after") -> Some(value + 1L))
        )
      }

      When("the direct provider property is evaluated")
      val checked =
        Test.check(
          Test.Parameters.default.withMinSuccessfulTests(100),
          property
        )

      Then("native direct mutation preserves the revision successor invariant")
      checked.passed shouldBe true
    }
  }

  private val _collection =
    DataStore.CollectionId("entity_native_mutation")
  private val _entry =
    DataStore.StringEntryId("entity-1")
  private val _revision_field = "cncf_revision"
  private val _applied_omitted =
    EntityMutationProviderResult.Applied(
      EntityMutationProviderReadback.Omitted
    )

  private def _direct_plan(
    name: String = "after",
    readbackrequirement: EntityMutationReadbackRequirement =
      EntityMutationReadbackRequirement.None
  ): EntityDirectMutationPlan =
    EntityDirectMutationPlan(
      _collection,
      _entry,
      _revision_field,
      Record.data("name" -> name),
      readbackrequirement
    )

  private def _compare_and_set_plan(
    expectedrevision: EntityRevision,
    name: String = "after",
    readbackrequirement: EntityMutationReadbackRequirement =
      EntityMutationReadbackRequirement.None
  ): EntityCompareAndSetMutationPlan =
    EntityCompareAndSetMutationPlan(
      _collection,
      _entry,
      _revision_field,
      expectedrevision,
      Record.data("name" -> name),
      readbackrequirement
    )

  private def _record(
    name: String,
    revision: Long
  ): Record =
    Record.dataAuto(
      "id" -> _entry.print,
      "name" -> name,
      _revision_field -> revision
    )

  private def _readback_name_and_revision(
    result: EntityMutationProviderResult
  ): (Option[String], Option[Any]) =
    result match {
      case EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Authoritative(record)
          ) =>
        record.getString("name") -> record.getAny(_revision_field)
      case other =>
        fail(s"authoritative applied result expected: $other")
    }

  private def _provider(
    store: DataStore
  ): EntityVersionedMutationDataStore =
    store match {
      case provider: EntityVersionedMutationDataStore => provider
      case other =>
        fail(
          s"EntityVersionedMutationDataStore expected: ${other.getClass.getName}"
        )
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private final class RecordingNativeStore
      extends DataStore
      with EntityVersionedMutationDataStore {
    var directCount = 0
    var compareAndSetCount = 0
    var guardedCount = 0
    var loadCount = 0

    override def entityMutationProviderCapabilities
        : EntityMutationProviderCapabilities =
      EntityMutationProviderCapabilities(
        Set(
          EntityMutationProviderFeature.DirectAlwaysWrite,
          EntityMutationProviderFeature.OptimisticCompareAndSet
        )
      )

    def isAccept(cid: DataStore.CollectionId): Boolean = true

    def create(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      Consequence.unit

    def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Option[Record]] = {
      loadCount += 1
      Consequence.success(None)
    }

    def save(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      Consequence.unit

    def update(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      changes: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      Consequence.unit

    def delete(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Unit] =
      Consequence.unit

    override def mutateEntityDirect(
      plan: EntityDirectMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityMutationProviderResult] = {
      directCount += 1
      Consequence.success(_applied_omitted)
    }

    override def compareAndSetEntity(
      plan: EntityCompareAndSetMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityMutationProviderResult] = {
      compareAndSetCount += 1
      Consequence.success(_applied_omitted)
    }

    def mutateVersionedEntity(
      plan: EntityVersionedMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityVersionedMutationResult] = {
      guardedCount += 1
      Consequence.operationInvalid("unexpected-guarded-mutation")
    }

    def prepare(tx: TransactionContext): PrepareResult =
      PrepareResult.Prepared

    def commit(tx: TransactionContext): Unit = ()

    def abort(tx: TransactionContext): Unit = ()
  }
}
