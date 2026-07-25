package org.goldenport.cncf.entity.runtime

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.entity.{EntityPersistent, EntityRevisionBinding}

/*
 * @since   Mar. 15, 2026
 *  version Mar. 24, 2026
 *  version Apr. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityDescriptor[E](
  collectionId: EntityCollectionId,
  plan: EntityRuntimePlan[E],
  persistent: EntityPersistent[E],
  revisionBinding: Option[EntityRevisionBinding] = None
)

final case class EntityStorage[E](
  // Loader lifecycle is owned by the storage realm (one loader per storage).
  storeRealm: EntityRealm[E],
  memoryRealm: Option[PartitionedMemoryRealm[E]] = None,
  workingSetStatus: WorkingSetStatusRef = WorkingSetStatusRef()
)

enum WorkingSetLoadState {
  case Disabled, NotStarted, Loading, Ready, Failed

  def label: String =
    productPrefix.toLowerCase(java.util.Locale.ROOT)
}

final case class WorkingSetStatus(
  state: WorkingSetLoadState,
  startedAt: Option[Instant] = None,
  completedAt: Option[Instant] = None,
  error: Option[String] = None
) {
  def isInitializing: Boolean =
    state == WorkingSetLoadState.NotStarted || state == WorkingSetLoadState.Loading

  def isReady: Boolean =
    state == WorkingSetLoadState.Ready
}

final class WorkingSetStatusRef(
  initial: WorkingSetStatus = WorkingSetStatus(WorkingSetLoadState.NotStarted)
) {
  private val _ref = new AtomicReference[WorkingSetStatus](initial)

  def get: WorkingSetStatus =
    _ref.get()

  def markDisabled(): Unit =
    _ref.set(WorkingSetStatus(WorkingSetLoadState.Disabled))

  private[cncf] def markLoading(at: Instant): Unit =
    _ref.set(WorkingSetStatus(WorkingSetLoadState.Loading, startedAt = Some(at)))

  private[cncf] def markReady(at: Instant): Unit = {
    val current = _ref.get()
    _ref.set(current.copy(state = WorkingSetLoadState.Ready, completedAt = Some(at), error = None))
  }

  private[cncf] def markFailed(message: String, at: Instant): Unit = {
    val current = _ref.get()
    _ref.set(current.copy(
      state = WorkingSetLoadState.Failed,
      completedAt = Some(at),
      error = Some(Option(message).getOrElse("").take(500))
    ))
  }

  // Context-free fixture transitions intentionally omit semantic timestamps.
  private[cncf] def markLoading(): Unit =
    _ref.set(WorkingSetStatus(WorkingSetLoadState.Loading))

  private[cncf] def markReady(): Unit = {
    val current = _ref.get()
    _ref.set(current.copy(state = WorkingSetLoadState.Ready, completedAt = None, error = None))
  }
}

object WorkingSetStatusRef {
  def apply(
    initial: WorkingSetStatus = WorkingSetStatus(WorkingSetLoadState.NotStarted)
  ): WorkingSetStatusRef =
    new WorkingSetStatusRef(initial)
}
