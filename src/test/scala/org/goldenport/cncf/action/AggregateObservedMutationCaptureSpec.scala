package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityVersionedMutationOutcomeFixture.{ProjectionMode, conclusion, revision, success, structuredFailure, withBackend, reasons}
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.observability.{DslChokepointOutcome, DslChokepointPhase}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.{EntityAbacCondition, EntityAccessRelation}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.scalacheck.{Prop, Test}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
final class AggregateObservedMutationCaptureSpec extends AnyWordSpec with AggregateObservedMutationCaptureFixture.Vocabulary {
  import AggregateObservedMutationCaptureFixture.*
  import AggregateObservedMutationCaptureFixture.given
  import AggregateObservedSnapshotFixture.{Root, Rendering}

  private var _sequence = 0
  private def _token(): String = { _sequence += 1; s"aggregate_capture_${_sequence}" }
  private def _caught(body: => Any): Throwable = {
    try { body; fail("the original signal must escape") } catch { case signal: Throwable => signal }
  }
  private def _callback_failure: Consequence.Failure[Nothing] = Consequence.operationInvalid(
    "aggregate-observed-capture", Cause.Kind.Inconsistency,
    Vector(Descriptor.Facet.Reason("aggregate-callback-invalid")), None)

  "Registered Aggregate observed mutation capture" should {
    "retain canonical full-save outcomes in the caller's active UnitOfWork" which {
      backends.foreach { backend =>
        s"AGC-01 retain every $backend representation, alias and revision branch" in {
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = representations.map(value => value -> new Session(_token(), value, writer)).toMap
            for { representation <- representations; alias <- Vector(false, true); mode <- Mode.values.toVector } {
              Given("the declared representation, registration and revision case with independent full state")
              val sample = Sample("日本 Alpha Ω", "retained security/audit", mode, representation, alias, ProjectionMode.Good, Fault.None)
              When("the complete Aggregate scenario interprets one actual command")
              val satisfied = checkLaw(sample, sessions(representation), reader, physical, backend)
              Then("the independently prescribed outcome, raw reference and full state correspond")
              satisfied shouldBe true
            }
          }
        }
        s"AGC-01 retain current $backend maximum-revision NoOp without a successor" in {
          withBackend(backend) { (writer, reader, physical) =>
            representations.foreach { representation =>
              Given("a primed registered Aggregate at the maximum revision and exact complete baseline")
              val current = new Session(_token(), representation, writer)
              current.seed(initialRevision = Long.MaxValue)
              val before = current.rows(reader); val beforephysical = physical(); val resident = current.resident
              val wanted = expected(current, before, beforephysical, "Before", false, Long.MaxValue, backend)
              current.arm()
              When("the domain method retains its full current value at the same maximum observation")
              val attempt = current.attempt(root => Consequence.success(root), revision(Long.MaxValue), current.aliasName)
              Then("the existing provider supplies its actual NoOp with no enclosing commit or revision guess")
              val capture = success(attempt.result)
              checkPrimitive(current, 1, revision(Long.MaxValue)); checkCapture(capture, current, wanted, false, Long.MaxValue)
              checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(representation), wanted.rows, current.data, Long.MaxValue)
              checkCoherence(current, capture, resident, Fault.None, true)
              checkRows(current.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
              attempt.callbacks shouldBe 1
            }
          }
        }
        s"AGC-05 contain every real post-ACK projection and Realm fault on $backend" in {
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = representations.map(value => value -> new Session(_token(), value, writer)).toMap
            for { representation <- representations; changed <- Vector(false, true)
              projection <- ProjectionMode.values.toVector; fault <- Fault.values.toVector } {
              Given("a real typed producer and faults armed only after its actual authoritative ACK")
              val sample = Sample("Before 日本 Ω", "retained fault baseline", if (changed) Mode.Changed else Mode.Current,
                representation, true, projection, fault)
              When("the actual Aggregate capture scenario applies the chosen legal fault dimensions")
              val satisfied = checkLaw(sample, sessions(representation), reader, physical, backend)
              Then("raw persistence, projection and coherence remain distinct without replay")
              satisfied shouldBe true
            }
          }
        }
        s"AGC-06 retain the independently committed $backend winner without replay" in {
          withBackend(backend) { (writer, reader, physical) =>
            representations.foreach { representation =>
              Given("an actual registered Aggregate session and an independent provider for the intervening winner")
              val current = new Session(_token(), representation, writer)
              When("the shared race scenario publishes a winner before the selected Aggregate save")
              checkRace(current, reader, reader, physical, backend)
              Then("the losing Aggregate makes one canonical attempt without enclosing commit or abort")
              checkPrimitive(current, 1)
            }
          }
        }
      }
      "AGC-05 retain SQLite raw capture after actual owned-writer closure during hydration" in {
        for { representation <- representations; changed <- Vector(false, true) } {
          withBackend("sqlite") { (writer, reader, physical) =>
            Given("an actual SQLite Aggregate session with an owned writer and independent live reader")
            val current = new Session(_token(), representation, writer)
            When("the shared closure scenario closes that writer after its authoritative ACK")
            checkClosed(current, reader, physical, "sqlite", changed,
              () => { success(writer.asInstanceOf[org.goldenport.cncf.datastore.sql.SqlDataStore].closeC()); () })
            Then("one canonical save attempt retains the caller-owned UnitOfWork lifecycle")
            checkPrimitive(current, 1)
          }
        }
      }
    }

    "retain Aggregate authorization and event ownership" which {
      "AGC-02 apply default role, relation, condition and factory policy before domain" in {
        for { backend <- backends; representation <- representations
          policy <- Vector("manager", "factory", "owner", "relation", "condition", "condition-denied", "relation-denied") } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("an actual declared Aggregate policy and independent complete rows before authorization")
            val relation = "business=subject.subjectId:command:changetitle"
            val condition = if (policy == "condition-denied") "title=Forbidden" else "title=Before"
            val access = CmlOperationAccess(if (policy == "manager") "manager_only" else "default",
              relation = if (Set("relation", "relation-denied").contains(policy)) Some(relation) else None,
              condition = if (Set("condition", "condition-denied").contains(policy)) Some(condition) else None)
            val current = new Session(_token(), representation, writer, access = access,
              principal = if (Set("relation", "relation-denied").contains(policy)) "related-user" else "owner",
              denyFactory = policy == "factory")
            current.seed(retained = if (policy == "relation") "related-user" else "retained")
            val before = current.rows(reader); val beforephysical = physical(); val resident = current.resident
            val denied = Set("manager", "factory", "condition-denied", "relation-denied").contains(policy)
            current.arm()
            When("the actual alias command retains its value under the declared policy")
            val attempt = current.attempt(root => Consequence.success(root), aggregateName = current.aliasName)
            Then("default policy and the actual factory precede domain execution and retain full Aggregate authorization")
            checkPrimitive(current, if (denied) 0 else 1)
            attempt.callbacks shouldBe (if (denied) 0 else 1)
            if (denied) {
              attempt.result shouldBe a[Consequence.Failure[?]]
              current.builderCalls shouldBe Vector.empty
              current.resident shouldBe resident
              if (policy == "factory") {
                (conclusion(attempt.result) eq current.factoryFailure.conclusion) shouldBe true
                checkAuthorization(current, current.aliasName)
              } else current.factoryCalls shouldBe Vector.empty
            } else {
              success(attempt.result).mutation.applied shouldBe false
              checkAuthorization(current, current.aliasName,
                if (policy == "relation") EntityAccessRelation.parseList(relation) else Vector.empty,
                if (policy == "condition") EntityAbacCondition.parseList(condition) else Vector.empty)
            }
            checkRows(current.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
      }
      "AGC-02 preserve genuine domain and transition Conclusions and staged events" in {
        for { backend <- backends; representation <- representations; rejected <- Vector("none", "domain", "transition") } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("an actual active UoW with prior events and an installed current-aware transition hook")
            val prior = new DomainEvent {}; val domain = new DomainEvent {}; val transition = new DomainEvent {}
            var calls = Vector.empty[(Record, Record)]
            val hook = new TransitionValidationHook {
              def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] =
                Consequence.operationInvalid("unexpected missing-current hook")
              override def beforeSave[T](entity: T, tc: EntityPersistent[T], current: Record, proposed: Record)
                (using ctx: ExecutionContext): Consequence[Unit] = {
                calls :+= current -> proposed; ctx.unitOfWork.stageEvent(transition)
                if (rejected == "transition") structuredFailure else Consequence.unit
              }
              def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] =
                Consequence.operationInvalid("unexpected update hook")
              def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] =
                Consequence.operationInvalid("unexpected patch hook")
            }
            val current = new Session(_token(), representation, writer, hook = hook)
            current.seed(); current.uow.stageEvent(prior)
            val before = current.rows(reader); val beforephysical = physical()
            val wanted = expected(current, before, beforephysical, "After", rejected == "none", if (rejected == "none") 2L else 1L, backend)
            current.arm()
            When("the registered domain command stages its event before its declared result")
            val attempt = current.attempt { root =>
              current.uow.stageEvent(domain)
              if (rejected == "domain") structuredFailure else Consequence.success(root.withTitle("After"))
            }
            Then("the same enclosing owner retains staged events and exact rejection without local restoration or abort")
            checkPrimitive(current, if (rejected == "none") 1 else 0)
            current.uow.pendingEvents shouldBe (if (rejected == "domain") Vector(prior, domain) else Vector(prior, domain, transition))
            calls should have size (if (rejected == "domain") 0 else 1)
            calls.foreach { case (record, proposed) =>
              checkRecord(record, before.selected.get)
              proposed.getString("title") shouldBe Some("After")
              proposed.getString("content") shouldBe Some(AggregateObservedSnapshotFixture.body)
            }
            if (rejected != "none") (conclusion(attempt.result) eq structuredFailure.conclusion) shouldBe true
            else checkCapture(success(attempt.result), current, wanted, true, 2L)
            checkRows(current.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
          }
        }
      }
    }

    "reject mismatched owners and malformed callbacks before writing" which {
      "AGC-03 require exact registration, root, managed policy, target and full-record correspondence" in {
        for { backend <- backends; representation <- representations
          rejected <- Vector("aggregate", "root", "ambiguous", "foreign", "unmanaged", "nonoptimistic", "binding", "row", "snapshot") } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("a primed real Aggregate with one explicitly selected invalid ownership boundary")
            val current = new Session(_token(), representation, writer,
              registered = rejected != "aggregate", rootRegistered = rejected != "root",
              managed = rejected != "unmanaged", optimistic = rejected != "nonoptimistic", invalidBinding = rejected == "binding")
            current.seed()
            if (rejected == "ambiguous") current.component.entitySpace.registerEntity(current.rootName,
              new EntityCollection(current.collection.descriptor.copy(collectionId = EntityCollectionId("other", "aggregate", current.rootName)), current.collection.storage))
            if (rejected == "row") success(writer.delete(current.data.dataCollection, DataStore.EntryId(current.data.id))(using current.context))
            val target = if (rejected == "foreign") org.goldenport.cncf.EntityIdFixtureBridge.fromParts("other", "foreign",
              EntityCollectionId("other", "aggregate", "foreign"), entropy = "foreign") else current.data.id
            val before = current.rows(reader); val beforephysical = physical(); val resident = current.resident
            current.arm()
            if (rejected == "snapshot") current.armBuilder(CallbackMode.Good, _.withTitle("Wrong aggregate snapshot"))
            When("the new capture helper admits the actual alias and selected target")
            val attempt = current.attempt(root => Consequence.success(root.withTitle("Forbidden")), aggregateName = current.aliasName, targetId = target)
            Then("structured rejection precedes domain and provider, preserving every complete baseline")
            attempt.result shouldBe a[Consequence.Failure[?]]
            attempt.callbacks shouldBe 0; checkPrimitive(current, 0)
            current.space.outcome shouldBe None
            current.resident shouldBe resident
            if (rejected == "ambiguous") reasons(attempt.result) shouldBe Vector("entity-collection-name-ambiguous")
            if (rejected == "snapshot") reasons(attempt.result) shouldBe Vector("aggregate-root-snapshot-mismatch")
            if (rejected == "row") checkSourceFailure(conclusion(attempt.result), conclusion(
              Consequence.successOrEntityNotFound(Option.empty[Root])(current.data.id)))
            checkRows(current.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
      }
      "AGC-03 require returned exact selected identity and a complete full-save Record" in {
        for { backend <- backends; rejected <- Vector("same-collection", "foreign", "missing", "malformed") } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("a registered real Aggregate and one independently selected invalid returned identity")
            val current = new Session(_token(), EntityRevisionRepresentation.Embedded, writer)
            current.seed(); val before = current.rows(reader); val beforephysical = physical()
            When("the domain method returns its complete Record with that invalid identity")
            val attempt = current.attempt { root =>
              val record = rejected match {
                case "same-collection" => root.record.upsertSingle("id", current.data.unrelatedId)
                case "foreign" => root.record.upsertSingle("id", org.goldenport.cncf.EntityIdFixtureBridge.fromParts("other", "root",
                  EntityCollectionId("other", "aggregate", "root"), entropy = "foreign"))
                case "missing" => Record(root.record.fields.filterNot(_.key == "id"))
                case "malformed" => root.record.upsertSingle("id", "invalid canonical id")
              }
              Consequence.success(root.copy(record = record))
            }
            Then("identity rejection preserves complete storage without a typed save attempt")
            attempt.result shouldBe a[Consequence.Failure[?]]
            attempt.callbacks shouldBe 1; checkPrimitive(current, 0)
            checkRows(current.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
      }
      "AGC-04 bound builder, domain, conversion, identity and Record callback defects with exact redacted structure" in {
        for { backend <- backends; phase <- Vector("builder", "domain", "conversion", "identity", "current-record", "root-record", "updated-record")
          mode <- Vector(CallbackMode.Structured, CallbackMode.Throw, CallbackMode.NullConsequence, CallbackMode.NullSuccess, CallbackMode.NullFailure) } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("a complete real Aggregate with a fault in the explicitly selected pre-provider callback")
            val current = new Session(_token(), EntityRevisionRepresentation.Embedded, writer)
            current.seed(); val before = current.rows(reader); val beforephysical = physical(); val resident = current.resident
            if (phase == "builder") current.armBuilder(mode)
            if (phase == "conversion") current.probe.armConversion(mode)
            if (phase == "identity") current.probe.armIdentity(mode)
            val rendering = if (mode == CallbackMode.Throw || mode == CallbackMode.Structured) Rendering.Throw else Rendering.Null
            if (phase == "current-record") current.armBuilder(CallbackMode.Good, _.copy(rendering = rendering))
            if (phase == "root-record") current.probe.armRendering(rendering)
            When("the actual command reaches the selected callback through the canonical outer and phase runners")
            val attempt = current.attempt { root =>
              if (phase == "domain") callback(mode)(Consequence.success(root))
              else Consequence.success(if (phase == "updated-record") root.copy(rendering = rendering) else root)
            }
            Then("real structured failures retain identity and malformed or thrown results receive the fixed complete boundary error")
            checkPrimitive(current, 0)
            attempt.result shouldBe a[Consequence.Failure[?]]
            if (mode == CallbackMode.Structured && Set("builder", "domain", "conversion").contains(phase))
              (conclusion(attempt.result) eq structuredFailure.conclusion) shouldBe true
            else checkSourceFailure(conclusion(attempt.result), _callback_failure.conclusion)
            conclusion(attempt.result).show should not include "private"
            current.resident shouldBe resident
            checkRows(current.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
      }
      "AGC-03 reject a decoded persistent identity that redirects the selected target" in {
        for { backend <- backends; representation <- representations } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("a complete registered root and a selected identity callback fault activated only after final conversion")
            val current = new Session(_token(), representation, writer)
            current.seed(); val before = current.rows(reader); val beforephysical = physical()
            current.probe.armIdentity(CallbackMode.ForeignId)
            When("the domain command retains its own ID but the exact descriptor persistent returns another target")
            val attempt = current.attempt(root => Consequence.success(root.withTitle("Forbidden redirect")))
            Then("the selected identity guard rejects before any native mutation and preserves complete state")
            checkSourceFailure(conclusion(attempt.result), Consequence.argumentInvalid(
              "Aggregate persistent must retain the exact target ID").conclusion)
            attempt.callbacks shouldBe 1; checkPrimitive(current, 0)
            checkRows(current.rows(reader), before); checkPhysical(physical(), beforephysical)
          }
        }
      }
      "AGC-04 reject malformed input shapes before component lookup or callbacks" in {
        Given("one real Aggregate and independent complete state before deliberately malformed input admission")
        val writer = new AggregateObservedSnapshotFixture.MemoryStore()
        val current = new Session(_token(), EntityRevisionRepresentation.Embedded, writer)
        current.seed(); val before = current.rows(writer)
        val inputs = Vector[(String, EntityId, String, EntityRevision, Root => Consequence[Root])](
          (null, current.data.id, "changeTitle", revision(1L), root => Consequence.success(root)),
          ("", current.data.id, "changeTitle", revision(1L), root => Consequence.success(root)),
          (" spaced ", current.data.id, "changeTitle", revision(1L), root => Consequence.success(root)),
          (current.rootName, null, "changeTitle", revision(1L), root => Consequence.success(root)),
          (current.rootName, EntityVersionedMutationPortFixture.nullCollectionId(current.data.collectionId), "changeTitle", revision(1L), root => Consequence.success(root)),
          (current.rootName, current.data.id, null, revision(1L), root => Consequence.success(root)),
          (current.rootName, current.data.id, "", revision(1L), root => Consequence.success(root)),
          (current.rootName, current.data.id, " changeTitle ", revision(1L), root => Consequence.success(root)),
          (current.rootName, current.data.id, "changeTitle", null, root => Consequence.success(root)),
          (current.rootName, current.data.id, "changeTitle", revision(1L), null))
        inputs.foreach { case (name, target, command, observed, callback) =>
          current.arm()
          When("the selected malformed input reaches the new capture entry")
          val attempt = current.attempt(callback, observed, name, target, command)
          Then("argument admission rejects before domain, builder, authorization or writes")
          attempt.result shouldBe a[Consequence.Failure[?]]
          attempt.callbacks shouldBe 0; current.factoryCalls shouldBe Vector.empty
          current.builderCalls shouldBe Vector.empty; checkPrimitive(current, 0)
          checkRows(current.rows(writer), before)
        }
      }
    }

    "use actual framework hooks without losing known outcomes" which {
      "AGC-04 propagate original fatal, control and interruption signals through both runners with balanced cleanup" in {
        for { representation <- representations; origin <- Vector("builder", "domain", "conversion", "identity")
          signal <- Vector[Throwable](new FatalSignal, new ControlSignal, new InterruptedException("owned signal")) } {
          Given("two real framework hooks and an exact pre-provider signal in the selected actual callback")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val log = new HookLog; val first = new RecordingHook("first", log); val second = new RecordingHook("second", log)
          val current = new Session(_token(), representation, writer, dslHooks = Some(Vector(first, second)))
          current.seed(); log.clear(); val before = current.rows(writer)
          if (origin == "builder") current.armBuilder(CallbackMode.Good, _ => throw signal)
          if (origin == "conversion") current.probe.armConversionSignal(signal)
          if (origin == "identity") current.probe.armIdentitySignal(signal)
          val interrupted = Thread.currentThread().isInterrupted
          When("the registered Aggregate reaches that exact signal through the canonical opt-in path")
          val escaped = _caught { current.attempt(root => if (origin == "domain") throw signal else Consequence.success(root)) }
          Then("the original signal escapes after deterministic reverse cleanup and no native write or enclosing lifecycle")
          (escaped eq signal) shouldBe true
          checkPrimitive(current, 0); checkRows(current.rows(writer), before)
          val enteredphases = if (origin == "builder") Vector(DslChokepointPhase.Authorization,
            DslChokepointPhase.Resolve, DslChokepointPhase.Resolve, DslChokepointPhase.Resolve)
          else Vector(DslChokepointPhase.Authorization, DslChokepointPhase.Resolve, DslChokepointPhase.Resolve,
            DslChokepointPhase.Resolve, DslChokepointPhase.Method) ++
              (if (origin == "domain") Vector.empty else Vector(DslChokepointPhase.Persistence))
          val expectedtrace = Vector(("first", None, None), ("second", None, None)) ++ enteredphases.zipWithIndex.flatMap { case (phase, index) =>
            Vector(("first", Some(phase), None), ("second", Some(phase), None),
              ("second", Some(phase), Some(if (index == enteredphases.size - 1) DslChokepointOutcome.Failure else DslChokepointOutcome.Success)),
              ("first", Some(phase), Some(if (index == enteredphases.size - 1) DslChokepointOutcome.Failure else DslChokepointOutcome.Success)))
          } ++ Vector(("second", None, Some(DslChokepointOutcome.Failure)), ("first", None, Some(DslChokepointOutcome.Failure)))
          log.events.map(event => (event.name, event.phase, event.outcome)) shouldBe expectedtrace
          log.events.foreach(event => (event.execution eq current.context) shouldBe true)
          signal.getSuppressed.toVector shouldBe Vector.empty
          Thread.currentThread().isInterrupted shouldBe interrupted
        }
      }
      "AGC-05 preserve actual Applied and NoOp capture after NonFatal persistence phaseLeave and outer leave faults" in {
        for { backend <- backends; representation <- representations; changed <- Vector(false, true); phasefault <- Vector(false, true) } {
          withBackend(backend) { (writer, reader, physical) =>
            Given("framework-selected recording hooks armed to throw only after a real provider ACK")
            val log = new HookLog; val first = new RecordingHook("first", log); val second = new RecordingHook("second", log)
            val current = new Session(_token(), representation, writer, dslHooks = Some(Vector(first, second)))
            current.seed(); log.clear()
            val before = current.rows(reader); val beforephysical = physical(); val resident = current.resident
            val title = if (changed) "After telemetry" else "Before"; val version = if (changed) 2L else 1L
            val wanted = expected(current, before, beforephysical, title, changed, version, backend)
            current.arm(afterAck = () => second.arm(event =>
              if (event.outcome.nonEmpty && (if (phasefault) event.phase.contains(DslChokepointPhase.Persistence) else event.phase.isEmpty))
                Some(new IllegalStateException("private telemetry credential")) else None))
            When("the registered Aggregate reaches the real ACK and then the selected throwing telemetry callback")
            val capture = success(current.attempt(root => Consequence.success(root.withTitle(title)), aggregateName = current.aliasName).result)
            Then("known capture, raw reference, projection, full state and remaining reverse callbacks survive unchanged")
            checkPrimitive(current, 1); checkCapture(capture, current, wanted, changed, version)
            checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(representation), wanted.rows, current.data, version)
            checkCoherence(current, capture, resident, Fault.None, true)
            checkRows(current.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
            log.events.takeRight(2).map(event => event.name -> event.outcome) shouldBe Vector(
              "second" -> Some(DslChokepointOutcome.Success), "first" -> Some(DslChokepointOutcome.Success))
            log.events.foreach { event =>
              (event.execution eq current.context) shouldBe true
              event.context.domain shouldBe "aggregate"
              event.context.resourceName shouldBe Some(current.aliasName)
              event.context.commandName shouldBe Some("changeTitle")
            }
            current.data.context.framework.dslChokepointHooks shouldBe None
          }
        }
      }
      "AGC-05 support explicit empty framework hooks and the actual default hook path" in {
        for (hooks <- Vector(None, Some(Vector.empty))) {
          Given("an actual Aggregate context configured before its one lazy UnitOfWork is selected")
          val writer = new AggregateObservedSnapshotFixture.MemoryStore()
          val current = new Session(_token(), EntityRevisionRepresentation.Embedded, writer, dslHooks = hooks)
          current.seed(); val before = current.rows(writer)
          When("the actual helper performs a current NoOp using the configured hook selection")
          val capture = success(current.attempt(root => Consequence.success(root)).result)
          Then("both default and empty paths retain the actual capture and active owner")
          capture.mutation.applied shouldBe false
          current.context.framework.dslChokepointHooks shouldBe hooks
          checkPrimitive(current, 1); checkRows(current.rows(writer), before)
        }
      }
    }

    "satisfy total backend laws on bounded reused roots" which {
      backends.foreach { backend =>
        s"AGC-P1 require one hundred $backend successes with zero discarded samples" in {
          Given("total Unicode/mode/representation/alias/projection/coherence generation and valid shrinking")
          withBackend(backend) { (writer, reader, physical) =>
            val sessions = representations.map(value => value -> new Session(_token(), value, writer)).toMap
            val property = Prop.forAll(samples)(sample => checkLaw(sample, sessions(sample.representation), reader, physical, backend))
            When("ScalaCheck interprets the actual Aggregate law sequentially with reused providers and roots")
            val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
            Then("the complete independent state law must pass at least one hundred successes without discard")
            checked.passed shouldBe true; checked.succeeded should be >= 100; checked.discarded shouldBe 0
          }
        }
      }
    }
  }
}
