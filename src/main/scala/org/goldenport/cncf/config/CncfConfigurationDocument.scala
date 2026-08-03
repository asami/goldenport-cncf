package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{
  CanonicalParameterId,
  ConfigurationBindingCandidateInput,
  ConfigurationDocument,
  ConfigurationParameter,
  ConfigurationSourceAdmission,
  ConfigurationValue
}
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}

/*
 * @since   Aug.  2, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait CncfConfigurationDocumentLocation

object CncfConfigurationDocumentLocation {
  case object Consolidated extends CncfConfigurationDocumentLocation
  case object Global extends CncfConfigurationDocumentLocation

  final class ComponentClass private[config] (
    val target: CncfConfigurationTarget.ComponentClass
  ) extends CncfConfigurationDocumentLocation

  object ComponentClass {
    def create(component: String): Consequence[ComponentClass] =
      CncfConfigurationTarget.ComponentClass.create(ComponentId(component)).map(new ComponentClass(_))
  }

  final class SubsystemInstance private[cncf] (
    val target: CncfConfigurationTarget.SubsystemInstance
  ) extends CncfConfigurationDocumentLocation

  object SubsystemInstance {
    def create(subsystem: String, instance: String): Consequence[SubsystemInstance] =
      for {
        identity <- SubsystemInstanceId.create(subsystem, instance)
        target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
      } yield new SubsystemInstance(target)
  }

  final class ComponentInstance private[config] (
    val target: CncfConfigurationTarget.ComponentInstance
  ) extends CncfConfigurationDocumentLocation

  object ComponentInstance {
    def create(
      subsystem: String,
      subsystemInstance: String,
      component: String,
      componentInstance: String
    ): Consequence[ComponentInstance] =
      for {
        subsystemidentity <- SubsystemInstanceId.create(subsystem, subsystemInstance)
        target <- CncfConfigurationTarget.ComponentInstance.create(
          subsystemidentity,
          ComponentInstanceId(component, componentInstance)
        )
      } yield new ComponentInstance(target)
  }
}

final case class CncfConfigurationDocumentBatch(
  location: CncfConfigurationDocumentLocation,
  admission: ConfigurationSourceAdmission[ConfigurationDocument]
)

sealed trait CncfConfigurationParameterDefinition {
  def canonicalSpelling: String
  def decodeOnlyAliases: Vector[String]
  def spellings: Vector[String]
  def parameterId: CanonicalParameterId
  def isConfidential: Boolean
  def allowedTargetKinds: Set[CncfConfigurationTargetKind]
  def input(
    spelling: String,
    target: CncfConfigurationTarget,
    rawValue: ConfigurationValue,
    path: String
  ): Consequence[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]
}

object CncfConfigurationParameterDefinition {
  final class Typed[A] private[config] (
    val canonicalSpelling: String,
    val decodeOnlyAliases: Vector[String],
    val parameter: ConfigurationParameter[A],
    val evidence: Vector[String],
    val isConfidential: Boolean,
    val allowedTargetKinds: Set[CncfConfigurationTargetKind]
  ) extends CncfConfigurationParameterDefinition {
    override val spellings: Vector[String] = canonicalSpelling +: decodeOnlyAliases
    override val parameterId: CanonicalParameterId = parameter.id

    override def input(
      spelling: String,
      target: CncfConfigurationTarget,
      rawValue: ConfigurationValue,
      path: String
    ): Consequence[ConfigurationBindingCandidateInput[CncfConfigurationTarget]] =
      if (spelling == null || !spellings.contains(spelling))
        Consequence.configurationInvalid("CNCF configuration parameter spelling is not admitted")
      else if (target == null || !allowedTargetKinds.contains(CncfConfigurationTargetKind.of(target)))
        Consequence.configurationInvalid("CNCF configuration parameter target is not admitted")
      else
        ConfigurationBindingCandidateInput.typed(
          parameter,
          target,
          rawValue,
          Some(path),
          Some(spelling),
          evidence,
          isConfidential
        )
  }

  def typed[A](
    spellings: Vector[String],
    parameter: ConfigurationParameter[A],
    evidence: Vector[String],
    isConfidential: Boolean
  ): Consequence[CncfConfigurationParameterDefinition] =
    if (spellings == null || spellings.isEmpty)
      Consequence.configurationInvalid("CNCF configuration parameter definition is invalid")
    else
      registered(
        spellings.head,
        spellings.tail,
        parameter,
        evidence,
        isConfidential,
        CncfConfigurationTargetKind.all
      )

  def registered[A](
    canonicalSpelling: String,
    decodeOnlyAliases: Vector[String],
    parameter: ConfigurationParameter[A],
    evidence: Vector[String],
    isConfidential: Boolean,
    allowedTargetKinds: Set[CncfConfigurationTargetKind]
  ): Consequence[CncfConfigurationParameterDefinition] =
    if (canonicalSpelling == null || canonicalSpelling.isEmpty || decodeOnlyAliases == null ||
      decodeOnlyAliases.exists(x => x == null || x.isEmpty) || parameter == null ||
      evidence == null || allowedTargetKinds == null || allowedTargetKinds.isEmpty ||
      !allowedTargetKinds.subsetOf(CncfConfigurationTargetKind.all))
      Consequence.configurationInvalid("CNCF configuration parameter definition is invalid")
    else {
      val spellings = canonicalSpelling +: decodeOnlyAliases
      if (spellings.distinct.size != spellings.size || canonicalSpelling != parameter.id.value)
        Consequence.configurationInvalid("CNCF configuration parameter definition spellings are invalid")
      else
        Consequence.success(new Typed(
          canonicalSpelling,
          decodeOnlyAliases,
          parameter,
          evidence,
          isConfidential,
          allowedTargetKinds
        ))
    }
}

sealed trait CncfConfigurationTargetKind

object CncfConfigurationTargetKind {
  case object Global extends CncfConfigurationTargetKind
  case object ComponentClass extends CncfConfigurationTargetKind
  case object SubsystemInstance extends CncfConfigurationTargetKind
  case object ComponentInstance extends CncfConfigurationTargetKind

  val all: Set[CncfConfigurationTargetKind] = Set(
    Global,
    ComponentClass,
    SubsystemInstance,
    ComponentInstance
  )

  def of(target: CncfConfigurationTarget): CncfConfigurationTargetKind =
    target match {
      case CncfConfigurationTarget.Global => Global
      case _: CncfConfigurationTarget.ComponentClass => ComponentClass
      case _: CncfConfigurationTarget.SubsystemInstance => SubsystemInstance
      case _: CncfConfigurationTarget.ComponentInstance => ComponentInstance
    }
}

final class CncfConfigurationDocumentSchema private (
  private val _definitions: Map[String, CncfConfigurationParameterDefinition]
) {
  def definition(
    spelling: String
  ): Consequence[CncfConfigurationParameterDefinition] =
    _definitions.get(spelling).fold[Consequence[CncfConfigurationParameterDefinition]](
      Consequence.configurationInvalid("CNCF configuration parameter spelling is not admitted")
    )(Consequence.success)
}

object CncfConfigurationDocumentSchema {
  def create(
    definitions: Vector[CncfConfigurationParameterDefinition]
  ): Consequence[CncfConfigurationDocumentSchema] =
    if (definitions == null || definitions.exists(_ == null))
      Consequence.configurationInvalid("CNCF configuration document schema is invalid")
    else {
      val spellings = definitions.flatMap(_.spellings)
      if (spellings.distinct.size != spellings.size)
        Consequence.configurationInvalid("CNCF configuration document schema contains duplicate spellings")
      else
        Consequence.success(new CncfConfigurationDocumentSchema(definitions.flatMap(x => x.spellings.map(_ -> x)).toMap))
    }
}

final case class CncfExternalDocumentScenarioInput(
  batches: Vector[CncfConfigurationDocumentBatch],
  schema: CncfConfigurationDocumentSchema
)
