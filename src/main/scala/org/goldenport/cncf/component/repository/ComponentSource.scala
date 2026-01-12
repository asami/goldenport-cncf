package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.{Component, ComponentDefinition}

sealed trait ComponentSource {
  def origin: String
}

object ComponentSource {
  final case class Definition(
    definition: ComponentDefinition,
    origin: String
  ) extends ComponentSource

  final case class ClassDef(
    componentClass: Class[_ <: Component],
    origin: String
  ) extends ComponentSource
}
