package org.goldenport.cncf.entity

import java.util.Locale
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityRevision

/*
 * Declarative ordinary Entity mutation policy.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityConcurrencyPolicy(val label: String) {
  case None extends EntityConcurrencyPolicy("none")
  case Optimistic extends EntityConcurrencyPolicy("optimistic")
}

object EntityConcurrencyPolicy {
  val default: EntityConcurrencyPolicy =
    EntityConcurrencyPolicy.None

  def effectivePolicy(
    assembledPolicy: EntityConcurrencyPolicy,
    attemptPolicy: EntityConcurrencyPolicy
  ): EntityConcurrencyPolicy =
    if (
      assembledPolicy == EntityConcurrencyPolicy.Optimistic ||
      attemptPolicy == EntityConcurrencyPolicy.Optimistic
    )
      EntityConcurrencyPolicy.Optimistic
    else
      EntityConcurrencyPolicy.None

  def parseC(text: String): Consequence[EntityConcurrencyPolicy] =
    Option(text)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .collect {
        case "none" | "last-write-wins" | "last_write_wins" =>
          EntityConcurrencyPolicy.None
        case "optimistic" | "occ" =>
          EntityConcurrencyPolicy.Optimistic
      }
      .map(Consequence.success)
      .getOrElse(
        Consequence.argumentInvalid(
          "concurrencyPolicy",
          "none or optimistic",
          text
        )
      )
}

enum EntityWritePolicy(val label: String) {
  case AlwaysWrite extends EntityWritePolicy("always-write")
  case WriteIfChanged extends EntityWritePolicy("write-if-changed")
}

object EntityWritePolicy {
  val default: EntityWritePolicy =
    EntityWritePolicy.AlwaysWrite

  def parseC(text: String): Consequence[EntityWritePolicy] =
    Option(text)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .collect {
        case "always-write" | "always_write" | "alwayswrite" =>
          EntityWritePolicy.AlwaysWrite
        case "write-if-changed" | "write_if_changed" | "writeifchanged" =>
          EntityWritePolicy.WriteIfChanged
      }
      .map(Consequence.success)
      .getOrElse(
        Consequence.argumentInvalid(
          "writePolicy",
          "always-write or write-if-changed",
          text
        )
      )
}

enum RevisionPreconditionPolicy(val label: String) {
  case Managed extends RevisionPreconditionPolicy("managed")
  case ObservedRequired
      extends RevisionPreconditionPolicy("observed-required")
}

object RevisionPreconditionPolicy {
  val default: RevisionPreconditionPolicy =
    RevisionPreconditionPolicy.Managed

  def parseC(text: String): Consequence[RevisionPreconditionPolicy] =
    Option(text)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .collect {
        case "managed" =>
          RevisionPreconditionPolicy.Managed
        case "observed-required" | "observed_required" | "observedrequired" =>
          RevisionPreconditionPolicy.ObservedRequired
      }
      .map(Consequence.success)
      .getOrElse(
        Consequence.argumentInvalid(
          "revisionPreconditionPolicy",
          "managed or observed-required",
          text
        )
      )
}

final case class EntityMutationExecutionPolicy(
  concurrencyPolicy: EntityConcurrencyPolicy =
    EntityConcurrencyPolicy.default,
  writePolicy: EntityWritePolicy = EntityWritePolicy.default,
  preconditionPolicy: RevisionPreconditionPolicy =
    RevisionPreconditionPolicy.default,
  observedRevision: Option[EntityRevision] = None
) {
  def validateC: Consequence[EntityMutationExecutionPolicy] =
    if (
      concurrencyPolicy == EntityConcurrencyPolicy.None &&
      preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired
    )
      Consequence.configurationInvalid(
        "Entity concurrency policy None cannot require an observed revision"
      )
    else if (
      preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired &&
      observedRevision.isEmpty
    )
      Consequence.argumentMissing("observedRevision")
    else
      Consequence.success(this)
}

object EntityMutationExecutionPolicy {
  val default: EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy()
}

final case class EntityMutationPolicyDeclaration(
  writePolicy: Option[EntityWritePolicy] = None,
  preconditionPolicy: Option[RevisionPreconditionPolicy] = None
)

final case class EntityMutationPolicySelection(
  writePolicy: EntityWritePolicy,
  preconditionPolicy: RevisionPreconditionPolicy
) {
  def validateC(
    concurrencyPolicy: EntityConcurrencyPolicy
  ): Consequence[EntityMutationPolicySelection] =
    if (
      concurrencyPolicy == EntityConcurrencyPolicy.None &&
      preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired
    )
      Consequence.configurationInvalid(
        "Entity concurrency policy None cannot require an observed revision"
      )
    else
      Consequence.success(this)

  def executionPolicyC(
    concurrencyPolicy: EntityConcurrencyPolicy,
    observedRevision: Option[EntityRevision]
  ): Consequence[EntityMutationExecutionPolicy] =
    validateC(concurrencyPolicy).flatMap { _ =>
      EntityMutationExecutionPolicy(
        concurrencyPolicy = concurrencyPolicy,
        writePolicy = writePolicy,
        preconditionPolicy = preconditionPolicy,
        observedRevision = observedRevision
      ).validateC
    }
}

object EntityMutationAdapterDefaults {
  val profilePropertyName: String =
    "textus.entity.mutation.adapter"

  val webFormProfile: String =
    "web-form-update"

  val idempotentRestProfile: String =
    "rest-idempotent-update"

  val strictRestProfile: String =
    "rest-strict-update"

  val core: EntityMutationPolicyDeclaration =
    EntityMutationPolicyDeclaration(
      writePolicy = Some(EntityWritePolicy.AlwaysWrite),
      preconditionPolicy = Some(RevisionPreconditionPolicy.Managed)
    )

  val webFormUpdate: EntityMutationPolicyDeclaration =
    EntityMutationPolicyDeclaration(
      writePolicy = Some(EntityWritePolicy.WriteIfChanged),
      preconditionPolicy =
        Some(RevisionPreconditionPolicy.ObservedRequired)
    )

  val idempotentRestUpdate: EntityMutationPolicyDeclaration =
    EntityMutationPolicyDeclaration(
      writePolicy = Some(EntityWritePolicy.WriteIfChanged),
      preconditionPolicy = Some(RevisionPreconditionPolicy.Managed)
    )

  val strictRestUpdate: EntityMutationPolicyDeclaration =
    EntityMutationPolicyDeclaration(
      writePolicy = Some(EntityWritePolicy.WriteIfChanged),
      preconditionPolicy =
        Some(RevisionPreconditionPolicy.ObservedRequired)
    )

  def forProfile(profile: Option[String]): EntityMutationPolicyDeclaration =
    profile.map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some(value) if value == webFormProfile =>
        webFormUpdate
      case Some(value) if value == idempotentRestProfile =>
        idempotentRestUpdate
      case Some(value) if value == strictRestProfile =>
        strictRestUpdate
      case _ =>
        core
    }

  def effectiveConcurrencyPolicy(
    collectionPolicy: EntityConcurrencyPolicy,
    preconditionPolicy: RevisionPreconditionPolicy
  ): EntityConcurrencyPolicy =
    preconditionPolicy match {
      case RevisionPreconditionPolicy.ObservedRequired =>
        EntityConcurrencyPolicy.Optimistic
      case RevisionPreconditionPolicy.Managed =>
        collectionPolicy
    }
}

object EntityMutationPolicyResolver {
  def resolveC(
    concurrencyPolicy: EntityConcurrencyPolicy,
    routeBinding: EntityMutationPolicyDeclaration =
      EntityMutationPolicyDeclaration(),
    operationDeclaration: EntityMutationPolicyDeclaration =
      EntityMutationPolicyDeclaration(),
    adapterDefault: EntityMutationPolicyDeclaration =
      EntityMutationAdapterDefaults.core
  ): Consequence[EntityMutationPolicySelection] = {
    val selection = EntityMutationPolicySelection(
      writePolicy =
        routeBinding.writePolicy
          .orElse(operationDeclaration.writePolicy)
          .orElse(adapterDefault.writePolicy)
          .getOrElse(EntityWritePolicy.default),
      preconditionPolicy =
        routeBinding.preconditionPolicy
          .orElse(operationDeclaration.preconditionPolicy)
          .orElse(adapterDefault.preconditionPolicy)
          .getOrElse(RevisionPreconditionPolicy.default)
    )
    selection.validateC(concurrencyPolicy)
  }
}
