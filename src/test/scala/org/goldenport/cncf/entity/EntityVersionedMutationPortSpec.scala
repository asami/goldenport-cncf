package org.goldenport.cncf.entity

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.Root
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedMutationResult}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
final class EntityVersionedMutationPortSpec extends AnyWordSpec with EntityVersionedMutationPortFixture.Vocabulary {
  import EntityVersionedMutationPortFixture.*
  import EntityVersionedMutationPortFixture.sampleShrink
  import EntityVersionedMutationOutcomeFixture.{Variant, Mode, ProjectionMode, Rows, success, revision,
    conclusion, reasons, structuredFailure, variants, typedVariant, withBackend, bodyStorage, bodyReference}

  private val _tokens = new AtomicInteger()
  private def _token(): String = s"vp_${_tokens.incrementAndGet()}"

  "Canonical internal versioned mutation ports" should {
    "produce actual persistence through direct and Space entry points" which {
      for { backend <- backends; variant <- Variant.values.toVector; route <- Route.values.toVector;
        mode <- Vector(Mode.Current, Mode.Changed, Mode.StaleCurrent, Mode.StaleChanged) } {
        s"VP-E1/VP-E2 retain $backend $route $variant $mode" in {
          Given("a lawful real provider, canonical collection and independently prescribed typed sample")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            val sample = Sample("日本 Alpha Ω", "retained business", 7L, mode, variant)
            When("the sample's action invokes exactly one selected new port")
            val satisfied = checkSample(sample, current, reader, physical, route)
            Then("the complete storage, raw reference, branch and projection correspondence holds")
            satisfied shouldBe true
          }
        }
      }
      for { backend <- backends; variant <- Variant.values.toVector } {
        s"VP-E2 preserve current maximum $variant NoOp on $backend" in {
          Given("complete selected/body/unrelated rows at current Long.MAX_VALUE")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            current.seed(initialRevision = Long.MaxValue)
            val observed = revision(Long.MaxValue)
            val entity = current.input("Before", observed)
            val before = current.rows(reader)
            val beforephysical = physical()
            current.arm()
            When("one new Space port receives current equal business data at maximum revision")
            val outcome = success(current.invokeC(Route.Space, entity, Patch("Before"), Some(observed), policy(observed)))
            Then("actual NoOp requires no successor and preserves every row and projected Field")
            checkCounters(current)
            checkCaptured(outcome, current, before, false, Long.MaxValue)
            checkProjection(outcome.projection, variant, before, current.setup, Long.MaxValue)
            checkRows(current.rows(reader), before)
            checkPhysical(physical(), beforephysical)
          }
        }
      }
      for { backend <- backends; variant <- Vector(Variant.TypedSnapshot) } {
        s"VP-E2 reject mismatched embedded input before the provider on $backend" in {
          Given("embedded input revision 2 but supplied observation 1 over complete persisted revision 2")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            current.seed(initialRevision = 2L)
            val entity = current.input("Before", revision(2L))
            val before = current.rows(reader)
            val beforephysical = physical()
            current.arm()
            When("one new typed port receives inconsistent embedded and expected revisions")
            val result = current.invokeC(Route.Space, entity, Patch("Before"), Some(revision(1L)), policy(revision(1L)))
            Then("the existing mismatch is structural and precedes persistence and projection")
            checkConstructedSourceFailure(conclusion(result), conclusion(Consequence.argumentExpectedActualMismatch(
              current.setup.revisionField, revision(1L), revision(2L))), "StandardEntityRevisionPart.scala")
            checkCounters(current, 0)
            current.probe.admissions shouldBe 0
            checkRows(current.rows(reader), before)
            checkPhysical(physical(), beforephysical)
          }
        }
      }
      for { backend <- backends; variant <- Vector(Variant.RecordSnapshot, Variant.RecordDetached);
        optimistic <- Vector(false, true) } {
        s"VP-E1 retain actual native patch selection $variant optimistic=$optimistic on $backend" in {
          Given("unchanged real provider capabilities and an AlwaysWrite patch at the registered concurrency policy")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer, optimistic = optimistic)
            current.seed()
            val entity = current.input("After", revision(1L))
            val before = current.rows(reader)
            val beforephysical = physical()
            val nativesqlschema = if (backend == "sqlite") Some(NativeSqlSchema.Sqlite) else None
            nativesqlschema.foreach(_ => checkNativeSqlSchemaAbsent(current, before, beforephysical))
            val attempt = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.None, EntityWritePolicy.AlwaysWrite,
              RevisionPreconditionPolicy.Managed, None)
            current.arm()
            When("one Record port passes the effective policy through the canonical native path planner")
            val outcome = success(current.invokeC(Route.Space, entity, Patch("After"), Some(revision(1L)), attempt))
            val after = current.rows(reader)
            Then("actual direct or CAS readback is captured without generic fallback and complete independent state corresponds")
            checkCounters(current)
            current.recording.captured.get shouldBe a[Captured.Native]
            current.recording.plans.head match {
              case SelectedPlan.Direct(_) => optimistic shouldBe false
              case SelectedPlan.CompareAndSet(plan) => optimistic shouldBe true; plan.expectedRevision shouldBe revision(1L)
              case _ => fail("native-capable patch must select direct or CAS")
            }
            checkCaptured(outcome, current, after, true, 2L)
            checkProjection(outcome.projection, variant, after, current.setup, 2L)
            checkState(current, before, after, beforephysical, physical(), "After", true, 2L,
              nativeSqlSchema = nativesqlschema)
          }
        }
      }
    }

    "capture persistence before typed projection and preserve separate legacy attempts" which {
      for { backend <- backends; representation <- representations; changed <- Vector(false, true);
        mode <- ProjectionMode.values.toVector } {
        s"VP-E3 retain $representation $mode changed=$changed on $backend" in {
          Given("separately freshly seeded new and legacy sessions for the same typed semantic input")
          withBackend(backend) { (writer, reader, physical) =>
            val variant = typedVariant(representation)
            val current = new Session(_token(), variant, writer)
            val legacy = new Session(_token(), variant, writer)
            current.seed()
            legacy.seed()
            val title = if (changed) "After" else "Before"
            val entity = current.input(title, revision(1L))
            val oldentity = legacy.input(title, revision(1L))
            val before = current.rows(reader)
            val oldbefore = legacy.rows(reader)
            val beforephysical = physical()
            var capturedrows = Option.empty[Rows]
            current.arm(mode, () => { capturedrows = Some(current.rows(reader)) })
            legacy.arm(mode)
            When("one new port captures the real result before projection and one separate legacy mirror handles its own result")
            val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), policy(revision(1L))))
            val thrown = if (mode == ProjectionMode.Throw)
              Some(intercept[IllegalStateException] { legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L))) })
            else if (mode == ProjectionMode.NullConsequence || mode == ProjectionMode.NullEntity)
              Some(intercept[NullPointerException] { legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L))) })
            else None
            val oldresult = if (thrown.isEmpty)
              Some(legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L)))) else None
            val after = current.rows(reader)
            val oldafter = legacy.rows(reader)
            Then("raw persistence predates the probe and legacy structured failure, direct throws and old identity/revision semantics survive")
            val version = if (changed) 2L else 1L
            checkRows(capturedrows.get, after)
            checkCaptured(outcome, current, after, changed, version)
            checkCounters(current)
            checkCounters(legacy)
            if (mode == ProjectionMode.Good) {
              checkProjection(outcome.projection, variant, after, current.setup, version)
              checkProjection(oldresult.get, variant, oldafter, legacy.setup, version)
            } else {
              checkProjectionFailure(outcome.projection, changed,
                if (mode == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
              if (mode == ProjectionMode.StructuredFailure) {
                checkProjectionFailure(oldresult.get, changed, Some(structuredFailure.conclusion))
                checkConstructedFailure(conclusion(outcome.projection), conclusion(oldresult.get))
                if (!changed) (conclusion(outcome.projection) eq structuredFailure.conclusion) shouldBe true
              }
              if (mode == ProjectionMode.Throw) thrown.get.getMessage shouldBe "private projection secret"
              if (mode == ProjectionMode.ForeignId) {
                val projected = success(oldresult.get)
                val root = if (variant == Variant.TypedSnapshot) projected.asInstanceOf[EntitySnapshot[Root]].entity
                  else projected.asInstanceOf[EntityRevisionCarrier[Root]].entity
                root.id shouldBe legacy.setup.unrelatedId
              }
              if (mode == ProjectionMode.WrongRevision) {
                val wrong = if (version == 1L) 2L else 1L
                checkLegacyWrongRevisionProjection(oldresult.get, variant, oldafter, legacy.setup, wrong)
              }
              conclusion(outcome.projection).show should not include "private projection secret"
            }
            val decodes = if (Set(ProjectionMode.Good, ProjectionMode.NullEntity,
              ProjectionMode.ForeignId, ProjectionMode.WrongRevision).contains(mode)) 1 else 0
            current.probe.admissions shouldBe 1
            current.probe.decodes shouldBe decodes
            legacy.probe.admissions shouldBe 1
            legacy.probe.decodes shouldBe decodes
            checkState(current, before, after, Map.empty, Map.empty, title, changed, version)
            checkState(legacy, oldbefore, oldafter, Map.empty, Map.empty, title, changed, version)
            val first = if (changed) expectedChangedPhysical(beforephysical, current, title, version)
              else beforephysical
            val expected = if (changed) expectedChangedPhysical(first, legacy, title, version) else first
            checkPhysical(physical(), expected)
            checkRows(current.rows(reader), after)
            checkRows(legacy.rows(reader), oldafter)
          }
        }
      }
      for { representation <- representations; control <- Vector("interrupt", "control", "fatal") } {
        s"VP-E3 propagate $control after actual $representation persistence" in {
          Given("an actual memory producer and a persistent whose postprovider admission raises the selected control signal")
          val current = new Session(_token(), typedVariant(representation), new AggregateObservedSnapshotFixture.MemoryStore())
          current.seed()
          val entity = current.input("After", revision(1L))
          val before = current.rows(current.recording.underlying)
          val signal: Throwable = control match {
            case "interrupt" => new InterruptedException("owned interrupt")
            case "control" => new scala.util.control.ControlThrowable {}
            case _ => new LinkageError("owned fatal")
          }
          val persistent = new EntityPersistent[Root] {
            def id(entity: Root): EntityId = current.probe.id(entity)
            def toRecord(entity: Root): Record = current.probe.toRecord(entity)
            override def toStoreRecord(entity: Root): Record = current.probe.toStoreRecord(entity)
            def fromRecord(record: Record): Consequence[Root] = current.probe.fromRecord(record)
            override private[cncf] def admitStoreRecord(record: Record): Consequence[Record] = throw signal
          }
          current.arm()
          When("the new save port reaches control-signal admission only after the actual successful provider result")
          val propagated = intercept[Throwable] {
            if (representation == EntityRevisionRepresentation.Embedded)
              current.setup.context.entityStoreSpace._save_snapshot_outcome_c(entity, persistent, Some(revision(1L)), policy(revision(1L)))(using current.setup.context)
            else current.setup.context.entityStoreSpace._save_detached_outcome_c(entity, persistent, Some(revision(1L)), policy(revision(1L)))(using current.setup.context)
          }
          Then("the exact control object propagates and the single real publication is retained without replay")
          (propagated eq signal) shouldBe true
          checkCounters(current)
          current.recording.captured.get.applied shouldBe true
          val after = current.rows(current.recording.underlying)
          checkRecord(current.recording.captured.get.record.get, after.selected.get)
          checkState(current, before, after, Map.empty, Map.empty, "After", true, 2L)
        }
      }
    }

    "preserve raw results when the actual owned SQL writer closes after ACK" which {
      for { variant <- Variant.values.toVector; changed <- Vector(false, true) } {
        s"VP-E4 retain SQLite $variant changed=$changed after actual post-ACK close" in {
          Given("a factory-owned SQLite writer, independent live reader and overflow-backed complete seed")
          withBackend("sqlite") { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            current.seed()
            val title = if (changed) "After" else "Before"
            val entity = current.input(title, revision(1L))
            val before = current.rows(reader)
            val beforephysical = physical()
            var durable = Option.empty[Rows]
            var durablephysical = Option.empty[Map[(String, String), org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable]]
            current.arm(afterAck = () => {
              durable = Some(current.rows(reader))
              durablephysical = Some(physical())
              success(writer.asInstanceOf[SqlDataStore].closeC())
            })
            When("one new Space port receives known ACK and its observing provider closes the actual writer before hydration")
            val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), policy(revision(1L))))
            val after = current.rows(reader)
            Then("literal System/Corrupted managed borrow failure preserves complete raw persistence and all physical data")
            bodyStorage(outcome.persisted.record) shouldBe Some("overflow")
            bodyReference(outcome.persisted.record).isDefined shouldBe true
            checkCaptured(outcome, current, after, changed, if (changed) 2L else 1L)
            checkClosedManagedReadFailure(outcome.projection, changed)
            checkCounters(current)
            current.probe.admissions shouldBe 0
            current.probe.decodes shouldBe 0
            checkRows(after, durable.get)
            checkPhysical(physical(), durablephysical.get)
            checkState(current, before, after, beforephysical, physical(), title, changed, if (changed) 2L else 1L)
          }
        }
      }
    }

    "admit shapes and retain unsupported legacy defaults" which {
      "VP-E6 return unsupported for all inherited defaults without an old method or provider invocation" in {
        Given("a NoopEntityStore-derived custom store whose legacy methods remain unimplemented")
        val current = new Session(_token(), Variant.TypedSnapshot, new AggregateObservedSnapshotFixture.MemoryStore())
        current.seed()
        val entity = current.input("After", revision(1L))
        val legacy = new NoopEntityStore() {}
        val before = current.rows(current.recording.underlying)
        current.arm()
        When("each inherited additive default is called directly")
        val results = Variant.values.toVector.map(variant => _boundary(variant, Route.Direct, legacy,
          current.setup.context.entityStoreSpace, current.setup.id, entity, current.probe,
          Patch("After"), current.patchPersistent, Some(revision(1L)), policy(revision(1L)))(using current.setup.context))
        Then("all four use the exact structured unsupported capability without a legacy fallback")
        results.foreach { result =>
          reasons(result) shouldBe Vector("unsupported-capability")
          conclusion(result).observation.cause.descriptor.facets should contain(
            Descriptor.Facet.Capability("entitystore.versioned-mutation-outcome"))
        }
        checkCounters(current, 0)
        checkRows(current.rows(current.recording.underlying), before)
      }
      for { variant <- Variant.values.toVector; route <- Route.values.toVector } {
        s"VP-E6 reject every nullable shape before $route $variant effects" in {
          Given("a complete managed memory seed and one malformed boundary shape per attempt")
          val current = new Session(_token(), variant, new AggregateObservedSnapshotFixture.MemoryStore())
          current.seed()
          val entity = current.input("After", revision(1L))
          val before = current.rows(current.recording.underlying)
          val good = policy(revision(1L))
          val badid = nullCollectionId(current.setup.collectionId)
          badid.collection shouldBe null
          val policies = Vector[EntityMutationExecutionPolicy](null,
            good.copy(concurrencyPolicy = null), good.copy(writePolicy = null), good.copy(preconditionPolicy = null),
            good.copy(observedRevision = null), good.copy(observedRevision = Some(null)))
          val malformed: Vector[() => Consequence[EntityVersionedMutationOutcome[?]]] =
            policies.map(attempt => () => _boundary(variant, route, current.store, current.setup.context.entityStoreSpace,
              current.setup.id, entity, current.probe, Patch("After"), current.patchPersistent,
              Some(revision(1L)), attempt)(using current.setup.context)) ++
            Vector(null, Some(null)).map(observed => () => _boundary(variant, route, current.store, current.setup.context.entityStoreSpace,
              current.setup.id, entity, current.probe, Patch("After"), current.patchPersistent, observed, good)(using current.setup.context)) ++
            Vector[EntityId](null, badid).map(id => () => {
              val candidate = entity.copy(id = id)
              _boundary(variant, route, current.store, current.setup.context.entityStoreSpace, id, candidate, current.probe,
                Patch("After"), current.patchPersistent, Some(revision(1L)), good)(using current.setup.context)
            }) ++ Vector(
              () => _boundary(variant, route, current.store, current.setup.context.entityStoreSpace, current.setup.id,
                null, current.probe, null, current.patchPersistent, Some(revision(1L)), good)(using current.setup.context),
              () => _boundary(variant, route, current.store, current.setup.context.entityStoreSpace, current.setup.id,
                entity, null, Patch("After"), null, Some(revision(1L)), good)(using current.setup.context))
          current.arm()
          When("each selected new port receives its malformed typed input, id, Option, policy or persistent")
          val results = malformed.map(_())
          Then("each rejects structurally without a primitive, generic write or projection")
          results.foreach { result =>
            result shouldBe a[Consequence.Failure[?]]
            val symptom = conclusion(result).observation.taxonomy.symptom
            Set(Taxonomy.Symptom.Missing, Taxonomy.Symptom.Invalid).contains(symptom) shouldBe true
          }
          checkConstructedSourceFailure(conclusion(results(policies.size + 3)),
            conclusion(Consequence.argumentMissing("id.collection")), "EntityStore.scala")
          checkCounters(current, 0)
          current.probe.admissions shouldBe 0
          current.probe.decodes shouldBe 0
          checkRows(current.rows(current.recording.underlying), before)
        }
      }
      for { variant <- Variant.values.toVector; managed <- Vector(false, true) } {
        s"VP-E6 reject unmanaged=$managed or wrong representation for $variant" in {
          Given("a registered collection with either no binding or the other managed representation")
          val other = variant match {
            case Variant.TypedSnapshot => Variant.TypedDetached
            case Variant.TypedDetached => Variant.TypedSnapshot
            case Variant.RecordSnapshot => Variant.RecordDetached
            case Variant.RecordDetached => Variant.RecordSnapshot
          }
          val current = new Session(_token(), other, new AggregateObservedSnapshotFixture.MemoryStore(), managed = managed)
          current.seed()
          val entity = current.input("After", revision(1L))
          val before = current.rows(current.recording.underlying)
          current.arm()
          When("one new port requires its frozen representation through the existing binding contract")
          val result = _boundary(variant, Route.Space, current.store, current.setup.context.entityStoreSpace,
            current.setup.id, entity, current.probe, Patch("After"), current.patchPersistent,
            Some(revision(1L)), policy(revision(1L)))(using current.setup.context)
          Then("binding rejection precedes all effects and retains every row")
          result shouldBe a[Consequence.Failure[?]]
          checkCounters(current, 0)
          checkRows(current.rows(current.recording.underlying), before)
        }
      }
    }

    "validate only after the assembled effective concurrency selection" which {
      for { backend <- backends; variant <- Variant.values.toVector; changed <- Vector(false, true) } {
        s"VP-E7 retain raw None attempt and assembled Optimistic legacy parity for $variant changed=$changed on $backend" in {
          Given("separately seeded legacy/new Optimistic collections and a raw None WriteIfChanged ObservedRequired attempt")
          withBackend(backend) { (writer, reader, physical) =>
            val current = new Session(_token(), variant, writer)
            val legacy = new Session(_token(), variant, writer)
            current.seed()
            legacy.seed()
            val title = if (changed) "After" else "Before"
            val entity = current.input(title, revision(1L))
            val oldentity = legacy.input(title, revision(1L))
            val before = current.rows(reader)
            val oldbefore = legacy.rows(reader)
            val beforephysical = physical()
            val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None)
            current.arm()
            legacy.arm()
            When("each independent port/mirror selects assembled Optimistic before its canonical semantic validation")
            val outcome = success(current.invokeC(Route.Space, entity, Patch(title), Some(revision(1L)), attempt))
            val oldresult = legacy.legacyC(oldentity, Patch(title), Some(revision(1L)), attempt)
            val after = current.rows(reader)
            val oldafter = legacy.rows(reader)
            Then("both actual plans and complete independently prescribed outputs retain the same effective policy and primitive")
            val version = if (changed) 2L else 1L
            checkCounters(current)
            checkCounters(legacy)
            Vector(current, legacy).foreach { session => session.recording.plans.head match {
              case SelectedPlan.Versioned(plan) =>
                plan.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
                plan.writePolicy shouldBe EntityWritePolicy.WriteIfChanged
                plan.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
                plan.expectedRevision shouldBe Some(revision(1L))
              case _ => fail("WriteIfChanged must retain guarded canonical primitive")
            } }
            checkCaptured(outcome, current, after, changed, version)
            checkProjection(outcome.projection, variant, after, current.setup, version)
            checkProjection(oldresult, variant, oldafter, legacy.setup, version)
            checkState(current, before, after, Map.empty, Map.empty, title, changed, version)
            checkState(legacy, oldbefore, oldafter, Map.empty, Map.empty, title, changed, version)
            val first = if (changed) expectedChangedPhysical(beforephysical, current, title, version) else beforephysical
            val expected = if (changed) expectedChangedPhysical(first, legacy, title, version) else first
            checkPhysical(physical(), expected)
          }
        }
      }
      for { variant <- Variant.values.toVector; missing <- Vector(false, true) } {
        s"VP-E7 reject invalid effective policy $variant missingObservation=$missing before write" in {
          Given("a valid typed input and either effective None+ObservedRequired or absent required observation")
          val current = new Session(_token(), variant, new AggregateObservedSnapshotFixture.MemoryStore(), optimistic = missing)
          current.seed()
          val entity = current.input("After", revision(1L))
          val before = current.rows(current.recording.underlying)
          val attempt = policy(revision(1L)).copy(concurrencyPolicy = EntityConcurrencyPolicy.None,
            observedRevision = if (missing) None else Some(revision(1L)))
          current.arm()
          When("one new port reaches existing effective semantic validation")
          val result = current.invokeC(Route.Space, entity, Patch("After"), Some(revision(1L)), attempt)
          Then("canonical policy rejection retains its exact structured taxonomy and all data")
          val expected = if (missing) Consequence.argumentMissing("observedRevision")
            else Consequence.configurationInvalid("Entity concurrency policy None cannot require an observed revision")
          checkConstructedSourceFailure(conclusion(result), conclusion(expected), "EntityMutationPolicy.scala")
          checkCounters(current, 0)
          checkRows(current.rows(current.recording.underlying), before)
        }
      }
    }

    "satisfy total portable laws over bounded reused rows" which {
      backends.foreach { backend =>
        s"VP-P1 pass at least one hundred complete $backend samples without discards" in {
          Given("nonempty multilingual generators and reducing text, revision and mode shrinking over all four ports")
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = Variant.values.toVector.map(variant => variant -> new Session(_token(), variant, writer)).toMap
            val property = Prop.forAll(samples) { sample => checkSample(sample, sessions(sample.variant), reader, physical) }
            When("the total law executes sequentially with one real Space port action per sample")
            val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
            Then("the actual property requires passed, at least one hundred successes and zero discards")
            withClue(checked.toString) {
              checked.passed shouldBe true
              checked.succeeded should be >= 100
              checked.discarded shouldBe 0
            }
          }
        }
      }
    }
  }

  private def _boundary(variant: Variant, route: Route, store: EntityStore, space: EntityStoreSpace,
    id: EntityId, entity: Root, persistent: EntityPersistent[Root], patch: Patch,
    patchpersistent: EntityPersistentUpdate[Patch], observed: Option[EntityRevision],
    policy: EntityMutationExecutionPolicy
  )(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[?]] = route match {
    case Route.Direct => variant match {
      case Variant.TypedSnapshot => store._save_snapshot_outcome_c(entity, observed, policy)(using persistent, ctx)
      case Variant.TypedDetached => store._save_detached_outcome_c(entity, observed, policy)(using persistent, ctx)
      case Variant.RecordSnapshot => store._update_record_snapshot_outcome_c(id, patch, observed, policy)(using patchpersistent, ctx)
      case Variant.RecordDetached => store._update_record_detached_outcome_c(id, patch, observed, policy)(using patchpersistent, ctx)
    }
    case Route.Space => variant match {
      case Variant.TypedSnapshot => space._save_snapshot_outcome_c(entity, persistent, observed, policy)
      case Variant.TypedDetached => space._save_detached_outcome_c(entity, persistent, observed, policy)
      case Variant.RecordSnapshot => space._update_record_snapshot_outcome_c(id, patch, patchpersistent, observed, policy)
      case Variant.RecordDetached => space._update_record_detached_outcome_c(id, patch, patchpersistent, observed, policy)
    }
  }
}
