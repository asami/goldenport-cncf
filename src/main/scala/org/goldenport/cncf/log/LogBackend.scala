package org.goldenport.cncf.log

import org.slf4j.LoggerFactory

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait LogBackend {
  def log(level: String, message: String): Unit
}

object LogBackend {
  case object NopLogBackend extends LogBackend {
    def log(level: String, message: String): Unit = {
      val _ = level
      val _ = message
      ()
    }
  }

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

  case object Slf4jLogBackend extends LogBackend {
    private val _logger = LoggerFactory.getLogger(classOf[LogBackend])

    def log(level: String, message: String): Unit = {
      level match {
        case "error" =>
          _logger.error(message)
        case "warn" =>
          _logger.warn(message)
        case "info" =>
          _logger.info(message)
        case "debug" =>
          _logger.debug(message)
        case "trace" =>
          _logger.trace(message)
        case _ =>
          _logger.info(message)
      }
    }
  }

  def fromString(p: String): Option[LogBackend] = p match {
    case "stdout" => Some(StdoutBackend)
    case "stderr" => Some(StderrBackend)
    case "nop" => Some(NopLogBackend)
    case "slf4j" => Some(Slf4jLogBackend)
    case _ => None
  }

  def fromOption(p: Option[String]): Option[LogBackend] =
    p.flatMap(fromString)
}

object LogBackendHolder {
  @volatile private var _backend: Option[LogBackend] = None

  def install(backend: LogBackend): Unit = {
    _backend = Some(backend)
  }

  def backend: Option[LogBackend] = _backend
}
