package org.goldenport.cncf.action

import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.operationalobservation.OperationalObservationSnapshot
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
trait BehaviorOperationalObservationPart { self: Behavior.Core.Holder =>
  protected final def operational_observations(limit: Int = 50): ExecUowM[OperationalObservationSnapshot] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ReadOperationalObservations(limit)))
}
