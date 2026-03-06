package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Mar.  6, 2026
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
      |  cncf command domain.entity.createPerson
      |  cncf command meta.help
      |  cncf server
      |  cncf client domain.entity.createPerson
      |
      |Use 'cncf <command> help' for command-specific help.
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
