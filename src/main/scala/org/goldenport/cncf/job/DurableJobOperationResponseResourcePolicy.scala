package org.goldenport.cncf.job

import io.circe.{Json, Printer}
import scala.util.control.NonFatal

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponseResourcePolicy {
  private val _maximum_bytes = 8388608
  private val _maximum_depth = 64
  private val _maximum_nodes = 32768
  private val _message = "durable job operation response refused"

  final class Refusal extends RuntimeException(null, null, false, false)

  def refuse(): Nothing = throw new Refusal

  def validateText(text: String): Either[String, Unit] =
    try {
      _string_bytes(text, false, _maximum_bytes)
      _scan(text)
      Right(())
    } catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Left(_message)
      case NonFatal(_) => Left(_message)
    }

  /* Resource admission only: the Circe parser owns grammar. */
  private def _scan(text: String): Unit = {
    var index = 0
    var depth = 0
    var nodes = 0
    while (index < text.length) {
      text.charAt(index) match {
        case '"' =>
          index += 1
          var quoted = true
          var escaped = false
          while (index < text.length && quoted) {
            val character = text.charAt(index)
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
            index += 1
          }
          var next = index
          while (next < text.length && _whitespace(text.charAt(next))) next += 1
          if (next == text.length || text.charAt(next) != ':') nodes += 1
        case '{' | '[' =>
          depth += 1
          nodes += 1
          index += 1
        case '}' | ']' =>
          depth = math.max(0, depth - 1)
          index += 1
        case character if character == '-' || (character >= '0' && character <= '9') ||
            character == 't' || character == 'f' || character == 'n' =>
          nodes += 1
          index += 1
          while (index < text.length && !_delimiter(text.charAt(index))) index += 1
        case _ => index += 1
      }
      if (depth > _maximum_depth || nodes > _maximum_nodes) refuse()
    }
  }

  private def _whitespace(character: Char): Boolean =
    character == ' ' || character == '\n' || character == '\r' || character == '\t'

  private def _delimiter(character: Char): Boolean =
    _whitespace(character) || character == ',' || character == ':' ||
      character == '{' || character == '}' || character == '[' || character == ']' || character == '"'

  /* Count before allocating escaped text. No UTF-8 replacement is permitted. */
  private def _string_bytes(text: String, quoted: Boolean, maximum: Int): Int = {
    if (text == null) refuse()
    var bytes = if (quoted) 2 else 0
    if (bytes > maximum) refuse()
    var index = 0
    while (index < text.length) {
      val character = text.charAt(index)
      if (Character.isHighSurrogate(character)) {
        if (index + 1 >= text.length || !Character.isLowSurrogate(text.charAt(index + 1))) refuse()
        bytes += 4
        index += 2
      } else {
        if (Character.isLowSurrogate(character)) refuse()
        bytes += (if (quoted && (character == '"' || character == '\\')) 2
          else if (quoted && (character == '\b' || character == '\f' || character == '\n' ||
              character == '\r' || character == '\t')) 2
          else if (quoted && character < ' ') 6
          else if (character < 0x80) 1
          else if (character < 0x800) 2
          else 3)
        index += 1
      }
      if (bytes > maximum) refuse()
    }
    bytes
  }

  final class RenderingBudget {
    private val _builder = new java.lang.StringBuilder
    private var _bytes = 0
    private var _depth = 0
    private var _nodes = 0

    def result: String = _builder.toString

    def remainingBytes: Int = _maximum_bytes - _bytes

    def punctuation(text: String): Unit = {
      val bytes = _string_bytes(text, false, _maximum_bytes - _bytes)
      _bytes += bytes
      _builder.append(text)
      ()
    }

    def begin(character: Char): Unit = {
      _node()
      if (_depth == _maximum_depth) refuse()
      _depth += 1
      punctuation(character.toString)
    }

    def end(character: Char): Unit = {
      punctuation(character.toString)
      _depth -= 1
    }

    def key(text: String): Unit = {
      _quoted(text)
      punctuation(":")
    }

    def string(text: String): Unit = {
      _node()
      _quoted(text)
    }

    def literal(text: => String): Unit = {
      _node()
      punctuation(text)
    }

    private def _quoted(text: String): Unit = {
      val bytes = _string_bytes(text, true, _maximum_bytes - _bytes)
      _bytes += bytes
      _builder.append(Printer.noSpaces.print(Json.fromString(text)))
      ()
    }

    private def _node(): Unit = {
      if (_nodes == _maximum_nodes) refuse()
      _nodes += 1
    }
  }
}
