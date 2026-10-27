package org.goldenport.cncf.operationalobservation

import java.net.URI
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.servicecontainer.ServiceContainerRegistryKey

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
final case class ObservationLimits(
  operationMillis: Int = 4000, sourceMillis: Int = 1000,
  sourceBytes: Int = 65536, responseBytes: Int = 262144
)

sealed trait DockerObservationSelector
object DockerObservationSelector {
  final case class Name(name: String) extends DockerObservationSelector
  final case class Compose(project: String, service: String) extends DockerObservationSelector
  final case class RuntimeOwned(key: ServiceContainerRegistryKey) extends DockerObservationSelector
}

sealed trait ObservationSource {
  def sourceId: String
  def freshnessMillis: Long
  def axis: ObservationAxis
}
object ObservationSource {
  final case class Docker(
    sourceId: String, baseUrl: String, selector: DockerObservationSelector, freshnessMillis: Long
  ) extends ObservationSource {
    val axis: ObservationAxis = ObservationAxis.Deployment
  }
  final case class Http(
    sourceId: String, url: String, axis: ObservationAxis, scope: String,
    vocabulary: Vector[String], sampleMode: String, freshnessMillis: Long
  ) extends ObservationSource
}

final case class ObservationTarget(
  targetId: String, service: String, instance: String, kind: String, enabled: Boolean,
  sources: Map[ObservationAxis, String], runtimeAssociation: Option[ComponentInstanceId] = None
)

final case class OperationalObservationPlan(
  profile: String, owners: Vector[ComponentId], limits: ObservationLimits,
  sources: Vector[ObservationSource], targets: Vector[ObservationTarget]
) {
  def validateC: Consequence[OperationalObservationPlan] = {
    val ids = sources.map(_.sourceId)
    val byid = sources.map(x => x.sourceId -> x).toMap
    val validlimits = limits.operationMillis > 0 && limits.operationMillis <= 4000 &&
      limits.sourceMillis > 0 && limits.sourceMillis <= 1000 && limits.sourceMillis <= limits.operationMillis &&
      limits.sourceBytes > 0 && limits.sourceBytes <= 65536 &&
      limits.responseBytes > 0 && limits.responseBytes <= 262144
    val validsources = sources.forall {
      case x: ObservationSource.Docker =>
        OperationalObservationPlan.origin(x.baseUrl) && (x.selector match {
          case DockerObservationSelector.Name(name) => OperationalObservationPlan.safe(name)
          case DockerObservationSelector.Compose(project, service) =>
            OperationalObservationPlan.safe(project) && OperationalObservationPlan.safe(service)
          case DockerObservationSelector.RuntimeOwned(key) => key != null
        })
      case x: ObservationSource.Http =>
        OperationalObservationPlan.url(x.url) && x.axis != ObservationAxis.Deployment &&
        OperationalObservationPlan.safe(x.scope) && Set("current", "reported")(x.sampleMode) &&
        x.vocabulary.nonEmpty && x.vocabulary.size <= 16 &&
        x.vocabulary.distinct.size == x.vocabulary.size && x.vocabulary.forall(OperationalObservationPlan.safe) &&
        (x.axis match {
          case ObservationAxis.Readiness => x.vocabulary.toSet == Set("ready", "not-ready")
          case ObservationAxis.Reachability => x.sampleMode == "current" &&
            x.vocabulary.toSet == Set("reachable", "unreachable")
          case _ => true
        })
    }
    val validtargets = targets.forall(t =>
      Vector(t.targetId, t.service, t.instance).forall(OperationalObservationPlan.safe) &&
      Set("runtime", "infrastructure")(t.kind) &&
      t.sources.forall { case (axis, id) => byid.get(id).exists(_.axis == axis) })
    if (OperationalObservationPlan.safe(profile) && owners.nonEmpty &&
        owners.distinct.size == owners.size && owners.forall(x =>
          x != null && ComponentId.parseC(x.name).toOption.contains(x)) &&
        validlimits && sources.size <= 8 && ids.distinct.size == ids.size &&
        sources.forall(x => OperationalObservationPlan.safe(x.sourceId) &&
          x.freshnessMillis > 0 && x.freshnessMillis <= 300000) && validsources &&
        targets.size <= 100 && targets.map(_.targetId).distinct.size == targets.size &&
        targets.map(t => t.service -> t.instance).distinct.size == targets.size && validtargets)
      Consequence.success(this)
    else Consequence.configurationInvalid("Invalid operational observation declaration")
  }
}
object OperationalObservationPlan {
  private[cncf] def safe(value: String): Boolean =
    value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
  private[cncf] def url(value: String): Boolean = try {
    val uri = new URI(value)
    value.length <= 2048 && !value.exists(c => c.isControl || c == '\\') &&
      Set("http", "https").contains(uri.getScheme) && uri.getHost != null &&
      uri.getRawUserInfo == null && uri.getRawFragment == null &&
      (uri.getPort == -1 || (uri.getPort > 0 && uri.getPort <= 65535))
  } catch { case _: IllegalArgumentException => false; case _: java.net.URISyntaxException => false }
  private[cncf] def origin(value: String): Boolean = url(value) && {
    val uri = new URI(value)
    Set("", "/")(Option(uri.getRawPath).getOrElse("")) && uri.getRawQuery == null
  }
}
