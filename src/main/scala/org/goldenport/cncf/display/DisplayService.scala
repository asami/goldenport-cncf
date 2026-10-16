package org.goldenport.cncf.display

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.directive.Query
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, OperationDefinitionGroup, ParameterDefinition, RequestDefinition, ResponseDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct.  5, 2026
 * @version Oct. 15, 2026
 */
/** Complete admitted bindings used only by ordinary component Operations. */
final class DisplayService private[DisplayService] (
  private val _component_id: ComponentId,
  private val _runtime: DisplayRuntime.Registry,
  private val _entries: Vector[DisplayService.Entry],
  private val _mutations: Option[DisplayMutationRuntimeEntry.Registry]
) {
  import DisplayService.*

  private[cncf] val _service_definition: ServiceDefinition = ServiceDefinition(
    name = "display",
    operations = OperationDefinitionGroup(NonEmptyVector.of[OperationDefinition](
      new BoundOperation("query"), new BoundOperation("read")
    ).concat(_mutations.toVector.flatMap(registry => DisplayMutationIngress.operations(_component_id, registry))))
  )

  private final class BoundOperation(opname: String) extends OperationDefinition {
    private val _operation_name = opname
    private val _names = Vector("resourceType", "screenId") ++
      (if (opname == "read") Vector("resourceId") else Vector.empty)

    val specification: OperationDefinition.Specification = OperationDefinition.Specification(
      name = opname,
      request = RequestDefinition(_names.map { name =>
        ParameterDefinition(content = BaseContent.simple(name), kind = ParameterDefinition.Kind.Property,
          domain = ValueDomain(XString, Multiplicity.One))
      }.toList),
      response = ResponseDefinition(result = List(DataType.Named("OperationResponse")))
    )

    def createOperationRequest(req: Request): Consequence[OperationRequest] = {
      for {
        _ <- _request_shape_c(req, _component_id.name, _operation_name, _names)
        resourcetype <- _property_c(req, "resourceType")
        screenid <- _property_c(req, "screenId")
        entry <- _entries.find(_.definition.screenId == screenid)
          .map(Consequence.success).getOrElse(_failure("SCREEN_UNDECLARED", "request.screenId"))
        _ <- if (entry.source.resourceType == resourcetype) Consequence.unit
          else _failure[Unit]("RESOURCE_MISMATCH", "request.resourceType")
        _ <- (entry.definition.screen, _operation_name) match {
          case (_: GeneratedLogicalUiAbi.ListDefinition, "query") => Consequence.unit
          case (_: GeneratedLogicalUiAbi.DetailDefinition, "read") => Consequence.unit
          case _ => _failure[Unit]("OPERATION_UNSUPPORTED", "request.screenId")
        }
        resourceid <- if (_operation_name == "read") {
          _property_c(req, "resourceId").flatMap { text =>
            EntityId.parse(text).toOption.filter(_.value == text)
              .map(id => Consequence.success(Some(id)))
              .getOrElse(_failure[Option[EntityId]]("ID_INVALID", "request.resourceId"))
          }
        } else Consequence.success(None)
      } yield new ReadAction(req, entry, resourceid)
    }
  }

  private final class ReadAction(
    val request: Request,
    private val _entry: Entry,
    private val _resource_id: Option[EntityId]
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = new ReadCall(core, _entry, _resource_id)
  }

  private final class ReadCall(
    val core: ActionCall.Core,
    private val _entry: Entry,
    private val _resource_id: Option[EntityId]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      bind_component_application_datastore_c().flatMap { _ =>
        val display = _resource_id match {
          case Some(id) => _runtime.readC(_entry.definition.screenId, id)(using executionContext)
          case None => _runtime.queryC(_entry.definition.screenId, Query.fromRecord(Record.empty))(using executionContext)
        }
        display.flatMap(value => DisplayProtocolCodec.encodeJsonC(_entry.source, value))
          .map(OperationResponse.Json.apply)
      }
    }
  }
}

object DisplayService {
  final case class ResourceBinding(resourceType: String, screenId: String)
  final case class Diagnostic(code: String, path: String, reason: String)
  /** Structured cause carrier; never thrown for admission or request validation. */
  final class ServiceException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  private final case class Entry(definition: GeneratedLogicalUiAbi.Definition, source: DisplayProtocol.Source)

  private[cncf] def _bind_c(componentid: ComponentId,
    definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    runtime: DisplayRuntime.Registry, bindings: Vector[ResourceBinding]): Consequence[DisplayService] = {
    _bind_c(componentid, definitions, runtime, bindings, None)
  }

