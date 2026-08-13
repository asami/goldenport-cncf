package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 *  version Mar. 19, 2026
 *  version Jun. 29, 2026
 *  version Jul.  1, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object CommandProtocolHelp {
  private val _help_flags = Set("--help", "-h")

  val text: String =
    """CNCF Command Help
      |
      |Usage
      |  cncf command <selector> [args...]
      |
      |Selector grammar
      |  <component>.<service>.<operation>
      |  <component>.meta.<operation>
      |  <component>.<service>.meta.<operation>
      |  meta.<operation>
      |
      |Examples
      |  cncf command org.goldenport.cncf.Admin.system.ping
      |  cncf command org.goldenport.cncf.Admin.meta.help
      |  cncf command org.goldenport.cncf.Admin.system.meta.operations
      |  cncf command meta.tree
      |  cncf command meta.mcp
      |  cncf command spec.export.mcp
      |
      |Navigation
      |  cncf command help <selector>
      |
      |AI/MCP Navigation
      |  1) cncf command help
      |  2) cncf command meta.mcp
      |  3) cncf command spec.export.mcp
      |""".stripMargin

  // Left(exitCode): handled as standalone help output
  // Right(args): proceed with command protocol execution using rewritten args
  def normalizeArgs(args: Array[String]): Either[Int, Array[String]] = {
    args.toVector match {
      case Vector("help") =>
        Console.out.println(text)
        Left(0)
      case Vector("help", selector, tail @ _*) =>
        Right(Array(rewriteSelector(selector)) ++ tail)
      case Vector(flag) if _help_flags.contains(flag) =>
        Console.out.println(text)
        Left(0)
      case Vector(selector, flag) if _help_flags.contains(flag) && selector.nonEmpty =>
        Right(Array("help", selector))
      case _ =>
        Right(args)
    }
  }

  def rewriteSelector(selector: String): String =
    selector match {
      case "meta" => "meta.help"
      case s if s.startsWith("meta.") => s
      case s if s.contains(".meta.") => s
      case s => s"$s.meta.help"
    }
}
