package org.goldenport.cncf.operation

import scala.util.Try
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.schema.{DataConfidentiality, DataType, Schema}

/*
 * @since   May.  8, 2026
 * @version May.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationConfidentiality {
  def request(
    component: Component,
    operationName: String
  ): Map[String, DataConfidentiality] =
    _cml_operation(component, operationName)
      .map(_.parameters.map(p => p.name -> p.effectiveConfidentiality).toMap)
      .getOrElse(Map.empty)

  def response(
    component: Component,
    operationName: String,
    operation: Option[OperationDefinition] = None
  ): Map[String, DataConfidentiality] = {
    val cml = _cml_operation(component, operationName)
    val cmlFields =
      cml.toVector.flatMap(_.resultFields).map(p => p.name -> p.effectiveConfidentiality)
    val cmlOutputTypes =
      cml.toVector.flatMap(op => _type_name_candidates(op.outputType))
    val protocolOutputTypes =
      operation.toVector.flatMap(_.specification.response.result).flatMap(_type_name_candidates)
    val schemaFields =
      (cmlOutputTypes ++ protocolOutputTypes)
        .distinct
        .flatMap(_generated_schema(component, _).toVector)
        .flatMap(_.columns)
        .map(column => column.name.value -> _effective_confidentiality(column.confidentiality, column.web.confidentiality))
    (schemaFields ++ cmlFields).toMap
  }

  def combined(
    component: Component,
    operationName: String,
    operation: Option[OperationDefinition] = None
  ): Map[String, DataConfidentiality] =
    request(component, operationName) ++ response(component, operationName, operation)

  private def _cml_operation(
    component: Component,
    operationName: String
  ): Option[CmlOperationDefinition] =
    component.operationDefinitions.find(x => _same_name(x.name, operationName))

  private def _same_name(lhs: String, rhs: String): Boolean =
    NamingConventions.equivalentByNormalized(lhs, rhs)

  private def _effective_confidentiality(
    primary: DataConfidentiality,
    secondary: DataConfidentiality
  ): DataConfidentiality =
    if (primary != DataConfidentiality.Public) primary else secondary

  private def _type_name_candidates(
    datatype: DataType
  ): Vector[String] =
    _type_name_candidates(datatype.name)

  private def _type_name_candidates(
    value: String
  ): Vector[String] = {
    val raw = Option(value).getOrElse("").trim
    val unwrapped =
      raw match {
        case s if s.startsWith("Option[") && s.endsWith("]") => s.drop("Option[".length).dropRight(1)
        case s if s.startsWith("List[") && s.endsWith("]") => s.drop("List[".length).dropRight(1)
        case s if s.startsWith("Vector[") && s.endsWith("]") => s.drop("Vector[".length).dropRight(1)
        case s if s.startsWith("Seq[") && s.endsWith("]") => s.drop("Seq[".length).dropRight(1)
        case s => s
      }
    Vector(raw, unwrapped).map(_.trim).filter(_.nonEmpty).distinct
  }

  private def _generated_schema(
    component: Component,
    typeName: String
  ): Option[Schema] =
    _generated_module(component, typeName).flatMap(_extract_schema)

  private def _generated_module(
    component: Component,
    typeName: String
  ): Option[AnyRef] = {
    val packageNames = _generated_module_package_names(component)
    val className = _class_name(typeName)
    val candidates = packageNames.flatMap { pkg =>
      Vector(
        s"${pkg}.${className}$$",
        s"${pkg}.operation.${className}$$",
        s"${pkg}.model.${className}$$",
        s"${pkg}.value.${className}$$",
        s"${pkg}.entity.${className}$$",
        s"${pkg}.entity.aggregate.${className}$$",
        s"${pkg}.entity.operation.${className}$$",
        s"${pkg}.entity.read.${className}$$",
        s"${pkg}.entity.view.${className}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private def _generated_module_package_names(
    component: Component
  ): Vector[String] =
    Vector(
      Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty),
      Some("org.goldenport.cncf.component")
    ).flatten.distinct

  private def _load_scala_module(
    loader: ClassLoader,
    className: String
  ): Option[AnyRef] = {
    val loaders = Vector(
      Option(loader),
      Option(Thread.currentThread.getContextClassLoader)
    ).flatten.distinct
    loaders.iterator.flatMap { cl =>
      try {
        val cls = Class.forName(className, true, cl)
        val field = cls.getField("MODULE$")
        Option(field.get(null).asInstanceOf[AnyRef])
      } catch {
        case _: Throwable => None
      }
    }.toSeq.headOption
  }

  private def _extract_schema(
    module: AnyRef
  ): Option[Schema] =
    Try(module.getClass.getMethod("schema").invoke(module)).toOption.collect {
      case schema: Schema => schema
    }

  private def _class_name(
    typeName: String
  ): String =
    NamingConventions.toNormalizedSegment(typeName)
      .split("-")
      .toVector
      .filter(_.nonEmpty)
      .map(s => s"${s.head.toUpper}${s.drop(1)}")
      .mkString
}