  private[cncf] def _bind_c(componentid: ComponentId,
    definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    runtime: DisplayRuntime.Registry, bindings: Vector[ResourceBinding],
    mutations: Option[DisplayMutationRuntimeEntry.Registry]): Consequence[DisplayService] = {
    val problem = if (componentid == null) _error("ELEMENT_INVALID", "componentId")
      else if (definitions == null) _error("ELEMENT_INVALID", "definitions")
      else if (runtime == null) _error("ELEMENT_INVALID", "runtime")
      else if (bindings == null) _error("ELEMENT_INVALID", "bindings")
      else {
        bindings.iterator.zipWithIndex.flatMap { case (binding, index) =>
          val path = s"bindings[$index]"
          if (binding == null) _error("ELEMENT_INVALID", path)
          else _id(binding.screenId, path + ".screenId")
            .orElse(_id(binding.resourceType, path + ".resourceType"))
            .orElse {
              if (bindings.take(index).exists(_.screenId == binding.screenId))
                _error("SCREEN_DUPLICATE", path + ".screenId")
              else if (!definitions.definitions.exists(_.screenId == binding.screenId))
                _error("SCREEN_UNDECLARED", path + ".screenId")
              else None
            }
        }.nextOption().orElse {
          definitions.definitions.zipWithIndex.find { case (definition, _) =>
            !bindings.exists(_.screenId == definition.screenId)
          }.map { case (_, index) => _diagnostic("SCREEN_MISSING", s"definitions[$index].screenId") }
        }.orElse {
          definitions.definitions.zipWithIndex.flatMap { case (definition, index) =>
            definition.screen match {
              case list: GeneratedLogicalUiAbi.ListDefinition =>
                val own = bindings.find(_.screenId == definition.screenId).map(_.resourceType)
                val target = bindings.find(_.screenId == list.detailTarget.screenId).map(_.resourceType)
                if (own == target) None else _error("RESOURCE_MISMATCH", s"definitions[$index].screen.detailTarget.screenId")
              case _: GeneratedLogicalUiAbi.DetailDefinition => None
            }
          }.headOption
        }.orElse {
          if (runtime.screenIds == definitions.definitions.map(_.screenId) && definitions.definitions.nonEmpty) None
          else _error("ELEMENT_INVALID", "runtime")
        }
      }
    problem match {
      case Some(diagnostic) => _diagnostic_failure(diagnostic)
      case None => definitions.definitions.foldLeft(Consequence.success(Vector.empty[Entry])) { (result, definition) =>
        result.flatMap { entries =>
          bindings.find(_.screenId == definition.screenId) match {
            case Some(binding) => DisplayProtocol.Source.createC(componentid, binding.resourceType)
              .map(source => entries :+ Entry(definition, source))
            case None => _failure("SCREEN_MISSING", "bindings")
          }
        }
      }.map(entries => new DisplayService(componentid, runtime, entries, mutations))
    }
  }

  private[cncf] def _provider_missing_c[A]: Consequence[A] = _failure("PROVIDER_MISSING", "bindings")
  private[cncf] def _provider_failed_c[A]: Consequence[A] = _failure("PROVIDER_FAILED", "bindings")
  private[cncf] def _service_collision_c[A]: Consequence[A] = _failure("SERVICE_COLLISION", "service")

  private def _request_shape_c(req: Request, componentname: String, opname: String,
    names: Vector[String]): Consequence[Unit] = {
    if (req == null) _failure("INPUT_INVALID", "request")
    else if (req.component != Some(componentname)) _failure("INPUT_INVALID", "request.component")
    else if (req.service != Some("display")) _failure("INPUT_INVALID", "request.service")
    else if (req.operation != opname) _failure("INPUT_INVALID", "request.operation")
    else if (req.arguments == null || req.arguments.nonEmpty) _failure("INPUT_INVALID", "request.arguments")
    else if (req.switches == null || req.switches.nonEmpty) _failure("INPUT_INVALID", "request.switches")
    else if (req.properties == null) _failure("INPUT_INVALID", "request.properties")
    else if (req.properties.exists(property => property == null || property.name == null ||
      (!names.contains(property.name) && !_transport_property(property.name))))
      _failure("INPUT_INVALID", "request.properties")
    else Consequence.unit
  }

  private def _property_c(req: Request, name: String): Consequence[String] = {
    req.properties.filter(_.name == name) match {
      case property :: Nil => property.value match {
        case text: String => _id(text, "request." + name) match {
          case Some(diagnostic) => _diagnostic_failure(diagnostic)
          case None => Consequence.success(text)
        }
        case _ => _failure("INPUT_INVALID", "request." + name)
      }
      case _ => _failure("INPUT_INVALID", "request." + name)
    }
  }

  private def _transport_property(name: String): Boolean = {
    val lower = name.toLowerCase(java.util.Locale.ROOT)
    name.startsWith("textus.") || name.startsWith("cncf.") ||
      lower == "x-textus-session" || lower.startsWith("x-textus-debug-") ||
      name.startsWith("security.") || name.startsWith("crud.") ||
      Set("principalId", "principal_id", "subjectId", "subject_id", "privilege", "capability", "capabilities", "role", "roles").contains(name)
  }
  private def _id(value: String, path: String): Option[Diagnostic] = {
    if (value != null && value.nonEmpty && value == value.trim) None else _error("ID_INVALID", path)
  }
  private def _diagnostic(code: String, path: String): Diagnostic = {
    val reason = code match {
      case "ELEMENT_INVALID" => "expected complete nonnull admitted service input"
      case "ID_INVALID" => "expected nonempty exactly trimmed canonical identity"
      case "PROVIDER_MISSING" => "declared screens require a service binding provider"
      case "PROVIDER_FAILED" => "service binding provider did not return normally"
      case "SCREEN_MISSING" => "declared screen requires a resource binding"
      case "SCREEN_UNDECLARED" => "screen must belong to this component registration"
      case "SCREEN_DUPLICATE" => "screen requires exactly one resource binding"
      case "RESOURCE_MISMATCH" => "resource type must match the admitted screen and detail target"
      case "SERVICE_COLLISION" => "display service must not collide with an existing service"
      case "INPUT_INVALID" => "expected exact bound selector and closed single String properties"
      case "OPERATION_UNSUPPORTED" => "operation requires the matching screen branch"
    }
    Diagnostic("DSP96_SERVICE_" + code, path, reason)
  }
  private def _error(code: String, path: String): Option[Diagnostic] = Some(_diagnostic(code, path))
  private def _failure[A](code: String, path: String): Consequence[A] = _diagnostic_failure(_diagnostic(code, path))
  private def _diagnostic_failure[A](diagnostic: Diagnostic): Consequence[A] = {
    Consequence.fail(Taxonomy.argumentInvalid, new ServiceException(diagnostic))
  }
}
