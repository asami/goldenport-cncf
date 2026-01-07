package org.goldenport.cncf.cli

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait LogBackend {
  def log(level: String, message: String): Unit
}

object LogBackend {
  case object StdoutBackend extends LogBackend {
    def log(level: String, message: String): Unit = {
      val _ = level
      System.out.println(message)
    }
  }
  case object StderrBackend extends LogBackend {
    def log(level: String, message: String): Unit = {
      val _ = level
      System.err.println(message)
    }
  }

  def from(p: String): Option[LogBackend] = p match {
    case "stdout" => Some(StdoutBackend)
    case "stderr" => Some(StderrBackend)
    case _ => None
  }
}

object LogBackendHolder {
  @volatile private var _backend: Option[LogBackend] = None

  def install(backend: LogBackend): Unit = {
    _backend = Some(backend)
  }

  def backend: Option[LogBackend] = _backend
}
