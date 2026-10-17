package org.goldenport.cncf.datastore

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record

/** Frozen declarative local intent. Not an authorization or a stored outcome.
  * Mutable source/projection Records are never retained as commit authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] sealed abstract class WorkflowClosingLocalIntent private {
  private[cncf] def projectC: Consequence[Either[DataStoreConditionalRoot, EntityVersionedSideEffect.Create]]
}

private[cncf] object WorkflowClosingLocalIntent {
  private final class Guard(value: DataStoreConditionalRoot) extends WorkflowClosingLocalIntent {
    def projectC = guardCopyC(value).map(Left(_))
  }
  private final class Create(value: EntityVersionedSideEffect.Create) extends WorkflowClosingLocalIntent {
    def projectC = createCopyC(value).map(Right(_))
  }
  def guardedC(value: DataStoreConditionalRoot): Consequence[WorkflowClosingLocalIntent] =
    guardCopyC(value).map(new Guard(_))
  def createC(value: EntityVersionedSideEffect.Create): Consequence[WorkflowClosingLocalIntent] =
    createCopyC(value).map(new Create(_))

  private def guardCopyC(value: DataStoreConditionalRoot): Consequence[DataStoreConditionalRoot] =
    if (value == null) Consequence.configurationInvalid("Closing guard is missing")
    else EntityConditionalTransitionSupport.validateRoot(value).flatMap { _ =>
      snapshotC(value.changes).flatMap { changes =>
        DataStoreConditionalRoot.create(value.componentOwner, value.collection, value.entryId,
          value.revisionField, Some(value.expectedRevision), value.expectedFields, changes, value.nextRevision)
      }
    }
  private def createCopyC(value: EntityVersionedSideEffect.Create): Consequence[EntityVersionedSideEffect.Create] =
    if (value == null || value.collection == null || value.entryId == null)
      Consequence.configurationInvalid("Closing Create target is missing")
    else EntityConditionalTransitionSupport.validateSideRecord(value.record).flatMap { _ =>
      snapshotC(value.record).map(record => value.copy(record = record))
    }
  private def snapshotC(value: Record): Consequence[Record] =
    try Consequence.success(snapshot(value))
    catch { case NonFatal(_) => Consequence.configurationInvalid("Closing record cannot be frozen") }
  private def snapshot(value: Record): Record =
    Record.dataAuto(value.fields.map(f => f.key -> snapshotValue(f.value.single))*)
  private def snapshotValue(value: Any): Any = value match {
    case record: Record => snapshot(record)
    case values: Seq[?] => values.map(snapshotValue).toVector
    case immutable => immutable // The canonical validator admits immutable scalar values only.
  }
}
