package org.goldenport.cncf.entity.runtime


/*
 * WorkingSetInitializer
 *
 * Responsible for preloading entities into the in-memory working set
 * during component startup.
 *
 * This supports the CNCF working-set entity runtime model.
 *
 * @since   Mar. 14, 2026
 * @version Mar. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkingSetInitializer(
  entityspace: EntitySpace
) {
  /**
   * Preload entities into the MemoryRealm for a specific entity type.
   *
   * If entityName is not registered, preload is ignored (no-op).
   */
  def preload[E](entityName: String, entities: Iterable[E]): Unit = {
    _storage[E](entityName).foreach { storage =>
      storage.memoryRealm
        .foreach { memory =>
          // NOTE:
          // Entities are inserted one-by-one for now.
          // Future optimization may introduce a bulk preload API
          // (e.g., memory.bulkPut) to improve startup performance
          // when loading large working sets.
          entities.foreach(memory.put)
        }
    }
  }

  def preload[E](spec: WorkingSetDefinition[E]): Unit = {
    _storage[E](spec.entityName).foreach { storage =>
      storage.memoryRealm
        .foreach { memory =>
          spec.entities.foreach(memory.put)
        }
    }
  }

  private def _storage[E](entityname: String): Option[EntityStorage[E]] =
    // Unregistered entities in working set definitions are ignored.
    entityspace.entityOption[E](entityname).map(_.storage)
}
