package org.goldenport.cncf.cli

/*
 * @since   Jan.  7, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait RunMode

object RunMode {
  case object Server extends RunMode
  case object Client extends RunMode
  case object Command extends RunMode
  case object ServerEmulator extends RunMode

  def from(p: String): Option[RunMode] = p match {
    case "server" => Some(Server)
    case "client" => Some(Client)
    case "command" => Some(Command)
    case "server-emulator" => Some(ServerEmulator)
    case _ => None
  }
}
