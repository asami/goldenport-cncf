package org.goldenport.cncf.component

import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor

/*
 * Static descriptor for a component as packaged in CAR or provided through a
 * CAR-style local override.
 *
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentDescriptor(
  componentName: Option[String] = None,
  entityRuntimeDescriptors: Vector[EntityRuntimeDescriptor] = Vector.empty
)
