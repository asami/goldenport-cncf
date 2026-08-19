package org.goldenport.cncf.cli

import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime extends GlobalObservable
  with CncfRuntimeBootstrapPart
  with CncfRuntimeDiscoveryPart
  with CncfRuntimeInteractionPart
  with CncfRuntimeConfigurationPart {
}

class CncfRuntime() extends GlobalObservable
  with CncfRuntimeInstanceLifecyclePart
  with CncfRuntimeInstanceClientPart
  with CncfRuntimeInstanceCommandPart {
}

enum RunMode(val name: String) {
  case Server extends RunMode("server")
  case Client extends RunMode("client")
  case Command extends RunMode("command")
  case Script extends RunMode("script")
  case ServerEmulator extends RunMode("server-emulator")
}
object RunMode {
  def from(p: String): Option[RunMode] = values.find(_.name == p)

  def parse(p: String): org.goldenport.Consequence[RunMode] =
    from(p) match {
      case Some(runmode) => org.goldenport.Consequence.success(runmode)
      case None => org.goldenport.Consequence.argumentInvalid(s"invalid run mode: ${p}")
    }
}
