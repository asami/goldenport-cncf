package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Mar.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object ServerCommandHelp {
  val text: String =
    """CNCF Server Command Help
      |
      |Usage
      |  cncf server
      |
      |Description
      |  Start and control the CNCF runtime server.
      |
      |Examples
      |  cncf server
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
