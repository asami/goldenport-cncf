package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 *  version Apr.  9, 2026
 * @version Aug. 13, 2026
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
      |  cncf client org.goldenport.cncf.Admin.system.ping
      |  cncf client org.goldenport.cncf.Admin.deployment.securityMermaid
      |  cncf client org.goldenport.cncf.Admin.deployment.securityMarkdown
      |""".stripMargin

  def execute(): Int = {
    Console.out.println(text)
    0
  }
}
