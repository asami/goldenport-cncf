package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{
  ConfigurationBindingCandidateBatch,
  ConfigurationBindingCandidateConstructor,
  ConfigurationBindingCandidateInput,
  ConfigurationBindingCandidates,
  ConfigurationDocument,
  ConfigurationSourceSnapshot,
  ConfigurationSourceSnapshots
}
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}

/*
 * @since   Aug.  2, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfConfigurationCandidateDecoder {
  import CncfConfigurationDocumentLocation.*

  def decodeCatalog(
    batches: Vector[CncfConfigurationDocumentBatch]
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (batches == null || batches.isEmpty || batches.exists(_ == null))
      Consequence.configurationInvalid("CNCF catalog configuration document batches are invalid")
    else
      decode(CncfExternalDocumentScenarioInput(batches, CncfConfigurationParameterCatalog.closed.schema))

  def decodeCatalogSnapshots(
    batches: Vector[(CncfConfigurationDocumentBatch, ConfigurationDocument)],
    supplementalInputs: Vector[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] = Vector.empty
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (batches == null || batches.isEmpty || batches.exists { case (batch, document) => batch == null || document == null } ||
      supplementalInputs == null || (supplementalInputs.nonEmpty && supplementalInputs.size != batches.size) || supplementalInputs.flatten.exists(_ == null))
      Consequence.configurationInvalid("CNCF catalog configuration snapshots are invalid")
    else {
      val additions = if (supplementalInputs.isEmpty) Vector.fill(batches.size)(Vector.empty) else supplementalInputs
      for {
        snapshots <- ConfigurationSourceSnapshots.fromValues(batches.map { case (batch, document) => batch.admission -> document })
        candidatebatches <- _batches(snapshots.snapshots.zip(batches.map(_._1)).zip(additions), CncfConfigurationParameterCatalog.closed.schema)
        candidates <- ConfigurationBindingCandidateConstructor.construct(candidatebatches)
      } yield candidates
    }

  def decode(
    input: CncfExternalDocumentScenarioInput
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (input == null || input.batches == null || input.batches.isEmpty || input.batches.exists(_ == null) || input.schema == null)
      Consequence.configurationInvalid("CNCF external configuration document input is invalid")
    else
      for {
        snapshots <- ConfigurationSourceSnapshots.load(input.batches.map(_.admission))
        batches <- _batches(
          snapshots.snapshots
            .zip(input.batches)
            .zip(Vector.fill(input.batches.size)(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])),
          input.schema
        )
        candidates <- ConfigurationBindingCandidateConstructor.construct(batches)
      } yield candidates

  private def _batches(
    entries: Vector[((ConfigurationSourceSnapshot[ConfigurationDocument], CncfConfigurationDocumentBatch), Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])],
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateBatch[CncfConfigurationTarget]]] =
    entries.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateBatch[CncfConfigurationTarget]])) {
      case (acc, ((snapshot, batch), additions)) =>
        for {
          batches <- acc
          inputs <- _inputs(snapshot, batch.location, schema)
          candidatebatch <- ConfigurationBindingCandidateBatch.create(snapshot, inputs ++ additions)
        } yield batches :+ candidatebatch
    }

  private def _inputs(
    snapshot: ConfigurationSourceSnapshot[ConfigurationDocument],
    location: CncfConfigurationDocumentLocation,
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    if (location == null)
      Consequence.configurationInvalid("CNCF configuration document location is required")
    else
      location match {
        case Consolidated => _consolidated(snapshot.value, schema)
        case Global => _config(snapshot.value, CncfConfigurationTarget.Global, "config", schema)
        case value: ComponentClass => _config(snapshot.value, value.target, "config", schema)
        case value: SubsystemInstance => _config(snapshot.value, value.target, "config", schema)
        case value: ComponentInstance => _config(snapshot.value, value.target, "config", schema)
      }

  private def _consolidated(
    document: ConfigurationDocument,
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    for {
      root <- _object(document, "document")
      global <- _global(root, schema)
      components <- _components(root, schema)
      subsystems <- _subsystems(root, schema)
    } yield global ++ components ++ subsystems

  private def _global(
    fields: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _named(fields, "global").fold(Consequence.success(Vector.empty)) { values =>
      _objects(values, "global").flatMap { objects =>
        _named(objects.flatMap(_.fields), "config").fold(Consequence.success(Vector.empty)) { configs =>
          _configs(configs, CncfConfigurationTarget.Global, "global.config", schema)
        }
      }
    }

  private def _components(
    root: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _named(root, "components").fold(Consequence.success(Vector.empty)) { values =>
      _objects(values, "components").flatMap { trees =>
        _component_nodes(trees.flatMap(_.fields), schema, "components")
      }
    }

  private def _component_nodes(
    fields: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema,
    prefix: String
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    fields.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
      case (acc, field) =>
        for {
          inputs <- acc
          componentid <- ComponentId.parseC(field.name)
          target <- CncfConfigurationTarget.ComponentClass.create(componentid)
          node <- _object(field.value, s"$prefix.${field.name}")
          config <- _named(node, "config").fold(Consequence.success(Vector.empty)) { values =>
            _configs(values, target, s"$prefix.${field.name}.config", schema)
          }
        } yield inputs ++ config
    }

  private def _subsystems(
    root: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _named(root, "subsystems").fold(Consequence.success(Vector.empty)) { values =>
      _objects(values, "subsystems").flatMap { trees =>
        trees.flatMap(_.fields).foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
          case (acc, subsystem) =>
            for {
              inputs <- acc
              node <- _object(subsystem.value, s"subsystems.${subsystem.name}")
              instances <- _named(node, "instances").fold(Consequence.success(Vector.empty)) { values =>
                _objects(values, s"subsystems.${subsystem.name}.instances").flatMap { trees =>
                  _subsystem_instances(subsystem.name, trees.flatMap(_.fields), schema)
                }
              }
            } yield inputs ++ instances
        }
      }
    }

  private def _subsystem_instances(
    subsystem: String,
    fields: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    fields.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
      case (acc, instance) =>
        for {
          inputs <- acc
          identity <- SubsystemInstanceId.create(subsystem, instance.name)
          target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
          node <- _object(instance.value, s"subsystems.$subsystem.instances.${instance.name}")
          config <- _named(node, "config").fold(Consequence.success(Vector.empty)) { values =>
            _configs(values, target, s"subsystems.$subsystem.instances.${instance.name}.config", schema)
          }
          components <- _nested_components(node, identity, schema, s"subsystems.$subsystem.instances.${instance.name}")
        } yield inputs ++ config ++ components
    }

  private def _nested_components(
    node: Vector[ConfigurationDocument.Field],
    subsystem: SubsystemInstanceId,
    schema: CncfConfigurationDocumentSchema,
    prefix: String
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _named(node, "components").fold(Consequence.success(Vector.empty)) { values =>
      _objects(values, s"$prefix.components").flatMap { trees =>
        trees.flatMap(_.fields).foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
          case (acc, component) =>
            for {
              inputs <- acc
              componentnode <- _object(component.value, s"$prefix.components.${component.name}")
              instances <- _named(componentnode, "instances").fold(Consequence.success(Vector.empty)) { values =>
                _objects(values, s"$prefix.components.${component.name}.instances").flatMap { trees =>
                  ComponentId.parseC(component.name).flatMap { componentid =>
                    _component_instances(subsystem, componentid, trees.flatMap(_.fields), schema, s"$prefix.components.${component.name}.instances")
                  }
                }
              }
            } yield inputs ++ instances
        }
      }
    }

  private def _component_instances(
    subsystem: SubsystemInstanceId,
    componentid: ComponentId,
    fields: Vector[ConfigurationDocument.Field],
    schema: CncfConfigurationDocumentSchema,
    prefix: String
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    fields.foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
      case (acc, instance) =>
        for {
          inputs <- acc
          instanceid <- ComponentInstanceId.createC(componentid, instance.name)
          target <- CncfConfigurationTarget.ComponentInstance.create(subsystem, instanceid)
          node <- _object(instance.value, s"$prefix.${instance.name}")
          config <- _named(node, "config").fold(Consequence.success(Vector.empty)) { values =>
            _configs(values, target, s"$prefix.${instance.name}.config", schema)
          }
        } yield inputs ++ config
    }

  private def _config(
    document: ConfigurationDocument,
    target: CncfConfigurationTarget,
    prefix: String,
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _object(document, prefix).flatMap(x => _configs(Vector(ConfigurationDocument.Object(x)), target, prefix, schema))

  private def _configs(
    documents: Vector[ConfigurationDocument],
    target: CncfConfigurationTarget,
    prefix: String,
    schema: CncfConfigurationDocumentSchema
  ): Consequence[Vector[ConfigurationBindingCandidateInput[CncfConfigurationTarget]]] =
    _objects(documents, prefix).flatMap { objects =>
      objects.flatMap(_.fields).foldLeft(Consequence.success(Vector.empty[ConfigurationBindingCandidateInput[CncfConfigurationTarget]])) {
        case (acc, field) =>
          for {
            inputs <- acc
            value <- _scalar(field.value, s"$prefix.${field.name}")
            definition <- schema.definition(field.name)
            input <- definition.input(field.name, target, value, s"$prefix.${field.name}")
          } yield inputs :+ input
      }
    }

  private def _named(
    fields: Vector[ConfigurationDocument.Field],
    name: String
  ): Option[Vector[ConfigurationDocument]] =
    Some(fields.collect { case ConfigurationDocument.Field(`name`, value) => value }).filter(_.nonEmpty)

  private def _object(
    document: ConfigurationDocument,
    path: String
  ): Consequence[Vector[ConfigurationDocument.Field]] =
    document match {
      case ConfigurationDocument.Object(fields) if fields != null && fields.forall(x => x != null && x.name != null && x.name.nonEmpty && x.value != null) =>
        Consequence.success(fields)
      case _ => Consequence.configurationInvalid(s"CNCF configuration document object is invalid at $path")
    }

  private def _objects(
    documents: Vector[ConfigurationDocument],
    path: String
  ): Consequence[Vector[ConfigurationDocument.Object]] =
    if (documents == null)
      Consequence.configurationInvalid(s"CNCF configuration document objects are invalid at $path")
    else
      documents.foldLeft(Consequence.success(Vector.empty[ConfigurationDocument.Object])) { (acc, document) =>
        for {
          values <- acc
          fields <- _object(document, path)
        } yield values :+ ConfigurationDocument.Object(fields)
      }

  private def _scalar(
    document: ConfigurationDocument,
    path: String
  ): Consequence[org.goldenport.configuration.ConfigurationValue] =
    document match {
      case ConfigurationDocument.Scalar(value) if value != null => Consequence.success(value)
      case _ => Consequence.configurationInvalid(s"CNCF configuration document scalar is invalid at $path")
    }
}
