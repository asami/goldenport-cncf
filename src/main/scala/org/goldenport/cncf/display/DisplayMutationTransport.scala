package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.entity.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure ordered-header adapter; no live HTTP binding or write proof. */
object DisplayMutationTransport {
  import DisplayMutation.Operation
  final class Metadata private[DisplayMutationTransport](val operation: Operation,
    val observedRevision: Option[EntityRevision], val properties: Record,
    val executionPolicy: Option[EntityMutationExecutionPolicy])

  def bindC(operation: Operation, headers: Vector[(String, String)]): Consequence[Metadata] = {
    if (operation == null || headers == null || headers.exists(header => header == null ||
      !_header_name_valid(header._1) || header._2 == null)) _format_error()
    else {
      val observations = headers.collect { case (name, value) if name.equalsIgnoreCase("If-Match") => value }
      operation match {
        case Operation.Create =>
          if (observations.nonEmpty) _format_error()
          else Consequence.success(new Metadata(operation, None, Record.empty, None))
        case Operation.Update | Operation.Delete =>
          if (observations.isEmpty) Consequence.argumentMissing(EntityRevisionTransport.ifMatchHeaderName)
          else if (observations.size != 1) _format_error()
          else _revision(observations.head).flatMap { revision =>
            val writepolicy = if (operation == Operation.Update) EntityWritePolicy.WriteIfChanged else EntityWritePolicy.AlwaysWrite
            EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic, writepolicy,
              RevisionPreconditionPolicy.ObservedRequired, Some(revision)).validateC.map { policy =>
              new Metadata(operation, Some(revision),
                Record.empty.upsertSingle(EntityRevisionTransport.observedRevisionPropertyName, revision.value), Some(policy))
            }
          }
      }
    }
  }
  private def _header_name_valid(name: String): Boolean =
    name != null && name.nonEmpty && name.forall(character =>
      character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' ||
        character >= '0' && character <= '9' || "!#$%&'*+-.^_`|~".contains(character))
  private def _format_error[A](): Consequence[A] =
    Consequence.argumentFormatError("If-Match", "one canonical strong revision tag", "invalid header")
  private def _revision(value: String): Consequence[EntityRevision] = {
    val trimmed = value.dropWhile(character => character == ' ' || character == '\t')
      .reverse.dropWhile(character => character == ' ' || character == '\t').reverse
    if (trimmed != value.trim) _format_error()
    else EntityRevisionTransport.parseEntityTagC(trimmed) match {
      case Consequence.Success(revision) if EntityRevisionTransport.entityTag(revision) == trimmed => Consequence.success(revision)
      case _ => _format_error()
    }
  }
}
