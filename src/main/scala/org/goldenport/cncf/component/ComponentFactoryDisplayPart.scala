package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.display.{DisplayBindingProvider, DisplayRuntime, DisplayService, DisplayServiceBindingProvider, GeneratedLogicalUiAbi, GeneratedLogicalUiMetadataProvider}
import scala.util.control.NonFatal
import org.goldenport.cncf.display.{DisplayMutation, DisplayMutationRuntime, DisplayMutationServiceBindingProvider}
import org.goldenport.cncf.display.{DisplayBusinessOperation, DisplayBusinessOperationBindingProvider}

/*
 * @since   Oct.  3, 2026
 *  version Oct. 15, 2026
 * @version Oct. 16, 2026
 */
private[component] trait ComponentFactoryDisplayPart { self: ComponentFactory =>
  private[component] def _prepare_display_service_c(
    component: Component,
    definitions: Option[GeneratedLogicalUiAbi.AdmittedDefinitions],
    runtime: Option[DisplayRuntime.Registry]
  ): Consequence[Option[Component.DisplayRegistration]] = {
    definitions match {
      case Some(admitted) if admitted.definitions.nonEmpty =>
        val bindings = try {
          val provider = component match {
            case selected: DisplayServiceBindingProvider => Some(selected)
            case _ => component.factory.collect { case selected: DisplayServiceBindingProvider => selected }
          }
          provider match {
            case Some(selected) => Consequence.success(selected.displayServiceBindings(component))
            case None => DisplayService._provider_missing_c[Vector[DisplayService.ResourceBinding]]
          }
        } catch {
          case NonFatal(_) => DisplayService._provider_failed_c[Vector[DisplayService.ResourceBinding]]
        }
        bindings.flatMap { value =>
          for {
            _ <- DisplayService._bind_c(component.componentId, admitted, runtime.orNull, value)
            registrations <- _mutation_bindings_c(component)
            mutations <- registrations match {
              case None => Consequence.success(None)
              case Some(selected) => DisplayMutationRuntime._bind_c(component, admitted, runtime.orNull, value, selected)
                .map(Some(_))
            }
            businessbindings <- _business_bindings_c(component)
            business <- businessbindings match {
              case None => Consequence.success(None)
              case Some(selected) => DisplayBusinessOperation._bind_c(component, admitted, value, selected).map(Some(_))
            }
            service <- DisplayService._bind_c(component.componentId, admitted, runtime.orNull, value, mutations, business)
          } yield service
        }.flatMap(component._prepare_display_registration_c).map(Some(_))
      case _ => Consequence.success(None)
    }
  }

  private def _business_bindings_c(component: Component): Consequence[Option[Vector[DisplayBusinessOperation.Binding]]] = {
    try {
      val provider = component match {
        case selected: DisplayBusinessOperationBindingProvider => Some(selected)
        case _ => component.factory.collect { case selected: DisplayBusinessOperationBindingProvider => selected }
      }
      provider match {
        case None => Consequence.success(None)
        case Some(selected) => Option(selected.displayBusinessOperationBindings(component)) match {
          case Some(value) => Consequence.success(Some(value))
          case None => DisplayBusinessOperation._input_invalid_c("PROVIDER_INVALID", "businessProvider")
        }
      }
    } catch { case NonFatal(_) => DisplayBusinessOperation._input_invalid_c("PROVIDER_INVALID", "businessProvider") }
  }

  private def _mutation_bindings_c(component: Component): Consequence[Option[Vector[DisplayMutationRuntime.Binding]]] = {
    try {
      val provider = component match {
        case selected: DisplayMutationServiceBindingProvider => Some(selected)
        case _ => component.factory.collect { case selected: DisplayMutationServiceBindingProvider => selected }
      }
      provider match {
        case None => Consequence.success(None)
        case Some(selected) => Option(selected.displayMutationBindings(component)) match {
          case Some(value) => Consequence.success(Some(value))
          case None => DisplayMutation.inputInvalidC("CALLBACK_INVALID", "mutationProvider")
        }
      }
    } catch { case NonFatal(_) => DisplayMutation.inputInvalidC("CALLBACK_INVALID", "mutationProvider") }
  }

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
