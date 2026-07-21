package org.goldenport.cncf.operationtool

import java.nio.charset.StandardCharsets

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, PortApi, ServiceContract, VariationPoint, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.io.RecordEncoder

/*
 * Runtime-owned, in-process source for admitted CNCF Operation tools.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class OperationToolService {
  def toolSetId: OperationToolSetId
  def catalog: Consequence[OperationToolCatalog]
  def withInvocation[A](
    body: OperationToolInvocation => Consequence[A]
  )(using ExecutionContext): Consequence[A]
}

abstract class OperationToolInvocation {
  def catalog: Consequence[OperationToolCatalog]
  def invoke(call: OperationToolCall)(using ExecutionContext): Consequence[OperationToolResult]
}

final case class OperationToolRequirement(toolSetId: OperationToolSetId)

final class OperationToolSocket private (
  val requirements: Vector[OperationToolRequirement]
) {
  private val _monitor = new Object()
  private var _services = Map.empty[OperationToolSetId, OperationToolService]

  def toolSetIds: Vector[OperationToolSetId] =
    requirements.map(_.toolSetId)

  def isInstalled: Boolean =
    _monitor.synchronized {
      _services.size == requirements.size
    }

  def service(toolsetid: OperationToolSetId): Consequence[OperationToolService] =
    _monitor.synchronized {
      _services.get(toolsetid) match {
        case Some(service) => Consequence.success(service)
        case None => Consequence.serviceUnavailable(
          s"Operation tool service is not installed: ${toolsetid.print}"
        )
      }
    }

  private[operationtool] def install(
    services: Map[OperationToolSetId, OperationToolService]
  ): Unit =
    _monitor.synchronized {
      _services = services
    }
}

object OperationToolSocket {
  def createC(
    requirements: Vector[OperationToolRequirement]
  ): Consequence[OperationToolSocket] = {
    val normalized = requirements.sortBy(_.toolSetId.print)
    val identities = normalized.map(_.toolSetId)
    if (normalized.isEmpty)
      Consequence.argumentInvalid(
        "requirements",
        "one or more Operation tool-set requirements",
        "empty"
      )
    else if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "requirements",
        "operation-tool.socket",
        "unique tool-set identities",
        "duplicate"
      )
    else
      Consequence.success(new OperationToolSocket(normalized))
  }
}

object OperationToolPortApi extends PortApi[OperationToolRequirement, OperationToolService] {
  private val _contract_prefix = "operation-tool/"

  def resolve(req: OperationToolRequirement): Consequence[ServiceContract[OperationToolService]] =
    Consequence.success(ServiceContract(
      s"${_contract_prefix}${req.toolSetId.print}",
      classOf[OperationToolService]
    ))

  private[operationtool] def toolSetId(
    contract: ServiceContract[OperationToolService]
  ): Option[OperationToolSetId] =
    Option(contract.name)
      .filter(_.startsWith(_contract_prefix))
      .flatMap(x => OperationToolSetId.parseC(x.substring(_contract_prefix.length)).toOption)
}

object OperationToolSelectionPoint extends VariationPoint[OperationToolRequirement] {
  def current(req: OperationToolRequirement)(using ExecutionContext): Consequence[VariationSelection] =
    Consequence.success(VariationSelection())

  def inject(
    req: OperationToolRequirement,
    selection: VariationSelection
  )(using ExecutionContext): Consequence[OperationToolRequirement] =
    if (selection == VariationSelection())
      Consequence.success(req)
    else
      Consequence.argumentPolicyViolation(
        "selection",
        "operation-tool.runtime-owned-selection",
        "empty caller selection",
        "infrastructure selector"
      )
}

final class OperationToolRuntimeRegistry private (
  private val _services: Map[OperationToolSetId, OperationToolService]
) {
  def toolSetIds: Vector[OperationToolSetId] =
    _services.keys.toVector.sortBy(_.print)

  def resolve(toolsetid: OperationToolSetId): Consequence[OperationToolService] =
    _services.get(toolsetid) match {
      case Some(service) => Consequence.success(service)
      case None => Consequence.serviceUnavailable(s"Operation tool set is unavailable: ${toolsetid.print}")
    }

  def extensionPoint: ExtensionPoint[OperationToolService] =
    new ExtensionPoint[OperationToolService] {
      def supports(
        contract: ServiceContract[OperationToolService],
        variation: VariationSelection
      )(using ExecutionContext): Boolean =
        variation == VariationSelection() && OperationToolPortApi.toolSetId(contract).exists(_services.contains)

      def provide(
        contract: ServiceContract[OperationToolService],
        variation: VariationSelection
      )(using ExecutionContext): Consequence[OperationToolService] =
        OperationToolPortApi.toolSetId(contract) match {
          case Some(toolsetid) => resolve(toolsetid)
          case None => Consequence.serviceUnavailable(s"Invalid Operation tool contract: ${contract.name}")
        }
    }

  def binding: Component.Binding[OperationToolRequirement, OperationToolService] =
    Component.Binding(Port(
      api = OperationToolPortApi,
      spi = Vector(extensionPoint),
      variation = OperationToolSelectionPoint
    ))

  def install(component: Component): Consequence[Component] =
    install(Vector(component)).map(_ => component)

  def install(components: Seq[Component]): Consequence[Unit] = {
    val sockets = components.flatMap(_.port.inputEntries).collect {
      case socket: OperationToolSocket => socket
    }.toVector
    val requirements = sockets.flatMap(_.requirements).distinct
    _resolve_services_c(requirements).map { services =>
      sockets.foreach { socket =>
        socket.install(socket.toolSetIds.map(id => id -> services(id)).toMap)
      }
      ()
    }
  }

  private def _resolve_services_c(
    requirements: Vector[OperationToolRequirement]
  ): Consequence[Map[OperationToolSetId, OperationToolService]] =
    requirements.foldLeft(Consequence.success(Map.empty[OperationToolSetId, OperationToolService])) {
      case (z, requirement) =>
        for {
          services <- z
          service <- resolve(requirement.toolSetId)
        } yield services.updated(requirement.toolSetId, service)
    }
}

object OperationToolRuntimeRegistry {
  def createC(
    subsystem: Subsystem,
    admissions: Vector[OperationToolAdmission]
  ): Consequence[OperationToolRuntimeRegistry] = {
    val identities = admissions.map(_.toolSetId)
    if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "admissions",
        "operation-tool.registry",
        "unique tool-set admissions",
        "duplicate"
      )
    else
      admissions.sortBy(_.toolSetId.print).foldLeft(
        Consequence.success(Map.empty[OperationToolSetId, OperationToolService])
      ) { (z, admission) =>
        for {
          services <- z
          service <- DefaultOperationToolService.createC(subsystem, admission)
        } yield services.updated(admission.toolSetId, service)
      }.map(new OperationToolRuntimeRegistry(_))
  }
}

private final class DefaultOperationToolService(
  subsystem: Subsystem,
  admission: OperationToolAdmission,
  currentcatalog: OperationToolCatalog
) extends OperationToolService {
  def toolSetId: OperationToolSetId = admission.toolSetId

  def catalog: Consequence[OperationToolCatalog] =
    Consequence.success(currentcatalog)

  def withInvocation[A](
    body: OperationToolInvocation => Consequence[A]
  )(using ExecutionContext): Consequence[A] = {
    val invocation = new _Invocation()
    try body(invocation)
    finally invocation.close()
  }

  private final class _Invocation extends OperationToolInvocation with AutoCloseable {
    private var _calls = 0
    private var _active = 0
    private var _closed = false

    def catalog: Consequence[OperationToolCatalog] =
      DefaultOperationToolService.this.catalog

    def invoke(
      call: OperationToolCall
    )(using executioncontext: ExecutionContext): Consequence[OperationToolResult] =
      currentcatalog.definition(call.identity) match {
        case None => Consequence.operationNotFound(s"Operation tool not admitted: ${call.identity.print}")
        case Some(definition) =>
          for {
            _ <- definition.validateArgumentsC(call.arguments)
            _ <- _check_input_size_c(call)
            _ <- _admit_c
            result <- try _execute_c(call)
              finally _release()
          } yield result
      }

    private def _execute_c(
      call: OperationToolCall
    )(using executioncontext: ExecutionContext): Consequence[OperationToolResult] = {
      val request = Request.of(
        component = call.identity.component,
        service = call.identity.service,
        operation = call.identity.operation,
        properties = call.arguments.fields.sortBy(_.key).map { field =>
          Property(field.key, field.value.single, None)
        }.toList
      )
      subsystem.executeOperationResponse(request, executioncontext).flatMap { response =>
        val bytes = response.print.getBytes(StandardCharsets.UTF_8).length.toLong
        if (bytes > admission.limits.maximumResultBytes)
          _limit_failure(
            "maximum-result-bytes",
            admission.limits.maximumResultBytes,
            bytes
          )
        else
          Consequence.success(OperationToolResult(response))
      }
    }

    private def _check_input_size_c(call: OperationToolCall): Consequence[Unit] = {
      val bytes = RecordEncoder.json(call.arguments).getBytes(StandardCharsets.UTF_8).length.toLong
      if (bytes > admission.limits.maximumInputBytes)
        _limit_failure("maximum-input-bytes", admission.limits.maximumInputBytes, bytes)
      else
        Consequence.unit
    }

    private def _admit_c: Consequence[Unit] = synchronized {
      if (_closed)
        _limit_failure("invocation-closed", 0L, 1L)
      else if (_calls >= admission.limits.maximumCalls)
        _limit_failure("maximum-calls", admission.limits.maximumCalls.toLong, _calls.toLong + 1L)
      else if (_active >= admission.limits.maximumConcurrency)
        _limit_failure("maximum-concurrency", admission.limits.maximumConcurrency.toLong, _active.toLong + 1L)
      else {
        _calls += 1
        _active += 1
        Consequence.unit
      }
    }

    private def _release(): Unit = synchronized {
      _active = math.max(0, _active - 1)
    }

    private def _limit_failure[A](
      reason: String,
      limit: Long,
      actual: Long
    ): Consequence.Failure[A] =
      Consequence.operationInvalid(
        "Operation tool invocation limit exceeded",
        Cause.Kind.Limit,
        Vector(
          Descriptor.Facet.Reason(reason),
          Descriptor.Facet.Policy("operation-tool.limits"),
          Descriptor.Facet.Limit(limit),
          Descriptor.Facet.Actual(actual)
        )
      )

    def close(): Unit = synchronized {
      _closed = true
    }
  }
}

private object DefaultOperationToolService {
  def createC(
    subsystem: Subsystem,
    admission: OperationToolAdmission
  ): Consequence[OperationToolService] =
    OperationToolCatalogBuilder.createC(subsystem, admission).map { catalog =>
      new DefaultOperationToolService(subsystem, admission, catalog)
    }
}

object OperationToolCatalogBuilder {
  def createC(
    subsystem: Subsystem,
    admission: OperationToolAdmission
  ): Consequence[OperationToolCatalog] = {
    val candidates = subsystem.components
      .filter(_.isPrimaryParticipant)
      .flatMap { component =>
        component.protocol.services.services.flatMap { service =>
          service.operations.operations.toVector.map { operation =>
            (component, service, operation)
          }
        }
      }
    candidates.foldLeft(Consequence.success(Vector.empty[OperationToolDefinition])) {
      case (z, (component, service, operation)) =>
        for {
          definitions <- z
          definition <- OperationToolDefinitionBuilder.definitionC(component, service, operation)
        } yield definitions :+ definition
    }.flatMap { definitions =>
      val available = definitions.sortBy(_.identity.print)
      val duplicates = available
        .groupBy(_.identity)
        .toVector
        .collect { case (identity, entries) if entries.size > 1 => identity.print }
        .sorted
      if (duplicates.nonEmpty)
        Consequence.stateConflict(s"duplicate Operation tool identities: ${duplicates.mkString(", ")}")
      else {
        val byidentity = available.map(x => x.identity -> x).toMap
        admission.identities.find(!byidentity.contains(_)) match {
          case Some(identity) =>
            Consequence.operationNotFound(s"admitted Operation tool is unavailable: ${identity.print}")
          case None =>
            Consequence.success(OperationToolCatalog(
              admission.toolSetId,
              admission.identities.flatMap(byidentity.get).sortBy(_.identity.print)
            ))
        }
      }
    }
  }
}
