package org.goldenport.cncf.bootstrap

/*
 * @since   Jan. 12, 2026
 * @version Jan. 12, 2026
 * @author  ASAMI, Tomoharu
 */
trait BootstrapLog {
  def info(msg: String): Unit
  def warn(msg: String): Unit
  def error(msg: String): Unit
}

object BootstrapLog {
  private val enabled: Boolean =
    sys.env.get("CNCF_BOOTSTRAP_LOG").exists(_.nonEmpty)

  val stderr: BootstrapLog = new BootstrapLog {
    def info(msg: String): Unit =
      if (enabled) System.err.println(s"[BOOT][INFO]  ${msg}")
    def warn(msg: String): Unit =
      if (enabled) System.err.println(s"[BOOT][WARN]  ${msg}")
    def error(msg: String): Unit =
      if (enabled) System.err.println(s"[BOOT][ERROR] ${msg}")
  }
}
