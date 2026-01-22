package org.goldenport.cncf.protocol.spec

import org.goldenport.model.value.BaseContent
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.spec.OperationDefinition as CoreOperationDefinition

/**
 * CNCF-specific OperationDefinition.
 *
 * Phase 2.85:
 * - attributes are defined but intentionally unused.
 */
/*
 * @since   Jan. 22, 2026
 * @version Jan. 22, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class OperationDefinition extends CoreOperationDefinition {
  override def specification: OperationDefinition.Specification
}

object OperationDefinition {
  /**
   * CNCF-level operation attributes.
   *
   * NOTE:
   * These attributes are defined but not yet consumed by
   * OpenAPI, REST projection, or runtime scheduling.
   */
  sealed trait OperationAttribute
  object OperationAttribute {
    // CQRS / semantics
    case object Command extends OperationAttribute
    case object Query extends OperationAttribute

    // semantics
    case object Idempotent extends OperationAttribute

    // operational
    case object HighLoad extends OperationAttribute
  }

  /**
   * CNCF extension of core Specification.
   *
   * This is value-backed and structurally symmetric with
   * org.goldenport.protocol.spec.OperationDefinition.Specification.
   */
  class Specification(
    content: BaseContent,
    request: RequestDefinition,
    response: ResponseDefinition,
    attributes: Set[OperationAttribute] = Set.empty
  ) extends CoreOperationDefinition.Specification(
        content = content,
        request = request,
        response = response
      )
}
