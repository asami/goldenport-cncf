package org.goldenport.cncf.unitofwork

/*
 * Metadata-independent planning values for the UnitOfWork operation algebra.
 *
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum UnitOfWorkEffectClass {
  case Control
  case Local
  case External
}

private[cncf] object UnitOfWorkOperationClassifier {
  def classify(operation: UnitOfWorkOp[?]): UnitOfWorkEffectClass =
    operation match {
      case UnitOfWorkOp.Authorize(_) |
          UnitOfWorkOp.StageOperationEvaluationSupplemental(_) =>
        UnitOfWorkEffectClass.Control

      case UnitOfWorkOp.HttpGet(_, _, _) |
          UnitOfWorkOp.HttpPost(_, _, _, _) |
          UnitOfWorkOp.HttpPostBag(_, _, _, _) |
          UnitOfWorkOp.HttpPut(_, _, _, _) |
          UnitOfWorkOp.ShellCommandExec(_) |
          UnitOfWorkOp.ProcessExec(_) =>
        UnitOfWorkEffectClass.External

      case UnitOfWorkOp.DataStoreLoad(_) |
          UnitOfWorkOp.DataStoreSave(_, _) |
          UnitOfWorkOp.DataStoreDelete(_) |
          UnitOfWorkOp.LocalDataDir(_) |
          UnitOfWorkOp.EmbeddedDataStoreOpen(_, _, _) |
          UnitOfWorkOp.EmbeddedDataStoreRead(_, _) |
          UnitOfWorkOp.EmbeddedDataStoreUpdate(_, _) |
          UnitOfWorkOp.EmbeddedDataStoreMigrate(_, _) |
          UnitOfWorkOp.EntityStoreCreate(_, _, _, _) |
          UnitOfWorkOp.EntityStoreClaimOrLoad(_, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpsert(_, _, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreLoad(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreLoadSnapshot(_, _, _) |
          UnitOfWorkOp.EntityStoreLoadDetached(_, _, _) |
          UnitOfWorkOp.EntityStoreLoadDirect(_, _) |
          UnitOfWorkOp.EntityStoreSave(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreSaveDetached(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreSaveManaged(_, _, _, _) |
          UnitOfWorkOp.EntityStoreSaveUnversioned(_, _, _, _) |
          UnitOfWorkOp.EntityStoreUpsertUnversioned(_, _, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdate(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateDetached(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateById(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateByIdObserved(_, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateByIdDetached(_, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreConditionalTransition(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateUnversioned(_, _, _, _) |
          UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(_, _, _, _, _) |
          UnitOfWorkOp.EntityStoreDelete(_, _) |
          UnitOfWorkOp.EntityStoreRestore(_, _) |
          UnitOfWorkOp.EntityStoreDeleteHard(_) |
          UnitOfWorkOp.EntityStoreSearch(_, _, _) |
          UnitOfWorkOp.EntityStoreSearchDirect(_, _, _) |
          UnitOfWorkOp.EntityStoreSearchInternal(_, _) |
          UnitOfWorkOp.EntityStoreUniqueValueExists(_, _, _, _, _, _, _) |
          UnitOfWorkOp.EntityStoreResolveIdentity(_, _, _, _, _, _) |
          UnitOfWorkOp.BlobNormalizeInlineImages(_) |
          UnitOfWorkOp.BlobAttachInlineImages(_, _) |
          UnitOfWorkOp.ContentNormalizeReferences(_) |
          UnitOfWorkOp.ContentAttachReferences(_, _) |
          UnitOfWorkOp.ContentValidateReferences(_) |
          UnitOfWorkOp.ContentSyncInlineReferences(_, _) |
          UnitOfWorkOp.ContentRenderHtml(_) =>
        UnitOfWorkEffectClass.Local
    }
}

private[cncf] final case class UnitOfWorkOperationOccurrence(
  operation: UnitOfWorkOp[?],
  ordinal: Int,
  effectClass: UnitOfWorkEffectClass
)

private[cncf] sealed trait UnitOfWorkPlanSegment

private[cncf] object UnitOfWorkPlanSegment {
  final case class LocalAtomic(
    occurrences: Vector[UnitOfWorkOperationOccurrence]
  ) extends UnitOfWorkPlanSegment

  final case class ExternalBoundary(
    occurrence: UnitOfWorkOperationOccurrence
  ) extends UnitOfWorkPlanSegment
}

private[cncf] final case class UnitOfWorkProgramPlan(
  occurrences: Vector[UnitOfWorkOperationOccurrence],
  segments: Vector[UnitOfWorkPlanSegment]
)

private[cncf] object UnitOfWorkProgramPlanner {
  def plan(operations: Vector[UnitOfWorkOp[?]]): UnitOfWorkProgramPlan = {
    val occurrences = operations.zipWithIndex.map { case (operation, ordinal) =>
      UnitOfWorkOperationOccurrence(
        operation,
        ordinal,
        UnitOfWorkOperationClassifier.classify(operation)
      )
    }
    val segments = occurrences.foldLeft(Vector.empty[UnitOfWorkPlanSegment]) {
      case (segments, occurrence) =>
        occurrence.effectClass match {
          case UnitOfWorkEffectClass.External =>
            segments :+ UnitOfWorkPlanSegment.ExternalBoundary(occurrence)
          case UnitOfWorkEffectClass.Control | UnitOfWorkEffectClass.Local =>
            segments.lastOption match {
              case Some(UnitOfWorkPlanSegment.LocalAtomic(previous)) =>
                segments.dropRight(1) :+
                  UnitOfWorkPlanSegment.LocalAtomic(previous :+ occurrence)
              case _ =>
                segments :+ UnitOfWorkPlanSegment.LocalAtomic(Vector(occurrence))
            }
        }
    }
    UnitOfWorkProgramPlan(occurrences, segments)
  }
}
