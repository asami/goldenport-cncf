package org.goldenport.cncf.observability

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.servicecontainer.{
  ServiceContainerCleanupOutcome,
  ServiceContainerCleanupPolicy,
  ServiceContainerDefinition,
  ServiceContainerOwnershipMode,
  ServiceContainerRegistryKey,
  ServiceContainerResolution,
  ServiceContainerRuntime
}

/*
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object ServiceContainerRuntimeObservation {
  def observed(
    runtime: ServiceContainerRuntime
  )(using context: ExecutionContext): ServiceContainerRuntime =
    new _ObservedServiceContainerRuntime(runtime, context)

  def shutdownC(
    runtime: ServiceContainerRuntime
  ): Consequence[Vector[ServiceContainerCleanupOutcome]] =
    _observe_without_calltree_c(
      _Metadata("shutdown", None, None, None)
    )(runtime.shutdownC())

  private final class _ObservedServiceContainerRuntime(
    runtime: ServiceContainerRuntime,
    context: ExecutionContext
  ) extends ServiceContainerRuntime {
    override private[cncf] def configuredDockerExecutable: Option[String] =
      runtime.configuredDockerExecutable

    def resolveC(
      definition: ServiceContainerDefinition
    ): Consequence[ServiceContainerResolution] = {
      val metadata = definition match {
        case x: ServiceContainerDefinition.External =>
          _Metadata(
            "resolve",
            Some(ServiceContainerOwnershipMode.External),
            None,
            Some(x.serviceId.print)
          )
        case x: ServiceContainerDefinition.RuntimeOwned =>
          _Metadata(
            "resolve",
            Some(ServiceContainerOwnershipMode.RuntimeOwned),
            Some(x.registryKey),
            Some(x.serviceId.print),
            cleanupPolicy = Some(x.cleanupPolicy),
            safeImage = Some(x.image.print)
          )
      }
      _observe_c(metadata)(runtime.resolveC(definition))
    }

    def stopC(
      key: ServiceContainerRegistryKey
    ): Consequence[ServiceContainerCleanupOutcome] =
      _observe_c(_Metadata(
        "stop",
        Some(ServiceContainerOwnershipMode.RuntimeOwned),
        Some(key),
        Some(key.serviceId.print),
        cleanupPolicy = Some(ServiceContainerCleanupPolicy.Stop)
      ))(runtime.stopC(key))

    def restartC(
      key: ServiceContainerRegistryKey
    ): Consequence[ServiceContainerResolution.RuntimeOwned] =
      _observe_c(_Metadata(
        "restart",
        Some(ServiceContainerOwnershipMode.RuntimeOwned),
        Some(key),
        Some(key.serviceId.print)
      ))(runtime.restartC(key))

    def removeC(
      key: ServiceContainerRegistryKey
    ): Consequence[ServiceContainerCleanupOutcome] =
      _observe_c(_Metadata(
        "remove",
        Some(ServiceContainerOwnershipMode.RuntimeOwned),
        Some(key),
        Some(key.serviceId.print),
        cleanupPolicy = Some(ServiceContainerCleanupPolicy.Remove)
      ))(runtime.removeC(key))

    def shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]] =
      _observe_c(_Metadata("shutdown", None, None, None))(runtime.shutdownC())

    private def _observe_c[A](
      metadata: _Metadata
    )(
      body: => Consequence[A]
    ): Consequence[A] = {
      given ExecutionContext = context
      val calltree = context.observability.callTreeContext
      if (calltree.isEnabled)
        calltree.enter(s"service-container:${metadata.operation}", metadata.callTreeAttributes)
      val started = System.nanoTime()
      val result =
        try {
          body
        } catch {
          case e: Throwable => Consequence.Failure(Conclusion.from(e))
        }
      val elapsedmillis = (System.nanoTime() - started) / 1000000L
      _record_metrics(metadata, result, elapsedmillis)
      if (calltree.isEnabled)
        calltree.leave(_result_attributes(result))
      result
    }
  }

  private final case class _Metadata(
    operation: String,
    ownershipMode: Option[ServiceContainerOwnershipMode],
    key: Option[ServiceContainerRegistryKey],
    serviceId: Option[String],
    cleanupPolicy: Option[ServiceContainerCleanupPolicy] = None,
    safeImage: Option[String] = None
  ) {
    def callTreeAttributes: Map[String, String] =
      Map(
        "calltree_kind" -> "service-container",
        "operation" -> operation
      ) ++
        ownershipMode.map(x => "ownership_mode" -> x.name) ++
        key.map(x => "owner_kind" -> x.owner.kind.name) ++
        key.map(x => "owner_id" -> x.owner.id.print) ++
        serviceId.map("service_id" -> _) ++
        cleanupPolicy.map(x => "cleanup_policy" -> _policy_name(x)) ++
        safeImage.map("image" -> _)
  }

  private def _observe_without_calltree_c[A](
    metadata: _Metadata
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val started = System.nanoTime()
    val result =
      try {
        body
      } catch {
        case e: Throwable => Consequence.Failure(Conclusion.from(e))
      }
    _record_metrics(metadata, result, (System.nanoTime() - started) / 1000000L)
    result
  }

  private def _record_metrics[A](
    metadata: _Metadata,
    result: Consequence[A],
    elapsedmillis: Long
  ): Unit = {
    val diagnostic = result match {
      case Consequence.Failure(conclusion) => Some(ConclusionDiagnostics.classify(conclusion))
      case _ => None
    }
    RuntimeDashboardMetrics.recordServiceContainerLifecycle(
      operation = metadata.operation,
      ownershipmode = metadata.ownershipMode.map(_.name),
      ownerkind = metadata.key.map(_.owner.kind.name),
      ownerid = metadata.key.map(_.owner.id.print),
      serviceid = metadata.serviceId,
      cleanuppolicy = metadata.cleanupPolicy.map(_policy_name),
      status = _result_status(result),
      error = result.isFaillure,
      diagnostic = diagnostic,
      elapsedmillis = Some(elapsedmillis)
    )
  }

  private def _result_attributes[A](
    result: Consequence[A]
  ): Map[String, String] =
    result match {
      case Consequence.Success(value) =>
        Map("outcome" -> "success") ++
          _result_status(result).map("status" -> _) ++
          _safe_endpoint_authority(value).map("endpoint_authority" -> _)
      case Consequence.Failure(conclusion) =>
        val diagnostic = ConclusionDiagnostics.classify(conclusion)
        Map(
          "outcome" -> "failure",
          "diagnostic_key" -> diagnostic.diagnosticKey,
          "status" -> conclusion.status.webCode.code.toString
        )
    }

  private def _result_status[A](result: Consequence[A]): Option[String] =
    result.toOption.flatMap {
      case _: ServiceContainerResolution => Some("ready")
      case x: ServiceContainerCleanupOutcome => Some(_status_name(x.status))
      case xs: Vector[?] if xs.forall(_.isInstanceOf[ServiceContainerCleanupOutcome]) => Some("completed")
      case _ => None
    }

  private def _safe_endpoint_authority(value: Any): Option[String] =
    value match {
      case x: ServiceContainerResolution => Some(x.endpoint.safeAuthority)
      case _ => None
    }

  private def _policy_name(value: ServiceContainerCleanupPolicy): String =
    value.toString.toLowerCase(java.util.Locale.ROOT)

  private def _status_name(value: org.goldenport.cncf.servicecontainer.ServiceContainerStatus): String =
    value.toString.toLowerCase(java.util.Locale.ROOT)
}
