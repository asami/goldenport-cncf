package org.goldenport.cncf.entity.runtime

import java.time.{Clock, Duration, Instant, ZoneOffset}
import scala.collection.mutable.ArrayBuffer
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent, EntityQuery, EntitySearchScope}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkingSetPolicyDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Entity working-set policy evaluation" should {
    "use one execution-clock instant for admission and resident search" in {
      Given("generated evaluation instants and a custom policy that records its inputs")
      val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val evaluatedat = Instant.ofEpochSecond(epochsecond)
        given ExecutionContext = ExecutionContext.create(Clock.fixed(evaluatedat, ZoneOffset.UTC))
        given EntityPersistent[PolicyEntity] = PolicyEntityPersistent
        val observed = ArrayBuffer.empty[Instant]
        val policy = WorkingSetPolicy.Custom(
          "record-evaluation-time",
          new WorkingSetPolicyEvaluator {
            def isResident(record: Record, now: Instant): Boolean = {
              val _ = record
              observed += now
              true
            }
          }
        )
        val collection = _collection(policy)
        val entity = PolicyEntity(EntityId("m", "one", PolicyCollectionId.value), "value")

        collection.putScoped(entity)(using summon[ExecutionContext])
        collection.storage.workingSetStatus.markReady(evaluatedat)
        val result = collection.search(
          EntityQuery(PolicyCollectionId.value, Query.plan(Record.empty), EntitySearchScope.WorkingSet)
        )

        observed.toVector == Vector(evaluatedat, evaluatedat) &&
          result.toOption.exists(_.data == Vector(entity))
      }

      When("the admission and search sequence is replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)

      Then("both policy evaluations receive the bound execution-clock instant")
      checked.passed shouldBe true
    }

    "reject context-free admission for a time-dependent policy" in {
      Given("an Entity collection whose recent policy requires an evaluation instant")
      given EntityPersistent[PolicyEntity] = PolicyEntityPersistent
      val collection = _collection(WorkingSetPolicy.Recent(Duration.ofHours(1), "updatedAt"))
      val entity = PolicyEntity(EntityId("m", "context_free", PolicyCollectionId.value), "value")

      When("context-free compatibility put attempts to admit the Entity")
      val failure = the[IllegalStateException] thrownBy collection.put(entity)

      Then("the missing execution context is reported before either realm is mutated")
      failure.getMessage should include ("requires ExecutionContext")
      collection.storage.storeRealm.values shouldBe empty
      collection.residentCount shouldBe 0
    }

    "return a structured failure from context-free record admission" in {
      Given("a record admission API and a time-dependent working-set policy")
      given EntityPersistent[PolicyEntity] = PolicyEntityPersistent
      val collection = _collection(WorkingSetPolicy.Recent(Duration.ofHours(1), "updatedAt"))
      val record = Record.data("value" -> "record")

      When("the record is admitted without an execution context")
      val result = collection.putRecord(record)

      Then("the Consequence reports the missing context without mutating storage")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.display should include ("requires ExecutionContext")
        case Consequence.Success(_) =>
          fail("context-free temporal record admission must fail")
      }
      collection.storage.storeRealm.values shouldBe empty
      collection.residentCount shouldBe 0
    }

    "avoid temporal cache mutation until scoped resolution supplies an execution instant" in {
      Given("a store-resident Entity and a custom policy that records evaluation instants")
      val evaluatedat = Instant.parse("2026-07-16T01:02:03Z")
      given ExecutionContext = ExecutionContext.create(Clock.fixed(evaluatedat, ZoneOffset.UTC))
      given EntityPersistent[PolicyEntity] = PolicyEntityPersistent
      val observed = ArrayBuffer.empty[Instant]
      val policy = WorkingSetPolicy.Custom(
        "record-resolution-time",
        new WorkingSetPolicyEvaluator {
          def isResident(record: Record, now: Instant): Boolean = {
            val _ = record
            observed += now
            true
          }
        }
      )
      val collection = _collection(policy)
      val entity = PolicyEntity(EntityId("m", "resolution", PolicyCollectionId.value), "value")
      collection.storage.storeRealm.put(entity)

      When("context-free resolution loads the Entity before scoped resolution")
      val contextfreeresult = collection.resolve(entity.id)
      val contextfreeresidentcount = collection.residentCount
      val scopedresult = collection.resolveScoped(entity.id)(using summon[ExecutionContext])

      Then("only scoped resolution evaluates the policy and admits the Entity")
      contextfreeresult.toOption should contain (entity)
      contextfreeresidentcount shouldBe 0
      scopedresult.toOption should contain (entity)
      observed.toVector shouldBe Vector(evaluatedat)
      collection.residentCount shouldBe 1
    }
  }

  private def _collection(
    policy: WorkingSetPolicy
  )(using EntityPersistent[PolicyEntity]): EntityCollection[PolicyEntity] = {
    val storerealm = new EntityRealm[PolicyEntity](
      entityName = "policy_entity",
      loader = EntityLoader[PolicyEntity](_ => None),
      state = new PolicyIdRef[EntityRealmState[PolicyEntity]](EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[PolicyEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = PolicyCollectionId.value,
      plan = EntityRuntimePlan(
        entityName = "policy_entity",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        workingSetPolicy = Some(policy),
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[PolicyEntity]]
    )
    new EntityCollection(descriptor, EntityStorage(storerealm, Some(memoryrealm)))
  }
}

private object PolicyEntityPersistent extends EntityPersistent[PolicyEntity] {
  def id(entity: PolicyEntity): EntityId = entity.id

  def toRecord(entity: PolicyEntity): Record = entity.toRecord()

  def fromRecord(record: Record): Consequence[PolicyEntity] =
    record.getString("value") match {
      case Some(value) =>
        Consequence.success(
          PolicyEntity(EntityId("m", "record", PolicyCollectionId.value), value)
        )
      case None =>
        Consequence.argumentMissing("value")
    }
}

private final case class PolicyEntity(
  id: EntityId,
  value: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto("id" -> id, "value" -> value)
}

private final class PolicyIdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value = initial

  def get: A = synchronized(_value)

  def set(value: A): Unit = synchronized {
    _value = value
  }

  override def getAndSet(value: A): A = synchronized {
    val previous = _value
    _value = value
    previous
  }

  def access: (A, A => Boolean) = synchronized {
    val snapshot = _value
    val setter: A => Boolean = (next: A) => synchronized {
      if (_value == snapshot) {
        _value = next
        true
      } else {
        false
      }
    }
    (snapshot, setter)
  }

  override def tryUpdate(update: A => A): Boolean = synchronized {
    _value = update(_value)
    true
  }

  override def tryModify[B](update: A => (A, B)): Option[B] = synchronized {
    val (next, result) = update(_value)
    _value = next
    Some(result)
  }

  def update(update: A => A): Unit = synchronized {
    _value = update(_value)
  }

  def modify[B](update: A => (A, B)): B = synchronized {
    val (next, result) = update(_value)
    _value = next
    result
  }

  override def modifyState[B](state: State[A, B]): B = synchronized {
    val (next, result) = state.run(_value).value
    _value = next
    result
  }

  override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
    val (next, result) = state.run(_value).value
    _value = next
    Some(result)
  }

  def tryUpdateState(state: State[A, Unit]): Boolean = synchronized {
    _value = state.runS(_value).value
    true
  }
}

private object PolicyCollectionId {
  val value: EntityCollectionId = EntityCollectionId("test", "a", "policy_entity")
}
