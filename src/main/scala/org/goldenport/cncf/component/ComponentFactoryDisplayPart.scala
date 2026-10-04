package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.display.{DisplayBindingProvider, DisplayRuntime, GeneratedLogicalUiAbi, GeneratedLogicalUiMetadataProvider}
import scala.util.control.NonFatal

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
private[component] trait ComponentFactoryDisplayPart { self: ComponentFactory =>
  private[component] def _bind_display_runtime_c(
    component: Component,
    definitions: Option[GeneratedLogicalUiAbi.AdmittedDefinitions]
  ): Consequence[Option[DisplayRuntime.Registry]] = {
    definitions match {
      case None => Consequence.success(None)
      case Some(admitted) =>
        val bindings = try {
          val provider = component match {
            case selected: DisplayBindingProvider => Some(selected)
            case _ => component.factory.collect { case selected: DisplayBindingProvider => selected }
          }
          provider match {
            case Some(selected) => Consequence.success(selected.displayBindings(component))
            case None => DisplayRuntime.providerMissingC[Vector[DisplayRuntime.Binding]]
          }
        } catch {
          case NonFatal(_) => DisplayRuntime.providerFailedC[Vector[DisplayRuntime.Binding]]
        }
        bindings.flatMap(value => DisplayRuntime.bindC(admitted, component.viewSpace, value)).map(Some(_))
    }
  }

  private[component] def _admit_generated_logical_ui_metadata_c(
    component: Component
  ): Consequence[Option[GeneratedLogicalUiAbi.AdmittedDefinitions]] = {
    try {
      val provider = component match {
        case selected: GeneratedLogicalUiMetadataProvider => Some(selected)
        case _ => component.factory.collect {
          case selected: GeneratedLogicalUiMetadataProvider => selected
        }
      }
      provider match {
        case Some(selected) =>
          GeneratedLogicalUiAbi.admitC(selected.generatedLogicalUiDefinitions).map(Some(_))
        case None =>
          Consequence.success(None)
      }
    } catch {
      case NonFatal(e) => Consequence.componentInvalid(e)
    }
  }
}
