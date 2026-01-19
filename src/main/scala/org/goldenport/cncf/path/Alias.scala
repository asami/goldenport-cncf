package org.goldenport.cncf.path

import java.util.Locale

import scala.annotation.tailrec
import scala.collection.immutable.Map
import scala.collection.mutable
import scala.util.matching.Regex

import org.goldenport.cncf.cli.RunMode
import org.goldenport.configuration.{Configuration, ConfigurationValue}

/*
 * @since   Jan. 19, 2026
 * @version Jan. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class Alias(
  input: String,
  output: String,
  modes: Set[RunMode],
  purpose: Option[Purpose]
)

enum Purpose {
  case develop, compat, spec
}

object Purpose {
  def from(value: String): Option[Purpose] = {
    val normalized = value.trim.toLowerCase(Locale.ROOT)
    Purpose.values.find(_.toString == normalized)
  }
}

object AliasLoader {
  val ConfigKey: String = "cncf.path.aliases"

  def load(configuration: Configuration, forbiddenShortcuts: Set[String] = AliasValidator.DefaultForbiddenShortcuts): AliasResolver = {
    val definitions = _alias_definitions(configuration)
    if (definitions.isEmpty) AliasResolver.empty
    else {
      val aliases = definitions.zipWithIndex.map { case (definition, index) => _alias_from(definition, index) }
      AliasValidator.validate(aliases, forbiddenShortcuts)
      AliasResolver.from(aliases)
    }
  }

  private def _alias_definitions(configuration: Configuration): Vector[Map[String, ConfigurationValue]] = {
    configuration.values.get(ConfigKey) match {
      case Some(ConfigurationValue.ListValue(values)) =>
        values.zipWithIndex.map { case (value, index) =>
          value match {
            case ConfigurationValue.ObjectValue(map) => map
            case other =>
              throw new IllegalArgumentException(s"alias entry #${index + 1} must be an object, found ${other.getClass.getSimpleName}")
          }
        }.toVector
      case Some(ConfigurationValue.ObjectValue(map)) =>
        Vector(map)
      case Some(other) =>
        throw new IllegalArgumentException(s"$ConfigKey must be an object or list, found ${other.getClass.getSimpleName}")
      case None =>
        Vector.empty
    }
  }

  private def _alias_from(definition: Map[String, ConfigurationValue], index: Int): Alias = {
    val label = s"alias entry #${index + 1}"
    val input = _require_string(definition, "input", label)
    val output = _require_string(definition, "output", label)
    val rawModes = definition.get("modes")
    val modes =
      rawModes match {
        case Some(value) => _parse_modes(value, label)
        case None => _all_modes
      }
    val purpose =
      definition.get("purpose") match {
        case Some(value) => Some(_parse_purpose(value, label))
        case None => None
      }
    Alias(
      input = _alias_normalization._normalize_input(input),
      output = _normalize_output(output),
      modes = modes,
      purpose = purpose
    )
  }

  private def _require_string(definition: Map[String, ConfigurationValue], key: String, label: String): String = {
    definition.get(key) match {
      case Some(ConfigurationValue.StringValue(value)) =>
        val trimmed = value.trim
        if (trimmed.isEmpty) throw new IllegalArgumentException(s"$label.$key must not be empty")
        trimmed
      case Some(other) =>
        throw new IllegalArgumentException(s"$label.$key must be a string, found ${other.getClass.getSimpleName}")
      case None =>
        throw new IllegalArgumentException(s"$label is missing required field '$key'")
    }
  }

  private def _normalize_output(value: String): String = {
    val trimmed = value.trim
    if (trimmed.contains('.')) trimmed
    else _alias_normalization._normalize_input(trimmed)
  }

  private def _parse_modes(value: ConfigurationValue, label: String): Set[RunMode] = {
    val strings = _extract_string_list(value, label, "modes")
    if (strings.isEmpty) throw new IllegalArgumentException(s"$label.modes must list at least one run mode")
    strings.map { modeName =>
      RunMode.from(modeName.trim) match {
        case Some(mode) => mode
        case None => throw new IllegalArgumentException(s"$label.modes contains invalid run mode '$modeName'")
      }
    }.toSet
  }

  private def _parse_purpose(value: ConfigurationValue, label: String): Purpose = {
    value match {
      case ConfigurationValue.StringValue(text) =>
        Purpose.from(text) match {
          case Some(purpose) => purpose
          case None => throw new IllegalArgumentException(s"$label.purpose must be one of ${Purpose.values.mkString(", ")}")
        }
      case other =>
        throw new IllegalArgumentException(s"$label.purpose must be a string, found ${other.getClass.getSimpleName}")
    }
  }

  private def _extract_string_list(value: ConfigurationValue, label: String, key: String): Vector[String] = {
    value match {
      case ConfigurationValue.ListValue(values) =>
        values.zipWithIndex.map { case (entry, entryIndex) =>
          entry match {
            case ConfigurationValue.StringValue(text) => text
            case other =>
              throw new IllegalArgumentException(s"$label.$key entry #${entryIndex + 1} must be a string, found ${other.getClass.getSimpleName}")
          }
        }.toVector
      case ConfigurationValue.StringValue(text) => Vector(text)
      case other =>
        throw new IllegalArgumentException(s"$label.$key must be a string or list, found ${other.getClass.getSimpleName}")
    }
  }

  private val _all_modes: Set[RunMode] =
    RunMode.values.toSet
}

object AliasValidator {
  val DefaultForbiddenShortcuts: Set[String] = Set.empty

  def validate(aliases: Seq[Alias], forbiddenShortcuts: Set[String]): Unit = {
    if (aliases.isEmpty) return
    _ensure_required_fields(aliases)
    _ensure_identifiers(aliases)
    _ensure_forbidden_shortcuts(aliases, forbiddenShortcuts)
    _ensure_non_empty_modes(aliases)
    _ensure_unique_inputs(aliases)
    _ensure_references_exist(aliases)
    _ensure_no_cycles(aliases)
  }

  private def _ensure_required_fields(aliases: Seq[Alias]): Unit = {
    aliases.foreach { alias =>
      if (alias.output.isEmpty) throw new IllegalArgumentException(s"alias '${alias.input}' has empty output")
    }
  }

  private val _identifier_pattern: Regex = "^[A-Za-z0-9_]+$".r

  private def _ensure_identifiers(aliases: Seq[Alias]): Unit = {
    aliases.foreach { alias =>
      if (!_identifier_pattern.matches(alias.input))
        throw new IllegalArgumentException(s"alias input '${alias.input}' must match [A-Za-z0-9_]+")
    }
  }

  private def _ensure_forbidden_shortcuts(aliases: Seq[Alias], forbiddenShortcuts: Set[String]): Unit = {
    if (forbiddenShortcuts.isEmpty) return
    val normalizedForbidden = forbiddenShortcuts.map(_alias_normalization._normalize_input)
    val blocked = aliases.filter(alias => normalizedForbidden.contains(alias.input))
    if (blocked.nonEmpty) {
      val values = blocked.map(_.input).distinct.mkString(", ")
      throw new IllegalArgumentException(s"alias inputs [$values] are forbidden shortcuts")
    }
  }

  private def _ensure_non_empty_modes(aliases: Seq[Alias]): Unit = {
    aliases.foreach { alias =>
      if (alias.modes.isEmpty)
        throw new IllegalArgumentException(s"alias '${alias.input}' must specify at least one run mode")
    }
  }

  private def _ensure_unique_inputs(aliases: Seq[Alias]): Unit = {
    val duplicates = aliases.groupBy(_.input).collect { case (input, group) if group.size > 1 => input }
    if (duplicates.nonEmpty) {
      throw new IllegalArgumentException(s"duplicate alias inputs detected: ${duplicates.mkString(", ")}")
    }
  }

  private def _ensure_references_exist(aliases: Seq[Alias]): Unit = {
    val aliasInputs = aliases.map(_.input).toSet
    val missing = aliases.filter(alias => _is_reference(alias.output) && !aliasInputs.contains(alias.output)).map(_.input)
    if (missing.nonEmpty) {
      throw new IllegalArgumentException(s"alias inputs ${missing.mkString(", ")} reference unknown targets")
    }
  }

  private def _is_reference(value: String): Boolean = !value.contains('.')

  private def _ensure_no_cycles(aliases: Seq[Alias]): Unit = {
    val aliasMap = aliases.map(alias => alias.input -> alias).toMap
    val visiting = mutable.Set.empty[String]
    val visited = mutable.Set.empty[String]

    def _visit(path: Vector[String], current: String): Unit = {
      if (visiting.contains(current)) {
        val cycle = (path :+ current).mkString(" -> ")
        throw new IllegalArgumentException(s"alias cycle detected: $cycle")
      }
      if (visited.contains(current)) return
      visiting += current
      aliasMap.get(current) match {
        case Some(alias) if _is_reference(alias.output) =>
          aliasMap.get(alias.output) match {
            case Some(next) =>
              _visit(path :+ alias.output, next.input)
            case None => ()
          }
        case _ => ()
      }
      visiting -= current
      visited += current
    }

    aliases.foreach(alias => _visit(Vector(alias.input), alias.input))
  }
}

final class AliasResolver private (private val _map: Map[String, Alias]) {
  def resolve(input: String, mode: RunMode): Option[String] = {
    val normalized = _alias_normalization._normalize_input(input)
    _map.get(normalized) match {
      case Some(alias) if alias.modes.contains(mode) =>
        Some(AliasResolver._expand(alias, _map))
      case _ => None
    }
  }
}

object AliasResolver {
  val empty: AliasResolver = new AliasResolver(Map.empty)

  def from(aliases: Seq[Alias]): AliasResolver =
    new AliasResolver(aliases.map(alias => alias.input -> alias).toMap)

  private def _expand(alias: Alias, aliasMap: Map[String, Alias]): String = {
    @tailrec
    def _loop(current: Alias, visited: Set[String]): String = {
      aliasMap.get(current.output) match {
        case Some(next) if !visited.contains(next.input) =>
          _loop(next, visited + next.input)
        case Some(_) =>
          throw new IllegalStateException(s"alias cycle detected when expanding '${alias.input}'")
        case None =>
          current.output
      }
    }
    _loop(alias, Set(alias.input))
  }
}

private object _alias_normalization {
  private val _locale: Locale = Locale.ROOT
  def _normalize_input(value: String): String = value.trim.toLowerCase(_locale)
}

object PathPreNormalizer {
  def rewriteSelector(
    selector: String,
    mode: RunMode,
    resolver: AliasResolver
  ): String = {
    val segments = _split_selector(selector)
    if (segments.isEmpty) return selector
    val (stripped, _) = _strip_suffix_from_last_segment(segments)
    if (stripped.isEmpty) return selector
    val normalized = stripped.mkString(".")
    resolver.resolve(normalized, mode).getOrElse(normalized)
  }

  def rewriteSegments(
    segments: Vector[String],
    mode: RunMode,
    resolver: AliasResolver
  ): Vector[String] = {
    if (segments.isEmpty) return segments
    val input = segments.mkString(".")
    val rewritten = rewriteSelector(input, mode, resolver)
    val finalSegments = rewritten.split("\\.").map(_.trim).filter(_.nonEmpty).toVector
    if (finalSegments.length == 3) finalSegments else segments
  }

  private def _split_selector(value: String): Array[String] = {
    val raw =
      if (value.contains("/")) value.split("/")
      else if (value.contains("\\")) value.split("\\\\")
      else value.split("\\.")
    raw.map(_.trim).filter(_.nonEmpty)
  }

  private def _strip_suffix_from_last_segment(
    segments: Array[String]
  ): (Array[String], Boolean) = {
    if (segments.isEmpty) return (segments, false)
    val updated = segments.clone()
    val lastIndex = updated.length - 1
    val last = updated(lastIndex)
    val dotIndex = last.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex == last.length - 1) {
      (updated.filter(_.nonEmpty), false)
    } else {
      updated(lastIndex) = last.substring(0, dotIndex).trim
      (updated.filter(_.nonEmpty), true)
    }
  }
}
