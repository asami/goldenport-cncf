package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{WorkflowCursorEventProposal, WorkflowEventSelection, WorkflowPreparedMultiGuardTransition, WorkflowSelectedEventRecords}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.goldenport.observation.Descriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * Host construction/admission examples, not atomic provider acceptance.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 12, 2026
 */
final class EventStorePersistenceHostSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private val owner = ComponentId("org.goldenport.cncf.test.SelectedEventWorkflow")
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "selection", name))
  private val rootCollection = collection("instance")
  private val nextCollection = collection("continuation")
  private val cursorCollection = collection("cursor")
  private val eventCollection = collection("events")
  private val indexCollection = collection("delivery_index")
  private val grant = EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow", rootCollection, nextCollection)
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    cursorCollection, eventCollection, indexCollection, Vector(grant)))
  private def config(values: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values.map { case (key, value) =>
      key -> ConfigurationValue.StringValue(value)
    }.toMap), ConfigurationTrace.empty)
  private def path: Path = Files.createTempDirectory("cncf-selected-host-").resolve("shared.sqlite")
  private def sharedConfig(file: Path) = config("textus.datastore.kind" -> "sqlite",
    "textus.datastore.path" -> file.toString)
  private def environment(subsystem: Subsystem) =
    ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
  private def root(who: DataStoreComponentOwner, cid: DataStore.CollectionId, token: String) =
    take(DataStoreConditionalRoot.create(who, cid, DataStore.StringEntryId(token), "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "next"),
      take(EntityRevision.createC(2L))))
  private def plan = planFor()
  private def planFor(
    workflowOwnerName: String = owner.name,
    guardOwnerName: String = EventStorePersistenceDeclaration.serviceId.name,
    guardCollection: DataStore.CollectionId = cursorCollection,
    sideCollection: DataStore.CollectionId = eventCollection
  ) = {
    val workflowOwner = take(DataStoreComponentOwner.create(workflowOwnerName))
    val eventOwner = take(DataStoreComponentOwner.create(guardOwnerName))
    val transition = take(DataStoreConditionalTransitionPlan.create(
      root(workflowOwner, rootCollection, "instance-1"),
      DataStoreConditionalSuccessor.Update(root(workflowOwner, nextCollection, "claim-1")),
      Vector(EntityVersionedSideEffect.Create(sideCollection,
        DataStore.StringEntryId("event-1"), Record.dataAuto("event_json" -> "canonical")))))
    val scope = take(DataStoreConditionalMultiGuardScope.create(workflowOwner,
      Vector(DataStoreConditionalGuardBinding(eventOwner, guardCollection))))
    take(DataStoreConditionalMultiGuardPlan.create(transition,
      Vector(root(eventOwner, guardCollection, "global")), scope))
  }
  private def reasons[A](result: Consequence[A]): Vector[String] = result match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Reason(value) => value
      }.toVector
    case _ => fail("expected a host rejection")
  }
  private def runtimePlan = {
    val input = plan
    val transition = take(DataStoreConditionalTransitionPlan.create(input.transition.root,
      input.transition.successor, input.transition.sideEffects :+
        EntityVersionedSideEffect.Create(indexCollection, DataStore.StringEntryId("index-1"),
          Record.dataAuto("event_id" -> "event-1"))))
    take(DataStoreConditionalMultiGuardPlan.create(transition, input.additionalGuards, input.scope))
  }
  private val selectedPolicy = take(EventPersistencePolicy.createC("selected-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist),
    EventPersistencePolicy.Rule(EventTypeId("progress"), EventPersistencePolicy.Mode.Transient))))
  private val selectedTime = java.time.Instant.parse("2026-09-30T00:00:00Z")
  private def selectedEvent(kind: String, token: String, hint: Boolean = true) =
    EventRecord(EventId("test", "managed_selection", Some(selectedTime), Some(token)),
      kind, kind, Map("typed" -> 9007199254740993L), Map.empty, selectedTime, hint,
      EventRecord.Status.Stored, EventLane.Transactional)
  private val mixedEvents = Vector(selectedEvent("progress", "1"), selectedEvent("fact", "2", false),
    selectedEvent("unknown", "3"), selectedEvent("fact", "4", false))
  private def selectedRecords(input: Vector[EventRecord] = mixedEvents) = {
    val selection = take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(selectedPolicy), input))
    take(WorkflowSelectedEventRecords.prepareC(selection, eventCollection, 10L))
  }
  private def selectedPlan(prepared: WorkflowSelectedEventRecords) = {
    val base = plan
    val cursor = base.additionalGuards.head
    val guardedCursor = take(DataStoreConditionalRoot.create(cursor.componentOwner,
      cursor.collection, cursor.entryId, cursor.revisionField, Some(cursor.expectedRevision),
      Vector(DataStoreConditionalExpectedField("last_sequence", take(DataStoreConditionalValue.from(10L)))),
      Record.dataAuto("last_sequence" -> prepared.proposedLastSequence), cursor.nextRevision))
    val transition = take(DataStoreConditionalTransitionPlan.create(base.transition.root,
      base.transition.successor, prepared.creates))
    take(DataStoreConditionalMultiGuardPlan.create(transition, Vector(guardedCursor), base.scope))
  }
  private def seedSelected(space: DataStoreSpace, input: DataStoreConditionalMultiGuardPlan): Unit = {
    seed(space, input)
    take(take(space.dataStore(cursorCollection)).save(cursorCollection, DataStore.StringEntryId("global"),
      Record.dataAuto("revision" -> 1L, "status" -> "open", "last_sequence" -> 10L)))
  }
  private def seed(space: DataStoreSpace, input: DataStoreConditionalMultiGuardPlan): Unit = {
    val guards = Vector(input.transition.root,
      input.transition.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root) ++ input.additionalGuards
    guards.foreach { guard =>
      take(take(space.dataStore(guard.collection)).create(guard.collection, guard.entryId,
        Record.dataAuto("revision" -> 1L, "status" -> "open", "retained" -> guard.collection.print)))
    }
  }
  private def stored(space: DataStoreSpace, input: DataStoreConditionalMultiGuardPlan) = {
    val targets = Vector(input.transition.root.collection -> input.transition.root.entryId,
      input.transition.successor.collection -> input.transition.successor.entryId) ++
      input.additionalGuards.map(guard => guard.collection -> guard.entryId) ++
      input.transition.sideEffects.map(effect => effect.collection -> effect.entryId)
    targets.map { case (cid, id) => take(take(space.dataStore(cid)).load(cid, id)).map(record =>
      Record(record.fields.filterNot(_.key == "id").sortBy(_.key))) }
  }

  "Subsystem selected-event storage admission" should {
    "keep declared storage separate from selection and open no resource during pure prepare" in {
      Given("declared storage and a policy selecting progress events as transient")
      val file = path
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          val policy = take(EventPersistencePolicy.createC("v1", Vector(
            EventPersistencePolicy.Rule(EventTypeId("progress"), EventPersistencePolicy.Mode.Transient))))
          val now = java.time.Instant.parse("2026-09-30T00:00:00Z")
          val event = EventRecord(EventId("test", "selection", Some(now), Some("progress-1")),
            "progress", "progress", Map.empty, Map.empty, now, true,
            EventRecord.Status.Stored, EventLane.Transactional)
          val policyStore = EventStore.inMemory(policy)
          When("the progress event is prepared using the store policy")
          val selected = take(WorkflowEventSelection.prepareFromStoreC(policyStore, Vector(event)))
          Then("selection remains transient without acquiring the declared physical resource")
          selected.persistent shouldBe empty
          selected.transient.size shouldBe 1
          subsystem.systemNode.managedDataStoreCount shouldBe 0
          Files.exists(file) shouldBe false
          When("an unknown event kind is prepared")
          val unknown = take(WorkflowEventSelection.prepareFromStoreC(policyStore, Vector(event.copy(kind = "unknown"))))
          Then("it is not made persistent by the storage declaration")
          unknown.persistent shouldBe empty
        }
      }
    }

    "deny legacy construction and unknown Workflow grants before resource resolution" in {
      Given("a settings-only subsystem without a declaration")
      val file = path
      TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
        var entered = false
        When("the legacy subsystem requests a Workflow host scope")
        val result = subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { _ =>
          entered = true
          Consequence.unit
        }
        Then("the scope is rejected before entering its body or resolving resources")
        result.toOption shouldBe None
        entered shouldBe false
        subsystem.systemNode.managedDataStoreCount shouldBe 0
      }
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          var entered = false
          When("a Workflow outside the declaration requests the host scope")
          val result = subsystem.withWorkflowMultiGuardSpaceC(ComponentId("org.goldenport.cncf.test.OtherWorkflow"),
            environment(subsystem)) { _ => entered = true; Consequence.unit }
          Then("the unknown grant is rejected without acquiring storage")
          result.toOption shouldBe None
          entered shouldBe false
          subsystem.systemNode.managedDataStoreCount shouldBe 0
        }
      }
      Files.exists(file) shouldBe false
    }

    "borrow one physical managed resource while keeping unsupported capability fail-closed" in {
      Given("a declared ordinary host scope backed by one managed SQLite resource")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          var escaped: Option[DataStoreSpace] = None
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            escaped = Some(space)
            When("the ordinary scoped view requests an unsupported multi-guard transition")
            val result = space.conditionalMultiGuard(plan)
            Then("the capability fails closed while borrowing only one physical resource")
            result.toOption shouldBe None
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            Consequence.unit
          })
          When("the escaped view is used after its scope ends")
          val released = escaped.get.conditionalMultiGuard(plan)
          Then("the expired view rejects the transition")
          released.toOption shouldBe None
        }
      }
    }

    "reject separate Workflow and EventStore resource identities before exposing a host view" in {
      Given("Workflow and EventStore declarations resolving to separate SQLite identities")
      val workflowSelector = ComponentDataStore.Request.forComponent(owner, "workflow").canonicalComponentSelector.get
      val serviceSelector = ComponentDataStore.Request.forComponent(EventStorePersistenceDeclaration.serviceId, "events")
        .canonicalComponentSelector.get
      val configuration = config(
        s"textus.component.$workflowSelector.datastores.workflow.kind" -> "sqlite",
        s"textus.component.$workflowSelector.datastores.workflow.path" -> path.toString,
        s"textus.component.$serviceSelector.datastores.events.kind" -> "sqlite",
        s"textus.component.$serviceSelector.datastores.events.path" -> path.toString)
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = configuration) { subsystem =>
          var entered = false
          When("the Workflow requests a common host view")
          val result = subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { _ =>
            entered = true
            Consequence.unit
          }
          Then("the resource identity mismatch is rejected before exposing the view")
          result.toOption shouldBe None
          entered shouldBe false
        }
      }
    }

    "keep the verified Workflow adapter fail-closed without rebinding the caller context" in {
      Given("an ordinary declared host and a caller context bound to a sentinel store")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          val callerSpace = context.dataStoreSpace
          val prior = callerSpace.captureBinding()
          val sentinel = DataStore.inMemorySearchable()
          callerSpace.bindDataStore(sentinel)
          val expected = callerSpace.captureBinding()
          var escaped: Option[WorkflowPreparedMultiGuardTransition] = None
          try {
            take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan))
              escaped = Some(prepared)
              When("a prepared transition requests verified commit through the ordinary host")
              val result = prepared.commitVerifiedC()
              Then("verified capability is denied without changing the caller binding")
              reasons(result) should contain("unsupported-verified-capability")
              callerSpace.captureBinding() shouldBe expected
              subsystem.systemNode.managedDataStoreCount shouldBe 1
              Consequence.unit
            })
            // Preparation cannot retain the host's authority after its lease ends.
            When("the prepared transition is reused after its host lease ends")
            val released = escaped.get.commitVerifiedC()
            Then("expired authority is rejected before capability evaluation")
            released.toOption shouldBe None
            reasons(released) should not contain "unsupported-verified-capability"
            callerSpace.captureBinding() shouldBe expected
          } finally callerSpace.restoreBinding(prior)
        }
      }
    }

    "reject a verified call on another thread even while the issuing lease remains live" in {
      Given("a prepared transition under a live issuing-thread lease")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
          try {
            take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, plan))
              When("another thread attempts the verified commit")
              val result = pool.submit(new java.util.concurrent.Callable[Consequence[DataStoreMultiGuardReadbackResult]] {
                def call() = prepared.commitVerifiedC()
              }).get(10L, java.util.concurrent.TimeUnit.SECONDS)
              Then("foreign-thread authority is rejected without transferring the lease")
              result.toOption shouldBe None
              reasons(result) should not contain "unsupported-verified-capability"
              // Rejection must not release or transfer the issuing thread's authority.
              When("the issuing thread submits the prepared transition again")
              val issuingresult = prepared.commitVerifiedC()
              Then("its lease remains live and reaches the ordinary capability refusal")
              reasons(issuingresult) should contain("unsupported-verified-capability")
              Consequence.unit
            })
          } finally {
            pool.shutdown()
            pool.awaitTermination(10L, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
          }
        }
      }
    }

    "check exact declaration owners and collections before verified provider capability" in {
      Given("plans varying the Workflow owner, guard owner, guard collection and side collection")
      val otherOwner = "org.goldenport.cncf.test.UnauthorizedWorkflow"
      val variants = Vector(
        planFor(workflowOwnerName = otherOwner) -> "workflow-owner-mismatch",
        planFor(guardOwnerName = otherOwner) -> "guard-owner-or-collection-not-admitted",
        planFor(guardCollection = collection("undeclared_cursor")) -> "guard-owner-or-collection-not-admitted",
        planFor(sideCollection = collection("undeclared_events")) -> "side-create-collection-not-admitted")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            variants.foreach { case (input, reason) =>
              val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, input))
              When("a plan outside the exact declaration requests verified commit")
              val result = prepared.commitVerifiedC()
              Then("the declaration-specific refusal precedes provider capability checking")
              reasons(result) should contain(reason)
            }
            When("the exact admitted plan requests the same ordinary verified route")
            val admitted = space.conditionalMultiGuardVerified(plan)
            Then("only the unsupported provider capability remains")
            reasons(admitted) should contain("unsupported-verified-capability")
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            Consequence.unit
          })
        }
      }
    }

    "release a failed host scope and reject replacement providers without poisoning a fresh lease" in {
      Given("a declared host scope whose body replaces the provider and then throws")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          var escaped: Option[DataStoreSpace] = None
          When("the failing host operation is entered")
          intercept[IllegalStateException] {
            subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              escaped = Some(space)
              space.bindDataStore(DataStore.inMemorySearchable())
              When("the replacement provider requests verified commit")
              val replaced = space.conditionalMultiGuardVerified(plan)
              Then("its provider domain is rejected")
              reasons(replaced) should contain("provider-domain-mismatch")
              throw new IllegalStateException("injected host operation failure")
            }
          }
          When("the escaped view is used after the failed scope is released")
          val released = escaped.get.conditionalMultiGuardVerified(plan)
          Then("the view has lost authority rather than retaining the replaced provider")
          released.toOption shouldBe None
          reasons(released) should not contain "provider-domain-mismatch"
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { fresh =>
            When("a fresh lease requests verified commit")
            val result = fresh.conditionalMultiGuardVerified(plan)
            Then("the original provider is restored and only its unsupported capability is refused")
            reasons(result) should contain("unsupported-verified-capability")
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            Consequence.unit
          })
        }
      }
    }

    "reject ambiguous collection ownership and malformed store declarations" in {
      Given("declaration fields with ambiguous ownership or malformed store/grant values")
      When("the Workflow root overlaps the event collection")
      val overlap = EventStorePersistenceDeclaration.createC("events", cursorCollection, eventCollection,
        indexCollection, Vector(grant.copy(rootCollection = eventCollection)))
      Then("ambiguous collection ownership is rejected")
      overlap.toOption shouldBe None
      When("the store name is noncanonical")
      val noncanonical = EventStorePersistenceDeclaration.createC("Events", cursorCollection, eventCollection,
        indexCollection, Vector(grant))
      Then("the malformed store declaration is rejected")
      noncanonical.toOption shouldBe None
      When("the same grant is supplied twice")
      val duplicate = EventStorePersistenceDeclaration.createC("events", cursorCollection, eventCollection,
        indexCollection, Vector(grant, grant))
      Then("duplicate grants are rejected")
      duplicate.toOption shouldBe None
      When("no Workflow grant is supplied")
      val absent = EventStorePersistenceDeclaration.createC("events", cursorCollection, eventCollection,
        indexCollection, Vector.empty)
      Then("the grant-less declaration is rejected")
      absent.toOption shouldBe None
    }

    "commit declared cross-owner guards and event/index Creates through the explicit managed verified route" in {
      Given("a declared verified host and seeded cross-owner guards with event/index Creates")
      val file = path
      val input = runtimePlan
      var escaped: Option[WorkflowPreparedMultiGuardTransition] = None
      val verified = EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seed(space, input)
            val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, input))
            escaped = Some(prepared)
            When("the prepared cross-owner transition is committed through the verified route")
            val result = take(prepared.commitVerifiedC()).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            Then("all guarded rows and Creates match the authoritative readback")
            result.records.size shouldBe 5
            result.records.take(3).foreach(_.record.getInt("revision") shouldBe Some(2))
            stored(space, input) shouldBe result.records.map(item => Some(item.record))
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            // An explicit stale submission is not replayed or treated as a second commit.
            When("the same stale prepared transition is explicitly resubmitted")
            val stale = take(prepared.commitVerifiedC())
            Then("it is not matched rather than replayed as another commit")
            stale.isInstanceOf[DataStoreMultiGuardReadbackResult.NotMatched] shouldBe true
            Consequence.success(result)
          })
        }
      }
      When("the escaped transition is used after the verified host closes")
      val released = escaped.get.commitVerifiedC()
      Then("the expired lease rejects the transition")
      released.toOption shouldBe None
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { ordinary =>
            When("the same storage is reopened through an ordinary host view")
            val recovered = stored(ordinary, input)
            Then("the verified committed records are retained")
            recovered shouldBe verified.records.map(item => Some(item.record))
            When("the ordinary reopened view requests verified commit")
            val ordinaryresult = ordinary.conditionalMultiGuardVerified(input)
            Then("the verified capability is still unavailable through the ordinary route")
            reasons(ordinaryresult) should contain("unsupported-verified-capability")
            subsystem.systemNode.managedDataStoreCount shouldBe 1
            Consequence.unit
          })
        }
      }
    }

    "rollback cross-owner updates and earlier Creates on stale cursor or delivery-index collision" in {
      Given("seeded cross-owner guards with a stale cursor or colliding delivery-index Create")
      Vector(false, true).foreach { collision =>
        val file = path
        val input = runtimePlan
        EventStorePersistenceConstruction.withDeclaration(declaration) {
          TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
            take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              seed(space, input)
              if (collision) take(take(space.dataStore(indexCollection)).create(indexCollection,
                DataStore.StringEntryId("index-1"), Record.dataAuto("event_id" -> "existing")))
              else take(take(space.dataStore(cursorCollection)).save(cursorCollection,
                DataStore.StringEntryId("global"), Record.dataAuto("revision" -> 2L, "status" -> "stale")))
              val before = stored(space, input)
              When("the conflicting prepared transition requests verified commit")
              val result = take(WorkflowPreparedMultiGuardTransition.prepareC(space, input)).commitVerifiedC()
              Then("neither earlier updates nor Creates are published")
              result.toOption shouldBe None
              stored(space, input) shouldBe before
              Consequence.unit
            })
          }
          TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
            take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              When("the same SQLite storage is reopened after rollback")
              val recovered = stored(space, input)
              Then("both original root revisions and the absence of the earlier Create are retained")
              recovered.take(2).flatten.foreach(_.getInt("revision") shouldBe Some(1))
              stored(space, input)(3) shouldBe None
              Consequence.unit
            })
          }
        }
      }
    }

    "reject unauthorized plans and cross-thread verified execution without changing admitted storage" in {
      Given("seeded admitted storage and a live verified host lease")
      val input = runtimePlan
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seed(space, input)
            val before = stored(space, input)
            When("a plan declares an unauthorized guard owner")
            val unauthorized = space.conditionalMultiGuardVerified(planFor(
              guardOwnerName = "org.goldenport.cncf.test.UnauthorizedWorkflow"))
            Then("the unauthorized owner is rejected")
            reasons(unauthorized) should contain("guard-owner-or-collection-not-admitted")
            val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
              val prepared = take(WorkflowPreparedMultiGuardTransition.prepareC(space, input))
              When("another thread attempts the admitted verified transition")
              val result = pool.submit(new java.util.concurrent.Callable[Consequence[DataStoreMultiGuardReadbackResult]] {
                def call() = prepared.commitVerifiedC()
              }).get(10L, java.util.concurrent.TimeUnit.SECONDS)
              Then("the cross-thread authority is rejected")
              result.toOption shouldBe None
            } finally {
              pool.shutdown()
              pool.awaitTermination(10L, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
            }
            Then("both refusals leave the admitted storage unchanged")
            stored(space, input) shouldBe before
            When("the issuing thread submits the admitted transition")
            val admitted = take(space.conditionalMultiGuardVerified(input))
            Then("the original lease remains valid and commits the transition")
            admitted.isInstanceOf[DataStoreMultiGuardReadbackResult.Verified] shouldBe true
            Consequence.unit
          })
        }
      }
    }

    "deny missing verified grants before resolving resources" in {
      Given("a declared host without a grant for the requesting Workflow")
      val file = path
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          var entered = false
          When("the unknown Workflow requests a verified scope")
          val result = subsystem.withWorkflowVerifiedMultiGuardSpaceC(ComponentId("org.goldenport.cncf.test.OtherWorkflow"),
            environment(subsystem)) { _ => entered = true; Consequence.unit }
          Then("the grant refusal occurs before entering the body or resolving resources")
          result.toOption shouldBe None
          entered shouldBe false
          subsystem.systemNode.managedDataStoreCount shouldBe 0
          Files.exists(file) shouldBe false
        }
      }
    }

    "atomically persist only store-selected canonical events and their guarded sequence proposal" in {
      Given("mixed durable and transient events selected by the store policy and seeded guarded storage")
      val file = path
      val prepared = selectedRecords()
      val input = selectedPlan(prepared)
      val verified = EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seedSelected(space, input)
            When("the selected canonical events and their cursor proposal are committed together")
            val result = take(space.conditionalMultiGuardVerified(input)).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            Then("the cursor and only selected durable event records are committed")
            result.records.size shouldBe 5
            result.records(2).record.getInt("last_sequence") shouldBe Some(12)
            When("delivery candidates are derived from the verified storage result")
            val candidates = take(prepared.deliveryCandidatesFromVerifiedStorageC(result))
            Then("the original event order, selected persistence and exact payloads are retained")
            candidates.map(_.id) shouldBe mixedEvents.map(_.id)
            candidates.map(_.sequence) shouldBe Vector(0L, 11L, 0L, 12L)
            candidates.map(_.persistent) shouldBe Vector(false, true, false, true)
            candidates.foreach(_.payload("typed") shouldBe 9007199254740993L)
            mixedEvents.foreach(_.sequence shouldBe 0L)
            val provider = take(space.dataStore(eventCollection))
            Vector(mixedEvents.head, mixedEvents(2)).foreach { event =>
              When("a transient event identity is loaded from persistent storage")
              val absent = take(provider.load(eventCollection, DataStore.StringEntryId(event.id.value)))
              Then("no persistent record exists for that transient event")
              absent shouldBe None
            }
            Consequence.success(result)
          })
        }
      }
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            When("the committed storage is reopened through an ordinary view")
            val reopened = stored(space, input)
            Then("all verified committed records survive reopening")
            reopened shouldBe verified.records.map(item => Some(item.record))
            When("the canonical durable event records are loaded and decoded")
            val recovered = prepared.creates.map { effect =>
              val record = take(take(space.dataStore(eventCollection)).load(eventCollection, effect.entryId)).get
              take(EventRecordJsonV1.decodeStorageC(record.getString("event_json").get))
            }
            Then("the recovered durable records match the selected proposal and sequences")
            recovered shouldBe prepared.persistent
            recovered.map(_.sequence) shouldBe Vector(11L, 12L)
            val provider = take(space.dataStore(eventCollection))
            Vector(mixedEvents.head, mixedEvents(2)).foreach { event =>
              When("transient event identities are loaded after reopening")
              val absent = take(provider.load(eventCollection, DataStore.StringEntryId(event.id.value)))
              Then("they remain absent from persistent storage")
              absent shouldBe None
            }
            Consequence.unit
          })
        }
      }
    }

    "rollback canonical events and sequence movement on cursor mismatch or the second event collision" in {
      Given("selected canonical events with a cursor mismatch or a collision on the second Create")
      Vector(false, true).foreach { collision =>
        val prepared = selectedRecords()
        val input = selectedPlan(prepared)
        EventStorePersistenceConstruction.withDeclaration(declaration) {
          TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
            take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              seedSelected(space, input)
              if (collision) {
                val effect = prepared.creates.last
                take(take(space.dataStore(eventCollection)).create(eventCollection, effect.entryId, effect.record))
              } else take(take(space.dataStore(cursorCollection)).save(cursorCollection,
                DataStore.StringEntryId("global"), Record.dataAuto("revision" -> 1L, "last_sequence" -> 11L)))
              val before = stored(space, input)
              When("the selected-event transition requests verified commit")
              val result = space.conditionalMultiGuardVerified(input)
              Then("the failure rolls back sequence movement and the earlier event Create")
              result.toOption shouldBe None
              stored(space, input) shouldBe before
              stored(space, input)(3) shouldBe None
              Consequence.unit
            })
          }
        }
      }
    }

    "consume actual committed event data despite a different proposal payload" in {
      Given("verified storage containing the original committed canonical event data")
      val prepared = selectedRecords()
      val input = selectedPlan(prepared)
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            seedSelected(space, input)
            val verified = take(space.conditionalMultiGuardVerified(input)).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            val changed = selectedRecords(mixedEvents.updated(1, mixedEvents(1).copy(payload = Map("typed" -> 7L))))
            When("a proposal with changed payload consumes the verified committed storage")
            val changedresult = changed.deliveryCandidatesFromVerifiedStorageC(verified)
            val changedcandidates = take(changedresult)
            Then("delivery retains the committed durable payloads, mixed event order and sequences")
            changedcandidates.filter(_.persistent).map(_.payload) shouldBe prepared.persistent.map(_.payload)
            changedcandidates.filter(_.persistent).map(_.payload) should not be changed.persistent.map(_.payload)
            changedcandidates.map(_.id) shouldBe mixedEvents.map(_.id)
            changedcandidates.map(_.sequence) shouldBe Vector(0L, 11L, 0L, 12L)
            val missing = selectedRecords(Vector(selectedEvent("fact", "absent")))
            When("a proposal for an absent event claims the verified storage")
            val missingresult = missing.deliveryCandidatesFromVerifiedStorageC(verified)
            Then("the missing event proposal is rejected")
            missingresult.toOption shouldBe None
            When("no verified storage evidence is supplied")
            val absentresult = prepared.deliveryCandidatesFromVerifiedStorageC(null)
            Then("the absent evidence is rejected")
            absentresult.toOption shouldBe None
            When("the exact original proposal uses its verified evidence")
            val candidates = take(prepared.deliveryCandidatesFromVerifiedStorageC(verified))
            Then("the original event identities are accepted without changing storage")
            candidates.map(_.id) shouldBe mixedEvents.map(_.id)
            stored(space, input) shouldBe verified.records.map(item => Some(item.record))
            Consequence.unit
          })
        }
      }
    }

    "commit a versioned decimal-text cursor and selected events through one managed guarded plan" in {
      Given("a versioned decimal-text cursor above the exact-double integer range and selected events")
      val file = path
      val last = 9007199254740993L
      val selection = take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(selectedPolicy), mixedEvents))
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          take(subsystem.initializeEventCursorC(environment(subsystem)))
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            val base = plan.transition
            Vector(base.root, base.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root).foreach { root =>
              take(take(space.dataStore(root.collection)).create(root.collection, root.entryId,
                Record.dataAuto("revision" -> 1L, "status" -> "open")))
            }
            take(take(space.dataStore(cursorCollection)).save(cursorCollection, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(1L, last)).toRecord))
            val loaded = take(subsystem.loadEventCursorC(environment(subsystem)))
            val proposal = take(WorkflowCursorEventProposal.prepareC(selection, declaration, loaded))
            val bare = take(DataStoreConditionalTransitionPlan.create(base.root, base.successor, Vector.empty))
            val input = take(proposal.multiGuardPlanC(bare))
            When("the complete versioned cursor and selected-event plan is committed")
            val verified = take(take(WorkflowPreparedMultiGuardTransition.prepareC(space, input)).commitVerifiedC())
              .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            Then("the authoritative cursor readback retains the exact decimal-text sequence")
            verified.records(2).record.getString("last_sequence") shouldBe Some((last + 2L).toString)
            When("delivery is derived from the verified versioned proposal")
            val candidates = take(proposal.deliveryCandidatesFromVerifiedStorageC(verified))
            Then("the selected durable sequences preserve exact integer precision")
            candidates.map(_.sequence) shouldBe Vector(0L, last + 1L, 0L, last + 2L)
            val cursorcopy = take(WorkflowCursorEventProposal.prepareC(selection, declaration,
              take(EventCursorRecordV1.createC(2L, last))))
            When("a proposal with a different unused cursor revision annotation consumes the committed cursor domain")
            val wrongresult = cursorcopy.deliveryCandidatesFromVerifiedStorageC(verified)
            Then("delivery preserves the actual committed cursor sequences with exact integer precision")
            take(wrongresult).map(_.sequence) shouldBe Vector(0L, last + 1L, 0L, last + 2L)
            // A stale complete plan cannot republish or advance the cursor again.
            val before = stored(space, input)
            When("the stale complete plan is resubmitted")
            val stale = take(space.conditionalMultiGuardVerified(input))
            Then("it is not matched and does not advance or republish any record")
            stale shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
            stored(space, input) shouldBe before
            Consequence.unit
          })
        }
      }
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          When("the declared versioned cursor is loaded after reopening")
          val loaded = take(subsystem.loadEventCursorC(environment(subsystem)))
          Then("both committed counters are recovered exactly")
          loaded.revision shouldBe 2L
          loaded.lastSequence shouldBe last + 2L
        }
      }
    }

    "rollback versioned selected-event plans on schema, sequence, revision or late Create conflict" in {
      Given("a versioned selected-event plan with schema, sequence, revision or late Create conflict")
      Vector("schema", "sequence", "revision", "collision").foreach { conflict =>
        EventStorePersistenceConstruction.withDeclaration(declaration) {
          TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
            val loaded = take(subsystem.initializeEventCursorC(environment(subsystem)))
            val selection = take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(selectedPolicy), mixedEvents))
            val proposal = take(WorkflowCursorEventProposal.prepareC(selection, declaration, loaded))
            val base = plan.transition
            val input = take(proposal.multiGuardPlanC(take(DataStoreConditionalTransitionPlan.create(
              base.root, base.successor, Vector.empty))))
            take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
              Vector(base.root, base.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root).foreach { root =>
                take(take(space.dataStore(root.collection)).create(root.collection, root.entryId,
                  Record.dataAuto("revision" -> 1L, "status" -> "open")))
              }
              if (conflict == "collision") {
                val effect = proposal.events.creates.last
                take(take(space.dataStore(eventCollection)).create(eventCollection, effect.entryId, effect.record))
              } else take(take(space.dataStore(cursorCollection)).save(cursorCollection, declaration.cursorEntryId,
                Record.dataAuto("cursor_schema" -> (if (conflict == "schema") "unknown" else EventCursorRecordV1.schema),
                  "revision" -> (if (conflict == "revision") 2L else 1L),
                  "last_sequence" -> (if (conflict == "sequence") "1" else "0"))))
              val before = stored(space, input)
              When("the conflicting versioned selected-event plan requests verified commit")
              val result = space.conditionalMultiGuardVerified(input)
              Then("all prior records and the absence of the earlier event Create are retained")
              result.toOption shouldBe None
              stored(space, input) shouldBe before
              stored(space, input)(3) shouldBe None
              Consequence.unit
            })
          }
        }
      }
    }

    "propose no cursor work for an all-transient batch even at an exhausted cursor" in {
      Given("an all-transient selection and an exhausted versioned cursor")
      val selection = take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(selectedPolicy),
        Vector(mixedEvents.head, mixedEvents(2))))
      When("a cursor proposal is prepared for the all-transient batch")
      val proposal = take(WorkflowCursorEventProposal.prepareC(selection, declaration,
        take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))))
      Then("no cursor guard or durable event Create is proposed")
      proposal.cursorGuard shouldBe None
      proposal.events.creates shouldBe empty
      When("the transient-only proposal is requested as a multi-guard plan")
      val noguard = proposal.multiGuardPlanC(plan.transition)
      Then("no cursor work can be manufactured")
      noguard.toOption shouldBe None
      When("no event selection is supplied")
      val absent = WorkflowCursorEventProposal.prepareC(null, declaration, EventCursorRecordV1.initial)
      Then("the missing selection is rejected")
      absent.toOption shouldBe None
    }

    "initialize the declared cursor by Create only and never reset retained progress" in {
      Given("a declared SQLite host with no cursor row")
      val file = path
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          When("the cursor is loaded before initialization")
          val missing = subsystem.loadEventCursorC(environment(subsystem))
          Then("no cursor is present")
          missing.toOption shouldBe None
          When("the declared cursor is initialized by Create")
          val initial = take(subsystem.initializeEventCursorC(environment(subsystem)))
          Then("the initial revision and zero sequence are retained")
          initial.revision shouldBe 1L
          initial.lastSequence shouldBe 0L
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            take(take(space.dataStore(cursorCollection)).save(cursorCollection, declaration.cursorEntryId,
              take(EventCursorRecordV1.createC(2L, 9L)).toRecord))
            Consequence.unit
          })
          When("initialization is requested after progress has been retained")
          val repeated = subsystem.initializeEventCursorC(environment(subsystem))
          Then("Create refuses to reset the existing cursor")
          repeated.toOption shouldBe None
          When("the existing cursor is explicitly loaded")
          val retained = take(subsystem.loadEventCursorC(environment(subsystem)))
          Then("the retained revision and sequence are unchanged under one managed resource")
          retained.revision shouldBe 2L
          retained.lastSequence shouldBe 9L
          subsystem.systemNode.managedDataStoreCount shouldBe 1
        }
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(file)) { subsystem =>
          When("the declared cursor is loaded from a reopened host")
          val recovered = take(subsystem.loadEventCursorC(environment(subsystem)))
          Then("retained progress survives the reopen")
          recovered.revision shouldBe 2L
          recovered.lastSequence shouldBe 9L
        }
      }
    }

    "pin a custom cursor identity in host admission rather than accepting a plan-selected global row" in {
      Given("a declaration pinning the cursor identity to central rather than global")
      val custom = take(EventStorePersistenceDeclaration.createC("events", cursorCollection,
        eventCollection, indexCollection, Vector(grant), DataStore.StringEntryId("central")))
      EventStorePersistenceConstruction.withDeclaration(custom) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          When("the custom declared cursor is initialized")
          val initial = take(subsystem.initializeEventCursorC(environment(subsystem)))
          Then("the declared cursor begins at sequence zero")
          initial.lastSequence shouldBe 0L
          take(subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            When("a plan tries to select the undeclared global guard row")
            val result = space.conditionalMultiGuardVerified(plan)
            Then("host admission rejects the plan-selected row")
            reasons(result) should contain("guard-entry-not-admitted")
            When("the undeclared global row is loaded")
            val global = take(take(space.dataStore(cursorCollection)).load(cursorCollection, DataStore.StringEntryId("global")))
            Then("no global cursor row was created")
            global shouldBe None
            Consequence.unit
          })
          When("the pinned cursor is loaded after the refusal")
          val retained = take(subsystem.loadEventCursorC(environment(subsystem)))
          Then("the original revision remains intact")
          retained.revision shouldBe 1L
        }
      }
      When("a declaration has no cursor identity")
      val absent = EventStorePersistenceDeclaration.createC("events", cursorCollection, eventCollection,
        indexCollection, Vector(grant), null)
      Then("the missing pinned identity is rejected")
      absent.toOption shouldBe None
    }

    "admit one Create winner across concurrent bootstrap calls without resetting the cursor" in {
      Given("two bootstrap callers waiting to Create the same absent declared cursor")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
          val start = new java.util.concurrent.CountDownLatch(1)
          try {
            val pending = Vector.fill(2)(pool.submit(new java.util.concurrent.Callable[Consequence[EventCursorRecordV1]] {
              def call() = {
                if (!start.await(10L, java.util.concurrent.TimeUnit.SECONDS))
                  throw new IllegalStateException("cursor bootstrap start timeout")
                subsystem.initializeEventCursorC(environment(subsystem))
              }
            }))
            When("both bootstrap calls race after the common start gate opens")
            start.countDown()
            val results = pending.map(_.get(20L, java.util.concurrent.TimeUnit.SECONDS))
            Then("exactly one Create succeeds")
            results.count(_.toOption.isDefined) shouldBe 1
            // The loser must explicitly load retained facts; bootstrap never retries or upserts.
            When("the loser explicitly loads the retained cursor")
            val retained = take(subsystem.loadEventCursorC(environment(subsystem)))
            Then("the winner's initial facts are retained without retry or reset")
            retained.revision shouldBe 1L
            retained.lastSequence shouldBe 0L
            subsystem.systemNode.managedDataStoreCount shouldBe 1
          } finally {
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(10L, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
          }
        }
      }
    }

    "reject malformed retained cursor bytes without repairing or reinitializing them" in {
      Given("a retained cursor whose decimal-text sequence is noncanonical")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          take(subsystem.initializeEventCursorC(environment(subsystem)))
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            val store = take(space.dataStore(cursorCollection))
            take(store.save(cursorCollection, declaration.cursorEntryId,
              Record.dataAuto("cursor_schema" -> EventCursorRecordV1.schema, "revision" -> 1L, "last_sequence" -> "00")))
            When("the malformed cursor is loaded")
            val malformed = subsystem.loadEventCursorC(environment(subsystem))
            Then("decoding rejects the malformed bytes")
            malformed.toOption shouldBe None
            When("initialization is attempted on the existing malformed row")
            val repeated = subsystem.initializeEventCursorC(environment(subsystem))
            Then("Create does not repair or overwrite the existing row")
            repeated.toOption shouldBe None
            When("the raw cursor row is loaded directly")
            val retained = take(store.load(cursorCollection, declaration.cursorEntryId)).get
            Then("the exact malformed decimal-text bytes remain")
            retained.getString("last_sequence") shouldBe Some("00")
            Consequence.unit
          })
        }
      }
    }

    "reject a retained legacy cursor before Create can expand its schema" in {
      Given("an existing legacy cursor row outside the current logical schema")
      EventStorePersistenceConstruction.withDeclaration(declaration) {
        TestComponentFactory.withEmptySubsystem(configuration = sharedConfig(path)) { subsystem =>
          take(subsystem.withWorkflowMultiGuardSpaceC(owner, environment(subsystem)) { space =>
            val store = take(space.dataStore(cursorCollection))
            take(store.create(cursorCollection, declaration.cursorEntryId, Record.dataAuto("legacy" -> "unchanged")))
            val before = take(store.load(cursorCollection, declaration.cursorEntryId))
            When("initialization is requested against the legacy row")
            val result = subsystem.initializeEventCursorC(environment(subsystem))
            Then("Create refuses to expand the existing schema")
            result.toOption shouldBe None
            When("the raw legacy row is loaded again")
            val retained = take(store.load(cursorCollection, declaration.cursorEntryId))
            Then("its exact original fields are preserved")
            retained shouldBe before
            When("the legacy row is loaded through the current cursor decoder")
            val decoded = subsystem.loadEventCursorC(environment(subsystem))
            Then("the legacy schema is still rejected")
            decoded.toOption shouldBe None
            Consequence.unit
          })
        }
      }
    }

    "restore host construction scope after failure" in {
      Given("no active host construction declaration")
      EventStorePersistenceConstruction.current shouldBe None
      When("a declared construction scope fails after observing its declaration")
      intercept[IllegalStateException] {
        EventStorePersistenceConstruction.withDeclaration(declaration) {
          Then("the declaration is visible only within the active scope")
          EventStorePersistenceConstruction.current shouldBe Some(declaration)
          throw new IllegalStateException("injected host construction failure")
        }
      }
      Then("the failed scope restores the previous absence of a declaration")
      EventStorePersistenceConstruction.current shouldBe None
    }
  }
}
