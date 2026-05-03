package org.goldenport.cncf.action

/*
 * @since   Mar. 30, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
trait Behavior
  extends ActionCall.Core.Holder
  with ActionCallRepositoryPart
  with ActionCallBrowserPart
  with ActionCallEntityStorePart
  with ActionCallBlobPart
  with ActionCallDataStorePart
  with ActionCallHttpPart
  with ActionCallShellCommandPart
