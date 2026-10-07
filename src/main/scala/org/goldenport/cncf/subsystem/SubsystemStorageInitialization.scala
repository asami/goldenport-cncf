package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.datastore.ComponentDataStore

/** Canonical host infrastructure preparation, before Component core assembly.
  * Views are borrowed only during verification; the SystemNode retains the pool.
  * This is not initial-data installation or a general schema migration service.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] object SubsystemStorageInitialization {
  def withPreparedStorage[A](subsystem: Subsystem)(assemble: => A): A = {
    val borrowedNode = SystemNodeConstruction.current.exists(_ eq subsystem.systemNode)
    try {
      prepareC(subsystem) match {
        case Consequence.Success(_) => assemble
        case Consequence.Failure(conclusion) =>
          throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
      }
    } catch {
      case error: Throwable =>
        try {
          if (borrowedNode) {
            subsystem.shutdownC() match {
              case Consequence.Success(_) => ()
              case Consequence.Failure(conclusion) =>
                throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
            }
          } else Subsystem.shutdownOwned(subsystem)
        } catch {
          case cleanup: Throwable => error.addSuppressed(cleanup)
        }
        throw error
    }
  }

  private def prepareC(subsystem: Subsystem): Consequence[Unit] =
    EventStorePersistenceConstruction.current match {
      case None => Consequence.unit
      case Some(declaration) =>
        val environment = ComponentDataStore.Environment(
          ResolvedParameters.fromResolvedConfiguration(subsystem.configuration),
          Some(subsystem.configuration))
        declaration.workflowGrants.foldLeft(Consequence.unit) { (previous, grant) =>
          previous.flatMap { _ =>
            subsystem.withWorkflowVerifiedMultiGuardSpaceC(grant.componentId, environment) { _ =>
              Consequence.unit
            }
          }
        }
    }
}
