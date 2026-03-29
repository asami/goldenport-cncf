package org.goldenport.cncf.action

/*
 * @since   Mar. 30, 2026
 * @version Mar. 30, 2026
 * @author  ASAMI, Tomoharu
 */
trait Behavior
  extends ActionCall.Core.Holder
  with ActionCallRepositoryPart
  with ActionCallBrowserPart
  with ActionCallEntityStorePart
  with ActionCallDataStorePart
  with ActionCallHttpPart
  with ActionCallShellCommandPart

