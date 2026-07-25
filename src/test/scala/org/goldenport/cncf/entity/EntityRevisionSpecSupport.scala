package org.goldenport.cncf.entity

import cats.data.State
import cats.effect.Ref
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy
}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EntityRevisionSpecSupport {
  def registerRevisionBinding[E](
    context: ExecutionContext,
    collectionId: EntityCollectionId,
    persistent: EntityPersistent[E],
    representation: EntityRevisionRepresentation,
    concurrencyPolicy: EntityConcurrencyPolicy =
      EntityConcurrencyPolicy.default
  ): Unit =
    context.entitySpace.entityOption(collectionId) match {
      case Some(collection) =>
        val actual = collection.descriptor.revisionBinding.map(_.representation)
        require(
          actual.contains(representation),
          s"Entity revision binding mismatch for ${collectionId.print}: expected=$representation, actual=$actual"
        )
      case None =>
        given EntityPersistent[E] = persistent
        val registrationname = collectionId.print
        val realm = new EntityRealm[E](
          entityName = registrationname,
          loader = EntityLoader[E](_ => None),
          state = new IdRef(EntityRealmState(Map.empty))
        )
        val descriptor = EntityDescriptor(
          collectionId = collectionId,
          plan = EntityRuntimePlan(
            entityName = registrationname,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = None,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 1,
            maxEntitiesPerPartition = 16,
            concurrencyPolicy = concurrencyPolicy
          ),
          persistent = persistent,
          revisionBinding = Some(EntityRevisionBinding(representation))
        )
        context.entitySpace.registerEntity(
          registrationname,
          new EntityCollection(
            descriptor,
            EntityStorage(realm)
          )
        )
    }

  private final class IdRef[A](
    initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized {
      _value
    }

    def set(a: A): Unit = synchronized {
      _value = a
    }

    override def getAndSet(a: A): A = synchronized {
      val previous = _value
      _value = a
      previous
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = next => synchronized {
        if (_value == snapshot) {
          _value = next
          true
        } else {
          false
        }
      }
      (snapshot, setter)
    }

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, result) = f(_value)
      _value = next
      Some(result)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, result) = f(_value)
      _value = next
      result
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }

    override def tryModifyState[B](
      state: State[A, B]
    ): Option[B] = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      Some(result)
    }
  }
}
