package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SubsystemStoragePlanSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Storage plan rejected: $failure")
  }
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "plan", name))
  private val owner = ComponentId("org.example.Workflow")
  private val policy = take(EventPersistencePolicy.createC("plan-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val persistence = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow",
        collection("roots"), collection("continuations")))))
  private def records(target: DataStore.CollectionId) =
    take(ComponentRecordStorageDeclaration.createC(Vector(
      ComponentRecordStorageDeclaration.CreateGrant(owner, "workflow", target))))

  "A typed host storage plan" should {
    "validate immutable data without entering resource or construction scopes" in {
      Given("valid disjoint declarations and the current enclosing captures")
      val before = (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current)
      val record = records(collection("registrations"))
      When("the host creates a validated plan without assembling a Subsystem")
      val plan = take(SubsystemStoragePlan.createC(policy, persistence, Some(record)))
      Then("exact data is retained and no construction capture or node is changed")
      plan.policy shouldBe policy
      plan.persistence shouldBe persistence
      plan.records shouldBe Some(record)
      (SystemNodeConstruction.current, EventStoreConstruction.current,
        EventStorePersistenceConstruction.current, ComponentRecordStorageConstruction.current) shouldBe before
    }

    "reject missing typed inputs rather than creating default authority" in {
      Given("a missing policy, persistence declaration, option or contained record declaration")
      val inputs = Vector((null, persistence, None), (policy, null, None),
        (policy, persistence, null), (policy, persistence, Some(null)))
      When("the host validates each incomplete plan")
      val results = inputs.map { case (p, d, r) => SubsystemStoragePlan.createC(p, d, r) }
      Then("all are rejected without a plan")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "exclude every EventStore and Workflow infrastructure collection from record Create" in {
      Given("otherwise valid record grants targeting each reserved collection")
      val reserved = Vector(persistence.cursorCollection, persistence.eventCollection,
        persistence.deliveryIndexCollection) ++ persistence.workflowGrants.flatMap(g =>
          Vector(g.rootCollection, g.continuationCollection))
      When("the host validates each composed declaration")
      val results = reserved.map(c => SubsystemStoragePlan.createC(policy, persistence, Some(records(c))))
      Then("no infrastructure target can become an ordinary Create grant")
      results.size shouldBe 5
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "restore enclosing service and declaration captures when assembly throws" in {
      Given("a validated plan inside different enclosing construction captures")
      val record = records(collection("registrations"))
      val enclosingRecord = records(collection("outer_records"))
      val enclosingPersistence = take(EventStorePersistenceDeclaration.createC("outer",
        collection("outer_cursor"), collection("outer_events"), collection("outer_indexes"), Vector(
          EventStorePersistenceDeclaration.WorkflowGrant(owner, "outer-workflow",
            collection("outer_roots"), collection("outer_continuations")))))
      val enclosingService = EventStore.inMemory
      val plan = take(SubsystemStoragePlan.createC(policy, persistence, Some(record)))
      EventStoreConstruction.withService(enclosingService) {
        EventStorePersistenceConstruction.withDeclaration(enclosingPersistence) {
          ComponentRecordStorageConstruction.withDeclaration(enclosingRecord) {
            When("assembly observes the exact inner declarations and fails")
            val failure = new IllegalStateException("assembly failed")
            val thrown = intercept[IllegalStateException] {
              plan.withConstruction {
                EventStorePersistenceConstruction.current shouldBe Some(persistence)
                ComponentRecordStorageConstruction.current shouldBe Some(record)
                EventStoreConstruction.current.get should not be (enclosingService)
                throw failure
              }
            }
            Then("the original failure and every enclosing capture are preserved")
            thrown shouldBe failure
            EventStoreConstruction.current shouldBe Some(enclosingService)
            EventStorePersistenceConstruction.current shouldBe Some(enclosingPersistence)
            ComponentRecordStorageConstruction.current shouldBe Some(enclosingRecord)
          }
        }
      }
    }
  }
}
