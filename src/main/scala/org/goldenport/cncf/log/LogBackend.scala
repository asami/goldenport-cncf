package org.goldenport.cncf.log

import org.slf4j.LoggerFactory
import scala.collection.mutable.ListBuffer

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait LogBackend {
  def log(level: String, message: String): Unit =
    writeLine(formatLine(level, message))

  def writeLine(line: String): Unit

  def supportsBootstrapReplay: Boolean = false

  protected[log] def formatLine(level: String, message: String): String = message
}

object LogBackend {
  case object NopLogBackend extends LogBackend {
    override def writeLine(line: String): Unit = {
      val _ = line
    }
  }

  case object StdoutBackend extends LogBackend {
    override def writeLine(line: String): Unit =
      System.out.println(line)

    override def supportsBootstrapReplay: Boolean = false
  }

  case object StderrBackend extends LogBackend {
    override def writeLine(line: String): Unit =
      System.err.println(line)

    override def supportsBootstrapReplay: Boolean = false
  }

  case object Slf4jLogBackend extends LogBackend {
    private val _logger = LoggerFactory.getLogger(classOf[LogBackend])

    override def log(level: String, message: String): Unit = {
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

    override def writeLine(line: String): Unit =
      _logger.info(line)

    override def supportsBootstrapReplay: Boolean = true
  }

  final case class BootstrapLogBackend(delegate: LogBackend) extends LogBackend {
    private val _buffer = ListBuffer.empty[String]

    override def log(level: String, message: String): Unit = {
      delegate.log(level, message)
      val line = delegate.formatLine(level, message)
      _buffer.synchronized {
        _buffer += line
      }
    }

    override def writeLine(line: String): Unit =
      delegate.writeLine(line)

    def replayTo(target: LogBackend): Unit = {
      val snapshot = _buffer.synchronized {
        val copy = _buffer.toVector
        _buffer.clear()
        copy
      }
      snapshot.foreach(target.writeLine)
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
  @volatile private var _backend: Option[LogBackend] =
    Some(LogBackend.BootstrapLogBackend(LogBackend.StdoutBackend))

  def reset(): Unit =
    _backend = Some(LogBackend.BootstrapLogBackend(LogBackend.StdoutBackend))

  def install(backend: LogBackend): Unit = {
    val old = _backend
    _backend = Some(backend)
    (old, backend) match {
      case (Some(b: LogBackend.BootstrapLogBackend), newBackend) if newBackend.supportsBootstrapReplay =>
        b.replayTo(newBackend)
      case _ => ()
    }
  }

  def backend: Option[LogBackend] = _backend
}
