package org.goldenport.cncf.mcp

import org.goldenport.Consequence

/*
 * Shared MCP protocol revision vocabulary for server and client transports.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class McpProtocolRevision private (val value: String) {
  def print: String = value
}

object McpProtocolRevision {
  private case object Revision20250326 extends McpProtocolRevision("2025-03-26")
  private case object Revision20250618 extends McpProtocolRevision("2025-06-18")
  private case object Revision20251125 extends McpProtocolRevision("2025-11-25")

  val REVISION_2025_03_26: McpProtocolRevision = Revision20250326
  val REVISION_2025_06_18: McpProtocolRevision = Revision20250618
  val REVISION_2025_11_25: McpProtocolRevision = Revision20251125

  val PREFERRED: McpProtocolRevision = REVISION_2025_11_25

  val SUPPORTED: Vector[McpProtocolRevision] = Vector(
    REVISION_2025_11_25,
    REVISION_2025_06_18,
    REVISION_2025_03_26
  )

  private val _revision_map = SUPPORTED.map(x => x.print -> x).toMap
  private val _pattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}".r

  def parseC(value: String): Consequence[McpProtocolRevision] = {
    val text = Option(value).getOrElse("")
    text match {
      case _pattern() => _revision_map.get(text).map(Consequence.success).getOrElse(
        Consequence.argumentPolicyViolation(
          "protocolVersion",
          "mcp.protocol-revision",
          "one supported MCP protocol revision",
          "unsupported"
        )
      )
      case _ => Consequence.argumentFormatError(
        "protocolVersion",
        "MCP protocol revision in YYYY-MM-DD form",
        "invalid"
      )
    }
  }

}
