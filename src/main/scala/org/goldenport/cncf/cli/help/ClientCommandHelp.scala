package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Mar.  6, 2026
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
      |  cncf client http get
      |  cncf client call domain.entity.createPerson
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
