package org.goldenport.cncf.protocol

import org.goldenport.model.value.BaseContent
import org.goldenport.protocol.spec.ParameterDefinition

// TODO
/**
 * Declarative catalog of runtime-scoped parameters defined by GlobalProtocol.
 *
 * This is a lightweight wrapper around core ParameterDefinition instances.
 * Parsing / ingestion is delegated to protocol.ingress helpers via TODOs.
 */
/*
 * @since   Jan. 22, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object GlobalParameterGroup {
  private val BASE_URL = ParameterDefinition(
    content = BaseContent.simple("baseurl"),
    kind = ParameterDefinition.Kind.Property
  )

  // TODO add other runtime parameters (log_level, log_backend, http_driver, env) as ParameterDefinition instances.

  val runtimeParameters: Vector[ParameterDefinition] = Vector(BASE_URL)

  /** Names -> definitions map for parser helpers. */
  val runtimeNameMap: Map[String, ParameterDefinition] =
    runtimeParameters.flatMap { param =>
      param.names.map(_ -> param)
    }.toMap

  def containsRuntimeName(name: String): Boolean =
    runtimeNameMap.contains(name)
}
