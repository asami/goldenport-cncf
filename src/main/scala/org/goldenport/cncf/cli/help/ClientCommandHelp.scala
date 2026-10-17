package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 *  version Apr.  9, 2026
 *  version Aug. 13, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object ClientCommandHelp {
  val text: String =
    """CNCF Client Command Help
      |
      |Usage
      |  cncf client <component.service.operation> <operation parameters> [--baseurl <URL>]
      |
      |Description
      |  Call operations on a remote CNCF server.
      |  Use the same operation selector and operation parameters as 'cncf command'.
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
