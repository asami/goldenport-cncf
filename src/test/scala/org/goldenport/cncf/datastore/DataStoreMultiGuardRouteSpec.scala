package org.goldenport.cncf.datastore

import java.util.concurrent.{Callable, Executors, TimeUnit}
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
 * Counting provider only: route admission evidence, never atomicity evidence.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class DataStoreMultiGuardRouteSpec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = take(DataStoreComponentOwner.create("workflow"))
  private val eventOwner = take(DataStoreComponentOwner.create("event-store"))
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "route", name))
  private val instance = collection("instance")
  private val next = collection("continuation")
  private val cursor = collection("cursor")
  private val events = collection("events")
  private def root(who: DataStoreComponentOwner, cid: DataStore.CollectionId, token: String) =
    take(DataStoreConditionalRoot.create(who, cid, DataStore.StringEntryId(token), "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "next"),
      take(EntityRevision.createC(2L))))
  private val plan = {
    val transition = take(DataStoreConditionalTransitionPlan.create(root(owner, instance, "run-1"),
      DataStoreConditionalSuccessor.Update(root(owner, next, "claim-1")),
      Vector(EntityVersionedSideEffect.Create(events, DataStore.StringEntryId("event-1"),
        Record.dataAuto("event_json" -> "canonical")))))
    val scope = take(DataStoreConditionalMultiGuardScope.create(owner,
      Vector(DataStoreConditionalGuardBinding(eventOwner, cursor))))
    take(DataStoreConditionalMultiGuardPlan.create(transition,
      Vector(root(eventOwner, cursor, "global")), scope))
  }
  private def policy(who: DataStoreComponentOwner = owner,
    rootCollection: DataStore.CollectionId = instance,
    guards: Vector[DataStoreConditionalGuardBinding] = Vector(DataStoreConditionalGuardBinding(eventOwner, cursor)),
    sideCollections: Vector[DataStore.CollectionId] = Vector(events)) =
    take(DataStoreConditionalMultiGuardHostPolicy.create(who, rootCollection, next, guards, sideCollections))
  private def reasons[A](value: Consequence[A]): Set[String] = value match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Reason(name) => name
    }.toSet
    case _ => Set.empty
  }

  private class CountingProvider extends DataStore.InMemoryDataStore(CommitRecorder.noop)
      with EntityConditionalMultiGuardDataStore {
    var calls = 0
    var lookups = 0
    var observed: Option[DataStoreConditionalMultiGuardPlan] = None
    override def isAccept(cid: DataStore.CollectionId): Boolean = {
      lookups += 1
      super.isAccept(cid)
    }
    protected def commit_multi_guard(input: DataStoreConditionalMultiGuardPlan)(using ExecutionContext):
        Consequence[DataStoreConditionalTransitionResult] = {
      calls += 1
      observed = Some(input)
      Consequence.success(DataStoreConditionalTransitionResult.NotMatched(Record.dataAuto("fixture" -> "no-write")))
    }
  }

  "Workflow multi-guard host route" should {
    "reach the opt-in provider exactly once through an admitted live DataStoreSpace" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan))
          take(prepared.commitC()).isInstanceOf[DataStoreConditionalTransitionResult.NotMatched] shouldBe true
          provider.calls shouldBe 1
          provider.observed shouldBe Some(prepared.plan)
          provider.observed.get.additionalGuards shouldBe plan.additionalGuards
          Consequence.unit
        })
      }
    }

    "deny an unadmitted space and a missing invocation without calling the provider" in {
      val provider = new CountingProvider
      val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(new DataStoreSpace().useDataStore(provider), plan))
      reasons(prepared.commitC()) should contain("host-admission-required")
      provider.conditionalMultiGuardAdmitted(null).toOption shouldBe None
      provider.calls shouldBe 0
      provider.lookups shouldBe 0
    }

    "reject wrong owner, root, guard owner and side collection before any provider lookup" in {
      Vector(policy(who = eventOwner), policy(rootCollection = cursor),
        policy(guards = Vector(DataStoreConditionalGuardBinding(owner, cursor))),
        policy(sideCollections = Vector.empty)).foreach { hostPolicy =>
        TestComponentFactory.withEmptySubsystem() { subsystem =>
          val provider = new CountingProvider
          take(subsystem._with_managed_datastore_lease_c { lease =>
            val space = take(DataStoreSpace.multiGuardHostViewC(hostPolicy, provider, lease))
            take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitC().toOption shouldBe None
            provider.calls shouldBe 0
            provider.lookups shouldBe 0
            Consequence.unit
          })
        }
      }
    }

    "reject a split provider domain before invoking either provider" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider {
          override def isAccept(cid: DataStore.CollectionId): Boolean = cid != events && super.isAccept(cid)
        }
        val other = new CountingProvider
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease)).addDataStore(other)
          reasons(take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitC()) should
            contain("provider-domain-mismatch")
          provider.calls shouldBe 0
          other.calls shouldBe 0
          Consequence.unit
        })
      }
    }

    "reject a changed scoped provider and a provider without the opt-in seam" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider
        val other = new CountingProvider
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          space.bindDataStore(other)
          reasons(take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitC()) should
            contain("provider-domain-mismatch")
          provider.calls shouldBe 0
          other.calls shouldBe 0
          val unsupported = new DataStore.InMemoryDataStore(CommitRecorder.noop)
          val unsupportedSpace = take(DataStoreSpace.multiGuardHostViewC(policy(), unsupported, lease))
          reasons(take(WorkflowPreparedMultiGuardTransition.prepareC(unsupportedSpace, plan)).commitC()) should
            contain("unsupported-capability")
          Consequence.unit
        })
      }
    }

    "reject an escaped adapter after its lease is released" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider
        var escaped: Option[WorkflowPreparedMultiGuardTransition] = None
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          escaped = Some(take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)))
          Consequence.unit
        })
        escaped.get.commitC().toOption shouldBe None
        provider.calls shouldBe 0
        provider.lookups shouldBe 0
      }
    }

    "recheck the live lease after provider lookup and before opt-in dispatch" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val provider = new CountingProvider {
            override def isAccept(cid: DataStore.CollectionId): Boolean = {
              lease.release()
              super.isAccept(cid)
            }
          }
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan)).commitC().toOption shouldBe None
          provider.lookups should be > 0
          provider.calls shouldBe 0
          Consequence.unit
        })
      }
    }

    "reject cross-thread use of a live view before provider lookup" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan))
          val pool = Executors.newSingleThreadExecutor()
          try {
            pool.submit(new Callable[Boolean] {
              def call(): Boolean = prepared.commitC().toOption.isEmpty
            }).get(10L, TimeUnit.SECONDS) shouldBe true
          } finally {
            pool.shutdownNow()
            pool.awaitTermination(10L, TimeUnit.SECONDS)
          }
          provider.calls shouldBe 0
          provider.lookups shouldBe 0
          Consequence.unit
        })
      }
    }

    "classify a throwing provider as indeterminate without replaying the invocation" in {
      TestComponentFactory.withEmptySubsystem() { subsystem =>
        val provider = new CountingProvider {
          override protected def commit_multi_guard(input: DataStoreConditionalMultiGuardPlan)(using ExecutionContext):
              Consequence[DataStoreConditionalTransitionResult] = {
            calls += 1
            throw new IllegalStateException("fixture uncertain outcome")
          }
        }
        take(subsystem._with_managed_datastore_lease_c { lease =>
          val space = take(DataStoreSpace.multiGuardHostViewC(policy(), provider, lease))
          val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan))
          reasons(prepared.commitC()) should contain("transaction-indeterminate")
          provider.calls shouldBe 1
          Consequence.unit
        })
      }
    }

    "reject incomplete adapter input before invoking any provider" in {
      WorkflowPreparedMultiGuardTransition.prepareC(null, plan).toOption shouldBe None
      WorkflowPreparedMultiGuardTransition.prepareC(new DataStoreSpace(), null).toOption shouldBe None
    }
  }
}
