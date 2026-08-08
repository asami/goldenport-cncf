package org.goldenport.cncf.component.builtin

import org.goldenport.cncf.component.ComponentId

/*
 * Canonical runtime identities for framework-provided components.
 *
 * @since Aug. 8, 2026
 * @version Aug. 8, 2026
 * @author ASAMI, Tomoharu
 */

/** Canonical runtime identities for framework-provided components. */
object BuiltinComponentIdentity {
  private val _namespace = "org.goldenport.cncf"

  val ADMIN: ComponentId = ComponentId(s"${_namespace}.Admin")
  val AUTH: ComponentId = ComponentId(s"${_namespace}.Auth")
  val BLOB: ComponentId = ComponentId(s"${_namespace}.Blob")
  val CLIENT: ComponentId = ComponentId(s"${_namespace}.Client")
  val DEBUG: ComponentId = ComponentId(s"${_namespace}.Debug")
  val EVENT: ComponentId = ComponentId(s"${_namespace}.Event")
  val JOB_CONTROL: ComponentId = ComponentId(s"${_namespace}.JobControl")
  val MESSAGE_DELIVERY_STUB: ComponentId = ComponentId(s"${_namespace}.MessageDeliveryStub")
  val METRICS: ComponentId = ComponentId(s"${_namespace}.Metrics")
  val SPECIFICATION: ComponentId = ComponentId(s"${_namespace}.Specification")
  val TAG: ComponentId = ComponentId(s"${_namespace}.Tag")
  val TOOL: ComponentId = ComponentId(s"${_namespace}.Tool")
  val WORKFLOW: ComponentId = ComponentId(s"${_namespace}.Workflow")
}
