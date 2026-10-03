package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Immutable generated definitions and the pure, atomic static admission boundary. */
object GeneratedLogicalUiAbi {
  final case class Definition(
    abiIdentity: String, abiVersion: Int, semanticContract: SemanticContract,
    provenance: SourceProvenance, screenId: String, definitionRevision: String,
    viewBindingId: String, screen: ScreenDefinition
  )

  sealed abstract class ScreenDefinition
  final case class ListDefinition(
    title: String, itemFields: Vector[FieldDefinition], detailTarget: DetailTarget,
    actions: Vector[ActionDefinition], itemActions: Vector[ActionDefinition]
  ) extends ScreenDefinition
  final case class DetailDefinition(
    title: String, sections: Vector[SectionDefinition], actions: Vector[ActionDefinition]
  ) extends ScreenDefinition
  final case class DetailTarget(screenId: String)
  final case class SectionDefinition(id: String, title: Option[String], fields: Vector[FieldDefinition])
  final case class FieldDefinition(id: String, label: String, role: PresentationRole, value: ValueDefinition)
  final case class ValueDefinition(bindingId: String, allowedKinds: Vector[ValueKind])
  final case class ActionDefinition(id: String, label: String, bindingId: String)
  final case class SemanticContract(
    schema: String, version: Int, semanticAuthoritySchema: String,
    assurance: String, driver: DriverReference
  )
  final case class DriverReference(repository: String, revision: String, path: String, scenarioId: String)
  final case class SourceProvenance(producerId: String, sourceId: String, revision: String)

  enum PresentationRole(val tag: String) {
    case Content extends PresentationRole("content")
    case Title extends PresentationRole("title")
    case Subtitle extends PresentationRole("subtitle")
    case Description extends PresentationRole("description")
    case Status extends PresentationRole("status")
  }
  enum ValueKind(val tag: String) {
    case Text extends ValueKind("text")
    case Number extends ValueKind("number")
    case Boolean extends ValueKind("boolean")
    case Timestamp extends ValueKind("timestamp")
    case Missing extends ValueKind("missing")
  }

  val identity: String = "cncf.logical-ui-generated.v1"
  val version: Int = 1
  val semanticContract: SemanticContract = SemanticContract(
    "cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario",
    DriverReference("KnowledgeHubProject/nict-editing-studio-app",
      "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail")
  )

  final class AdmittedDefinitions private[GeneratedLogicalUiAbi](val definitions: Vector[Definition])
  final case class Diagnostic(code: String, path: String, reason: String)
  /** A typed cause carrier; admission returns it through Consequence and never throws it. */
  final class AdmissionException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  def admitC(definitions: Vector[Definition]): Consequence[AdmittedDefinitions] = {
    val diagnostic = if (definitions == null) {
      _error("ELEMENT_INVALID", "definitions", "null definition vector")
    } else if (definitions.isEmpty) {
      _error("DEFINITIONS_MISSING", "definitions", "selected provider has no definitions")
    } else {
      definitions.iterator.zipWithIndex.flatMap { case (definition, index) =>
        _definition(definition, s"definitions[$index]")
      }.nextOption().orElse {
        _duplicate_ids(definitions.map(_.screenId), "definitions", "screenId")
      }.orElse {
        definitions.iterator.zipWithIndex.flatMap { case (definition, index) =>
          definition.screen match {
            case list: ListDefinition => _detail_target(list.detailTarget, definition.screenId, definitions,
              s"definitions[$index].screen.detailTarget")
            case _: DetailDefinition => None
          }
        }.nextOption()
      }
    }
    diagnostic match {
      case Some(problem) => _admission_failure(problem)
      case None => Consequence.success(new AdmittedDefinitions(definitions))
    }
  }

  private def _admission_failure(diagnostic: Diagnostic): Consequence[AdmittedDefinitions] = {
    Consequence.fail(Taxonomy.argumentInvalid, new AdmissionException(diagnostic))
  }

  private def _error(code: String, path: String, reason: String): Option[Diagnostic] = {
    Some(Diagnostic("LUI96_" + code, path, reason))
  }

