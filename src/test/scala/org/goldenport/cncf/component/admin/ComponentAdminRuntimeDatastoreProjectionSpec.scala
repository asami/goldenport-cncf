package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityMemoryPolicy,
  EntityRuntimePlan,
  EntitySpace,
  EntityStorage,
  PartitionStrategy
}
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Executable specification for ADM05-RUNTIME-DATASTORE-IDENTITY: the
 * package-private, value-only Admin runtime/datastore projection.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminRuntimeDatastoreProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E1, rules:ADM05-R1, phase:60.4, slice:ADM-05A")
  private val _e2 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E2, rules:ADM05-R2, phase:60.4, slice:ADM-05A")
  private val _e3 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E3, rules:ADM05-R3, phase:60.4, slice:ADM-05A")
  private val _e4 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E4, rules:ADM05-R4, phase:60.4, slice:ADM-05A")
  private val _e5 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E5, rules:ADM05-R5, phase:60.4, slice:ADM-05A")
  private val _e6 = afterWord("in spec:component-admin-runtime-datastore-identity, example:E6, rules:ADM05-R6, phase:60.4, slice:ADM-05A")

  "ADM05-RUNTIME-DATASTORE-IDENTITY Component Admin projection" should {
    "E1 retain distinct operational, lifecycle, health, runtime, datastore, schema, and provenance evidence" must _e1 {
      "when projecting already-supplied runtime and datastore facts" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R1; Example: E1; a validated identity view and complete authoritative facts for one loaded instance")
        val view = _view
        val suppliedcollection = ComponentAdminCollectionEvidence("facility", _collection_id, _provenance)
        val basefacts = _facts(view, _space(_collection_id), Vector.empty, SubsystemUserMode.Standalone)
        val facts = basefacts.copy(
          datastore = basefacts.datastore.copy(
            collections = Vector(suppliedcollection)
          )
        )

        When("the pure projection retains the evidence")
        val projected = ComponentAdminRuntimeDatastoreProjection.projectC(view, facts).toOption

        Then("all five operational states remain distinct and every evidence axis retains its value and safe provenance")
        projected should not be empty
        projected.map(_.identityview) shouldBe Some(view)
        projected.map(_.identityview.selectedLoadedInstance.value.instanceId) shouldBe Some(_selected_instance)
        projected.map(_.operationalstates) shouldBe Some(facts.operationalstates)
        projected.map(_.operationalstates.map(_.state)) shouldBe Some(Vector(
          ComponentAdminOperationalState.Configured,
          ComponentAdminOperationalState.Resolved,
          ComponentAdminOperationalState.Active,
          ComponentAdminOperationalState.Degraded,
          ComponentAdminOperationalState.Failed
        ))
        projected.map(_.lifecycle) shouldBe Some(facts.lifecycle)
        projected.map(_.lifecycle.state) shouldBe Some(ComponentAdminLifecycleState.Active)
        projected.map(_.health) shouldBe Some(facts.health)
        projected.map(_.health.state) shouldBe Some(ComponentAdminHealthState.Healthy)
        projected.map(_.runtime) shouldBe Some(facts.runtime)
        projected.map(_.runtime.loadedinstanceid) shouldBe Some(_selected_instance)
        projected.map(_.runtime.dependencies.map(_.name)) shouldBe Some(Vector("component-runtime"))
        projected.map(_.runtime.classloader.identity) shouldBe Some("loader:adm-blue")
        projected.map(_.datastore) shouldBe Some(facts.datastore)
        projected.map(_.datastore.identity) shouldBe Some("datastore:adm")
        projected.map(_.datastore.schema) shouldBe Some("schema:adm-v1")
        projected.map(_.datastore.collections) shouldBe Some(Vector(suppliedcollection))
        projected.map(_.executioncontext) shouldBe Some(facts.executioncontext)
        projected.map(_.executioncontext.mode) shouldBe Some(SubsystemUserMode.Standalone)
      }
    }

    "E2 isolate the selected loaded instance while admitting both execution modes" must _e2 {
      "when facts carry either supported Subsystem user mode" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R2; Example: E2; one selected loaded Component instance and supplied Standalone or MultiUser context evidence")
        val modes = Vector(SubsystemUserMode.Standalone, SubsystemUserMode.MultiUser)

        When("each supplied mode is projected without deriving policy or fallback")
        val results = modes.map { mode =>
          ComponentAdminRuntimeDatastoreProjection.projectC(
            _view,
            _facts(_view, _space(_collection_id), Vector.empty, mode)
          )
        }

        Then("both modes are retained exactly as supplied")
        results.map(_.toOption.map(_.executioncontext.mode)) shouldBe modes.map(Some(_))

        Given("facts belonging to another loaded instance are rejected")
        val otherinstance = ComponentInstanceId(_component_id, "green")

        When("the projection evaluates facts belonging to another loaded instance")
        val isolated = ComponentAdminRuntimeDatastoreProjection.projectC(
          _view,
          _facts(_view, _space(_collection_id), Vector.empty, SubsystemUserMode.Standalone, otherinstance)
        )

        Then("facts belonging to another loaded instance remain rejected")
        isolated shouldBe a[Consequence.Failure[?]]
        isolated.display should include("different loaded Component instance")
      }
    }

    "E3 resolve only the declared backing collection and preserve exact canonical identity" must _e3 {
      "when an Admin Entity-ID input names its owner explicitly" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R3; Example: E3; one declared entity name, one registered exact collection, and a canonical EntityId owned by it")
        val entityid = EntityId("runtime", "entry_1", _collection_id)
        val input = ComponentAdminEntityIdInput(Some("facility"), entityid.value, _provenance)
        val facts = _facts(_view, _space(_collection_id), Vector(input), SubsystemUserMode.Standalone)

        When("the projection resolves the owner through EntitySpace.entityByNameC")
        val projected = ComponentAdminRuntimeDatastoreProjection.projectC(_view, facts).toOption

        Then("the canonical EntityId, exact collection ID, declared name, and provenance are retained")
        projected.flatMap(_.entityids.headOption).map(_.entityid) shouldBe Some(entityid)
        projected.flatMap(_.entityids.headOption).map(_.collection.collectionid) shouldBe Some(_collection_id)
        projected.flatMap(_.entityids.headOption).map(_.declaredentityname) shouldBe Some("facility")
        projected.flatMap(_.datastore.collections.find(_.collectionid == _collection_id)).map(_.entityname) shouldBe Some("facility")
      }
    }

    "E4 reject scalar, foreign, missing-owner, and ambiguous-owner Entity-ID inputs deterministically" must _e4 {
      "when malformed or non-exact identity evidence reaches the boundary" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R4; Example: E4; scalar input, foreign canonical ID, absent declared owner, and two exact collections sharing one name")
        val selectedspace = _space(_collection_id)
        val scalar = _input(Some("facility"), "notice_1")
        val foreign = _input(Some("facility"), EntityId("runtime", "notice_1", _foreign_collection).value)
        val missing = _input(None, EntityId("runtime", "notice_1", _collection_id).value)
        val ambiguousspace = new EntitySpace()
        ambiguousspace.registerEntity("facility", _collection(_first_collection))
        ambiguousspace.registerEntity("facility", _collection(_second_collection))
        val ambiguous = _input(Some("facility"), EntityId("runtime", "notice_1", _first_collection).value)

        When("the projection evaluates each independent boundary input")
        val scalarresult = ComponentAdminRuntimeDatastoreProjection.projectC(_view, _facts(_view, selectedspace, Vector(scalar), SubsystemUserMode.Standalone))
        val foreignresult = ComponentAdminRuntimeDatastoreProjection.projectC(_view, _facts(_view, selectedspace, Vector(foreign), SubsystemUserMode.Standalone))
        val missingresult = ComponentAdminRuntimeDatastoreProjection.projectC(_view, _facts(_view, selectedspace, Vector(missing), SubsystemUserMode.Standalone))
        val ambiguousresult = ComponentAdminRuntimeDatastoreProjection.projectC(_view, _facts(_view, ambiguousspace, Vector(ambiguous), SubsystemUserMode.Standalone))

        Then("the scalar parser failure, exact collection mismatch, missing owner, and EntitySpace ambiguity remain failures")
        scalarresult shouldBe a[Consequence.Failure[?]]
        foreignresult shouldBe a[Consequence.Failure[?]]
        missingresult shouldBe a[Consequence.Failure[?]]
        ambiguousresult shouldBe a[Consequence.Failure[?]]
        foreignresult.display should include("collection")
        missingresult.display should include("backing entity name")
        ambiguousresult.display should include("ambiguous")
      }
    }

    "E5 preserve identity and state across fifty finite valid cases" must _e5 {
      "when the pure projection evaluates generated canonical inputs" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R5; Example: E5; a finite generator of canonical EntityIds owned by one registered collection")
        val property = Prop.forAll(Gen.choose(1, 500)) { number =>
          val entityid = EntityId("runtime", s"entry_$number", _collection_id)
          val input = ComponentAdminEntityIdInput(Some("facility"), entityid.value, _provenance)
          ComponentAdminRuntimeDatastoreProjection.projectC(
            _view,
            _facts(_view, _space(_collection_id), Vector(input), SubsystemUserMode.MultiUser)
          ).toOption.exists { projected =>
            projected.identityview.selectedLoadedInstance.value.instanceId == _selected_instance &&
              projected.operationalstates.map(_.state).toSet == ComponentAdminOperationalState.values.toSet &&
              projected.entityids.map(_.entityid) == Vector(entityid) &&
              projected.entityids.head.collection.collectionid == _collection_id &&
              projected.executioncontext.mode == SubsystemUserMode.MultiUser
          }
        }

        When("the projection evaluates the generated closed valid input space")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every successful case preserves the selected instance, all distinct states, exact owner, and supplied mode")
        checked.passed shouldBe true
      }
    }

    "E6 reject a null operational-state evidence element as a structured failure" must _e6 {
      "when supplied runtime facts contain a null operational-state evidence element" in {
        Given("Spec: ADM05-RUNTIME-DATASTORE-IDENTITY; Rules: ADM05-R6; Example: E6; a validated identity view and facts containing a null operational-state evidence element")
        val facts = _facts(_view, _space(_collection_id), Vector.empty, SubsystemUserMode.Standalone).copy(
          operationalstates = Vector[ComponentAdminOperationalStateEvidence](null)
        )

        When("the projection validates the supplied runtime facts")
        val result = ComponentAdminRuntimeDatastoreProjection.projectC(_view, facts)

        Then("the malformed operational-state evidence is reported as a structured failure")
        result shouldBe a[Consequence.Failure[?]]
        result.display should include("operational state evidence")
      }
    }
  }

  private val _component_id = ComponentId("org.goldenport.cncf.admin.Runtime")
  private val _selected_instance = ComponentInstanceId(_component_id, "blue")
  private val _collection_id = EntityCollectionId("runtime", "adm", "facility")
  private val _foreign_collection = EntityCollectionId("runtime", "other", "facility")
  private val _first_collection = EntityCollectionId("first", "runtime", "facility")
  private val _second_collection = EntityCollectionId("second", "runtime", "facility")
  private val _provenance = ComponentAdminSafeProvenance(
    ComponentAdminSourceKind.RuntimeFact,
    Some(ComponentResourceLogicalIdentity(
      _component_id,
      "1.0.0",
      None,
      "runtime",
      "urn:cncf:resource:adm/runtime"
    ))
  )

  private def _view: ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(_component_id)
    val componentfield = ComponentAdminViewField(componentclass, _provenance)
    val release = ComponentAdminLogicalRelease(componentclass, "1.0.0")
    val releasefield = ComponentAdminViewField(release, _provenance)
    val instance = ComponentAdminLoadedInstance(componentclass, _selected_instance)
    val instancefield = ComponentAdminViewField(instance, _provenance)
    val subsystemclass = ComponentAdminSubsystemClass("component-subsystem")
    val subsystemfield = ComponentAdminViewField(subsystemclass, _provenance)
    val subsysteminstance = ComponentAdminViewField(
      ComponentAdminSubsystemInstance(subsystemclass, "main"),
      _provenance
    )
    val implicitsubsystem = ComponentAdminViewField(
      ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"),
      _provenance
    )
    ComponentAdminViewModel.createC(
      componentfield,
      releasefield,
      Vector(releasefield),
      instancefield,
      Vector(instancefield),
      subsystemfield,
      subsysteminstance,
      implicitsubsystem,
      ComponentAdminViewField(ComponentAdminResourceState.Available, _provenance)
    ).toOption.get
  }

  private def _facts(
    identityview: ComponentAdminViewModel,
    entityspace: EntitySpace,
    inputs: Vector[ComponentAdminEntityIdInput],
    mode: SubsystemUserMode,
    instanceid: ComponentInstanceId = _selected_instance
  ): ComponentAdminRuntimeDatastoreFacts = {
    val states = ComponentAdminOperationalState.values.toVector.map { state =>
      ComponentAdminOperationalStateEvidence(state, s"state-${state.toString.toLowerCase}", _provenance)
    }
    val dependency = ComponentAdminDependencyEvidence("component-runtime", ComponentAdminDependencyState.Active, _provenance)
    ComponentAdminRuntimeDatastoreFacts(
      instanceid,
      states,
      ComponentAdminLifecycleEvidence(ComponentAdminLifecycleState.Active, "loaded and active", _provenance),
      ComponentAdminHealthEvidence(ComponentAdminHealthState.Healthy, "health-ok", _provenance),
      ComponentAdminRuntimeEvidence(
        instanceid,
        Vector(dependency),
        ComponentAdminClassLoaderEvidence(ComponentAdminClassLoaderState.Active, "loader:adm-blue", _provenance),
        _provenance
      ),
      ComponentAdminDatastoreEvidence("datastore:adm", "schema:adm-v1", Vector.empty, _provenance),
      ComponentAdminExecutionContextEvidence(mode, s"context:${mode.toString.toLowerCase}", _provenance),
      entityspace,
      inputs
    )
  }

  private def _input(
    entityname: Option[String],
    entityid: String
  ): ComponentAdminEntityIdInput =
    ComponentAdminEntityIdInput(entityname, entityid, _provenance)

  private def _space(collectionid: EntityCollectionId): EntitySpace = {
    val space = new EntitySpace()
    space.registerEntity(collectionid.name, _collection(collectionid))
    space
  }

  private def _collection(collectionid: EntityCollectionId): EntityCollection[FixtureEntity] = {
    val persistent = new EntityPersistent[FixtureEntity] {
      def id(value: FixtureEntity): EntityId = value.entityid
      def toRecord(value: FixtureEntity): Record = Record.dataAuto("id" -> value.entityid.value)
      def fromRecord(record: Record): Consequence[FixtureEntity] = EntityId.createC(record).map(FixtureEntity.apply)
    }
    val plan = EntityRuntimePlan[FixtureEntity](
      entityName = collectionid.name,
      memoryPolicy = EntityMemoryPolicy.LoadToMemory,
      workingSet = None,
      partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 1,
      maxEntitiesPerPartition = 16
    )
    new EntityCollection(
      EntityDescriptor(collectionid, plan, persistent),
      EntityStorage[FixtureEntity](null)
    )
  }

  private final case class FixtureEntity(entityid: EntityId)
}
