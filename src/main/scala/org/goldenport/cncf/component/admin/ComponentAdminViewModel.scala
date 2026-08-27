package org.goldenport.cncf.component.admin

import java.net.URI
import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Internal, value-only Component Admin identity and provenance view. This
 * model carries already-resolved facts only; it performs no discovery or I/O.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class ComponentAdminViewModel(
  schema: String,
  componentClass: ComponentAdminViewField[ComponentAdminComponentClass],
  selectedLogicalRelease: ComponentAdminViewField[ComponentAdminLogicalRelease],
  logicalReleaseCandidates: Vector[ComponentAdminViewField[ComponentAdminLogicalRelease]],
  selectedLoadedInstance: ComponentAdminViewField[ComponentAdminLoadedInstance],
  loadedInstanceCandidates: Vector[ComponentAdminViewField[ComponentAdminLoadedInstance]],
  subsystemClass: ComponentAdminViewField[ComponentAdminSubsystemClass],
  subsystemInstance: ComponentAdminViewField[ComponentAdminSubsystemInstance],
  implicitComponentSubsystem: ComponentAdminViewField[ComponentAdminImplicitComponentSubsystem],
  resourceState: ComponentAdminViewField[ComponentAdminResourceState]
)

private[cncf] final case class ComponentAdminViewField[A](
  value: A,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminSafeProvenance(
  sourceKind: ComponentAdminSourceKind,
  logicalIdentity: Option[ComponentResourceLogicalIdentity]
)

private[cncf] enum ComponentAdminSourceKind {
  case RuntimeFact, ResolvedResource, KnowledgeManifest, Descriptor
}

private[cncf] final case class ComponentAdminComponentClass(componentId: ComponentId)

private[cncf] final case class ComponentAdminLogicalRelease(
  componentClass: ComponentAdminComponentClass,
  release: String
)

private[cncf] final case class ComponentAdminLoadedInstance(
  componentClass: ComponentAdminComponentClass,
  instanceId: ComponentInstanceId
)

private[cncf] final case class ComponentAdminSubsystemClass(name: String)

private[cncf] final case class ComponentAdminSubsystemInstance(
  subsystemClass: ComponentAdminSubsystemClass,
  instance: String
)

private[cncf] final case class ComponentAdminImplicitComponentSubsystem(
  componentClass: ComponentAdminComponentClass,
  name: String
)

private[cncf] enum ComponentAdminResourceState {
  case Available, Unavailable, Forbidden, Stale, Incompatible, Corrupt
}

private[cncf] object ComponentAdminViewModel {
  val SCHEMA = "cncf.component-admin-view.v1"

  private val _safe_text_pattern = "[A-Za-z0-9][A-Za-z0-9._:-]*".r
  private val _role_pattern = "[A-Za-z][A-Za-z0-9._-]*".r

  def createC(
    componentClass: ComponentAdminViewField[ComponentAdminComponentClass],
    selectedLogicalRelease: ComponentAdminViewField[ComponentAdminLogicalRelease],
    logicalReleaseCandidates: Vector[ComponentAdminViewField[ComponentAdminLogicalRelease]],
    selectedLoadedInstance: ComponentAdminViewField[ComponentAdminLoadedInstance],
    loadedInstanceCandidates: Vector[ComponentAdminViewField[ComponentAdminLoadedInstance]],
    subsystemClass: ComponentAdminViewField[ComponentAdminSubsystemClass],
    subsystemInstance: ComponentAdminViewField[ComponentAdminSubsystemInstance],
    implicitComponentSubsystem: ComponentAdminViewField[ComponentAdminImplicitComponentSubsystem],
    resourceState: ComponentAdminViewField[ComponentAdminResourceState]
  ): Consequence[ComponentAdminViewModel] = {
    val value = ComponentAdminViewModel(
      SCHEMA,
      componentClass,
      selectedLogicalRelease,
      logicalReleaseCandidates,
      selectedLoadedInstance,
      loadedInstanceCandidates,
      subsystemClass,
      subsystemInstance,
      implicitComponentSubsystem,
      resourceState
    )
    validateC(value)
  }

  def validateC(value: ComponentAdminViewModel): Consequence[ComponentAdminViewModel] =
    _validate(value).fold(Consequence.argumentInvalid, Consequence.success)

  private def _validate(value: ComponentAdminViewModel): Either[String, ComponentAdminViewModel] =
    for {
      _ <- Either.cond(value != null, (), "Component Admin view model is required")
      _ <- Either.cond(value.schema == SCHEMA, (), s"Component Admin view model schema must be $SCHEMA")
      _ <- _component_class_field(value.componentClass, "componentClass")
      _ <- _logical_release_field(value.selectedLogicalRelease, value.componentClass.value, "selectedLogicalRelease")
      _ <- _logical_release_candidates(value.logicalReleaseCandidates, value.componentClass.value)
      _ <- Either.cond(value.logicalReleaseCandidates.map(_.value).contains(value.selectedLogicalRelease.value), (), "selectedLogicalRelease must be supplied in logicalReleaseCandidates")
      _ <- _loaded_instance_field(value.selectedLoadedInstance, value.componentClass.value, "selectedLoadedInstance")
      _ <- _loaded_instance_candidates(value.loadedInstanceCandidates, value.componentClass.value)
      _ <- Either.cond(value.loadedInstanceCandidates.map(_.value).contains(value.selectedLoadedInstance.value), (), "selectedLoadedInstance must be supplied in loadedInstanceCandidates")
      _ <- _subsystem_class_field(value.subsystemClass, "subsystemClass")
      _ <- _subsystem_instance_field(value.subsystemInstance, value.subsystemClass.value, "subsystemInstance")
      _ <- _implicit_subsystem_field(value.implicitComponentSubsystem, value.componentClass.value, "implicitComponentSubsystem")
      _ <- _resource_state_field(value.resourceState, "resourceState")
    } yield value

  private def _component_class_field(
    field: ComponentAdminViewField[ComponentAdminComponentClass],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- Either.cond(field.value.componentId != null, (), s"$context.componentId is required")
    } yield ()

  private def _logical_release_field(
    field: ComponentAdminViewField[ComponentAdminLogicalRelease],
    componentclass: ComponentAdminComponentClass,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- Either.cond(field.value.componentClass == componentclass, (), s"$context must retain the declared Component class")
      _ <- _safe_text(field.value.release, s"$context.release")
    } yield ()

  private def _logical_release_candidates(
    fields: Vector[ComponentAdminViewField[ComponentAdminLogicalRelease]],
    componentclass: ComponentAdminComponentClass
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(fields != null, (), "logicalReleaseCandidates is required")
      _ <- Either.cond(fields.nonEmpty, (), "logicalReleaseCandidates must not be empty")
      _ <- _sequence(fields.zipWithIndex.map { case (field, index) => _logical_release_field(field, componentclass, s"logicalReleaseCandidates[$index]") })
      _ <- Either.cond(fields.map(_.value).distinct.size == fields.size, (), "logicalReleaseCandidates must not contain duplicate identities")
    } yield ()

  private def _loaded_instance_field(
    field: ComponentAdminViewField[ComponentAdminLoadedInstance],
    componentclass: ComponentAdminComponentClass,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- Either.cond(field.value.componentClass == componentclass, (), s"$context must retain the declared Component class")
      _ <- Either.cond(field.value.instanceId != null, (), s"$context.instanceId is required")
      _ <- Either.cond(field.value.instanceId.componentId == componentclass.componentId, (), s"$context.instanceId must retain the declared Component class")
    } yield ()

  private def _loaded_instance_candidates(
    fields: Vector[ComponentAdminViewField[ComponentAdminLoadedInstance]],
    componentclass: ComponentAdminComponentClass
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(fields != null, (), "loadedInstanceCandidates is required")
      _ <- Either.cond(fields.nonEmpty, (), "loadedInstanceCandidates must not be empty")
      _ <- _sequence(fields.zipWithIndex.map { case (field, index) => _loaded_instance_field(field, componentclass, s"loadedInstanceCandidates[$index]") })
      _ <- Either.cond(fields.map(_.value).distinct.size == fields.size, (), "loadedInstanceCandidates must not contain duplicate identities")
    } yield ()

  private def _subsystem_class_field(
    field: ComponentAdminViewField[ComponentAdminSubsystemClass],
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- _safe_text(field.value.name, s"$context.name")
    } yield ()

  private def _subsystem_instance_field(
    field: ComponentAdminViewField[ComponentAdminSubsystemInstance],
    subsystemclass: ComponentAdminSubsystemClass,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- Either.cond(field.value.subsystemClass == subsystemclass, (), s"$context must retain the declared Subsystem class")
      _ <- _safe_text(field.value.instance, s"$context.instance")
    } yield ()

  private def _implicit_subsystem_field(
    field: ComponentAdminViewField[ComponentAdminImplicitComponentSubsystem],
    componentclass: ComponentAdminComponentClass,
    context: String
  ): Either[String, Unit] =
    for {
      _ <- _field(field, context)
      _ <- Either.cond(field.value.componentClass == componentclass, (), s"$context must retain the declared Component class")
      _ <- _safe_text(field.value.name, s"$context.name")
    } yield ()

  private def _resource_state_field(
    field: ComponentAdminViewField[ComponentAdminResourceState],
    context: String
  ): Either[String, Unit] =
    _field(field, context)

  private def _field[A](field: ComponentAdminViewField[A], context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(field != null, (), s"$context is required")
      _ <- Either.cond(field.value != null, (), s"$context.value is required")
      _ <- _provenance(field.provenance, s"$context.provenance")
    } yield ()

  private def _provenance(value: ComponentAdminSafeProvenance, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(value != null, (), s"$context is required")
      _ <- Either.cond(value.sourceKind != null, (), s"$context.sourceKind is required")
      _ <- Either.cond(value.logicalIdentity != null, (), s"$context.logicalIdentity is required")
      _ <- value.logicalIdentity.map(_logical_identity(_, s"$context.logicalIdentity")).getOrElse(Right(()))
    } yield ()

  private def _logical_identity(value: ComponentResourceLogicalIdentity, context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(value != null && value.componentId != null, (), s"$context.componentId is required")
      _ <- _nonempty_trimmed_text(value.logicalRelease, s"$context.logicalRelease")
      _ <- _safe_role(value.childRole, s"$context.childRole")
      _ <- _logical_resource(value.logicalResource, s"$context.logicalResource")
      _ <- Either.cond(value.parentComponentId != null, (), s"$context.parentComponentId is required")
      _ <- value.parentComponentId.fold[Either[String, Unit]](Right(()))(parent => Either.cond(parent != value.componentId, (), s"$context.parentComponentId must differ from componentId"))
    } yield ()

  private def _nonempty_trimmed_text(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && value.nonEmpty && value == value.trim && !value.exists(_.isControl), (), s"$context must be non-empty trimmed text")

  private def _safe_role(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && _role_pattern.matches(value), (), s"$context must be a safe role token")

  private def _logical_resource(value: String, context: String): Either[String, Unit] =
    try {
      val uri = URI.create(value)
      Either.cond(uri.isAbsolute && Option(uri.getScheme).exists(_.nonEmpty) && Option(uri.getSchemeSpecificPart).exists(_.nonEmpty), (), s"$context must be an absolute logical resource URI")
    } catch {
      case NonFatal(_) => Left(s"$context must be an absolute logical resource URI")
    }

  private def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && _safe_text_pattern.matches(value), (), s"$context must be safe logical text")

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft[Either[String, Unit]](Right(()))((z, x) => z.flatMap(_ => x))
}
