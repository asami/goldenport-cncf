package org.goldenport.cncf.component

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import org.goldenport.Consequence
import org.goldenport.cncf.component.identity.{
  ComponentIdentityMigrationClassifier,
  ComponentIdentityMigrationDecision,
  ComponentIdentityMigrationRequest
}

/*
 * Runtime adapter over the shared deferred-release migration authority.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentIdentityDeferredReleaseEntry(
  componentid: ComponentId,
  release: String,
  legacyartifact: String,
  legacylocalid: String,
  migrationowner: String
)

private[cncf] final case class ComponentIdentityDeferredReleaseRegistry(
  entries: Vector[ComponentIdentityDeferredReleaseEntry],
  private val _classifier: ComponentIdentityMigrationClassifier
) {
  import ComponentIdentityDeferredReleaseRegistry.*

  def admitDescriptorC(
    descriptor: ComponentDescriptor
  ): Consequence[DescriptorAdmission] =
    classifyC(descriptor).flatMap {
      case exact @ ExactDeferred(entry, effective) =>
        Consequence.success(DescriptorAdmission(descriptor, effective, Some(entry), exact))
      case Strict =>
        descriptor.requireCanonicalIdentityC.map(_ =>
          DescriptorAdmission(descriptor, descriptor, None, Strict)
        )
      case migration: MigrationRequired =>
        descriptor.requireCanonicalIdentityC.map(_ =>
          DescriptorAdmission(descriptor, descriptor, None, migration)
        )
      case InventoryError(diagnostic) =>
        Consequence.resourceInvalid(diagnostic)
    }

  def classifyC(
    descriptor: ComponentDescriptor
  ): Consequence[Classification] =
    if (descriptor == null)
      Consequence.componentInvalid(
        "component.identity.deferred-release.inventory-error reason=descriptor-required"
      )
    else
      descriptor.schemaVersion match {
        case Some(3) => Consequence.success(Strict)
        case Some(2) => _classify_legacy_c(descriptor)
        case actual =>
          Consequence.success(
            InventoryError(
              s"component.identity.deferred-release.inventory-error reason=descriptor-schema-version; expected=2-or-3; actual=${actual.map(_.toString).getOrElse("missing")}"
            )
          )
      }

  private def _classify_legacy_c(
    descriptor: ComponentDescriptor
  ): Consequence[Classification] = {
    val request = new ComponentIdentityMigrationRequest(
      null,
      null,
      descriptor.name.orNull,
      descriptor.componentName.orNull,
      descriptor.version.orNull,
      java.util.Map.of[String, String]()
    )
    val result = _classifier.classify(request)
    if (result.isFailure) {
      val error = result.error.orElseThrow()
      Consequence.resourceInvalid(
        s"component.identity.deferred-release.inventory-error reason=shared-classifier; code=${error.code}; detail=${error.message}"
      )
    } else {
      val decision = result.value.orElseThrow()
      decision.status match {
        case ComponentIdentityMigrationDecision.Status.DEFERRED_TO_NEXT_VERSION =>
          val entry = _entry(decision.entry.orElseThrow())
          ComponentIdentityCompatibilityAdapter
            .projectDescriptorC(
              descriptor.copy(
                name = Some(entry.componentid.name),
                componentName = Some(entry.componentid.name)
              ),
              entry.componentid
            )
            .map(projection => ExactDeferred(entry, projection.descriptor))
        case ComponentIdentityMigrationDecision.Status.MIGRATION_REQUIRED =>
          decision.entry.toScala match {
            case Some(sharedentry) =>
              Consequence.success(
                MigrationRequired(
                  _entry(sharedentry),
                  decision.release.orElse("missing"),
                  decision.reason
                )
              )
            case None => Consequence.success(Strict)
          }
        case ComponentIdentityMigrationDecision.Status.INVENTORY_ERROR =>
          Consequence.success(
            InventoryError(_diagnostic(descriptor, decision.reason, decision.entry.toScala.map(_entry)))
          )
        case ComponentIdentityMigrationDecision.Status.STRICT_LEGACY =>
          Consequence.success(Strict)
        case ComponentIdentityMigrationDecision.Status.CANONICAL =>
          Consequence.success(Strict)
        case ComponentIdentityMigrationDecision.Status.PROJECTION_DISAGREEMENT =>
          Consequence.success(
            InventoryError(_diagnostic(descriptor, decision.reason, decision.entry.toScala.map(_entry)))
          )
      }
    }
  }

  private def _diagnostic(
    descriptor: ComponentDescriptor,
    reason: String,
    entry: Option[ComponentIdentityDeferredReleaseEntry]
  ): String = {
    val runtimereason =
      if (reason == "partial-registry-match") "partial-match" else reason
    Vector(
      "component.identity.deferred-release.inventory-error",
      s"reason=$runtimereason",
      s"artifact=${descriptor.name.getOrElse("missing")}",
      s"release=${descriptor.version.getOrElse("missing")}",
      s"local-id=${descriptor.componentName.getOrElse("missing")}",
      s"expected=${entry.map(_.componentid.name).getOrElse("unregistered")}"
    ).mkString(" ")
  }
}

private[cncf] object ComponentIdentityDeferredReleaseRegistry {
  val RESOURCE_PATH: String = ComponentIdentityMigrationClassifier.RESOURCE_PATH
  val SCHEMA_VERSION: String = ComponentIdentityMigrationClassifier.SCHEMA_VERSION

  sealed trait Classification
  final case class DescriptorAdmission(
    raw: ComponentDescriptor,
    effective: ComponentDescriptor,
    entry: Option[ComponentIdentityDeferredReleaseEntry],
    classification: Classification
  )
  final case class ExactDeferred(
    entry: ComponentIdentityDeferredReleaseEntry,
    descriptor: ComponentDescriptor
  ) extends Classification
  case object Strict extends Classification
  final case class MigrationRequired(
    entry: ComponentIdentityDeferredReleaseEntry,
    actualrelease: String,
    reason: String
  ) extends Classification
  final case class InventoryError(diagnostic: String) extends Classification

  lazy val default: ComponentIdentityDeferredReleaseRegistry =
    loadC() match {
      case Consequence.Success(registry) => registry
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
    }

  def loadC(
    loader: ClassLoader = getClass.getClassLoader
  ): Consequence[ComponentIdentityDeferredReleaseRegistry] =
    _classifier_c(ComponentIdentityMigrationClassifier.load(loader))

  private[component] def parseC(
    text: String
  ): Consequence[ComponentIdentityDeferredReleaseRegistry] =
    _classifier_c(ComponentIdentityMigrationClassifier.parseRegistry(text))

  private def _classifier_c(
    result: org.goldenport.cncf.component.identity.ComponentIdentityResult[ComponentIdentityMigrationClassifier]
  ): Consequence[ComponentIdentityDeferredReleaseRegistry] =
    if (result.isFailure) {
      val error = result.error.orElseThrow()
      Consequence.resourceInvalid(
        s"component.identity.deferred-release.registry.invalid code=${error.code}; detail=${error.message}"
      )
    } else {
      val classifier = result.value.orElseThrow()
      Consequence.success(
        ComponentIdentityDeferredReleaseRegistry(
          classifier.entries.asScala.toVector.map(_entry),
          classifier
        )
      )
    }

  private def _entry(
    entry: ComponentIdentityMigrationClassifier.RegistryEntry
  ): ComponentIdentityDeferredReleaseEntry =
    ComponentIdentityDeferredReleaseEntry(
      ComponentId(entry.componentId.qualifiedName),
      entry.release,
      entry.legacyArtifact,
      entry.legacyLocalId,
      entry.migrationOwner
    )
}
