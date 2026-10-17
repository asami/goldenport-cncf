package org.goldenport.cncf.display

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, DisplayMutationActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.entity.EntityRevisionTransport
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
/** Selected by the actual resolved Operation, never by a textual route heuristic. */
private[cncf] trait DisplayMutationOperation extends OperationDefinition {
  private[cncf] def _string_input_names: Set[String]
}

private[cncf] trait DisplayMutationHttpOperation extends DisplayMutationOperation {
  private[cncf] def _expected_http_method: HttpRequest.Method
  private[cncf] def _raw_mutation_body: Boolean
  private[cncf] def _encode_http_request_c(request: HttpRequest): Consequence[Request]
}

/** Strict adapter admission into the same ordinary descriptor and command Actions. */
private[cncf] object DisplayMutationIngress {
  import DisplayMutation.inputInvalidC
  import DisplayMutationRuntimeEntry.{Entry, Registry}
  private val _selectors = Vector("resourceType", "screenId")
  private val _observation = EntityRevisionTransport.observedRevisionPropertyName

  def operations(componentId: ComponentId, registry: Registry): Vector[OperationDefinition] =
    if (registry.nonEmpty) Vector(new BoundOperation(componentId, registry, None),
      new BoundOperation(componentId, registry, Some(DisplayMutation.Operation.Update))) ++
      (if (registry.lifecycle) Vector(new BoundOperation(componentId, registry, Some(DisplayMutation.Operation.Create)),
        new BoundOperation(componentId, registry, Some(DisplayMutation.Operation.Delete))) else Vector.empty)
    else Vector.empty

  private final class BoundOperation(private val _component_id: ComponentId,
    private val _registry: Registry, private val _operation: Option[DisplayMutation.Operation]) extends DisplayMutationHttpOperation {
    private val _operation_name = _operation match {
      case None => "describeMutation"
      case Some(DisplayMutation.Operation.Create) => "create"
      case Some(DisplayMutation.Operation.Update) => "update"
      case Some(DisplayMutation.Operation.Delete) => "delete"
    }
    private val _input_names = _selectors ++ (if (_operation.nonEmpty) Vector("payload") else Vector.empty) ++
      (if (_operation.exists(_ != DisplayMutation.Operation.Create)) Vector(_observation) else Vector.empty)
    private[cncf] val _string_input_names: Set[String] =
      _input_names.toSet
    private[cncf] def _raw_mutation_body: Boolean = _operation.nonEmpty
    private[cncf] def _expected_http_method: HttpRequest.Method = _http_method(_operation)
    val specification: OperationDefinition.Specification = OperationDefinition.Specification(
      name = _operation_name,
      request = RequestDefinition(_input_names.map(name => name -> (if (_selectors.contains(name)) Multiplicity.One else Multiplicity.ZeroOne))
        .map { case (name, multiplicity) => ParameterDefinition(content = BaseContent.simple(name),
          kind = ParameterDefinition.Kind.Property, domain = ValueDomain(XString, multiplicity)) }.toList),
      response = ResponseDefinition(result = List(DataType.Named("OperationResponse"))))

    private[cncf] def _encode_http_request_c(request: HttpRequest): Consequence[Request] = {
      for {
        _ <- _http_shape_c(request, _operation)
        resource <- _record_string_c(request.query, "resourceType")
        screen <- _record_string_c(request.query, "screenId")
      } yield Request.ofHttpRequest(request, _component_id.name, "display", _operation_name,
        properties = List(Property("resourceType", resource, None), Property("screenId", screen, None)))
    }

    def createOperationRequest(request: Request): Consequence[OperationRequest] = {
      for {
        _ <- _request_shape_c(request, _component_id.name, _operation_name, _input_names)
        _ <- if (_operation.contains(DisplayMutation.Operation.Create) &&
            request.properties.exists(_.name.equalsIgnoreCase(_observation)))
          inputInvalidC[Unit]("ELEMENT_INVALID", "request.observedRevision") else Consequence.unit
        resource <- _property_c(request, "resourceType")
        screen <- _property_c(request, "screenId")
        entry <- _registry.selectC(resource, screen)
        result <- if (_operation.nonEmpty) {
          val operation = _operation.get
          for {
            input <- request.source match {
              case Some(Request.Source.Http(http)) => for {
                _ <- if (request.properties.exists(value => value.name == "payload" || value.name.equalsIgnoreCase(_observation)))
                  inputInvalidC[Unit]("SELECTION_MISMATCH", "request.metadata") else Consequence.unit
                _ <- _http_shape_c(http, _operation)
                headers <- _pairs_c(http.header)
                metadata <- DisplayMutationTransport.bindC(operation, headers)
                payload <- _body_c(http)
              } yield payload -> metadata
              case _ => for {
                payload <- _property_c(request, "payload", false)
                metadata <- if (operation == DisplayMutation.Operation.Create) DisplayMutationTransport.bindC(operation, Vector.empty)
                  else for {
                    observation <- _property_c(request, _observation)
                    revision <- EntityRevision.createC(observation)
                    _ <- if (revision.value.toString == observation) Consequence.unit
                      else inputInvalidC[Unit]("VALUE_INVALID", "request.observedRevision")
                    current <- DisplayMutationTransport.bindC(operation, Vector("If-Match" -> EntityRevisionTransport.entityTag(revision)))
                  } yield current
              } yield payload -> metadata
            }
            decoded <- DisplayMutationCodec.decodeRequestC(entry.selection, operation, input._1)
          } yield new MutationAction(request, _registry, entry, decoded, input._2): OperationRequest
        } else Consequence.success(new DescriptorAction(request, entry): OperationRequest)
      } yield result
    }
  }

  private final class MutationAction(val request: Request, private val _registry: Registry,
    private val _entry: Entry, private val _mutation: DisplayMutation.Request,
    private val _metadata: DisplayMutationTransport.Metadata) extends CommandAction {
    def createCall(core: ActionCall.Core): ActionCall =
      new DisplayMutationActionCall(core, _registry, _entry, _mutation, _metadata)
  }
  private final class DescriptorAction(val request: Request, private val _entry: Entry) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = new DescriptorCall(core, _entry)
  }
  private final class DescriptorCall(val core: ActionCall.Core, private val _entry: Entry) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _entry.descriptorC(executionContext).map(OperationResponse.Json.apply)
  }

  private def _request_shape_c(request: Request, componentname: String, operationname: String,
    names: Vector[String]): Consequence[Unit] = {
    if (request == null || request.component != Some(componentname) || request.service != Some("display") ||
        request.operation != operationname || request.arguments == null || request.arguments.nonEmpty ||
        request.switches == null || request.switches.nonEmpty || request.properties == null ||
        request.properties.exists(value => value == null || value.name == null ||
          (!names.contains(value.name) && !_framework_property(value.name)))) inputInvalidC("ELEMENT_INVALID", "request")
    else Consequence.unit
  }
  private def _property_c(request: Request, name: String, identity: Boolean = true): Consequence[String] = {
    request.properties.filter(_.name == name) match {
      case property :: Nil => property.value match {
        case value: String if !identity || DisplayMutation._id_valid(value) => Consequence.success(value)
        case _ => inputInvalidC("VALUE_INVALID", "request." + name)
      }
      case _ => inputInvalidC("ELEMENT_INVALID", "request." + name)
    }
  }
  private def _record_string_c(record: Record, name: String): Consequence[String] = {
    record.fields.filter(_.key == name) match {
      case Vector(field) => field.value.single match {
        case value: String if DisplayMutation._id_valid(value) => Consequence.success(value)
        case _ => inputInvalidC("VALUE_INVALID", "request." + name)
      }
      case _ => inputInvalidC("ELEMENT_INVALID", "request." + name)
    }
  }
  private def _http_method(operation: Option[DisplayMutation.Operation]): HttpRequest.Method = operation match {
    case None => HttpRequest.GET
    case Some(DisplayMutation.Operation.Create) => HttpRequest.POST
    case Some(DisplayMutation.Operation.Update) => HttpRequest.PUT
    case Some(DisplayMutation.Operation.Delete) => HttpRequest.DELETE
  }
  private def _http_shape_c(request: HttpRequest, operation: Option[DisplayMutation.Operation]): Consequence[Unit] = {
    if (request == null || request.query == null || request.form == null || request.header == null ||
        request.form.fields.nonEmpty || request.query.fields.exists(field =>
          field.key.equalsIgnoreCase(_observation) || (!_selectors.contains(field.key) && !_framework_property(field.key))) ||
        request.method != _http_method(operation)) inputInvalidC("ELEMENT_INVALID", "request.http")
    else for {
      _ <- _record_string_c(request.query, "resourceType")
      _ <- _record_string_c(request.query, "screenId")
      headers <- _pairs_c(request.header)
      _ <- if (operation.isEmpty) {
        if (request.body.nonEmpty) inputInvalidC[Unit]("ELEMENT_INVALID", "request.body") else Consequence.unit
      } else {
        val contenttypes = headers.collect { case (name, value) if name.equalsIgnoreCase("Content-Type") => value }
        val valid = contenttypes match {
          case Vector(value) =>
            val parts = value.split(";", -1).toVector.map(_.trim.toLowerCase(java.util.Locale.ROOT))
            parts.head == "application/json" && parts.tail.forall(part => part == "charset=utf-8" || part == "charset=\"utf-8\"")
          case _ => false
        }
        if (valid && request.body.nonEmpty) Consequence.unit else inputInvalidC[Unit]("VALUE_INVALID", "request.body")
      }
    } yield ()
  }
  private def _pairs_c(record: Record): Consequence[Vector[(String, String)]] = {
    record.fields.foldLeft(Consequence.success(Vector.empty[(String, String)])) { (result, field) =>
      result.flatMap { values => field.value.single match {
        case value: String => Consequence.success(values :+ (field.key -> value))
        case _ => inputInvalidC("ELEMENT_INVALID", "request.headers")
      } }
    }
  }
  private def _body_c(request: HttpRequest): Consequence[String] = {
    try {
      val stream = request.body.get.openInputStream()
      val bytes = try stream.readAllBytes() finally stream.close()
      val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      Consequence.success(decoder.decode(ByteBuffer.wrap(bytes)).toString)
    } catch { case scala.util.control.NonFatal(_) => inputInvalidC("VALUE_INVALID", "request.body") }
  }
  private def _framework_property(name: String): Boolean = {
    val lower = name.toLowerCase(java.util.Locale.ROOT)
    name.startsWith("textus.") || name.startsWith("cncf.") || name.startsWith("security.") || name.startsWith("crud.") ||
      lower == "x-textus-session" || lower.startsWith("x-textus-debug-") ||
      Set("principalId", "principal_id", "subjectId", "subject_id", "privilege", "capability", "capabilities", "role", "roles").contains(name)
  }
}
