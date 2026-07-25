package org.goldenport.cncf.datastore

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStoreConditionalTransitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _model_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, examples:E5,E13,E14, rules:R6-R13, phase:49"
    )
  private val _admission_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E15, rules:R8,R11-R13, phase:49"
    )

  "Conditional transition provider model" should {
    "admit only the closed bounded exact-value algebra" must _model_metadata {
      "when ordinary storage values and unsafe values are normalized" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R6,R7,R12; exact scalar values and rejected general values"
        )
        val entityid =
          EntityId("test", "entity_1", _root_collection_id)
        val accepted = Vector(
          "open",
          true,
          42,
          BigInt(43),
          BigDecimal("43.25"),
          Instant.parse("2026-07-24T00:00:00Z"),
          Identifier("sku_1"),
          entityid
        ).map(DataStoreConditionalValue.from)
        val oversized =
          "x" * (DataStoreConditionalValue.MAX_ENCODED_LENGTH + 1)

        When("the provider-neutral exact values are constructed")
        val rejected = Vector(
          DataStoreConditionalValue.from(1.5d),
          DataStoreConditionalValue.from(1.5f),
          DataStoreConditionalValue.from(Record.dataAuto("a" -> 1)),
          DataStoreConditionalValue.from(Vector(1, 2)),
          DataStoreConditionalValue.from(null),
          DataStoreConditionalValue.from(oversized)
        )

        Then("all admitted scalar kinds succeed and all unsafe or unbounded kinds fail")
        accepted.forall(_.isSuccess) shouldBe true
        rejected.forall(_is_failure) shouldBe true
      }
    }

    "reject malformed or unbounded roots before a provider effect" must _model_metadata {
      "when revision, field, and root-change admission is invalid" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R6-R9,R12; one valid root shape and invalid variants"
        )
        val expected = _expected("status", "open")
        val toomany =
          Vector.tabulate(
            EntityConditionalTransitionSupport.MAX_EXPECTED_FIELDS + 1
          )(index => _expected(s"field_$index", index))

        When("root factories validate every provider-bound value")
        val missingrevision = DataStoreConditionalRoot.create(
          _component_owner,
          _root_collection,
          _root_entry,
          _revision_field,
          None,
          Vector(expected),
          Record.dataAuto("status" -> "closed"),
          _revision(2L)
        )
        val duplicatefields =
          _root(Vector(expected, expected))
        val reservedfield =
          _root(Vector(_expected(_revision_field, 1L)))
        val emptychanges =
          DataStoreConditionalRoot.create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            Vector(expected),
            Record.empty,
            _revision(2L)
          )
        val excessfields = _root(toomany)

        Then("missing tokens, duplicate/reserved fields, empty changes, and excess fields fail")
        Vector(
          missingrevision,
          duplicatefields,
          reservedfield,
          emptychanges,
          excessfields
        ).forall(_is_failure) shouldBe true
      }
    }

    "reject overlapping and unbounded compound targets" must _model_metadata {
      "when a successor or side record overlaps the guarded records" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R12,R13; one admitted root and closed successor"
        )
        val root = _success(_root(Vector(_expected("status", "open"))))
        val overlapping =
          DataStoreConditionalSuccessor.Create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            _successor_record(_root_entry.print)
          )
        val successor = _create_successor(_successor_entry)
        val sideeffects =
          Vector.fill(EntityConditionalTransitionSupport.MAX_SIDE_EFFECTS + 1)(
            EntityVersionedSideEffect.Save(
              _side_collection,
              DataStore.StringEntryId(java.util.UUID.randomUUID.toString),
              Record.dataAuto("value" -> "x")
            )
          )

        When("the complete provider plan is admitted")
        val overlap =
          DataStoreConditionalTransitionPlan.create(root, overlapping)
        val excess =
          DataStoreConditionalTransitionPlan.create(
            root,
            successor,
            sideeffects
          )

        Then("target isolation and side-effect bounds are enforced")
        overlap shouldBe a[Consequence.Failure[?]]
        excess shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject open or malformed provider record values" must _model_metadata {
      "when records contain null payloads, arbitrary objects, or non-deterministic collections" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R7,R12; one valid root and malformed provider-bound records"
        )
        val root = _success(_root(Vector(_expected("status", "open"))))
        val successor = _create_successor(_successor_entry)
        val unsafechange =
          DataStoreConditionalRoot.create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            Vector.empty,
            Record.dataAuto("unsafe" -> new Object),
            _revision(2L)
          )
        val unsafesuccessor =
          DataStoreConditionalSuccessor.Create(
            _component_owner,
            _successor_collection,
            _successor_entry,
            _revision_field,
            Record.dataAuto(
              "id" -> _successor_entry.print,
              "unsafe" -> new Object,
              _revision_field -> 1L
            )
          )
        val nullsave =
          EntityVersionedSideEffect.Save(
            _side_collection,
            DataStore.StringEntryId("null-side"),
            null
          )
        val nondeterministicsave =
          EntityVersionedSideEffect.Save(
            _side_collection,
            DataStore.StringEntryId("set-side"),
            Record.dataAuto("values" -> Set("a", "b"))
          )

        When("the root, successor, and complete plans are admitted")
        val unsaferootresult = unsafechange
        val unsafesuccessorresult =
          DataStoreConditionalTransitionPlan.create(root, unsafesuccessor)
        val nullsideresult =
          DataStoreConditionalTransitionPlan.create(
            root,
            successor,
            Vector(nullsave)
          )
        val nondeterministicsideresult =
          DataStoreConditionalTransitionPlan.create(
            root,
            successor,
            Vector(nondeterministicsave)
          )

        Then("every non-closed provider record fails before datastore execution")
        Vector(
          unsaferootresult,
          unsafesuccessorresult,
          nullsideresult,
          nondeterministicsideresult
        ).forall(_is_failure) shouldBe true
      }
    }

    "admit every declared safety limit at its exact boundary" must _model_metadata {
      "when fields, effects, values, collections, nesting, and correlation reach their maxima" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R7,R12; exact lower and upper boundary values"
        )
        val maximumtext =
          "v" * DataStoreConditionalValue.MAX_ENCODED_LENGTH
        val maximumfield =
          "f" * EntityConditionalTransitionSupport.MAX_FIELD_LENGTH
        val maximumrecord =
          Record.dataAuto(
            Vector.tabulate(
              EntityConditionalTransitionSupport.MAX_RECORD_FIELDS
            )(index => s"change_$index" -> index)*
          )
        val expectedfields =
          Vector.tabulate(
            EntityConditionalTransitionSupport.MAX_EXPECTED_FIELDS
          )(index => _expected(s"field_$index", index))
        val root =
          DataStoreConditionalRoot.create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            expectedfields,
            maximumrecord,
            _revision(2L)
          )
        val sideeffects =
          Vector.tabulate(
            EntityConditionalTransitionSupport.MAX_SIDE_EFFECTS
          )(index =>
            EntityVersionedSideEffect.Save(
              _side_collection,
              DataStore.StringEntryId(s"side-$index"),
              Record.dataAuto("value" -> index)
            )
          )
        val maximumcorrelation =
          "c" * EntityConditionalTransitionSupport.MAX_CORRELATION_LENGTH
        def _root_with_changes_(
          changes: Record
        ): Consequence[DataStoreConditionalRoot] =
          DataStoreConditionalRoot.create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            Vector.empty,
            changes,
            _revision(2L)
          )
        def _nested_record_(
          nestedrecords: Int
        ): Record =
          if (nestedrecords <= 1)
            Record.dataAuto("value" -> 1)
          else
            Record.dataAuto(
              "nested" -> _nested_record_(nestedrecords - 1)
            )
        val maximumsequence =
          Vector.tabulate(
            EntityConditionalTransitionSupport.MAX_COLLECTION_VALUES
          )(identity)
        val sequenceevaluation = new AtomicInteger(0)
        val lazyoverlimit =
          LazyList
            .from(0)
            .map { value =>
              sequenceevaluation.incrementAndGet()
              value
            }
            .take(
              EntityConditionalTransitionSupport.MAX_COLLECTION_VALUES + 64
            )

        When("the factories admit each exact maximum and reject its first over-limit value")
        val admittedvalue = DataStoreConditionalValue.from(maximumtext)
        val admittedplan =
          root.flatMap(
            DataStoreConditionalTransitionPlan.create(
              _,
              _create_successor(_successor_entry),
              sideeffects,
              DataStoreConditionalCorrelation(maximumcorrelation)
            )
          )
        val oversizedfield =
          DataStoreConditionalRoot.create(
            _component_owner,
            _root_collection,
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            Vector.empty,
            Record.dataAuto(
              ("f" * (EntityConditionalTransitionSupport.MAX_FIELD_LENGTH + 1)) ->
                "changed"
            ),
            _revision(2L)
          )
        val oversizedcorrelation =
          root.flatMap(
            DataStoreConditionalTransitionPlan.create(
              _,
              _create_successor(_successor_entry),
              Vector.empty,
              DataStoreConditionalCorrelation(
                "c" * (
                  EntityConditionalTransitionSupport.MAX_CORRELATION_LENGTH + 1
                )
              )
            )
          )
        val admittedsequence =
          _root_with_changes_(
            Record.dataAuto("values" -> maximumsequence)
          )
        val oversizedsequence =
          _root_with_changes_(
            Record.dataAuto(
              "values" -> Vector.tabulate(
                EntityConditionalTransitionSupport.MAX_COLLECTION_VALUES + 1
              )(identity)
            )
          )
        val admitteddepth =
          _root_with_changes_(
            Record.dataAuto(
              "nested" -> _nested_record_(
                EntityConditionalTransitionSupport.MAX_RECORD_DEPTH
              )
            )
          )
        val oversizeddepth =
          _root_with_changes_(
            Record.dataAuto(
              "nested" -> _nested_record_(
                EntityConditionalTransitionSupport.MAX_RECORD_DEPTH + 1
              )
            )
          )
        val oversizedrecord =
          _root_with_changes_(
            Record.dataAuto(
              Vector.tabulate(
                EntityConditionalTransitionSupport.MAX_RECORD_FIELDS + 1
              )(index => s"change_$index" -> index)*
            )
          )
        val boundedlazyfailure =
          _root_with_changes_(
            Record.dataAuto("values" -> lazyoverlimit)
          )

        Then("all exact maxima succeed and every first-over-limit value fails with bounded evaluation")
        admittedvalue shouldBe a[Consequence.Success[?]]
        admittedplan shouldBe a[Consequence.Success[?]]
        admittedsequence shouldBe a[Consequence.Success[?]]
        admitteddepth shouldBe a[Consequence.Success[?]]
        oversizedfield shouldBe a[Consequence.Failure[?]]
        oversizedcorrelation shouldBe a[Consequence.Failure[?]]
        oversizedsequence shouldBe a[Consequence.Failure[?]]
        oversizeddepth shouldBe a[Consequence.Failure[?]]
        oversizedrecord shouldBe a[Consequence.Failure[?]]
        boundedlazyfailure shouldBe a[Consequence.Failure[?]]
        sequenceevaluation.get() shouldBe
          EntityConditionalTransitionSupport.MAX_COLLECTION_VALUES + 1
      }
    }
  }

  "DataStoreSpace conditional transition admission" should {
    "reject non-Entity and cross-component roots before provider invocation" must _admission_metadata {
      "when a capable provider receives inadmissible collection ownership" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R11-R13; one invocation-counting provider"
        )
        val provider = new InvocationCountingDataStore
        given ExecutionContext = _context(provider)
        val space = summon[ExecutionContext].dataStoreSpace
        val root =
          _success(_root(Vector(_expected("status", "open"))))
        val nonentityroot =
          DataStoreConditionalRoot.create(
            _component_owner,
            DataStore.CollectionId("plain_root"),
            _root_entry,
            _revision_field,
            Some(EntityRevision.INITIAL),
            Vector(_expected("status", "open")),
            Record.dataAuto("status" -> "closed"),
            _revision(2L)
          )
        val nonentitysuccessor =
          DataStoreConditionalSuccessor.Create(
            _component_owner,
            DataStore.CollectionId("plain_successor"),
            _successor_entry,
            _revision_field,
            _successor_record(_successor_entry.print)
          )
        val nonentitysuccessorplan =
          DataStoreConditionalTransitionPlan.create(
            root,
            nonentitysuccessor
          )
        val crosscomponentplan =
          DataStoreConditionalTransitionPlan.create(
            root,
            _create_successor(
              _successor_entry,
              componentowner = _other_component_owner
            )
          )

        When("root, plan, and DataStoreSpace admission validate collection ownership")
        val nonentityprovider =
          nonentityroot.flatMap(root =>
            DataStoreConditionalTransitionPlan
              .create(root, _create_successor(_successor_entry))
              .flatMap(provider.conditionalTransition)
          )
        val nonentitysuccessorprovider =
          nonentitysuccessorplan.flatMap(provider.conditionalTransition)
        val crosscomponentprovider =
          crosscomponentplan.flatMap(provider.conditionalTransition)
        val nullroute = space.conditionalTransition(null)

        Then("every invalid model fails before the provider capability is called")
        Vector(
          nonentityroot,
          nonentitysuccessorplan,
          crosscomponentplan,
          nullroute,
          nonentityprovider,
          nonentitysuccessorprovider,
          crosscomponentprovider
        ).forall(_is_failure) shouldBe true
        provider.invocations shouldBe 0
      }
    }

    "reject unsupported and split provider domains without CRUD fallback" must _admission_metadata {
      "when collections do not share one capable datastore instance" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R11-R13; unsupported and collection-scoped providers"
        )
        val plan = _plan()
        val unsupportedcontext = _context(DataStore.noop())
        val unsupported = {
          given ExecutionContext = unsupportedcontext
          unsupportedcontext.dataStoreSpace.conditionalTransition(plan)
        }
        val rootprovider =
          new ScopedInvocationCountingDataStore(
            _root_collection.collectionName
          )
        val successorprovider =
          new ScopedInvocationCountingDataStore(
            _successor_collection.collectionName
          )
        val splitcontext = ExecutionContext.create()
        splitcontext.dataStoreSpace
          .useDataStore(rootprovider)
          .addDataStore(successorprovider)

        When("DataStoreSpace resolves all collections before execution")
        val split = {
          given ExecutionContext = splitcontext
          splitcontext.dataStoreSpace.conditionalTransition(plan)
        }

        Then("unsupported capability and split domains fail without invoking a transition")
        unsupported shouldBe a[Consequence.Failure[?]]
        split shouldBe a[Consequence.Failure[?]]
        rootprovider.invocations shouldBe 0
        successorprovider.invocations shouldBe 0
      }
    }

    "invoke one admitted capable provider through DataStoreSpace" must _admission_metadata {
      "when root, successor, and side records share one component and provider" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R11-R13; one capable datastore transaction domain"
        )
        val provider = new InvocationCountingDataStore
        given ExecutionContext = _context(provider)
        provider.create(
          _root_collection,
          _root_entry,
          Record.dataAuto(
            "id" -> _root_entry.print,
            "status" -> "open",
            _revision_field -> 1L
          )
        ) shouldBe Consequence.unit

        When("the admitted plan is submitted through the datastore space")
        val result =
          summon[ExecutionContext].dataStoreSpace.conditionalTransition(
            _plan()
          )

        Then("the supplementary provider is invoked once and returns Transitioned")
        provider.invocations shouldBe 1
        result.toOption.exists(
          _.isInstanceOf[
            DataStoreConditionalTransitionResult.Transitioned
          ]
        ) shouldBe true
      }
    }
  }

  private val _root_collection_id =
    EntityCollectionId("test", "conditional", "root")
  private val _successor_collection_id =
    EntityCollectionId("test", "conditional", "successor")
  private val _side_collection_id =
    EntityCollectionId("test", "conditional", "side")
  private val _root_collection =
    DataStore.CollectionId.EntityStore(_root_collection_id)
  private val _successor_collection =
    DataStore.CollectionId.EntityStore(_successor_collection_id)
  private val _side_collection =
    DataStore.CollectionId.EntityStore(_side_collection_id)
  private val _root_entry = DataStore.StringEntryId("root-1")
  private val _successor_entry = DataStore.StringEntryId("successor-1")
  private val _revision_field = "cncf_revision"
  private val _component_owner =
    _success(DataStoreComponentOwner.create("test-component"))
  private val _other_component_owner =
    _success(DataStoreComponentOwner.create("other-component"))

  private def _expected(
    field: String,
    value: Any
  ): DataStoreConditionalExpectedField =
    DataStoreConditionalExpectedField(
      field,
      _success(DataStoreConditionalValue.from(value))
    )

  private def _root(
    fields: Vector[DataStoreConditionalExpectedField]
  ): Consequence[DataStoreConditionalRoot] =
    DataStoreConditionalRoot.create(
      _component_owner,
      _root_collection,
      _root_entry,
      _revision_field,
      Some(EntityRevision.INITIAL),
      fields,
      Record.dataAuto(
        "status" -> "closed",
        "successor_id" -> _successor_entry.print
      ),
      _revision(2L)
    )

  private def _create_successor(
    entryid: DataStore.EntryId,
    collection: DataStore.CollectionId = _successor_collection,
    componentowner: DataStoreComponentOwner = _component_owner
  ): DataStoreConditionalSuccessor.Create =
    DataStoreConditionalSuccessor.Create(
      componentowner,
      collection,
      entryid,
      _revision_field,
      _successor_record(entryid.print)
    )

  private def _successor_record(
    id: String
  ): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> "successor",
      _revision_field -> 1L
    )

  private def _plan(): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _success(_root(Vector(_expected("status", "open")))),
        _create_successor(_successor_entry)
      )
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _success[A](
    consequence: Consequence[A]
  ): A =
    consequence match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        fail(conclusion.display)
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )

  private def _is_failure[A](
    consequence: Consequence[A]
  ): Boolean =
    consequence match {
      case _: Consequence.Failure[?] => true
      case _ => false
    }

  private class InvocationCountingDataStore
      extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    private val _invocations = new AtomicInteger(0)

    def invocations: Int = _invocations.get()

    override def conditionalTransition(
      plan: DataStoreConditionalTransitionPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[DataStoreConditionalTransitionResult] = {
      _invocations.incrementAndGet()
      super.conditionalTransition(plan)
    }
  }

  private final class ScopedInvocationCountingDataStore(
    collectionname: String
  ) extends InvocationCountingDataStore {
    override def isAccept(
      collection: DataStore.CollectionId
    ): Boolean =
      collection.collectionName == collectionname
  }
}
