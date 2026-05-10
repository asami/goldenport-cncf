package org.goldenport.cncf.composite

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record

/*
 * @since   May. 10, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CompositeQueryRequest(
  queries: Vector[NamedQuery],
  policy: CompositeQueryPolicy = CompositeQueryPolicy()
) {
  def validate: Consequence[CompositeQueryRequest] = {
    val names = queries.map(_.name)
    val duplicate = names.groupBy(identity).collectFirst {
      case (name, xs) if xs.size > 1 => name
    }
    duplicate match {
      case Some(name) =>
        Consequence.argumentInvalid(s"CompositeQuery duplicate name: ${name}")
      case None =>
        val known = names.toSet
        val missing = queries.iterator.flatMap { query =>
          query.dependsOn.filterNot(known).map(dep => query.name -> dep)
        }.toVector
        missing.headOption match {
          case Some((name, dep)) =>
            Consequence.argumentInvalid(s"CompositeQuery '${name}' depends on unknown query '${dep}'")
          case None =>
            queries.find(_is_trace_job_query) match {
              case Some(query) =>
                Consequence.operationInvalid(s"CompositeQuery '${query.name}' cannot use trace-job execution")
              case None =>
                Consequence.success(this)
            }
        }
    }
  }

  private def _is_trace_job_query(query: NamedQuery): Boolean = {
    val keys = Set(
      RuntimeConfig.DebugTraceJobKey,
      RuntimeConfig.RuntimeDebugTraceJobKey,
      "cncf.debug.trace-job",
      "cncf.runtime.debug.trace-job",
      "x-textus-debug-trace-job"
    ).map(_.toLowerCase(java.util.Locale.ROOT))
    query.request.properties.exists { property =>
      keys.contains(property.name.toLowerCase(java.util.Locale.ROOT)) &&
        _is_truthy(property.value.toString)
    }
  }

  private def _is_truthy(value: String): Boolean =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => true
      case _ => false
    }
}

final case class NamedQuery(
  name: String,
  request: Request,
  required: Boolean = true,
  dependsOn: Vector[String] = Vector.empty
)

final case class CompositeQueryPolicy()

final case class CompositeQueryResponse(
  results: Vector[CompositeQueryResult],
  diagnostics: Vector[CompositeQueryDiagnostic] = Vector.empty
) {
  lazy val resultMap: Map[String, CompositeQueryResult] =
    results.map(x => x.name -> x).toMap

  def result(name: String): Option[CompositeQueryResult] =
    resultMap.get(name)

  def requiredRecord(name: String): Consequence[Record] =
    result(name) match {
      case Some(CompositeQueryResult(_, _, Some(OperationResponse.RecordResponse(record)), None)) =>
        Consequence.success(record)
      case Some(CompositeQueryResult(_, _, Some(response), None)) =>
        Consequence.operationInvalid(s"CompositeQuery result '${name}' is not RecordResponse: ${response.getClass.getSimpleName}")
      case Some(CompositeQueryResult(_, _, _, Some(diagnostic))) =>
        Consequence.operationInvalid(s"CompositeQuery result '${name}' failed: ${diagnostic.message}")
      case Some(_) =>
        Consequence.operationInvalid(s"CompositeQuery result '${name}' has no response")
      case None =>
        Consequence.operationInvalid(s"CompositeQuery result '${name}' not found")
    }

  def optionalRecord(name: String): Option[Record] =
    result(name).flatMap {
      case CompositeQueryResult(_, _, Some(OperationResponse.RecordResponse(record)), None) =>
        Some(record)
      case _ =>
        None
    }
}

final case class CompositeQueryResult(
  name: String,
  required: Boolean,
  response: Option[OperationResponse],
  diagnostic: Option[CompositeQueryDiagnostic] = None
) {
  def isSuccess: Boolean = diagnostic.isEmpty
}

final case class CompositeQueryDiagnostic(
  name: String,
  required: Boolean,
  message: String
)

final class CompositeQueryEngine(
  subsystem: Subsystem
) {
  def execute(
    request: CompositeQueryRequest
  )(using ExecutionContext): Consequence[CompositeQueryResponse] =
    request.validate.flatMap(_execute)

  private def _execute(
    request: CompositeQueryRequest
  )(using ExecutionContext): Consequence[CompositeQueryResponse] = {
    val seed = Consequence.success(CompositeQueryResponse(Vector.empty))
    request.queries.foldLeft(seed) { (z, named) =>
      z.flatMap { response =>
        subsystem.executeQueryOnlyWithMetadata(named.request) match {
          case Consequence.Success(result) =>
            Consequence.success(response.copy(
              results = response.results :+ CompositeQueryResult(
                named.name,
                named.required,
                Some(result.response)
              )
            ))
          case Consequence.Failure(conclusion) if named.required =>
            Consequence.Failure(conclusion)
          case Consequence.Failure(conclusion) =>
            val diagnostic = CompositeQueryDiagnostic(
              named.name,
              named.required,
              conclusion.toString
            )
            Consequence.success(response.copy(
              results = response.results :+ CompositeQueryResult(
                named.name,
                named.required,
                None,
                Some(diagnostic)
              ),
              diagnostics = response.diagnostics :+ diagnostic
            ))
        }
      }
    }
  }
}

object CompositeQueryEngine {
  def apply(subsystem: Subsystem): CompositeQueryEngine =
    new CompositeQueryEngine(subsystem)
}
