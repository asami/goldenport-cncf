package org.goldenport.cncf.cli.help

/*
 * @since   Mar.  6, 2026
 * @version Mar.  6, 2026
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
      |  cncf command domain.entity.createPerson
      |  cncf command domain.meta.help
      |  cncf command domain.entity.meta.operations
      |  cncf command meta.tree
      |
      |Navigation
      |  cncf command help <selector>
      |""".stripMargin

  // Left(exitCode): handled as standalone help output
  // Right(args): proceed with command protocol execution using rewritten args
  def normalizeArgs(args: Array[String]): Either[Int, Array[String]] = {
    val normalized = _normalize_help_aliases(args)
    normalized.toVector match {
      case Vector("help") =>
        Console.out.println(text)
        Left(0)
      case Vector("help", selector, tail @ _*) =>
        Right(Array("help", selector) ++ tail)
      case _ =>
        Right(normalized)
    }
  }

  private def _normalize_help_aliases(args: Array[String]): Array[String] =
    args.toVector match {
      case Vector(flag) if _help_flags.contains(flag) =>
        Array("help")
      case Vector(selector, flag) if _help_flags.contains(flag) && selector.nonEmpty =>
        Array("help", selector)
      case _ =>
        args
    }

  def rewriteSelector(selector: String): String =
    selector match {
      case "meta" => "meta.help"
      case s if s.startsWith("meta.") => s
      case s if s.contains(".meta.") => s
      case s => s"$s.meta.help"
    }
}
