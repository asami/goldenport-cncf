package org.goldenport.cncf.effect

import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
/** Supplied source-use observations; these values carry no execution authority. */
enum ExternalEffectUsage {
  case ProcessCommand(commandName: String)
  case TypedGit(kind: GitOperationKind)
  case TypedSbt(operation: SbtOperation)
  case ResourceRead
}
