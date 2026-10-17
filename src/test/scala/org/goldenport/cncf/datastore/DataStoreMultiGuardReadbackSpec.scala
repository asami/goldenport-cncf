package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.cncf.workflow.WorkflowPreparedMultiGuardTransition
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * Test-only same-owner real memory provider; no production host activation.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreMultiGuardReadbackSpec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = take(DataStoreComponentOwner.create("workflow"))
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "verified_route", name))
  private val instance = collection("instance")
  private val continuation = collection("continuation")
  private val cursor = collection("cursor")
  private val events = collection("events")
  private val id = DataStore.StringEntryId("guard")
  private def root(cid: DataStore.CollectionId) =
    take(DataStoreConditionalRoot.create(owner, cid, id, "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "next"),
      take(EntityRevision.createC(2L))))
  private val guards = Vector(root(instance), root(continuation), root(cursor))
  private val effects = Vector("event-1", "event-2").map(name =>
    EntityVersionedSideEffect.Create(events, DataStore.StringEntryId(name), Record.dataAuto("event_json" -> name)))
  private val plan = take(DataStoreConditionalMultiGuardPlan.create(
    take(DataStoreConditionalTransitionPlan.create(guards.head,
      DataStoreConditionalSuccessor.Update(guards(1)), effects)), Vector(guards(2)),
    take(DataStoreConditionalMultiGuardScope.create(owner,
      Vector(DataStoreConditionalGuardBinding(owner, cursor))))))
  private val policy = take(DataStoreConditionalMultiGuardHostPolicy.create(owner,
    instance, continuation, Vector(DataStoreConditionalGuardBinding(owner, cursor)), Vector(events)))
  private def uncertain[A](value: Consequence[A]): Unit = value match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Reason(name) => name
      } should contain("transaction-indeterminate")
    case _ => fail("uncertain outcome projected as success")
  }
  private class VerifiedMemory extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with EntityVerifiedMultiGuardDataStore {
    var commits = 0
    var reads = 0
    var committed = false
    var expected = Vector.empty[DataStoreMultiGuardRecord]
    var shape: Vector[DataStoreMultiGuardRecord] => Vector[DataStoreMultiGuardRecord] = identity
    var readFault: ((DataStore.CollectionId, DataStore.EntryId, Consequence[Option[Record]]) =>
      Consequence[Option[Record]]) = (_, _, result) => result
    override def load(cid: DataStore.CollectionId, entry: DataStore.EntryId)(using ExecutionContext):
        Consequence[Option[Record]] = {
      val result = super.load(cid, entry)
      if (!committed) result
      else {
        reads += 1
        readFault(cid, entry, result)
      }
    }
    override protected def commit_multi_guard_for_readback(input: DataStoreConditionalMultiGuardPlan)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = synchronized {
      commits += 1
      conditionalMultiGuardAcknowledgedSameOwner(input).map {
        case DataStoreMultiGuardAcknowledgement.Committed(records) =>
          expected = records
          committed = true
          DataStoreMultiGuardAcknowledgement.Committed(shape(expected))
        case DataStoreMultiGuardAcknowledgement.NotMatched(existing) =>
          DataStoreMultiGuardAcknowledgement.NotMatched(existing)
      }
    }
    override protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
        (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = synchronized {
      DataStoreMultiGuardReadback.targets(invocation.plan)
        .foldLeft(Consequence.success(Vector.empty[DataStoreMultiGuardRecord])) {
          case (acc, (cid, entry)) => for {
            records <- acc
            _ <- invocation.validateProviderC(this)
            stored <- load(cid, entry)
            record <- stored match {
              case Some(value) => Consequence.success(value)
              case None => Consequence.configurationInvalid("fixture missing readback")
            }
          } yield records :+ DataStoreMultiGuardRecord(cid, entry, record)
        }
    }
  }
  private def seed(store: DataStore): Unit = guards.foreach { guard =>
    take(store.create(guard.collection, guard.entryId,
      Record.dataAuto("revision" -> 1L, "status" -> "open", "retained" -> guard.collection.print)))
  }
  private def within(store: DataStore)(check: (DataStoreSpace, WorkflowPreparedMultiGuardTransition) => Unit): Unit =
    TestComponentFactory.withEmptySubsystem() { subsystem =>
      take(subsystem._with_managed_datastore_lease_c { lease =>
        val space = take(DataStoreSpace.multiGuardHostViewC(policy, store, lease))
        check(space, take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)))
        Consequence.unit
      })
    }

  "Workflow complete admitted multi-guard readback" should {
    "admit field-order differences without dropping logical fields or values" in {
      val store = new VerifiedMemory
      seed(store)
      val records = take(store.conditionalMultiGuardAcknowledgedSameOwner(plan))
        .asInstanceOf[DataStoreMultiGuardAcknowledgement.Committed].records
      val reordered = records.map(item => item.copy(record = Record(item.record.fields.reverse)))
      DataStoreMultiGuardReadback.validateC(plan, reordered).isSuccess shouldBe true
      val extra = reordered.updated(4, reordered(4).copy(record =
        reordered(4).record ++ Record.dataAuto("unexpected" -> "value")))
      DataStoreMultiGuardReadback.validateC(plan, extra).toOption shouldBe None
    }

    "reject duplicate logical fields instead of collapsing them during comparison" in {
      val store = new VerifiedMemory
      seed(store)
      val records = take(store.conditionalMultiGuardAcknowledgedSameOwner(plan))
        .asInstanceOf[DataStoreMultiGuardAcknowledgement.Committed].records
      Vector(0, 4).foreach { index =>
        val fields = records(index).record.fields
        val duplicated = records.updated(index, records(index).copy(record = Record(fields :+ fields.head)))
        DataStoreMultiGuardReadback.validateC(plan, duplicated).toOption shouldBe None
      }
    }

    "verify all prepared records including unchanged fields through the same provider" in {
      val store = new VerifiedMemory
      seed(store)
      within(store) { (_, prepared) =>
        val result = take(prepared.commitVerifiedC()).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
        result.records shouldBe store.expected
        result.records.take(3).foreach(_.record.getString("retained").isDefined shouldBe true)
        store.commits shouldBe 1
        store.reads shouldBe 5
      }
    }

    "classify every missing or divergent guarded/Create readback as indeterminate without replay" in {
      DataStoreMultiGuardReadback.targets(plan).foreach { target =>
        Vector(false, true).foreach { missing =>
          val store = new VerifiedMemory
          seed(store)
          store.readFault = (cid, entry, result) =>
            if ((cid -> entry) != target) result
            else if (missing) Consequence.success(None)
            else result.map(_.map(record => record ++ Record.dataAuto("unexpected" -> "changed")))
          within(store) { (_, prepared) => uncertain(prepared.commitVerifiedC()) }
          store.committed shouldBe true
          store.commits shouldBe 1
        }
      }
    }

    "retain native prepared post-images independently of later mutable storage" in {
      val store = new VerifiedMemory
      seed(store)
      within(store) { (_, prepared) =>
        val verified = take(prepared.commitVerifiedC())
          .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
        val retained = verified.records
        val replacement = Record.dataAuto("revision" -> 3L, "status" -> "later")
        take(store.save(cursor, id, replacement))
        take(store.load(cursor, id)).get shouldBe replacement
        verified.records shouldBe retained
        retained(2).record.getInt("revision") shouldBe Some(2)
        retained(2).record.getString("retained").isDefined shouldBe true
        store.commits shouldBe 1
      }
    }

    "publish neither native acknowledgement nor staged records on pre-publication failure" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { selected =>
        val store = new VerifiedMemory {
          override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] =
            if (stage == selected) Consequence.operationInvalid("fixture-precommit-failure")
            else Consequence.unit
        }
        seed(store)
        val targets = DataStoreMultiGuardReadback.targets(plan)
        val before = targets.map { case (cid, entry) => take(store.load(cid, entry)) }
        within(store) { (_, prepared) => prepared.commitVerifiedC().toOption shouldBe None }
        targets.map { case (cid, entry) => take(store.load(cid, entry)) } shouldBe before
        store.expected shouldBe empty
        store.committed shouldBe false
        store.reads shouldBe 0
        store.commits shouldBe 1
      }
    }

    "reject incomplete, reordered, duplicate, unexpected or invalid committed post-images" in {
      val faults: Vector[Vector[DataStoreMultiGuardRecord] => Vector[DataStoreMultiGuardRecord]] = Vector(
        _.dropRight(1), _.reverse, records => records.updated(1, records.head),
        records => records :+ records.head,
        records => records.updated(2, records(2).copy(entryId = DataStore.StringEntryId("other"))),
        records => records.updated(2, records(2).copy(record = Record.dataAuto("revision" -> 1L))),
        records => records.updated(4, records(4).copy(record = Record.dataAuto("event_json" -> "wrong"))),
        _ => null)
      faults.foreach { fault =>
        val store = new VerifiedMemory
        seed(store)
        store.shape = fault
        within(store) { (_, prepared) => uncertain(prepared.commitVerifiedC()) }
        store.committed shouldBe true
        store.commits shouldBe 1
        store.reads shouldBe 0
      }
    }

    "suppress verification on failed or throwing readback without rolling back committed records" in {
      Vector(false, true).foreach { throwing =>
        val store = new VerifiedMemory
        seed(store)
        store.readFault = (_, _, _) =>
          if (throwing) throw new IllegalStateException("fixture readback failure")
          else Consequence.operationInvalid("fixture readback failure")
        within(store) { (_, prepared) => uncertain(prepared.commitVerifiedC()) }
        store.commits shouldBe 1
        store.committed shouldBe true
        store.reads shouldBe 1
      }
    }

    "recheck lease after the final storage read before producing Verified" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val store = new VerifiedMemory
        seed(store)
        take(subsystem._with_managed_datastore_lease_c { lease =>
          store.readFault = (_, _, result) => {
            if (store.reads == 5) lease.release()
            result
          }
          val space = take(DataStoreSpace.multiGuardHostViewC(policy, store, lease))
          uncertain(take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitVerifiedC())
          store.commits shouldBe 1
          store.reads shouldBe 5
          Consequence.unit
        })
      }
    }

    "return NotMatched without readback or verification when the root guard is stale" in {
      val store = new VerifiedMemory
      seed(store)
      take(store.save(instance, id, Record.dataAuto("revision" -> 2L, "status" -> "already")))
      within(store) { (_, prepared) =>
        take(prepared.commitVerifiedC()).isInstanceOf[DataStoreMultiGuardReadbackResult.NotMatched] shouldBe true
      }
      store.committed shouldBe false
      store.reads shouldBe 0
      store.commits shouldBe 1
    }

    "deny unadmitted views and raw-only capability before any commit or readback" in {
      val store = new VerifiedMemory
      seed(store)
      take(WorkflowPreparedMultiGuardTransition.prepareC(new DataStoreSpace().useDataStore(store), plan))
        .commitVerifiedC().toOption shouldBe None
      store.commits shouldBe 0
      store.reads shouldBe 0
      var rawCalls = 0
      val raw = new DataStore.InMemoryDataStore(CommitRecorder.noop) with EntityConditionalMultiGuardDataStore {
        override protected def commit_multi_guard(input: DataStoreConditionalMultiGuardPlan)(using ExecutionContext):
            Consequence[DataStoreConditionalTransitionResult] = {
          rawCalls += 1
          conditionalMultiGuardSameOwner(input)
        }
      }
      seed(raw)
      within(raw) { (_, prepared) => prepared.commitVerifiedC().toOption shouldBe None }
      rawCalls shouldBe 0
    }
  }
}
