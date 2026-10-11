package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue

/**
 * Ordinary host-selected progress, with no issued AI interaction or claim.
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
object WorkflowMechanicalTransition {
  final case class Reference(identity: String, revision: String)
  final case class Intent[A](configuration: Configuration, reference: Reference,
    current: InstanceRecord, expectedRevision: InstanceRevision, entry: HistoryEntry,
    payload: TypedValue[A])
  /** Inert retained data; decoding this record confers no execution authority. */
  final case class Receipt[A](reference: Reference, component: ComponentId,
    operation: StateMachineOperationIdentity, current: InstanceRecord,
    next: InstanceRecord, payload: TypedValue[A])

  def keyC(reference: Reference): Consequence[String] =
    if (reference == null || !canonical(reference.identity) || !canonical(reference.revision))
      Consequence.stateConflict("Mechanical checkpoint reference is incomplete or noncanonical")
    else {
      val key = "mechanical:" + escaped(reference.identity) + "@" + escaped(reference.revision)
      if (key.getBytes(UTF_8).length > 1024)
        Consequence.stateConflict("Mechanical checkpoint key exceeds its bound")
      else Consequence.success(key)
    }

  private[workflow] def admitC[A](component: ComponentId, operation: StateMachineOperationIdentity,
    intent: Intent[A]): Consequence[Receipt[A]] = guarded {
    if (intent == null)
      Consequence.stateConflict("Mechanical transition intent is missing")
    else transitionC(component, operation, intent.reference, intent.current,
      intent.expectedRevision, intent.entry, intent.payload)
  }

  private def transitionC[A](component: ComponentId, operation: StateMachineOperationIdentity,
    reference: Reference, current: InstanceRecord, expected: InstanceRevision,
    entry: HistoryEntry, payload: TypedValue[A]): Consequence[Receipt[A]] =
    if (component == null || !canonical(component.name) || operation == null ||
        !canonical(operation.service) || !canonical(operation.operation) || current == null ||
        current.history == null || current.suspension == null || current.currentProgression == null ||
        expected == null || entry == null || entry.suspension == null || payload == null ||
        current.lifecycle != Lifecycle.Active || current.suspension.nonEmpty ||
        entry.lifecycle != Lifecycle.Active || entry.suspension.nonEmpty ||
        entry.correlation != current.correlation)
      Consequence.stateConflict("Mechanical progress requires an owned active unsuspended one-append intent")
    else for {
      _ <- keyC(reference)
      _ <- payload.validateC
      next <- current.appendC(expected, entry)
    } yield Receipt(reference, component, operation, current, next, payload)

  private[workflow] def canonical(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value &&
      !value.exists(ch => Character.isISOControl(ch)) && UTF_8.newEncoder().canEncode(value)

  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case e: InterruptedException => Thread.currentThread().interrupt(); throw e
    case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
  }

  private def escaped(value: String): String = value.getBytes(UTF_8).map { byte =>
    val b = byte & 0xff
    if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') ||
        b == '-' || b == '.' || b == '_' || b == '~') b.toChar.toString
    else "%%%02X".format(b)
  }.mkString
}
