package org.goldenport.cncf.operation

import org.goldenport.cncf.service.{IntResult, OperationResult, UnitResult}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PredefinedResultDefinition(
  name: String,
  runtimeClassName: String,
  resultFields: Vector[CmlOperationField]
)

final case class PredefinedResultCatalog(
  schemaVersion: String,
  results: Vector[PredefinedResultDefinition]
) {
  private lazy val _result_map: Map[String, PredefinedResultDefinition] = {
    val pairs = results.map(x => x.name -> x)
    require(pairs.map(_._1).distinct.size == pairs.size, "Predefined Result names must be unique.")
    pairs.toMap
  }

  def get(name: String): Option[PredefinedResultDefinition] =
    _result_map.get(name)

  def contains(name: String): Boolean =
    _result_map.contains(name)
}

object PredefinedResultCatalog {
  val default: PredefinedResultCatalog = PredefinedResultCatalog(
    schemaVersion = "cncf.predefined-result.v1",
    results = Vector(
      PredefinedResultDefinition(
        name = "OperationResult",
        runtimeClassName = classOf[OperationResult].getName,
        resultFields = Vector.empty
      ),
      PredefinedResultDefinition(
        name = "UnitResult",
        runtimeClassName = classOf[UnitResult].getName,
        resultFields = Vector.empty
      ),
      PredefinedResultDefinition(
        name = "IntResult",
        runtimeClassName = classOf[IntResult].getName,
        resultFields = Vector(CmlOperationField("value", "int"))
      )
    )
  )
}
