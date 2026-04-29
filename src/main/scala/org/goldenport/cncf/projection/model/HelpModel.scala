package org.goldenport.cncf.projection.model

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class HelpModel(
  `type`: String,
  name: String,
  summary: String,
  component: Option[String] = None,
  service: Option[String] = None,
  selector: Option[HelpSelectorModel] = None,
  children: Vector[String] = Vector.empty,
  details: Map[String, Vector[String]] = Map.empty,
  childEntityBindings: Vector[org.goldenport.record.Record] = Vector.empty,
  associationBinding: Option[org.goldenport.record.Record] = None,
  imageBinding: Option[org.goldenport.record.Record] = None,
  usage: Vector[String] = Vector.empty,
  domainVisions: Vector[HelpVisionModel] = Vector.empty,
  domainContexts: Vector[HelpContextModel] = Vector.empty,
  domainSystemContexts: Vector[HelpSystemContextModel] = Vector.empty,
  domainContextMaps: Vector[HelpContextMapModel] = Vector.empty,
  domainCapabilities: Vector[HelpCapabilityModel] = Vector.empty,
  domainQualities: Vector[HelpQualityModel] = Vector.empty,
  domainConstraints: Vector[HelpConstraintModel] = Vector.empty,
  useCases: Vector[HelpUseCaseModel] = Vector.empty,
  domainUseCases: Vector[HelpUseCaseModel] = Vector.empty
)

final case class HelpVisionModel(
  name: String,
  summary: Option[String] = None,
  goal: Option[String] = None,
  precondition: Option[String] = None,
  postcondition: Option[String] = None
)

final case class HelpContextModel(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None
)

final case class HelpSystemContextModel(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None
)

final case class HelpContextMapModel(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None
)

final case class HelpCapabilityModel(
  name: String,
  summary: Option[String] = None,
  actor: Option[String] = None,
  primaryActor: Option[String] = None,
  secondaryActor: Option[String] = None,
  supportingActor: Option[String] = None,
  stakeholder: Option[String] = None,
  goal: Option[String] = None,
  precondition: Option[String] = None,
  postcondition: Option[String] = None
)

final case class HelpQualityModel(
  name: String,
  summary: Option[String] = None,
  goal: Option[String] = None,
  precondition: Option[String] = None,
  postcondition: Option[String] = None
)

final case class HelpConstraintModel(
  name: String,
  summary: Option[String] = None,
  goal: Option[String] = None,
  precondition: Option[String] = None,
  postcondition: Option[String] = None
)

final case class HelpUseCaseModel(
  name: String,
  summary: Option[String] = None,
  actor: Option[String] = None,
  primaryActor: Option[String] = None,
  secondaryActor: Option[String] = None,
  supportingActor: Option[String] = None,
  stakeholder: Option[String] = None,
  goal: Option[String] = None,
  precondition: Option[String] = None,
  postcondition: Option[String] = None,
  scenarios: Vector[HelpUseCaseScenarioModel] = Vector.empty
)

final case class HelpUseCaseScenarioModel(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  steps: Vector[String] = Vector.empty,
  alternates: Vector[String] = Vector.empty,
  exceptions: Vector[String] = Vector.empty
)

final case class HelpSelectorModel(
  canonical: String,
  cli: String,
  rest: String,
  accepted: Vector[String] = Vector.empty
)
