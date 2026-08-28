package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Internal, value-only Component Admin runtime and datastore projection.  The
 * input facts are already authoritative runtime facts; this projection does
 * not discover, load, activate, manage, or grant authority.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum ComponentAdminOperationalState {
  case Configured, Resolved, Active, Degraded, Failed
}

private[cncf] final case class ComponentAdminOperationalStateEvidence(
  state: ComponentAdminOperationalState,
  detail: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] enum ComponentAdminLifecycleState {
  case Constructed, Loaded, Active, Stopped, Failed
}

private[cncf] final case class ComponentAdminLifecycleEvidence(
  state: ComponentAdminLifecycleState,
  detail: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] enum ComponentAdminHealthState {
  case Healthy, Degraded, Failed
}

private[cncf] final case class ComponentAdminHealthEvidence(
  state: ComponentAdminHealthState,
  detail: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] enum ComponentAdminDependencyState {
  case Configured, Resolved, Active, Degraded, Failed
}

private[cncf] final case class ComponentAdminDependencyEvidence(
  name: String,
  state: ComponentAdminDependencyState,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] enum ComponentAdminClassLoaderState {
  case Configured, Resolved, Active, Failed
}

private[cncf] final case class ComponentAdminClassLoaderEvidence(
  state: ComponentAdminClassLoaderState,
  identity: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminRuntimeEvidence(
  loadedinstanceid: ComponentInstanceId,
  dependencies: Vector[ComponentAdminDependencyEvidence],
  classloader: ComponentAdminClassLoaderEvidence,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminCollectionEvidence(
  entityname: String,
  collectionid: EntityCollectionId,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminDatastoreEvidence(
  identity: String,
  schema: String,
  collections: Vector[ComponentAdminCollectionEvidence],
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminExecutionContextEvidence(
  mode: SubsystemUserMode,
  identity: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminEntityIdInput(
  declaredentityname: Option[String],
  suppliedentityid: String,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminRuntimeIdentityBinding(
  selectedlogicalrelease: ComponentAdminLogicalRelease,
  subsystemclass: ComponentAdminSubsystemClass,
  subsysteminstance: ComponentAdminSubsystemInstance,
  implicitcomponentsubsystem: ComponentAdminImplicitComponentSubsystem
)

private[cncf] final case class ComponentAdminRuntimeDatastoreFacts(
  loadedinstanceid: ComponentInstanceId,
  operationalstates: Vector[ComponentAdminOperationalStateEvidence],
  lifecycle: ComponentAdminLifecycleEvidence,
  health: ComponentAdminHealthEvidence,
  runtime: ComponentAdminRuntimeEvidence,
  datastore: ComponentAdminDatastoreEvidence,
  executioncontext: ComponentAdminExecutionContextEvidence,
  entityspace: EntitySpace,
  entityidinputs: Vector[ComponentAdminEntityIdInput],
  identitybinding: ComponentAdminRuntimeIdentityBinding
)

private[cncf] final case class ComponentAdminEntityIdEvidence(
  declaredentityname: String,
  entityid: EntityId,
  collection: ComponentAdminCollectionEvidence,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminRuntimeDatastoreProjectionView(
  identityview: ComponentAdminViewModel,
  selectedloadedinstance: ComponentAdminViewField[ComponentAdminLoadedInstance],
  operationalstates: Vector[ComponentAdminOperationalStateEvidence],
  lifecycle: ComponentAdminLifecycleEvidence,
  health: ComponentAdminHealthEvidence,
  runtime: ComponentAdminRuntimeEvidence,
  datastore: ComponentAdminDatastoreEvidence,
  executioncontext: ComponentAdminExecutionContextEvidence,
  entityids: Vector[ComponentAdminEntityIdEvidence]
)

private[cncf] object ComponentAdminRuntimeDatastoreProjection {
  def projectC(
    identityView: ComponentAdminViewModel,
    facts: ComponentAdminRuntimeDatastoreFacts
  ): Consequence[ComponentAdminRuntimeDatastoreProjectionView] =
    ComponentAdminViewModel.validateC(identityView).flatMap { validated =>
      if (facts == null) {
        Consequence.argumentInvalid("Component Admin runtime and datastore facts are required")
      } else {
        _validate_facts(validated, facts).flatMap { _ =>
          _project_entity_ids(facts.entityspace, facts.entityidinputs).map { entityids =>
            val resolvedcollections = entityids.map(_.collection)
            val collections =
              (facts.datastore.collections ++ resolvedcollections)
                .distinctBy(_.collectionid)
            ComponentAdminRuntimeDatastoreProjectionView(
              validated,
              validated.selectedLoadedInstance,
              facts.operationalstates,
              facts.lifecycle,
              facts.health,
              facts.runtime,
              facts.datastore.copy(collections = collections),
              facts.executioncontext,
              entityids
            )
          }
        }
      }
    }

  private def _validate_facts(
    identityview: ComponentAdminViewModel,
    facts: ComponentAdminRuntimeDatastoreFacts
  ): Consequence[Unit] = {
    val selectedinstance = identityview.selectedLoadedInstance.value.instanceId
    val states = Option(facts.operationalstates).getOrElse(Vector.empty)
    val invalidstates = states.exists(_invalid_operational_state_evidence)
    lazy val statevalues = states.map(_.state)
    val expectedstates = ComponentAdminOperationalState.values.toSet
    if (facts.identitybinding == null) {
      Consequence.argumentInvalid("Component Admin runtime identity binding is required")
    } else if (_invalid_runtime_identity_binding(facts.identitybinding)) {
      Consequence.argumentInvalid("Component Admin runtime identity binding is incomplete")
    } else if (facts.identitybinding.selectedlogicalrelease != identityview.selectedLogicalRelease.value) {
      Consequence.argumentInvalid("Component Admin runtime facts belong to a different selected logical release")
    } else if (facts.identitybinding.subsystemclass != identityview.subsystemClass.value) {
      Consequence.argumentInvalid("Component Admin runtime facts belong to a different Subsystem class")
    } else if (facts.identitybinding.subsysteminstance != identityview.subsystemInstance.value) {
      Consequence.argumentInvalid("Component Admin runtime facts belong to a different Subsystem instance")
    } else if (facts.identitybinding.implicitcomponentsubsystem != identityview.implicitComponentSubsystem.value) {
      Consequence.argumentInvalid("Component Admin runtime facts belong to a different implicit Component Subsystem")
    } else if (facts.loadedinstanceid == null) {
      Consequence.argumentInvalid("Component Admin loaded instance evidence is required")
    } else if (facts.loadedinstanceid != selectedinstance) {
      Consequence.argumentInvalid("Component Admin runtime facts belong to a different loaded Component instance")
    } else if (invalidstates) {
      Consequence.argumentInvalid("Component Admin operational state evidence is incomplete")
    } else if (statevalues.toSet != expectedstates || statevalues.size != expectedstates.size) {
      Consequence.argumentInvalid("Component Admin operational state evidence must retain configured, resolved, active, degraded, and failed states")
    } else if (_invalid_lifecycle(facts.lifecycle)) {
      Consequence.argumentInvalid("Component Admin lifecycle evidence is required")
    } else if (_invalid_health(facts.health)) {
      Consequence.argumentInvalid("Component Admin health evidence is required")
    } else if (_invalid_runtime(facts.runtime, facts.loadedinstanceid)) {
      Consequence.argumentInvalid("Component Admin runtime and dependency evidence is required")
    } else if (_invalid_datastore(facts.datastore)) {
      Consequence.argumentInvalid("Component Admin datastore and schema evidence is required")
    } else if (_invalid_execution_context(facts.executioncontext)) {
      Consequence.argumentInvalid("Component Admin ExecutionContext evidence is required")
    } else if (facts.entityspace == null) {
      Consequence.argumentInvalid("Component Admin EntitySpace evidence is required")
    } else if (facts.entityidinputs == null || facts.entityidinputs.exists(_invalid_entity_id_input)) {
      Consequence.argumentInvalid("Component Admin Entity-ID evidence is required")
    } else {
      Consequence.unit
    }
  }

  private def _invalid_runtime_identity_binding(
    value: ComponentAdminRuntimeIdentityBinding
  ): Boolean =
    value.selectedlogicalrelease == null ||
      value.subsystemclass == null ||
      value.subsysteminstance == null ||
      value.implicitcomponentsubsystem == null

  private def _invalid_operational_state_evidence(
    value: ComponentAdminOperationalStateEvidence
  ): Boolean =
    value == null || value.state == null || value.provenance == null || !_nonempty(value.detail)

  private def _invalid_lifecycle(value: ComponentAdminLifecycleEvidence): Boolean =
    value == null || value.state == null || value.provenance == null || !_nonempty(value.detail)

  private def _invalid_health(value: ComponentAdminHealthEvidence): Boolean =
    value == null || value.state == null || value.provenance == null || !_nonempty(value.detail)

  private def _invalid_runtime(
    value: ComponentAdminRuntimeEvidence,
    selectedinstance: ComponentInstanceId
  ): Boolean =
    value == null ||
      value.loadedinstanceid == null ||
      value.loadedinstanceid != selectedinstance ||
      value.provenance == null ||
      value.classloader == null ||
      value.classloader.state == null ||
      value.classloader.provenance == null ||
      !_nonempty(value.classloader.identity) ||
      value.dependencies == null ||
      value.dependencies.exists(value =>
        value == null || value.state == null || value.provenance == null || !_nonempty(value.name)
      )

  private def _invalid_datastore(value: ComponentAdminDatastoreEvidence): Boolean =
    value == null ||
      value.provenance == null ||
      !_nonempty(value.identity) ||
      !_nonempty(value.schema) ||
      value.collections == null ||
      value.collections.exists(value =>
      value == null || value.collectionid == null || value.provenance == null || !_nonempty(value.entityname)
      )

  private def _invalid_execution_context(value: ComponentAdminExecutionContextEvidence): Boolean =
    value == null || value.mode == null || value.provenance == null || !_nonempty(value.identity)

  private def _invalid_entity_id_input(value: ComponentAdminEntityIdInput): Boolean =
    value == null || value.declaredentityname == null || value.provenance == null || value.suppliedentityid == null

  private def _project_entity_ids(
    entityspace: EntitySpace,
    inputs: Vector[ComponentAdminEntityIdInput]
  ): Consequence[Vector[ComponentAdminEntityIdEvidence]] =
    inputs.foldLeft[Consequence[Vector[ComponentAdminEntityIdEvidence]]](Consequence.success(Vector.empty)) {
      (result, input) =>
        result.flatMap(values => _project_entity_id(entityspace, input).map(value => values :+ value))
    }

  private def _project_entity_id(
    entityspace: EntitySpace,
    input: ComponentAdminEntityIdInput
  ): Consequence[ComponentAdminEntityIdEvidence] =
    input.declaredentityname match {
      case None =>
        Consequence.argumentMissing("Admin Entity-ID backing entity name is required")
      case Some(entityname) if !_nonempty(entityname) =>
        Consequence.argumentMissing("Admin Entity-ID backing entity name is required")
      case Some(entityname) =>
        entityspace.entityByNameC[Any](entityname).flatMap { collection =>
          EntityId.parse(input.suppliedentityid).flatMap { entityid =>
            val collectionid = collection.descriptor.collectionId
            if (entityid.collection == collectionid) {
              Consequence.success(ComponentAdminEntityIdEvidence(
                entityname,
                entityid,
                ComponentAdminCollectionEvidence(entityname, collectionid, input.provenance),
                input.provenance
              ))
            } else {
              EntityPersistent._collection_mismatch(entityid.collection, collectionid)
            }
          }
        }
    }

  private def _nonempty(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim && !value.exists(_.isControl)
}