  private def _valid_id(value: String): Boolean = {
    value != null && value.nonEmpty && value == value.trim
  }
  private def _id(value: String, path: String): Option[Diagnostic] = {
    if (_valid_id(value)) None else _error("ID_INVALID", path, "expected nonempty exactly trimmed opaque text")
  }
  private def _text(value: String, path: String): Option[Diagnostic] = {
    if (value != null && value.trim.nonEmpty) None
    else _error("TEXT_INVALID", path, "expected nonblank display text")
  }

  private def _definition(definition: Definition, path: String): Option[Diagnostic] = {
    if (definition == null) _error("ELEMENT_INVALID", path, "null definition")
    else if (definition.screen == null) _error("ELEMENT_INVALID", path + ".screen", "null screen")
    else if (definition.abiIdentity != identity) {
      _error("ABI_INCOMPATIBLE", path + ".abiIdentity", "unsupported ABI identity")
    } else if (definition.abiVersion != version) {
      _error("ABI_INCOMPATIBLE", path + ".abiVersion", "unsupported ABI version")
    } else if (definition.semanticContract != semanticContract) {
      _error("CONTRACT_INCOMPATIBLE", path + ".semanticContract", "expected exact semantic descriptor")
    } else {
      _provenance(definition.provenance, path + ".provenance")
        .orElse(_id(definition.screenId, path + ".screenId"))
        .orElse(_id(definition.definitionRevision, path + ".definitionRevision"))
        .orElse(_id(definition.viewBindingId, path + ".viewBindingId"))
        .orElse(_screen(definition.screen, path + ".screen"))
    }
  }

  private def _provenance(provenance: SourceProvenance, path: String): Option[Diagnostic] = {
    if (provenance == null) _error("PROVENANCE_INVALID", path, "missing source provenance")
    else {
      Vector("producerId" -> provenance.producerId, "sourceId" -> provenance.sourceId,
        "revision" -> provenance.revision).iterator.collectFirst {
        case (member, value) if !_valid_id(value) =>
          Diagnostic("LUI96_PROVENANCE_INVALID", path + "." + member, "invalid opaque provenance text")
      }
    }
  }

  private def _screen(screen: ScreenDefinition, path: String): Option[Diagnostic] = {
    screen match {
      case list: ListDefinition =>
        _text(list.title, path + ".title")
          .orElse(_field_structure(list.itemFields, path + ".itemFields"))
          .orElse(_action_structure(list.actions, path + ".actions"))
          .orElse(_action_structure(list.itemActions, path + ".itemActions"))
          .orElse(_fields(list.itemFields, path + ".itemFields"))
          .orElse(_actions(list.actions, path + ".actions"))
          .orElse(_actions(list.itemActions, path + ".itemActions"))
      case detail: DetailDefinition =>
        _text(detail.title, path + ".title")
          .orElse(_section_structure(detail.sections, path + ".sections"))
          .orElse(_action_structure(detail.actions, path + ".actions"))
          .orElse(detail.sections.iterator.zipWithIndex.flatMap { case (section, index) =>
            _section(section, s"$path.sections[$index]")
          }.nextOption())
          .orElse(_actions(detail.actions, path + ".actions"))
    }
  }

  private def _duplicate_ids(ids: Vector[String], path: String, member: String): Option[Diagnostic] = {
    ids.indices.find(index => ids.take(index).contains(ids(index))).map { index =>
      Diagnostic("LUI96_DUPLICATE_ID", s"$path[$index].$member", "duplicate within declared ID scope")
    }
  }

  private def _section_structure(sections: Vector[SectionDefinition], path: String): Option[Diagnostic] = {
    if (sections == null) _error("ELEMENT_INVALID", path, "null section vector")
    else {
      sections.indices.find(index => sections(index) == null).map { index =>
        Diagnostic("LUI96_ELEMENT_INVALID", s"$path[$index]", "null section")
      }.orElse(_duplicate_ids(sections.map(_.id), path, "id"))
    }
  }

  private def _section(section: SectionDefinition, path: String): Option[Diagnostic] = {
    _id(section.id, path + ".id").orElse {
      if (section.title == null || section.title.contains(null)) {
        _error("ELEMENT_INVALID", path + ".title", "null optional title shape")
      } else section.title.flatMap(value => _text(value, path + ".title"))
    }.orElse(_field_structure(section.fields, path + ".fields"))
      .orElse(_fields(section.fields, path + ".fields"))
  }

