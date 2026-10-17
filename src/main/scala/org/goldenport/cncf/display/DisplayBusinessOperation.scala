package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId}
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition}
import scala.util.control.NonFatal

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** Candidate values are descriptive data; only admission constructs a Registry. */
object DisplayBusinessOperation {
  enum ActionScope(val tag: String) {
    case Screen extends ActionScope("screen")
    case Item extends ActionScope("item")
  }
  final case class Binding(screenId: String, scope: ActionScope, actionId: String,
    serviceName: String, operationName: String)
  final case class OperationReference(componentId: ComponentId, service: String, operation: String)
  final case class InputParameter(name: String, kind: ParameterDefinition.Kind, datatype: String, multiplicity: String)
  final case class ActionDescriptor(scope: ActionScope, id: String, label: String, bindingId: String,
    target: OperationReference, parameters: Vector[InputParameter])
  final case class Descriptor(source: DisplayProtocol.Source, definition: GeneratedLogicalUiAbi.Definition,
    actions: Vector[ActionDescriptor])
  final case class Diagnostic(code: String, path: String, reason: String)
  final class DescriptorException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  final class Registry private[DisplayBusinessOperation](private val _descriptors: Vector[Descriptor]) {
    def nonEmpty: Boolean = _descriptors.exists(_.actions.nonEmpty)
    def describeC(resourceType: String, screenId: String): Consequence[Descriptor] = {
      if (!_id_valid(resourceType)) _input_invalid_c("ID_INVALID", "request.resourceType")
      else if (!_id_valid(screenId)) _input_invalid_c("ID_INVALID", "request.screenId")
      else _descriptors.find(_.definition.screenId == screenId) match {
        case Some(value) if value.source.resourceType == resourceType => Consequence.success(value)
        case Some(_) => _input_invalid_c("SELECTION_MISMATCH", "request.resourceType")
        case None => _input_invalid_c("SCREEN_UNDECLARED", "request.screenId")
      }
    }
  }

  private[cncf] def _bind_c(component: Component,
    admitteddefinitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    resourcebindings: Vector[DisplayService.ResourceBinding], bindings: Vector[Binding]): Consequence[Registry] = {
    try {
      if (component == null || component.componentId == null || admitteddefinitions == null ||
          resourcebindings == null || bindings == null || component.protocol == null || component.protocol.services == null)
        _input_invalid_c("ELEMENT_INVALID", "registration")
      else for {
        _ <- _resources_c(admitteddefinitions, resourcebindings)
        selected <- bindings.zipWithIndex.foldLeft(Consequence.success(Vector.empty[(Binding, ActionDescriptor)])) {
          case (result, (binding, index)) => result.flatMap { previous =>
            val path = s"bindings[$index]"
            for {
              _ <- _binding_shape_c(binding, path)
              _ <- if (previous.exists { case (prior, _) => _locator(prior) == _locator(binding) })
                _input_invalid_c[Unit]("BINDING_DUPLICATE", path) else Consequence.unit
              declaration <- _declaration_c(admitteddefinitions, binding, path)
              target <- _target_c(component, binding, path)
              parameters <- _parameters_c(target, path + ".input")
              reference = OperationReference(component.componentId, binding.serviceName, target.name)
              _ <- if (previous.exists { case (_, action) => action.bindingId == declaration.bindingId && action.target != reference })
                _input_invalid_c[Unit]("TARGET_CONFLICT", path) else Consequence.unit
            } yield previous :+ (binding -> ActionDescriptor(binding.scope, declaration.id, declaration.label,
              declaration.bindingId, reference, parameters))
          }
        }
        descriptors <- admitteddefinitions.definitions.foldLeft(Consequence.success(Vector.empty[Descriptor])) { (result, definition) =>
          result.flatMap { values =>
            val resource = resourcebindings.find(_.screenId == definition.screenId).get
            DisplayProtocol.Source.createC(component.componentId, resource.resourceType).map { source =>
              val actions = _declarations(definition).flatMap { case (scope, declaration) =>
                selected.collectFirst { case (binding, action) if binding.screenId == definition.screenId &&
                  binding.scope == scope && binding.actionId == declaration.id => action }
              }
              values :+ Descriptor(source, definition, actions)
            }
          }
        }
      } yield new Registry(descriptors)
    } catch { case NonFatal(_) => _input_invalid_c("ELEMENT_INVALID", "registration") }
  }

