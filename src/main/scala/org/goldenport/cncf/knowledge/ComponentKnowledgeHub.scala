package org.goldenport.cncf.knowledge

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.{Component, ComponentActivation, ComponentInstanceId, ComponentKnowledgeProvider}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.observation.{Cause, Observation, Taxonomy}
import scala.util.control.NonFatal

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ComponentKnowledgeHub {
  def discoverC(knowledgeDomainId: String): Consequence[Option[ComponentKnowledgeContribution]]
  def readC(contribution: ComponentKnowledgeContribution, expectedSchema: KnowledgeContributionSchema): Consequence[ComponentKnowledgeSnapshot]
}

object ComponentKnowledgeHub {
  def createC(subsystem: Subsystem): Consequence[ComponentKnowledgeHub] =
    if (subsystem == null) _failure("argument-invalid")
    else Consequence.success(new AssemblyHub(subsystem))

  private final class BoundContribution(
    val owner: Subsystem,
    val provider: Component & ComponentKnowledgeProvider,
    declaration: KnowledgeContributionDeclaration
  ) extends ComponentKnowledgeContribution {
    override val descriptor: ComponentKnowledgeDescriptor =
      ComponentKnowledgeDescriptor(provider.instanceId, declaration)
  }

  private final class AssemblyHub(owner: Subsystem) extends ComponentKnowledgeHub {
    override def discoverC(knowledgeDomainId: String): Consequence[Option[ComponentKnowledgeContribution]] = {
      if (!_nonblank(knowledgeDomainId))
        _failure("argument-invalid")
      else {
        try {
          val providers = owner.components.collect {
            case provider: (Component & ComponentKnowledgeProvider) => provider
          }
          val declarations = providers.foldLeft(Consequence.success(Vector.empty[BoundContribution])) { (result, provider) =>
            result.flatMap(values => _declarations_c(provider).map(values ++ _))
          }
          declarations.flatMap { values =>
            val matches = values.filter(_.descriptor.knowledgeDomainId == knowledgeDomainId)
            matches.size match {
              case 0 => Consequence.success(None)
              case 1 => Consequence.success(Some(matches.head))
              case _ => _failure("conflict")
            }
          }
        } catch {
          case NonFatal(_) => _failure("declaration-invalid")
        }
      }
    }

    override def readC(contribution: ComponentKnowledgeContribution, expectedSchema: KnowledgeContributionSchema): Consequence[ComponentKnowledgeSnapshot] = {
      contribution match {
        case bound: BoundContribution if bound.owner eq owner =>
          _read_bound_c(bound, expectedSchema)
        case _ => _failure("foreign-contribution")
      }
    }

    private def _declarations_c(provider: Component & ComponentKnowledgeProvider): Consequence[Vector[BoundContribution]] = {
      val identity = _provider_identity(provider)
      try {
        val declarations = provider.knowledgeContributionDeclarations
        if (declarations == null || !declarations.forall(_valid_declaration))
          _failure("declaration-invalid", identity)
        else
          Consequence.success(declarations.map(declaration => new BoundContribution(owner, provider, declaration)))
      } catch {
        case NonFatal(_) => _failure("declaration-invalid", identity)
      }
    }

    private def _read_bound_c(bound: BoundContribution, expectedschema: KnowledgeContributionSchema): Consequence[ComponentKnowledgeSnapshot] = {
      val descriptor = bound.descriptor
      val identity = Some(descriptor.providerComponent)
      try {
        val installed = owner.components.exists(component => component eq bound.provider) &&
          bound.provider.subsystem.exists(subsystem => subsystem eq owner)
        if (!installed)
          _failure("provider-not-installed", identity)
        else if (!_valid_schema(expectedschema))
          _failure("argument-invalid", identity)
        else {
          descriptor.availability match {
            case KnowledgeContributionAvailability.Unavailable => _failure("unavailable", identity)
            case KnowledgeContributionAvailability.Failed => _failure("failed", identity)
            case KnowledgeContributionAvailability.Ready =>
              if (expectedschema != descriptor.schema)
                _failure("schema-incompatible", identity)
              else if (bound.provider.isInstanceOf[ComponentActivation] && !ComponentActivation.isCompleteFor(owner))
                _failure("activation-not-ready", identity)
              else
                _read_snapshot_c(bound)
          }
        }
      } catch {
        case NonFatal(_) => _failure("provider-failed", identity)
      }
    }

    private def _read_snapshot_c(bound: BoundContribution): Consequence[ComponentKnowledgeSnapshot] = {
      val descriptor = bound.descriptor
      val identity = Some(descriptor.providerComponent)
      try {
        bound.provider.readKnowledgeContributionC(descriptor.declaration) match {
          case Consequence.Success(snapshot) =>
            if (snapshot == null || snapshot.declaration != descriptor.declaration || snapshot.payload == null)
              _failure("snapshot-binding-invalid", identity)
            else
              Consequence.success(ComponentKnowledgeSnapshot(descriptor, snapshot.payload))
          case _ => _failure("provider-failed", identity)
        }
      } catch {
        case NonFatal(_) => _failure("provider-failed", identity)
      }
    }
  }

  private def _nonblank(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _valid_schema(schema: KnowledgeContributionSchema): Boolean =
    schema != null && _nonblank(schema.id) && _nonblank(schema.version)

  private def _valid_declaration(declaration: KnowledgeContributionDeclaration): Boolean =
    declaration != null &&
      _nonblank(declaration.knowledgeDomainId) &&
      _nonblank(declaration.generation) &&
      _valid_schema(declaration.schema) &&
      declaration.provenance != null && declaration.provenance.forall(_ != null) &&
      declaration.availability != null && declaration.contractVersion == 1

  private def _provider_identity(provider: Component): Option[ComponentInstanceId] = {
    try Option(provider.instanceId)
    catch { case NonFatal(_) => None }
  }

  private def _failure[A](code: String, provider: Option[ComponentInstanceId] = None): Consequence[A] = {
    val message = "knowledge-contribution." + code + provider.fold("")(identity => s" provider=$identity")
    Consequence.Failure[A](
      Conclusion.serviceUnavailable(Observation.failure(Taxonomy.serviceUnavailable, Cause.message(message)))
    )
  }
}
