package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Mar. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object ClientCommandHelp {
  val text: String =
    """CNCF Client Command Help
      |
      |Usage
      |  cncf client <args...>
      |
      |Description
      |  Call operations on a remote CNCF server.
      |
      |Examples
      |  cncf client admin.system.ping
      |  cncf client crud.entity.create-item --name alpha --title Alpha
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
