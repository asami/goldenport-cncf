package org.goldenport.cncf.config

import scala.jdk.CollectionConverters._

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCandidateInput, ConfigurationBindingCandidates, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationResolutionSnapshot, ConfigurationRuntimeSourceSnapshot, ConfigurationSourceAdmission}
import org.goldenport.configuration.source.ConfigurationSource

/*
 * Projects already-loaded runtime sources into the closed CNCF catalog.  It
 * never loads a physical source and must not receive a merged configuration.
 *
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntimeConfigurationProjection {
  def global(
    snapshot: ConfigurationResolutionSnapshot,
    argumentBindings: Vector[CncfConfigurationArgumentBindingAssignment] = Vector.empty,
    environmentBindings: Vector[CncfConfigurationEnvironmentBindingAssignment] = Vector.empty
  ): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    if (snapshot == null || argumentBindings == null || argumentBindings.exists(_ == null) || environmentBindings == null || environmentBindings.exists(_ == null))
      Consequence.configurationInvalid("CNCF global runtime configuration projection is invalid")
    else
      for {
        candidates <- globalCandidates(snapshot, argumentBindings, environmentBindings)
        context <- CncfConfigurationResolutionContext.globalOnly
        collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
      } yield collection

  def globalCandidates(
    snapshot: ConfigurationResolutionSnapshot,
    argumentBindings: Vector[CncfConfigurationArgumentBindingAssignment] = Vector.empty,
    environmentBindings: Vector[CncfConfigurationEnvironmentBindingAssignment] = Vector.empty
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (snapshot == null || argumentBindings == null || argumentBindings.exists(_ == null) || environmentBindings == null || environmentBindings.exists(_ == null))
      Consequence.configurationInvalid("CNCF global runtime configuration candidate projection is invalid")
    else
      for {
        batches <- _batches(snapshot, CncfConfigurationDocumentLocation.Global)
        additions <- _binding_additions(batches, argumentBindings, environmentBindings)
        candidates <- CncfConfigurationCandidateDecoder.decodeCatalogSnapshots(batches.map(x => x._2 -> x._3), additions)
      } yield candidates

  def subsystemUserMode(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: SubsystemInstanceId
  ): Consequence[Option[ConfigurationBinding[SubsystemUserMode, CncfConfigurationTarget]]] =
    if (snapshot == null || subsystem == null)
      Consequence.configurationInvalid("CNCF runtime configuration projection is invalid")
    else
      for {
        collection <- forSubsystem(snapshot, subsystem)
        binding <- collection.binding(CncfConfigurationParameterCatalog.subsystemUserMode)
      } yield binding

  def forSubsystem(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    if (snapshot == null || subsystem == null)
      Consequence.configurationInvalid("CNCF runtime configuration projection is invalid")
    else
      for {
        candidates <- candidates(snapshot, subsystem)
        context <- CncfConfigurationResolutionContext.forSubsystem(subsystem)
        collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
      } yield collection

  def candidates(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    candidates(snapshot, subsystem, Vector.empty, Vector.empty)

  def candidates(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: SubsystemInstanceId,
    argumentBindings: Vector[CncfConfigurationArgumentBindingAssignment],
    environmentBindings: Vector[CncfConfigurationEnvironmentBindingAssignment] = Vector.empty
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (snapshot == null || subsystem == null || argumentBindings == null || argumentBindings.exists(_ == null) || environmentBindings == null || environmentBindings.exists(_ == null))
      Consequence.configurationInvalid("CNCF runtime configuration candidate projection is invalid")
    else
      for {
        target <- CncfConfigurationTarget.SubsystemInstance.create(subsystem)
        batches <- _batches(snapshot, new CncfConfigurationDocumentLocation.SubsystemInstance(target))
        additions <- _binding_additions(batches, argumentBindings, environmentBindings)
        candidates <- CncfConfigurationCandidateDecoder.decodeCatalogSnapshots(batches.map(x => x._2 -> x._3), additions)
      } yield candidates

  private def _batches(
    snapshot: ConfigurationResolutionSnapshot,
    defaultlocation: CncfConfigurationDocumentLocation
  ): Consequence[Vector[(ConfigurationRuntimeSourceSnapshot, CncfConfigurationDocumentBatch, ConfigurationDocument)]] =
    snapshot.sources.foldLeft(Consequence.success(Vector.empty[(ConfigurationRuntimeSourceSnapshot, CncfConfigurationDocumentBatch, ConfigurationDocument)])) {
      case (acc, source) =>
        for {
          batches <- acc
          splitlocation <- _split_file_location(source)
          location = splitlocation.getOrElse(defaultlocation)
          spellings <- _spellings(location)
          consolidated = splitlocation.isEmpty && _is_consolidated_file(source)
          document <- _source_document(source, spellings, consolidated, splitlocation.nonEmpty)
          documentlocation = if (consolidated)
            CncfConfigurationDocumentLocation.Consolidated
          else
            location
          admission <- ConfigurationSourceAdmission.create(
            source.origin,
            _layer(source),
            source.sourceIdentity,
            source.sourceRank,
            _collision_domain(source, splitlocation.nonEmpty || _is_canonical_textus_consolidated_file(source)),
            () => Consequence.success(document),
            _source_type(source)
          )
        } yield batches :+ (
          source,
          CncfConfigurationDocumentBatch(
            documentlocation,
            admission
          ),
          document
        )
    }

  private def _spellings(
    location: CncfConfigurationDocumentLocation
  ): Consequence[Set[String]] = {
    val targetkind = location match {
      case CncfConfigurationDocumentLocation.Global => CncfConfigurationTargetKind.Global
      case _: CncfConfigurationDocumentLocation.ComponentClass => CncfConfigurationTargetKind.ComponentClass
      case _: CncfConfigurationDocumentLocation.SubsystemInstance => CncfConfigurationTargetKind.SubsystemInstance
      case _: CncfConfigurationDocumentLocation.ComponentInstance => CncfConfigurationTargetKind.ComponentInstance
      case _ => return Consequence.configurationInvalid("CNCF runtime configuration document location is invalid")
    }
    Consequence.success(CncfConfigurationParameterCatalog.closed.definitions
      .filter(_.allowedTargetKinds.contains(targetkind))
      .flatMap(_.spellings)
      .toSet)
  }

  private def _binding_additions(
    batches: Vector[(ConfigurationRuntimeSourceSnapshot, CncfConfigurationDocumentBatch, ConfigurationDocument)],
    argumentbindings: Vector[CncfConfigurationArgumentBindingAssignment],
    environmentbindings: Vector[CncfConfigurationEnvironmentBindingAssignment]
  ): Consequence[Vector[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]]] = {
    val argumentindices = batches.zipWithIndex.collect {
      case ((source, _, _), index) if source.source.isInstanceOf[ConfigurationSource.Args] => index
    }
    val environmentindices = batches.zipWithIndex.collect {
      case ((source, _, _), index) if source.source.isInstanceOf[ConfigurationSource.Env] => index
    }
    if (argumentbindings.nonEmpty && argumentindices.size != 1)
      Consequence.configurationInvalid("CNCF runtime configuration requires exactly one argument source")
    else if (environmentbindings.nonEmpty && environmentindices.size != 1)
      Consequence.configurationInvalid("CNCF runtime configuration requires exactly one environment source")
    else
      for {
        argumentinputs <- if (argumentbindings.isEmpty)
          Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])
        else
          CncfConfigurationArgumentBindingCandidateDecoder.inputs(argumentbindings, CncfConfigurationParameterCatalog.closed)
        environmentinputs <- if (environmentbindings.isEmpty)
          Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])
        else
          CncfConfigurationEnvironmentBindingCandidateDecoder.inputs(environmentbindings, CncfConfigurationParameterCatalog.closed)
      } yield {
        batches.indices.map { index =>
          (if (argumentindices.contains(index)) argumentinputs else Vector.empty) ++
            (if (environmentindices.contains(index)) environmentinputs else Vector.empty)
        }.toVector
      }
  }

  private def _layer(source: org.goldenport.configuration.ConfigurationRuntimeSourceSnapshot): String = {
    val identity = source.sourceIdentity.toLowerCase(java.util.Locale.ROOT).replace('\\', '/')
    if (identity.contains("/.textus/") || identity.endsWith("/.textus.conf"))
      "textus"
    else if (identity.contains("/.cncf/") || identity.endsWith("/.cncf.conf"))
      "cncf"
    else
      source.origin.toString.toLowerCase(java.util.Locale.ROOT)
  }

  private def _collision_domain(
    source: ConfigurationRuntimeSourceSnapshot,
    canonicaltree: Boolean
  ): String =
    if (canonicaltree)
      s"${source.origin}:${source.sourceRank}:canonical-textus-tree"
    else
      source.sourceIdentity

  private def _source_type(
    source: org.goldenport.configuration.ConfigurationRuntimeSourceSnapshot
  ): Option[String] =
    source.source match {
      case _: ConfigurationSource.File => Some("file")
      case _: ConfigurationSource.Args => Some("arguments")
      case _: ConfigurationSource.Env => Some("environment")
      case _ => None
    }

  private def _source_document(
    source: ConfigurationRuntimeSourceSnapshot,
    spellings: Set[String],
    consolidated: Boolean,
    enforcetarget: Boolean
  ): Consequence[ConfigurationDocument] = {
    val document = _file_raw_document(source) match {
      case Some(raw) if consolidated => raw
      case Some(raw) =>
        ConfigurationDocument.Object(
          _flatten_document(raw)
            .collect { case (spelling, value) if spellings.contains(spelling) => spelling -> value }
            .map { case (spelling, value) =>
              ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(value))
            }
        )
      case None if consolidated => _document(source.value.values)
      case None =>
        ConfigurationDocument.Object(
          _flatten(source.value.values)
        .collect { case (spelling, value) if spellings.contains(spelling) => spelling -> value }
        .groupBy(_._1)
        .values
        .map(_.last)
        .toVector
        .sortBy(_._1)
        .map { case (spelling, value) =>
          ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(value))
        }
        )
    }
    val targetdocument = _file_raw_document(source).getOrElse(document)
    if (enforcetarget)
      _reject_disallowed_target_spellings(targetdocument, spellings)
        .map(_ => document)
    else
      Consequence.success(document)
  }

  /**
   * A canonical split file names one concrete target. Recognized catalog
   * spellings outside that target must not disappear during source filtering:
   * they are structural admission errors, not inert legacy keys.
   */
  private def _reject_disallowed_target_spellings(
    document: ConfigurationDocument,
    allowedspellings: Set[String]
  ): Consequence[Unit] = {
    val rejected = _flatten_document(document)
      .map(_._1)
      .filter { spelling =>
        CncfConfigurationParameterCatalog.closed.schema.definition(spelling).isSuccess &&
          !allowedspellings.contains(spelling)
      }
      .distinct
      .sorted
    if (rejected.nonEmpty)
      Consequence.configurationInvalid(
        s"CNCF configuration parameter target is not admitted: ${rejected.mkString(", ")}"
      )
    else
      Consequence.success(())
  }

  private def _file_raw_document(
    source: ConfigurationRuntimeSourceSnapshot
  ): Option[ConfigurationDocument.Object] =
    if (source != null && source.source.isInstanceOf[ConfigurationSource.File])
      source.rawDocument
    else
      None

  private def _is_consolidated_document(
    values: Map[String, org.goldenport.configuration.ConfigurationValue]
  ): Boolean =
    values != null && values.keys.exists(Set("global", "components", "subsystems"))

  private def _is_consolidated_file(
    source: ConfigurationRuntimeSourceSnapshot
  ): Boolean =
    source != null && source.source.isInstanceOf[ConfigurationSource.File] &&
      (_file_raw_document(source).map(_is_consolidated_document).getOrElse(
        _is_consolidated_document(source.value.values)
      ))

  private def _is_canonical_textus_consolidated_file(
    source: ConfigurationRuntimeSourceSnapshot
  ): Boolean =
    source.source match {
      case file: ConfigurationSource.File =>
        _split_file_parts(file.path).contains(Vector("config.yaml")) &&
          (_file_raw_document(source).map(_is_consolidated_document).getOrElse(
            _is_consolidated_document(source.value.values)
          ))
      case _ => false
    }

  private def _split_file_location(
    source: ConfigurationRuntimeSourceSnapshot
  ): Consequence[Option[CncfConfigurationDocumentLocation]] =
    source.source match {
      case file: ConfigurationSource.File =>
        _split_file_parts(file.path).fold(Consequence.success(Option.empty[CncfConfigurationDocumentLocation])) {
          case Vector("components", component, "config.yaml") =>
            CncfConfigurationDocumentLocation.ComponentClass.create(component).map(Some(_))
          case Vector("subsystems", subsystem, "instances", instance, "config.yaml") =>
            CncfConfigurationDocumentLocation.SubsystemInstance.create(subsystem, instance).map(Some(_))
          case Vector("subsystems", subsystem, "instances", instance, "components", component, "instances", componentinstance, "config.yaml") =>
            CncfConfigurationDocumentLocation.ComponentInstance.create(
              subsystem,
              instance,
              component,
              componentinstance
            ).map(Some(_))
          case _ => Consequence.success(None)
        }
      case _ => Consequence.success(None)
    }

  private def _split_file_parts(
    path: java.nio.file.Path
  ): Option[Vector[String]] =
    Option(path).flatMap { value =>
      val parts = value.normalize.iterator().asScala.map(_.toString).toVector
      parts.lastIndexOf(".textus") match {
        case index if index >= 0 => Some(parts.drop(index + 1))
        case _ => None
      }
    }

  private def _document(
    values: Map[String, org.goldenport.configuration.ConfigurationValue]
  ): ConfigurationDocument.Object =
    ConfigurationDocument.Object(
      values.toVector.sortBy(_._1).map { case (name, value) =>
        val document = value match {
          case org.goldenport.configuration.ConfigurationValue.ObjectValue(nested) => _document(nested)
          case scalar => ConfigurationDocument.Scalar(scalar)
        }
        ConfigurationDocument.Field(name, document)
      }
    )

  private def _is_consolidated_document(
    document: ConfigurationDocument.Object
  ): Boolean =
    document != null && document.fields.exists(x => Set("global", "components", "subsystems").contains(x.name))

  private def _flatten_document(
    document: ConfigurationDocument,
    prefix: Vector[String] = Vector.empty
  ): Vector[(String, org.goldenport.configuration.ConfigurationValue)] =
    document match {
      case ConfigurationDocument.Object(fields) =>
        fields.flatMap { field =>
          _flatten_document(field.value, prefix :+ field.name)
        }
      case ConfigurationDocument.Scalar(value) => Vector(prefix.mkString(".") -> value)
      case ConfigurationDocument.Sequence(values) =>
        Vector(prefix.mkString(".") -> org.goldenport.configuration.ConfigurationValue.ListValue(
          values.map(_document_value).toList
        ))
    }

  private def _document_value(
    document: ConfigurationDocument
  ): org.goldenport.configuration.ConfigurationValue =
    document match {
      case ConfigurationDocument.Scalar(value) => value
      case ConfigurationDocument.Sequence(values) =>
        org.goldenport.configuration.ConfigurationValue.ListValue(values.map(_document_value).toList)
      case ConfigurationDocument.Object(fields) =>
        org.goldenport.configuration.ConfigurationValue.ObjectValue(
          fields.foldLeft(Map.empty[String, org.goldenport.configuration.ConfigurationValue]) { case (z, field) =>
            z.updated(field.name, _document_value(field.value))
          }
        )
    }

  private def _flatten(
    values: Map[String, org.goldenport.configuration.ConfigurationValue],
    prefix: Vector[String] = Vector.empty
  ): Vector[(String, org.goldenport.configuration.ConfigurationValue)] =
    values.toVector.flatMap { case (key, value) =>
      val path = prefix :+ key
      value match {
        case org.goldenport.configuration.ConfigurationValue.ObjectValue(nested) =>
          _flatten(nested, path)
        case scalar =>
          Vector(path.mkString(".") -> scalar)
      }
    }
}
