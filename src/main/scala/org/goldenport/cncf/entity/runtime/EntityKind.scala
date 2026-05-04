package org.goldenport.cncf.entity.runtime

import org.goldenport.Consequence
import org.goldenport.cncf.security.EntityOperationKind

/*
 * Canonical runtime/modeling kind for CNCF Entities.
 *
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityKind(val label: String) {
  case Master extends EntityKind("master")
  case Document extends EntityKind("document")
  case Workflow extends EntityKind("workflow")
  case Task extends EntityKind("task")
  case Actor extends EntityKind("actor")
  case Asset extends EntityKind("asset")

  override def toString: String = label

  def runtimePolicy: EntityKindRuntimePolicy =
    EntityKindRuntimePolicy.forKind(this)

  def legacyOperationKind: EntityOperationKind =
    runtimePolicy.legacyOperationKind

  def defaultWorkingSetPolicy: Option[WorkingSetPolicy] =
    runtimePolicy.defaultWorkingSetPolicy
}

object EntityKind {
  val default: EntityKind = EntityKind.Master

  def parse(text: String): EntityKind =
    parseOption(text).getOrElse(
      throw new IllegalArgumentException(s"unknown entityKind: $text")
    )

  def parseOrDefault(text: String): EntityKind =
    parseOption(text).getOrElse(default)

  def parseC(text: String): Consequence[EntityKind] =
    parseOption(text)
      .map(Consequence.success)
      .getOrElse(Consequence.argumentInvalid(s"unknown entityKind: $text"))

  def parseOption(text: String): Option[EntityKind] =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match {
      case "master" | "reference" | "reference-data" | "master-data" | "resource" =>
        Some(Master)
      case "document" | "content" | "cms" | "cms-resource" | "public-content" | "article" =>
        Some(Document)
      case "workflow" | "process" | "state-machine" | "stateful-process" =>
        Some(Workflow)
      case "task" | "job" | "execution" | "command" =>
        Some(Task)
      case "actor" | "agent" | "party" | "external-actor" | "external-object" =>
        Some(Actor)
      case "asset" | "media" | "blob" | "image" | "video" | "audio" | "attachment" =>
        Some(Asset)
      case _ =>
        None
    }
}
