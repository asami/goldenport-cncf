package org.goldenport.cncf.effect

import org.goldenport.cncf.workspace.WorkspaceId

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
/** Effect descriptions do not install or imply permissions. */
enum ExternalEffectKind {
  case ResourceRead, FileRead, FileWrite, GitRead, GitWrite, Build, Process, Http, Persistence
}

final case class ExternalEffect(kind: ExternalEffectKind, workspaceId: WorkspaceId)

/** A route describes authority provenance; it never supplies a grant. */
enum ExternalEffectRoute(val diagnosticCode: String) {
  case Managed extends ExternalEffectRoute("external.effect.managed")
  case Direct extends ExternalEffectRoute("external.effect.direct")
  case CapabilityLaundering extends ExternalEffectRoute("external.effect.capability-laundering")
  case Unclassified extends ExternalEffectRoute("external.effect.unclassified")
}

/** Unclassified effects are not evidence of sandbox safety. */
final case class ExternalEffectClassification(
  effectKinds: Set[ExternalEffectKind],
  route: ExternalEffectRoute
)