  private def _resources_c(definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    resources: Vector[DisplayService.ResourceBinding]): Consequence[Unit] = {
    if (resources.exists(value => value == null || !_id_valid(value.screenId) || !_id_valid(value.resourceType)))
      _input_invalid_c("ELEMENT_INVALID", "resourceBindings")
    else if (resources.map(_.screenId).distinct.size != resources.size)
      _input_invalid_c("BINDING_DUPLICATE", "resourceBindings")
    else if (resources.map(_.screenId).toSet != definitions.definitions.map(_.screenId).toSet)
      _input_invalid_c("SELECTION_MISMATCH", "resourceBindings")
    else if (definitions.definitions.exists { definition => definition.screen match {
      case list: GeneratedLogicalUiAbi.ListDefinition =>
        resources.find(_.screenId == definition.screenId).get.resourceType !=
          resources.find(_.screenId == list.detailTarget.screenId).get.resourceType
      case _: GeneratedLogicalUiAbi.DetailDefinition => false
    } }) _input_invalid_c("SELECTION_MISMATCH", "resourceBindings")
    else Consequence.unit
  }
  private def _binding_shape_c(binding: Binding, path: String): Consequence[Unit] = {
    if (binding == null || binding.scope == null) _input_invalid_c("ELEMENT_INVALID", path)
    else if (!_id_valid(binding.screenId) || !_id_valid(binding.actionId)) _input_invalid_c("ID_INVALID", path)
    else if (!_selector_valid(binding.serviceName) || !_selector_valid(binding.operationName))
      _input_invalid_c("TARGET_INVALID", path + ".target")
    else Consequence.unit
  }
  private def _locator(binding: Binding): (String, ActionScope, String) =
    (binding.screenId, binding.scope, binding.actionId)
  private def _declarations(definition: GeneratedLogicalUiAbi.Definition): Vector[(ActionScope, GeneratedLogicalUiAbi.ActionDefinition)] =
    definition.screen match {
      case list: GeneratedLogicalUiAbi.ListDefinition =>
        list.actions.map(ActionScope.Screen -> _) ++ list.itemActions.map(ActionScope.Item -> _)
      case detail: GeneratedLogicalUiAbi.DetailDefinition => detail.actions.map(ActionScope.Screen -> _)
    }
  private def _declaration_c(definitions: GeneratedLogicalUiAbi.AdmittedDefinitions, binding: Binding,
    path: String): Consequence[GeneratedLogicalUiAbi.ActionDefinition] = {
    definitions.definitions.find(_.screenId == binding.screenId) match {
      case None => _input_invalid_c("SCREEN_UNDECLARED", path + ".screenId")
      case Some(definition) => _declarations(definition).collect {
        case (scope, action) if scope == binding.scope && action.id == binding.actionId => action
      } match {
        case Vector(action) => Consequence.success(action)
        case _ => _input_invalid_c("ACTION_UNDECLARED", path + ".actionId")
      }
    }
  }
  private def _target_c(component: Component, binding: Binding, path: String): Consequence[OperationDefinition] = {
    if (binding.serviceName == "display") _input_invalid_c("TARGET_INVALID", path + ".target")
    else component.protocol.services.services.filter(_.name == binding.serviceName) match {
      case Vector(service) => service.operations.operations.toVector.filter(_.name == binding.operationName) match {
        case Vector(operation) => Consequence.success(operation)
        case _ => _input_invalid_c("TARGET_INVALID", path + ".target.operation")
      }
      case _ => _input_invalid_c("TARGET_INVALID", path + ".target.service")
    }
  }
  private def _parameters_c(operation: OperationDefinition, path: String): Consequence[Vector[InputParameter]] = {
    val specification = operation.specification
    if (specification == null || specification.request == null || specification.request.parameters == null)
      _input_invalid_c("PARAMETER_INVALID", path)
    else specification.request.parameters.zipWithIndex.foldLeft(Consequence.success(Vector.empty[InputParameter])) {
      case (result, (parameter, index)) => result.flatMap { previous =>
        val entrypath = s"$path[$index]"
        if (parameter == null || parameter.content == null || parameter.kind == null || parameter.domain == null ||
            parameter.datatype == null || parameter.multiplicity == null) _input_invalid_c("PARAMETER_INVALID", entrypath)
        else if (!_id_valid(parameter.name) || !_id_valid(parameter.datatype.name) || parameter.multiplicity.mark == null)
          _input_invalid_c("PARAMETER_INVALID", entrypath)
        else if (previous.exists(_.name == parameter.name)) _input_invalid_c("PARAMETER_INVALID", entrypath + ".name")
        else Consequence.success(previous :+ InputParameter(parameter.name, parameter.kind,
          parameter.datatype.name, parameter.multiplicity.mark))
      }
    }
  }
  private[display] def _id_valid(value: String): Boolean = value != null && value.nonEmpty && value == value.trim
  private def _selector_valid(value: String): Boolean =
    _id_valid(value) && !value.exists(char => ":/\\?#".contains(char))

  private[cncf] def _input_invalid_c[A](code: String, path: String): Consequence[A] = {
    val reason = code match {
      case "ID_INVALID" => "expected nonempty exactly trimmed identity"
      case "BINDING_DUPLICATE" => "expected exactly one binding per declared locator"
      case "SCREEN_UNDECLARED" => "screen must belong to this registration"
      case "ACTION_UNDECLARED" => "action must belong to the exact declared scope"
      case "TARGET_INVALID" => "expected one exact same-component non-display Operation"
      case "TARGET_CONFLICT" => "one application binding identity requires one canonical target"
      case "PARAMETER_INVALID" => "expected complete unique canonical request parameter metadata"
      case "PROVIDER_INVALID" => "selected provider must return nonnull bindings normally"
      case "PROTOCOL_INVALID" => "expected the exact supported descriptor schema and version"
      case "SELECTION_MISMATCH" => "descriptor must correspond to the immutable admitted registration"
      case _ => "expected complete closed nonnull descriptor input"
    }
    Consequence.fail(Taxonomy.argumentInvalid, new DescriptorException(Diagnostic("DSP96_BUSINESS_" + code, path, reason)))
  }
}