  private def _field_structure(fields: Vector[FieldDefinition], path: String): Option[Diagnostic] = {
    if (fields == null) _error("ELEMENT_INVALID", path, "null field vector")
    else {
      fields.indices.find(index => fields(index) == null).map { index =>
        Diagnostic("LUI96_ELEMENT_INVALID", s"$path[$index]", "null field")
      }.orElse(_duplicate_ids(fields.map(_.id), path, "id")).orElse {
        fields.indices.find { index =>
          val role = fields(index).role
          role != null && role != PresentationRole.Content && fields.take(index).exists(_.role == role)
        }.map { index =>
          Diagnostic("LUI96_DUPLICATE_ROLE", s"$path[$index].role", "repeated non-content presentation role")
        }
      }
    }
  }

  private def _fields(fields: Vector[FieldDefinition], path: String): Option[Diagnostic] = {
    fields.iterator.zipWithIndex.flatMap { case (field, index) =>
      val fieldpath = s"$path[$index]"
      _id(field.id, fieldpath + ".id").orElse(_text(field.label, fieldpath + ".label")).orElse {
        if (field.role == null) _error("ROLE_INVALID", fieldpath + ".role", "null presentation role")
        else None
      }.orElse {
        if (field.value == null) _error("ELEMENT_INVALID", fieldpath + ".value", "null value definition")
        else _id(field.value.bindingId, fieldpath + ".value.bindingId")
          .orElse(_kinds(field.value.allowedKinds, fieldpath + ".value.allowedKinds"))
          .orElse {
            if (field.role != PresentationRole.Content && field.value.allowedKinds != Vector(ValueKind.Text)) {
              _error("ROLE_INVALID", fieldpath + ".role", "non-content role requires exactly text")
            } else None
          }
      }
    }.nextOption()
  }

  private def _kinds(kinds: Vector[ValueKind], path: String): Option[Diagnostic] = {
    if (kinds == null) _error("ELEMENT_INVALID", path, "null allowed-kind vector")
    else if (kinds.isEmpty) _error("VALUE_KIND_INVALID", path, "allowed kinds must be nonempty")
    else {
      kinds.indices.find(index => kinds(index) == null || kinds.take(index).contains(kinds(index))).map { index =>
        Diagnostic("LUI96_VALUE_KIND_INVALID", s"$path[$index]", "null or repeated allowed kind")
      }
    }
  }

  private def _action_structure(actions: Vector[ActionDefinition], path: String): Option[Diagnostic] = {
    if (actions == null) _error("ELEMENT_INVALID", path, "null action vector")
    else {
      actions.indices.find(index => actions(index) == null).map { index =>
        Diagnostic("LUI96_ELEMENT_INVALID", s"$path[$index]", "null action")
      }.orElse(_duplicate_ids(actions.map(_.id), path, "id"))
    }
  }

  private def _actions(actions: Vector[ActionDefinition], path: String): Option[Diagnostic] = {
    actions.iterator.zipWithIndex.flatMap { case (action, index) =>
      val actionpath = s"$path[$index]"
      _id(action.id, actionpath + ".id")
        .orElse(_text(action.label, actionpath + ".label"))
        .orElse(_id(action.bindingId, actionpath + ".bindingId"))
    }.nextOption()
  }

  private def _detail_target(
    target: DetailTarget, screenid: String, definitions: Vector[Definition], path: String
  ): Option[Diagnostic] = {
    if (target == null) _error("DETAIL_TARGET_INVALID", path, "missing logical Detail target")
    else if (!_valid_id(target.screenId) || target.screenId == screenid) {
      _error("DETAIL_TARGET_INVALID", path + ".screenId", "malformed or self Detail target")
    } else if (!definitions.exists(definition =>
      definition.screenId == target.screenId && definition.screen.isInstanceOf[DetailDefinition])) {
      _error("DETAIL_TARGET_INVALID", path + ".screenId", "target must resolve to Detail in selected vector")
    } else None
  }
}
