package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity
import org.goldenport.cncf.knowledge.{ComponentKnowledgeHelpContract, ComponentKnowledgeHelpHumanNavigation, ComponentKnowledgeHelpManifestIdentity, ComponentKnowledgeHelpResourceNavigation, ComponentKnowledgeHelpResourceRoute, ComponentKnowledgeManifest, ComponentKnowledgeManifestConsumerContract, ComponentKnowledgeManifestConsumerResourceEvidence, ComponentKnowledgeResourceKind, ComponentKnowledgeResourceRole, ResolvedComponentKnowledge, ResolvedComponentKnowledgeEntry}

/*
 * Internal, value-only Component Admin documentation navigation. It binds
 * explicit caller selections to existing Phase 59 manifest and Help values;
 * it does not scan, generate, read, resolve, cache, or authorize resources.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class ComponentAdminDocumentationNavigationSelection(
  userguide: ComponentResourceLogicalIdentity,
  referencemanual: ComponentResourceLogicalIdentity,
  scaladoc: ComponentResourceLogicalIdentity,
  modeldiagrams: Vector[ComponentResourceLogicalIdentity],
  examples: Vector[ComponentResourceLogicalIdentity],
  sourceavailability: ComponentResourceLogicalIdentity,
  troubleshooting: ComponentResourceLogicalIdentity
) {
  def identities: Vector[ComponentResourceLogicalIdentity] =
    Vector(userguide, referencemanual, scaladoc) ++
      Option(modeldiagrams).getOrElse(Vector.empty) ++
      Option(examples).getOrElse(Vector.empty) ++
      Vector(sourceavailability, troubleshooting)
}

private[cncf] final case class ComponentAdminDocumentationNavigationFacts(
  knowledge: ResolvedComponentKnowledge,
  help: ComponentKnowledgeHelpContract,
  selection: ComponentAdminDocumentationNavigationSelection
)

private[cncf] final case class ComponentAdminDocumentationResourceNavigation(
  resource: ComponentKnowledgeManifestConsumerResourceEvidence,
  route: ComponentKnowledgeHelpResourceRoute
)

private[cncf] final case class ComponentAdminDocumentationNavigationView(
  identityview: ComponentAdminViewModel,
  helpnavigation: ComponentKnowledgeHelpHumanNavigation,
  userguide: ComponentAdminDocumentationResourceNavigation,
  referencemanual: ComponentAdminDocumentationResourceNavigation,
  scaladoc: ComponentAdminDocumentationResourceNavigation,
  modeldiagrams: Vector[ComponentAdminDocumentationResourceNavigation],
  examples: Vector[ComponentAdminDocumentationResourceNavigation],
  sourceavailability: ComponentAdminDocumentationResourceNavigation,
  troubleshooting: ComponentAdminDocumentationResourceNavigation,
  navigations: Vector[ComponentAdminDocumentationResourceNavigation]
)

private[cncf] object ComponentAdminDocumentationNavigationProjection {
  def projectC(
    identityView: ComponentAdminViewModel,
    facts: ComponentAdminDocumentationNavigationFacts
  ): Consequence[ComponentAdminDocumentationNavigationView] =
    ComponentAdminViewModel.validateC(identityView).flatMap { identityview =>
      if (facts == null) {
        Consequence.argumentInvalid("Component Admin documentation navigation facts are required")
      } else {
        _project(identityview, facts).fold(Consequence.argumentInvalid, Consequence.success)
      }
    }

  private def _project(
    identityview: ComponentAdminViewModel,
    facts: ComponentAdminDocumentationNavigationFacts
  ): Either[String, ComponentAdminDocumentationNavigationView] =
    for {
      knowledge <- Option(facts.knowledge).toRight("Component Admin documentation knowledge is required")
      manifest <- Option(knowledge.manifest).toRight("Component Admin documentation manifest is required")
      _ <- ComponentKnowledgeManifest.validateC(manifest).toOption.toRight("Component Admin documentation manifest is invalid")
      contract <- ComponentKnowledgeManifestConsumerContract.fromManifestC(manifest).toOption.toRight("Component Admin documentation consumer contract is invalid")
      _ <- _identity_matches(identityview, contract)
      _ <- _knowledge_matches(knowledge, manifest)
      help <- Option(facts.help).toRight("Component Admin Help navigation is required")
      _ <- _help_matches(help, contract)
      selection <- Option(facts.selection).toRight("Component Admin documentation selection is required")
      _ <- _validate_selection(selection, contract)
      userguide <- _navigation(selection.userguide, contract, help.humanNavigation)
      referencemanual <- _navigation(selection.referencemanual, contract, help.humanNavigation)
      scaladoc <- _navigation(selection.scaladoc, contract, help.humanNavigation)
      modeldiagrams <- _navigations(selection.modeldiagrams, contract, help.humanNavigation)
      examples <- _navigations(selection.examples, contract, help.humanNavigation)
      sourceavailability <- _navigation(selection.sourceavailability, contract, help.humanNavigation)
      troubleshooting <- _navigation(selection.troubleshooting, contract, help.humanNavigation)
      values = Vector(userguide, referencemanual, scaladoc) ++ modeldiagrams ++ examples ++ Vector(sourceavailability, troubleshooting)
      navigations <- _help_ordered(values, help.humanNavigation)
    } yield ComponentAdminDocumentationNavigationView(
      identityview,
      help.humanNavigation,
      userguide,
      referencemanual,
      scaladoc,
      modeldiagrams,
      examples,
      sourceavailability,
      troubleshooting,
      navigations
    )

  private def _identity_matches(
    identityview: ComponentAdminViewModel,
    contract: ComponentKnowledgeManifestConsumerContract
  ): Either[String, Unit] =
    if (identityview.componentClass.value.componentId != contract.componentId) {
      Left("Component Admin documentation manifest component identity must match the Admin identity view")
    } else if (identityview.selectedLogicalRelease.value.release != contract.logicalRelease) {
      Left("Component Admin documentation manifest logical release must match the Admin identity view")
    } else {
      Right(())
    }

  private def _knowledge_matches(
    knowledge: ResolvedComponentKnowledge,
    manifest: ComponentKnowledgeManifest
  ): Either[String, Unit] = {
    val pairs = knowledge.entries
    if (pairs == null) {
      Left("Component Admin documentation knowledge entries are required")
    } else if (pairs.exists(_ == null)) {
      Left("Component Admin documentation knowledge entries must not contain null values")
    } else if (pairs.size != manifest.resources.size) {
      Left("Component Admin documentation knowledge must retain exactly one Phase 58 pair for every manifest resource")
    } else {
      _sequence(manifest.resources.map { entry =>
        pairs.filter(_.entry == entry) match {
          case Vector(pair) => _pair_matches(entry, pair)
          case Vector() => Left(s"Component Admin documentation manifest resource ${entry.logicalPath} has no exact Phase 58 pair")
          case _ => Left(s"Component Admin documentation manifest resource ${entry.logicalPath} has more than one Phase 58 pair")
        }
      })
    }
  }

  private def _pair_matches(
    entry: org.goldenport.cncf.knowledge.ComponentKnowledgeResourceEntry,
    pair: ResolvedComponentKnowledgeEntry
  ): Either[String, Unit] = {
    val resource = pair.resource
    if (resource == null || resource.provenance == null) {
      Left(s"Component Admin documentation resource ${entry.logicalPath} must retain resolver-owned evidence")
    } else if (resource.logicalIdentity != entry.binding.logicalIdentity) {
      Left(s"Component Admin documentation resource ${entry.logicalPath} must retain the exact manifest logical identity")
    } else if (
      resource.availability != entry.availability ||
        resource.integrity != entry.integrity ||
        resource.authorization != entry.authorization
    ) {
      Left(s"Component Admin documentation resource ${entry.logicalPath} must retain exact Phase 58 state")
    } else if (
      resource.provenance.sourceKind != entry.provenance.sourceKind ||
        resource.provenance.artifactCoordinate != entry.provenance.artifactCoordinate ||
        resource.provenance.logicalSource != entry.provenance.logicalSource ||
        resource.provenance.resolutionStep != entry.provenance.resolutionStep ||
        resource.provenance.externalDeploymentRequired != entry.provenance.externalDeploymentRequired ||
        resource.provenance.sha256 != entry.sha256 ||
        resource.provenance.sha256 != entry.provenance.matchingDigest
    ) {
      Left(s"Component Admin documentation resource ${entry.logicalPath} must retain exact Phase 58 provenance")
    } else if (
      resource.activationAuthority ||
        resource.operationAuthority ||
        resource.mcpAuthority ||
        resource.disclosureAuthority ||
        resource.deploymentAuthority
    ) {
      Left(s"Component Admin documentation resource ${entry.logicalPath} must not grant authority")
    } else {
      Right(())
    }
  }

  private def _help_matches(
    help: ComponentKnowledgeHelpContract,
    contract: ComponentKnowledgeManifestConsumerContract
  ): Either[String, Unit] = {
    val human = help.humanNavigation
    val directai = help.directAiNavigation
    val identity = ComponentKnowledgeHelpManifestIdentity(contract.componentId, contract.logicalRelease)
    if (human == null || directai == null) {
      Left("Component Admin Help navigation is incomplete")
    } else if (human.manifestIdentity != identity || directai.manifestIdentity != identity) {
      Left("Component Admin Help navigation must match the Admin manifest identity and logical release")
    } else if (directai.manifestConsumerContract != contract) {
      Left("Component Admin Help navigation must retain the exact existing manifest consumer contract")
    } else if (human.resources != directai.resources) {
      Left("Component Admin Help human and Direct-AI navigation must retain the same resource inventory")
    } else {
      _help_resources_match(human.resources, contract.resources, identity)
    }
  }

  private def _help_resources_match(
    navigations: Vector[ComponentKnowledgeHelpResourceNavigation],
    resources: Vector[ComponentKnowledgeManifestConsumerResourceEvidence],
    identity: ComponentKnowledgeHelpManifestIdentity
  ): Either[String, Unit] =
    if (navigations == null || resources == null) {
      Left("Component Admin Help resource navigation is required")
    } else if (navigations.exists(_ == null)) {
      Left("Component Admin Help resource navigation must not contain null values")
    } else if (navigations.size != resources.size) {
      Left("Component Admin Help resource navigation must retain every manifest resource")
    } else {
      _sequence(resources.map { resource =>
        navigations.filter(_.logicalPath == resource.logicalPath) match {
          case Vector(navigation) =>
            if (
              navigation.kind != resource.kind ||
                navigation.role != resource.role ||
                navigation.language != resource.language ||
                navigation.mediaType != resource.mediaType ||
                navigation.availability != resource.availability ||
                navigation.integrity != resource.integrity ||
                navigation.authorization != resource.authorization
            ) {
              Left(s"Component Admin Help resource navigation must retain exact manifest state for ${resource.logicalPath}")
            } else if (
              navigation.route == null ||
                navigation.route.manifestIdentity != identity ||
                navigation.route.logicalPath != resource.logicalPath
            ) {
              Left(s"Component Admin Help route must be canonical for ${resource.logicalPath}")
            } else {
              Right(())
            }
          case Vector() => Left(s"Component Admin Help navigation has no route for ${resource.logicalPath}")
          case _ => Left(s"Component Admin Help navigation has ambiguous routes for ${resource.logicalPath}")
        }
      })
    }

  private def _validate_selection(
    selection: ComponentAdminDocumentationNavigationSelection,
    contract: ComponentKnowledgeManifestConsumerContract
  ): Either[String, Unit] =
    if (
      selection.userguide == null ||
        selection.referencemanual == null ||
        selection.scaladoc == null ||
        selection.modeldiagrams == null ||
        selection.examples == null ||
        selection.sourceavailability == null ||
        selection.troubleshooting == null
    ) {
      Left("Component Admin documentation selection is incomplete")
    } else if (selection.modeldiagrams.isEmpty || selection.examples.isEmpty) {
      Left("Component Admin documentation selection requires model diagrams and examples")
    } else if (selection.identities.exists(_ == null)) {
      Left("Component Admin documentation selection must not contain null identities")
    } else if (selection.identities.distinct.size != selection.identities.size) {
      Left("Component Admin documentation selection must not repeat a manifest resource")
    } else {
      for {
        userguide <- _resource(selection.userguide, contract)
        _ <- _documentation_resource(userguide, "User Guide")
        referencemanual <- _resource(selection.referencemanual, contract)
        _ <- _documentation_resource(referencemanual, "Reference Manual")
        scaladoc <- _resource(selection.scaladoc, contract)
        _ <- _documentation_resource(scaladoc, "Scaladoc")
        diagrams <- _resources(selection.modeldiagrams, contract)
        _ <- _sequence(diagrams.map(_diagram_resource))
        examples <- _resources(selection.examples, contract)
        _ <- _sequence(examples.map(value => _documentation_resource(value, "example")))
        source <- _resource(selection.sourceavailability, contract)
        _ <- _source_resource(source)
        troubleshooting <- _resource(selection.troubleshooting, contract)
        _ <- _documentation_resource(troubleshooting, "troubleshooting")
      } yield ()
    }

  private def _documentation_resource(
    resource: ComponentKnowledgeManifestConsumerResourceEvidence,
    label: String
  ): Either[String, Unit] =
    Either.cond(
      resource.kind == ComponentKnowledgeResourceKind.Documentation && resource.role == ComponentKnowledgeResourceRole.Documentation,
      (),
      s"Component Admin documentation $label selection must identify a documentation resource"
    )

  private def _diagram_resource(
    resource: ComponentKnowledgeManifestConsumerResourceEvidence
  ): Either[String, Unit] =
    Either.cond(
      (resource.kind == ComponentKnowledgeResourceKind.ClassDiagram || resource.kind == ComponentKnowledgeResourceKind.StateDiagram) &&
        resource.role == ComponentKnowledgeResourceRole.Diagram,
      (),
      "Component Admin documentation model diagram selection must identify a diagram resource"
    )

  private def _source_resource(
    resource: ComponentKnowledgeManifestConsumerResourceEvidence
  ): Either[String, Unit] =
    Either.cond(
      resource.kind == ComponentKnowledgeResourceKind.SourceCode && resource.role == ComponentKnowledgeResourceRole.SourceCode,
      (),
      "Component Admin documentation source availability selection must identify a source resource"
    )

  private def _navigation(
    identity: ComponentResourceLogicalIdentity,
    contract: ComponentKnowledgeManifestConsumerContract,
    helpnavigation: ComponentKnowledgeHelpHumanNavigation
  ): Either[String, ComponentAdminDocumentationResourceNavigation] =
    for {
      resource <- _resource(identity, contract)
      route <- helpnavigation.resources.find(_.logicalPath == resource.logicalPath).map(_.route)
        .toRight(s"Component Admin Help navigation has no route for ${resource.logicalPath}")
    } yield ComponentAdminDocumentationResourceNavigation(resource, route)

  private def _navigations(
    identities: Vector[ComponentResourceLogicalIdentity],
    contract: ComponentKnowledgeManifestConsumerContract,
    helpnavigation: ComponentKnowledgeHelpHumanNavigation
  ): Either[String, Vector[ComponentAdminDocumentationResourceNavigation]] =
    _sequence_values(identities.map(_navigation(_, contract, helpnavigation)))

  private def _help_ordered(
    values: Vector[ComponentAdminDocumentationResourceNavigation],
    helpnavigation: ComponentKnowledgeHelpHumanNavigation
  ): Either[String, Vector[ComponentAdminDocumentationResourceNavigation]] = {
    val valuebyroute = values.map(value => value.route.logicalPath -> value).toMap
    val paths = helpnavigation.resources.map(_.logicalPath)
    if (valuebyroute.size != values.size) {
      Left("Component Admin documentation navigation must not repeat a selected Help resource route")
    } else {
      Right(paths.filter(valuebyroute.contains).map(valuebyroute))
    }
  }

  private def _resource(
    identity: ComponentResourceLogicalIdentity,
    contract: ComponentKnowledgeManifestConsumerContract
  ): Either[String, ComponentKnowledgeManifestConsumerResourceEvidence] =
    contract.resources.filter(_.logicalIdentity == identity) match {
      case Vector(resource) => Right(resource)
      case Vector() => Left("Component Admin documentation selection must identify an admitted manifest resource")
      case _ => Left("Component Admin documentation selection is ambiguous in the manifest resource set")
    }

  private def _resources(
    identities: Vector[ComponentResourceLogicalIdentity],
    contract: ComponentKnowledgeManifestConsumerContract
  ): Either[String, Vector[ComponentKnowledgeManifestConsumerResourceEvidence]] =
    _sequence_values(identities.map(_resource(_, contract)))

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft[Either[String, Unit]](Right(()))((result, value) => result.flatMap(_ => value))

  private def _sequence_values[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (result, value) =>
      for {
        xs <- result
        x <- value
      } yield xs :+ x
    }
}
