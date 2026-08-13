package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object CliHelpOperation {
  val text: String =
      """CNCF Command Line Interface
      |
      |Usage
      |  cncf <command> [arguments]
      |
      |Commands
      |  command   Execute component operations
      |  server    Control CNCF runtime server
      |  client    Call operations on a remote server
      |
      |Examples
      |  cncf command org.goldenport.cncf.Admin.system.ping
      |  cncf command meta.help
      |  cncf server
      |  cncf client org.goldenport.cncf.Admin.system.ping
      |
      |Use 'cncf <command> help' for command-specific help.
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
