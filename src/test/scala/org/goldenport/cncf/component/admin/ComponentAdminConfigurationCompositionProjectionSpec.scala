package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.{ComponentResourceAuthorization, ComponentResourceAvailability, ComponentResourceCompositionShape, ComponentResourceDiagnostic, ComponentResourceDiagnosticKind, ComponentResourceIntegrity, ComponentResourceLogicalIdentity, ComponentResourceProvenance, ComponentResourceSourceKind, ResolvedComponentResource, ResolvedComponentResources}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCandidate, ConfigurationBindingCollection, ConfigurationOrigin, ConfigurationParameter, ConfigurationProvenance}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for ADM03-CONFIGURATION-COMPOSITION-NO-SCAN:
 * an internal, value-only projection of typed configuration provenance and
 * already-resolved resource composition.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentAdminConfigurationCompositionProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "ADM03-CONFIGURATION-COMPOSITION-NO-SCAN Component Admin projection" should {
    "retain typed configuration winner, override history, and provenance" in {
      Given("one typed confidential Subsystem binding winner with an overridden binding and bounded source provenance")
      val parameter = CncfConfigurationParameterCatalog.subsystemUserMode
      val target = _target
      val previous = _take(ConfigurationBinding.initial(_candidate(parameter, target, SubsystemUserMode.Standalone, "previous-source", 10, 0)))
      val winner = _take(ConfigurationBinding.overrideWith(
        _candidate(parameter, target, SubsystemUserMode.MultiUser, "winner-source", 20, 1),
        previous
      ))
      val collection = _take(ConfigurationBindingCollection.from(Vector(winner)))

      When("the projection derives its trace from the supplied resolved binding collection")
      val projected = _take(ComponentAdminConfigurationCompositionProjection.projectC(_identity_view, collection, _resources(Vector.empty)))
      val explanation = _take(projected.configuration.explain(parameter)).get

      Then("the typed winner chain, Subsystem scope, redacted trace values, and source provenance remain exact")
      winner.value shouldBe SubsystemUserMode.MultiUser
      winner.target shouldBe target
      winner.overridden.map(_.value) shouldBe Some(SubsystemUserMode.Standalone)
      explanation.effective.target shouldBe target
      explanation.effective.value shouldBe org.goldenport.configuration.ConfigurationBindingTraceValue.Redacted
      explanation.entries.map(_.value) shouldBe Vector(
        org.goldenport.configuration.ConfigurationBindingTraceValue.Redacted,
        org.goldenport.configuration.ConfigurationBindingTraceValue.Redacted
      )
      explanation.entries.map(_.provenance.sourceIdentity) shouldBe Vector("winner-source", "previous-source")
      explanation.entries.map(_.provenance.inputPath) shouldBe Vector(
        Some(".textus/config.yaml"),
        Some(".textus/config.yaml")
      )
      explanation.entries.map(_.provenance.inputSpelling) shouldBe Vector(
        Some(parameter.id.value),
        Some(parameter.id.value)
      )
      explanation.entries.map(_.provenance.sourceRank) shouldBe Vector(20, 10)
      explanation.entries.map(_.provenance.sourceOrdinal) shouldBe Vector(1, 0)
      projected.identityView shouldBe _identity_view
    }

    "retain already-resolved primary Documentation and SourceCode composition" in {
      Given("one supplied MultiComponent result containing ordered primary, Documentation, and SourceCode evidence")
      val primaryid = ComponentId("org.goldenport.cncf.admin.Sample")
      val documentationid = ComponentId("org.goldenport.cncf.admin.Documentation")
      val sourceid = ComponentId("org.goldenport.cncf.admin.SourceCode")
      val supplied = _resources(Vector(
        _resource(primaryid, None, "parent", "urn:cncf:resource:adm/primary", "embedded:primary", ComponentResourceSourceKind.EmbeddedPrimary, ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        _resource(documentationid, Some(primaryid), "Documentation", "urn:cncf:resource:adm/documentation", "expanded-car:documentation", ComponentResourceSourceKind.ExpandedCar, ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted),
        _resource(sourceid, Some(primaryid), "SourceCode", "urn:cncf:resource:adm/source-code", "expanded-car:source-code", ComponentResourceSourceKind.ExpandedCar, ComponentResourceAvailability.Available, ComponentResourceIntegrity.Verified, ComponentResourceAuthorization.Granted)
      ))

      When("the projection receives the already-resolved composition")
      val projected = _take(ComponentAdminConfigurationCompositionProjection.projectC(_identity_view, _configuration, supplied))
      val resources = projected.resources.resources

      Then("ordered identity, availability, integrity, authorization, access, and physical provenance remain unchanged")
      projected.resources shouldBe supplied
      resources.map(_.logicalIdentity.childRole) shouldBe Vector("parent", "Documentation", "SourceCode")
      resources.map(resource => (
        resource.logicalIdentity,
        resource.availability,
        resource.integrity,
        resource.authorization,
        resource.provenance.access,
        resource.provenance.physicalSource
      )) shouldBe supplied.resources.map(resource => (
        resource.logicalIdentity,
        resource.availability,
        resource.integrity,
        resource.authorization,
        resource.provenance.access,
        resource.provenance.physicalSource
      ))
      resources.map(_.provenance.resolutionStep) shouldBe Vector(
        "embedded-primary:0",
        "expanded-car:2",
        "expanded-car:2"
      )
    }

    "retain stale restricted and denied evidence without fallback or authority expansion" in {
      Given("already-resolved evidence carrying stale, restricted, and denied observations")
      val parentid = ComponentId("org.goldenport.cncf.admin.Sample")
      val supplied = _resources(Vector(
        _resource(parentid, None, "parent", "urn:cncf:resource:adm/primary", "embedded:primary", ComponentResourceSourceKind.EmbeddedPrimary, ComponentResourceAvailability.Stale, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Denied),
        _resource(ComponentId("org.goldenport.cncf.admin.Documentation"), Some(parentid), "Documentation", "urn:cncf:resource:adm/documentation", "expanded-car:documentation", ComponentResourceSourceKind.ExpandedCar, ComponentResourceAvailability.Restricted, ComponentResourceIntegrity.NotEvaluated, ComponentResourceAuthorization.Denied),
        _resource(ComponentId("org.goldenport.cncf.admin.SourceCode"), Some(parentid), "SourceCode", "urn:cncf:resource:adm/source-code", "expanded-car:source-code", ComponentResourceSourceKind.ExpandedCar, ComponentResourceAvailability.Available, ComponentResourceIntegrity.Unverified, ComponentResourceAuthorization.Denied)
      ))

      When("the projection retains the supplied composition as descriptive input")
      val projected = _take(ComponentAdminConfigurationCompositionProjection.projectC(_identity_view, _configuration, supplied))

      Then("each restricted, stale, or denied fact remains unchanged and no authority is added")
      projected.resources shouldBe supplied
      projected.resources.resources.map(_.availability) shouldBe Vector(
        ComponentResourceAvailability.Stale,
        ComponentResourceAvailability.Restricted,
        ComponentResourceAvailability.Available
      )
      projected.resources.resources.foreach { resource =>
        resource.authorization should not be ComponentResourceAuthorization.Granted
        resource.activationAuthority shouldBe false
        resource.operationAuthority shouldBe false
        resource.mcpAuthority shouldBe false
        resource.disclosureAuthority shouldBe false
        resource.deploymentAuthority shouldBe false
      }
    }

    "reject a null resolved-resource input structurally" in {
      Given("a valid ADM-02 identity and resolved configuration with no resource result")
      val configuration = _configuration

      When("the projection is given a null resolved-resource value")
      val result = ComponentAdminConfigurationCompositionProjection.projectC(_identity_view, configuration, null)

      Then("the projection fails without creating a fallback resource or authority")
      result.isSuccess shouldBe false
      result.display should include("resolved resources are required")
    }

    "retain every supplied resolved resource in order" in {
      Given("a generator of arbitrary finite ordered already-resolved resource evidence")
      When("the projection receives each generated ordered resource vector")
      val property = Prop.forAll(Gen.listOf(Gen.choose(0, 24))) { indexes =>
        val entries = indexes.toVector.map(_generated_resource)
        val supplied = _resources(entries)
        val result = ComponentAdminConfigurationCompositionProjection.projectC(_identity_view, _configuration, supplied)

        result.toOption.exists(_.resources.resources == entries)
      }

      Then("at least fifty generated inputs retain every resource in the original order")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      checked.passed shouldBe true
    }
  }

  private val _configuration: ConfigurationBindingCollection[CncfConfigurationTarget] = {
    val parameter = CncfConfigurationParameterCatalog.subsystemUserMode
    val target = _target
    val binding = _take(ConfigurationBinding.initial(_candidate(parameter, target, SubsystemUserMode.MultiUser, "configuration-source", 10, 0)))
    _take(ConfigurationBindingCollection.from(Vector(binding)))
  }

  private val _identity_view: ComponentAdminViewModel = {
    val componentclass = ComponentAdminComponentClass(ComponentId("org.goldenport.cncf.admin.Sample"))
    val provenance = ComponentAdminSafeProvenance(
      ComponentAdminSourceKind.ResolvedResource,
      Some(ComponentResourceLogicalIdentity(componentclass.componentId, "1.0.0", None, "parent", "urn:cncf:resource:adm/primary"))
    )
    val componentfield = ComponentAdminViewField(componentclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val release = ComponentAdminLogicalRelease(componentclass, "1.0.0")
    val releasefield = ComponentAdminViewField(release, provenance)
    val instance = ComponentAdminLoadedInstance(componentclass, ComponentInstanceId(componentclass.componentId, "default"))
    val instancefield = ComponentAdminViewField(instance, ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val subsystemclass = ComponentAdminSubsystemClass("component-subsystem")
    val subsystemfield = ComponentAdminViewField(subsystemclass, ComponentAdminSafeProvenance(ComponentAdminSourceKind.Descriptor, None))
    val subsysteminstance = ComponentAdminViewField(ComponentAdminSubsystemInstance(subsystemclass, "main"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.RuntimeFact, None))
    val implicitsubsystem = ComponentAdminViewField(ComponentAdminImplicitComponentSubsystem(componentclass, "implicit"), ComponentAdminSafeProvenance(ComponentAdminSourceKind.KnowledgeManifest, None))
    _take(ComponentAdminViewModel.createC(
      componentfield,
      releasefield,
      Vector(releasefield),
      instancefield,
      Vector(instancefield),
      subsystemfield,
      subsysteminstance,
      implicitsubsystem,
      ComponentAdminViewField(ComponentAdminResourceState.Available, provenance)
    ))
  }

  private def _target: CncfConfigurationTarget =
    _take(CncfConfigurationTarget.SubsystemInstance.create(_take(SubsystemInstanceId.create("orders", "default"))))

  private def _candidate(
    parameter: ConfigurationParameter[SubsystemUserMode],
    target: CncfConfigurationTarget,
    value: SubsystemUserMode,
    sourceidentity: String,
    sourcerank: Int,
    sourceordinal: Int
  ): ConfigurationBindingCandidate[SubsystemUserMode, CncfConfigurationTarget] =
    _take(
      for {
        provenance <- ConfigurationProvenance.create(
          ConfigurationOrigin.Project,
          "textus",
          sourceidentity,
          Some(".textus/config.yaml"),
          Some(parameter.id.value),
          sourcerank,
          sourceordinal,
          Vector("resolved-resource-evidence"),
          isConfidential = true
        )
        candidate <- ConfigurationBindingCandidate.create(parameter, target, value, provenance)
      } yield candidate
    )

  private def _resources(entries: Vector[ResolvedComponentResource]): ResolvedComponentResources =
    ResolvedComponentResources(
      ComponentResourceCompositionShape.MultiComponent,
      entries,
      Vector(ComponentResourceDiagnostic(ComponentResourceDiagnosticKind.MultiComponent, None, None, "already-resolved test composition"))
    )

  private def _resource(
    componentid: ComponentId,
    parentcomponentid: Option[ComponentId],
    role: String,
    logicalresource: String,
    physicalsource: String,
    sourcekind: ComponentResourceSourceKind,
    availability: ComponentResourceAvailability,
    integrity: ComponentResourceIntegrity,
    authorization: ComponentResourceAuthorization
  ): ResolvedComponentResource =
    ResolvedComponentResource(
      ComponentResourceLogicalIdentity(componentid, "1.0.0", parentcomponentid, role, logicalresource),
      ComponentResourceProvenance(
        sourcekind,
        "https://repository.example.invalid/cncf/adm",
        s"org.example:adm-${role.toLowerCase}-car:1.0.0",
        "0000000000000000000000000000000000000000000000000000000000000000",
        s"expanded/${role.toLowerCase}.car",
        "composition-registry:adm",
        physicalsource,
        sourcekind match {
          case ComponentResourceSourceKind.EmbeddedPrimary => "embedded-primary:0"
          case ComponentResourceSourceKind.DevelopmentDirectory => "development-directory:1"
          case ComponentResourceSourceKind.ExpandedCar => "expanded-car:2"
          case ComponentResourceSourceKind.LocalRepository => "local-repository:3"
          case ComponentResourceSourceKind.ManagedCache => "managed-cache:4"
          case ComponentResourceSourceKind.OfflineBundle => "offline-bundle:5"
          case ComponentResourceSourceKind.RemoteRepository => "remote-repository:6"
        },
        role,
        logicalresource,
        "described",
        "Apache-2.0",
        false
      ),
      availability,
      integrity,
      authorization,
      activationAuthority = false,
      operationAuthority = false,
      mcpAuthority = false,
      disclosureAuthority = false,
      deploymentAuthority = false
    )

  private def _generated_resource(index: Int): ResolvedComponentResource = {
    val componentid = ComponentId(s"org.goldenport.cncf.admin.Generated$index")
    _resource(
      componentid,
      None,
      s"generated$index",
      s"urn:cncf:resource:adm/generated-$index",
      s"expanded-car:generated-$index",
      ComponentResourceSourceKind.ExpandedCar,
      ComponentResourceAvailability.Available,
      ComponentResourceIntegrity.Verified,
      ComponentResourceAuthorization.Granted
    )
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
