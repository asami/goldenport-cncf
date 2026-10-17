package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.BodyMode
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.observability.{DslChokepointOutcome, DslChokepointPhase}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.unitofwork.UnitOfWorkMutationCaptureFixture
import org.goldenport.cncf.unitofwork.UnitOfWorkMutationCaptureFixture.{DecodeMode, Input, conclusion, reason, revision, success}
import org.goldenport.observation.Cause
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class AggregateLifecycleMutationCaptureSpec extends AnyWordSpec with AggregateLifecycleMutationCaptureFixture.Vocabulary {
  import AggregateLifecycleMutationCaptureFixture.*
  import AggregateObservedMutationCaptureFixture.{CallbackMode, callback, FatalSignal, ControlSignal, HookLog, RecordingHook}

  private var _sequence = 0
  private def _token(): String = { _sequence += 1; s"aggregate_lifecycle_$_sequence" }
  private def _caught(body: => Any): Throwable = {
    try { body; fail("The exact original signal must escape") } catch { case signal: Throwable => signal }
  }
  "Registered Aggregate lifecycle capture" should {
    "retain canonical create and observed-delete receipts" which {
      backends.foreach { backend =>
        s"ALC-E1 retain every $backend root/alias supplied/generated ID representation and body mode" in {
          withBackend(backend) { environment =>
            for { representation <- representations; alias <- Vector(false, true); supplied <- Vector(false, true)
              body <- BodyMode.values.toVector } {
              Given("independent complete initial state and an actual typed creation registered under its root and alias")
              val value = sample.copy(value = sample.value.copy(representation = representation, supplied = supplied, body = body), alias = alias)
              val session = new Session(_token(), value, environment)
              When("the real Aggregate create scenario interprets one canonical typed create")
              val satisfied = checkCreateLaw(session)
              Then("every raw INITIAL field/reference, physical cell, resident and View agrees without enclosing lifecycle")
              satisfied shouldBe true
            }
          }
        }
        s"ALC-E2 retain every $backend physical/logical delete under root/alias and both representations" in {
          withBackend(backend) { environment =>
            for { representation <- representations; alias <- Vector(false, true); logical <- Vector(false, true) } {
              Given("a registered current Aggregate and complete independently prescribed physical absence or logical tombstone")
              val value = sample.copy(value = sample.value.copy(representation = representation, logical = logical), alias = alias)
              val session = new Session(_token(), value, environment)
              When("one actual Aggregate helper runs the complete observed-delete scenario")
              val satisfied = checkDeleteLaw(session)
              Then("the exact matched prior revision and full root/body/unrelated state survive without response reload or replay")
              satisfied shouldBe true
            }
          }
        }
        s"ALC-E6 retain real post-ACK $backend decoder and Realm failures" in {
          withBackend(backend) { environment =>
            for { representation <- representations; mode <- DecodeMode.values.toVector } {
              Given("a real registered decoder armed only by the actual native create acknowledgement")
              val session = new Session(_token(), sample.copy(value = sample.value.copy(representation = representation, mode = mode), alias = true), environment)
              When("the actual Aggregate create scenario reaches optional decoding after capture")
              val satisfied = checkCreateLaw(session)
              Then("the complete durable state and exact result remain retained through the selected optional coherence fault")
              satisfied shouldBe true
            }
            for { representation <- representations; logical <- Vector(false, true) } {
              Given("a selected real Realm whose eviction throws only after actual observed deletion")
              val session = new Session(_token(), sample.copy(value = sample.value.copy(representation = representation, logical = logical), alias = true), environment)
              session.seed(); val before = session.baseline
              val wanted = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.rows, before.physical, environment.normalize)
              val exact = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.exact, before.physical, false)
              session.reset(throwEvict = true)
              When("the registered Aggregate validates once and the canonical writer acknowledges before Realm failure")
              val attempt = session.delete()
              Then("the exact deletion result and every durable field remain while optional resident failure is explicit")
              checkDelete(session, before, wanted, exact, attempt, evictionFailure = true)
            }
          }
        }
      }
    }

    "retain declared policy and complete current factory authorization" which {
      "ALC-E3 deny default manager and actual factory policy before either domain callback" in {
        for { backend <- backends; representation <- representations; create <- Vector(false, true); factory <- Vector(false, true) } {
          withBackend(backend) { environment =>
            Given("a selected declared manager policy or exact factory Failure and the complete independent pre-action state")
            val session = new Session(_token(), sample.copy(value = sample.value.copy(representation = representation), alias = true), environment,
              access = CmlOperationAccess(if (factory) "default" else "manager_only"), denyFactory = factory)
            session.seed(); val before = session.baseline
            When("one lifecycle helper requests its normal Aggregate type or instance authority")
            val attempt = if (create) session.create() else session.delete()
            Then("denial precedes command/builder/write, preserves the original factory Conclusion and every prior state")
            attempt.result shouldBe a[Consequence.Failure[?]]; attempt.callbacks shouldBe 0
            session.builderCalls shouldBe Vector.empty
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            if (factory) {
              (conclusion(attempt.result) eq session.factoryFailure.conclusion) shouldBe true
              checkAuthorization(session, create, 1)
            } else { reason(attempt.result) shouldBe Some("manager-only"); session.factoryCalls shouldBe Vector.empty }
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E3 retain independently declared relation condition component family model and active UoW evidence" in {
        for { create <- Vector(false, true); alias <- Vector(false, true) } {
          withBackend("memory") { environment =>
            Given("an explicit normal Aggregate access declaration with relation and natural-condition metadata")
            val access = CmlOperationAccess("default", relation = Some("business=subject.subjectId:delete"), condition = Some("title=Before"))
            val session = new Session(_token(), sample.copy(alias = alias), environment, access = access)
            session.seed(); val before = session.baseline
            val wanted = if (create) UnitOfWorkMutationCaptureFixture.expectedCreate(session.data, before.rows, before.physical, false)
              else UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.rows, before.physical, false)
            When("the real lifecycle helper carries the complete request through initial authorization and persistence recheck")
            val created = if (create) Some(session.create()) else None
            val deleted = if (create) None else Some(session.delete())
            Then("both factory requests retain the exact Aggregate vector and same current UoW without internal grants")
            if (create) checkCreate(session, before, wanted, wanted, created.get)
            else checkDelete(session, before, wanted, wanted, deleted.get)
          }
        }
      }
    }

    "reject inadmissible lifecycle targets without state changes" which {
      "ALC-E4 reject malformed helper input before authorization or callbacks" in {
        for (variant <- Vector("null-name", "empty-name", "padded-name", "null-command-name", "empty-command-name",
          "null-command", "null-persistent", "null-validator", "null-target", "null-observation")) {
          withBackend("memory") { environment =>
            Given("a real registered Aggregate with a complete baseline and one malformed lifecycle input")
            val session = new Session(_token(), sample, environment)
            session.seed(); val before = session.baseline
            When("the helper receives the selected malformed shape before lifecycle admission")
            val result: Consequence[?] = variant match {
              case "null-name" => session.create(selected = null).result
              case "empty-name" => session.create(selected = "").result
              case "padded-name" => session.create(selected = " " + session.rootName).result
              case "null-command-name" => session.create(commandName = null).result
              case "empty-command-name" => session.create(commandName = "").result
              case "null-command" => session.create(command = null).result
              case "null-persistent" => session.create(createPersistent = null).result
              case "null-validator" => session.delete(validate = null).result
              case "null-target" => session.delete(target = null).result
              case _ => session.delete(observed = null).result
            }
            Then("structured argument admission performs no factory authorization builder callback or writer and preserves all state")
            result shouldBe a[Consequence.Failure[?]]
            session.factoryCalls shouldBe Vector.empty; session.builderCalls shouldBe Vector.empty; session.order shouldBe Vector.empty
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E4 reject absent and ambiguous registrations and unmanaged or nonoptimistic bindings" in {
        for { backend <- backends; create <- Vector(false, true); variant <- Vector("aggregate", "root", "ambiguous", "unmanaged", "nonoptimistic") } {
          withBackend(backend) { environment =>
            Given("one inadmissible registration or policy and a complete independent state baseline")
            val session = new Session(_token(), sample.copy(alias = true), environment,
              managed = variant != "unmanaged", optimistic = variant != "nonoptimistic", registered = variant != "aggregate",
              rootRegistered = variant != "root", ambiguous = variant == "ambiguous")
            session.seed(); val before = session.baseline
            When("the selected root/alias helper attempts lifecycle admission")
            val attempt = if (create) session.create() else session.delete()
            Then("registration and binding rejection invokes no domain writer or authority bypass and preserves all state")
            attempt.result shouldBe a[Consequence.Failure[?]]; attempt.callbacks shouldBe 0
            session.builderCalls shouldBe Vector.empty; session.factoryCalls shouldBe Vector.empty
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E4 reject duplicate and foreign create identities or persistent collections" in {
        for { backend <- backends; variant <- Vector("duplicate", "collection", "identity") } {
          withBackend(backend) { environment =>
            Given("a registered root with existing selected data and independently captured entire pre-action state")
            val session = new Session(_token(), sample.copy(alias = true), environment)
            session.seed(); val before = session.baseline
            val foreign = EntityCollectionId("foreign", "capture", session.rootName)
            val identity = new UnitOfWorkMutationCaptureFixture.CanonicalId(foreign, "foreign")
            val input = if (variant == "duplicate") session.data.input.copy(identity = Some(session.data.id),
              record = session.data.input.record.upsertSingle("id", session.data.id)) else session.data.input
            val persistent = new EntityPersistentCreate[Input] {
              def collection(entity: Input): EntityCollectionId = if (variant == "collection") foreign else session.data.collectionId
              def id(entity: Input): Option[EntityId] = if (variant == "identity") Some(identity) else entity.identity
              def toRecord(entity: Input): Record = entity.record
            }
            When("one real typed Aggregate create receives the selected duplicate or ownership error")
            val attempt = session.create(() => Consequence.success(input), createPersistent = persistent)
            Then("one command and at most the single duplicate primitive preserve every old root/body and resident/View state")
            attempt.result shouldBe a[Consequence.Failure[?]]; attempt.callbacks shouldBe 1
            session.data.recording.creates shouldBe (if (variant == "duplicate") 1 else 0)
            session.data.recording.deletes shouldBe 0
            session.data.space.created shouldBe None
            checkAuthorization(session, true, if (variant == "duplicate") 2 else 1)
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E4 reject missing stale equal-stale exhausted foreign and domain-vetoed delete targets" in {
        for { backend <- backends; representation <- representations
          variant <- Vector("missing", "stale", "equal-stale", "exhausted", "foreign", "veto") } {
          withBackend(backend) { environment =>
            Given("the independently observed current Aggregate state and the selected missing/stale/ownership/domain precondition")
            val session = new Session(_token(), sample.copy(value = sample.value.copy(representation = representation,
              version = if (variant == "exhausted") Long.MaxValue else sample.value.version), alias = true), environment)
            session.seed(); val before = session.baseline
            val target = variant match {
              case "foreign" => new UnitOfWorkMutationCaptureFixture.CanonicalId(EntityCollectionId("foreign", "capture", session.rootName), "foreign")
              case "missing" => new UnitOfWorkMutationCaptureFixture.CanonicalId(session.data.collectionId, "absent")
              case _ => session.data.id
            }
            val observed = revision(session.sample.value.version - (if (variant.endsWith("stale")) 1L else 0L))
            When("the actual Aggregate observed-delete helper admits the observation before any builder or validation")
            val attempt = session.delete(_ => if (variant == "veto") session.veto else Consequence.unit, observed, target = target)
            Then("every rejected branch preserves full state, with no deletion receipt, provider attempt or callback replay")
            attempt.result shouldBe a[Consequence.Failure[?]]
            attempt.callbacks shouldBe (if (variant == "veto") 1 else 0)
            session.builderCalls should have size (if (variant == "veto") 1 else 0)
            session.data.recording.deletes shouldBe 0; session.data.recording.creates shouldBe 0
            if (variant == "veto") (conclusion(attempt.result) eq session.veto.conclusion) shouldBe true
            if (variant.endsWith("stale")) reason(attempt.result) shouldBe Some("stale-entity-revision")
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E4 retain exact target and unmanaged root-subset correspondence before validation" in {
        for (variant <- Vector("aggregate-target", "aggregate-fields", "root-target", "root-identity")) {
          withBackend("memory") { environment =>
            Given("a current registered Aggregate with one foreign identity or mismatched root field and full independent baseline")
            val session = new Session(_token(), sample.copy(alias = true), environment)
            session.seed(); val before = session.baseline
            if (variant == "aggregate-target") session.armRendering(() => Some(before.rows.selected.get.upsertSingle("id", session.data.unrelatedId)))
            if (variant == "aggregate-fields") session.armRendering(() => Some(before.rows.selected.get.upsertSingle("business", "wrong root value")))
            if (variant == "root-target") session.armRendering(() => Some(before.rows.selected.get.upsertSingle("id", session.data.unrelatedId)), root = true)
            if (variant == "root-identity") session.armIdentity(session.data.unrelatedId)
            When("the actual contextual Aggregate resolves before its new delete helper checks exact root correspondence")
            val attempt = session.delete()
            Then("a wrong exact target or root subset prevents domain validation and persistence while retaining all state")
            reason(attempt.result) shouldBe Some("aggregate-root-snapshot-mismatch")
            attempt.callbacks shouldBe 0; session.builderCalls should have size 1
            session.data.recording.deletes shouldBe 0; session.data.recording.creates shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
    }

    "preserve callback and telemetry boundaries" which {
      "ALC-E5 preserve exact structured callbacks and redact malformed or NonFatal results" in {
        for { backend <- backends; create <- Vector(false, true)
          mode <- Vector(CallbackMode.Structured, CallbackMode.Throw, CallbackMode.NullConsequence, CallbackMode.NullSuccess, CallbackMode.NullFailure) } {
          withBackend(backend) { environment =>
            Given("a genuine application Failure or an explicitly malformed callback and complete pre-action state")
            val session = new Session(_token(), sample.copy(alias = true), environment)
            session.seed(); val before = session.baseline
            val prior = new DomainEvent {}; val staged = new DomainEvent {}
            session.uow.stageEvent(prior)
            val rejected: Consequence.Failure[Nothing] = Consequence.operationInvalid("aggregate-lifecycle-application-callback")
            def _callback_[A](good: => Consequence[A]): Consequence[A] = {
              session.uow.stageEvent(staged)
              if (mode == CallbackMode.Structured) rejected else callback(mode)(good)
            }
            When("one authorized typed application callback returns its selected boundary value")
            val attempt = if (create) session.create(() => _callback_(Consequence.success(session.data.input)))
              else session.delete(_ => _callback_(Consequence.unit))
            Then("the callback executes once, retains genuine Conclusion identity, redacts malformed details and leaves events with the caller")
            attempt.callbacks shouldBe 1; attempt.result shouldBe a[Consequence.Failure[?]]
            if (mode == CallbackMode.Structured) (conclusion(attempt.result) eq conclusion(rejected)) shouldBe true
            else {
              reason(attempt.result) shouldBe Some("aggregate-lifecycle-callback-invalid")
              conclusion(attempt.result).observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
              conclusion(attempt.result).show should not include "private callback credential"
            }
            session.uow.pendingEvents shouldBe Vector(prior, staged)
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E5 guard create collection/identity and delete builder/root-rendering callbacks" in {
        for { phase <- Vector("collection", "identity", "builder", "aggregate-render", "root-render")
          malformed <- Vector(false, true) } {
          withBackend("memory") { environment =>
            Given("a registered callback boundary which either throws NonFatal or returns its null carrier")
            val session = new Session(_token(), sample.copy(alias = true), environment)
            session.seed(); val before = session.baseline
            val persistent = new EntityPersistentCreate[Input] {
              def collection(entity: Input): EntityCollectionId = if (phase == "collection") {
                if (malformed) null else throw new IllegalStateException("private collection credential")
              } else session.data.collectionId
              def id(entity: Input): Option[EntityId] = if (phase == "identity") {
                if (malformed) null else throw new IllegalStateException("private identity credential")
              } else entity.identity
              def toRecord(entity: Input): Record = entity.record
            }
            if (phase == "builder") session.armBuilder(if (malformed) CallbackMode.NullSuccess else CallbackMode.Throw)
            if (phase.endsWith("render")) session.armRendering(() => {
              if (malformed) Some(null) else throw new IllegalStateException("private rendering credential")
            }, root = phase == "root-render")
            When("the actual new helper reaches that application or root correspondence callback")
            val attempt = if (phase == "collection" || phase == "identity") session.create(createPersistent = persistent) else session.delete()
            Then("the lifecycle-specific redacted structured Failure precedes every persistence attempt and preserves all state")
            reason(attempt.result) shouldBe Some("aggregate-lifecycle-callback-invalid")
            conclusion(attempt.result).show should not include "private"
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
      "ALC-E5 propagate fatal control and interruption identity after balanced preserving telemetry cleanup" in {
        for { create <- Vector(false, true); kind <- Vector("fatal", "control", "interruption") } {
          withBackend("memory") { environment =>
            Given("two actual framework hooks, the selected exact signal and a complete state baseline")
            val log = new HookLog
            val first = new RecordingHook("first", log); val second = new RecordingHook("second", log)
            val session = new Session(_token(), sample, environment, hooks = Some(Vector(first, second)))
            session.seed(); val before = session.baseline; log.clear()
            val signal: Throwable = kind match {
              case "fatal" => new FatalSignal
              case "control" => new ControlSignal
              case _ => new InterruptedException("private interruption credential")
            }
            var calls = 0
            When("the one application command or validation throws the original signal inside preserving Aggregate phases")
            val thrown = _caught {
              if (create) session.create(() => { calls += 1; throw signal })
              else session.delete(_ => { calls += 1; throw signal })
            }
            Then("the same signal escapes after reverse Method and outer releases, with no mutation or enclosing lifecycle")
            (thrown eq signal) shouldBe true; calls shouldBe 1
            val method = log.events.filter(_.phase.contains(DslChokepointPhase.Method))
            method.map(_.name) shouldBe Vector("first", "second", "second", "first")
            method.takeRight(2).map(_.outcome) shouldBe Vector(Some(DslChokepointOutcome.Failure), Some(DslChokepointOutcome.Failure))
            log.events.takeRight(2).map(_.name) shouldBe Vector("second", "first")
            log.events.takeRight(2).foreach(_.phase shouldBe None)
            session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 0
            checkPreserved(session, before)
          }
        }
      }
    }
    "retain native acknowledgement ownership" which {
      "ALC-E4 retain the real independent memory and SQLite stale winner without deletion or replay" in {
        for { backend <- backends; representation <- representations; logical <- Vector(false, true) } {
          Given("an actual registered Aggregate and separate canonical provider access for a winning mutation")
          withBackend(backend) { environment =>
            val session = new Session(_token(), sample.copy(value = sample.value.copy(representation = representation, logical = logical), alias = true), environment)
            When("the complete independent winner scenario reaches the real atomic observed-delete recheck")
            checkRace(session)
            Then("the selected Aggregate makes exactly one delete attempt and no enclosing lifecycle call")
            session.data.recording.deletes shouldBe 1; checkLifecycle(session)
          }
        }
      }
      "ALC-E6 distinguish real SQLite rollback and durable unknown ACK without capture or replay" in {
        for { create <- Vector(false, true); logical <- Vector(false, true)
          mode <- Vector(AcknowledgementMode.Normal, AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null) } {
          Given("an actual SQLite ACK seam and an ordinary registered Aggregate lifecycle helper")
          UnitOfWorkMutationCaptureFixture.withSqlite(acknowledgement = true) { environment =>
            val session = new Session(_token(), sample.copy(value = sample.value.copy(logical = logical), alias = true), environment)
            When("the complete real-provider acknowledgement scenario executes")
            checkAcknowledgement(session, environment.writer.asInstanceOf[AcknowledgementStore], create, mode)
            Then("the caller retains all enclosing lifecycle authority")
            checkLifecycle(session)
          }
        }
      }
    }
  }
}
