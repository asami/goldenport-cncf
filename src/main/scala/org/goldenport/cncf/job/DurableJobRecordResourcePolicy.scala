package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets

/*
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobRecordResourcePolicy {
  private val _maximum_bytes = 1048576
  private val _maximum_depth = 64
  private val _byte_message = "Durable job record exceeds UTF-8 byte limit (1048576)"
  private val _depth_message = "Durable job record exceeds JSON nesting depth limit (64)"

  def validateBytes(text: String): Either[String, Unit] =
    for {
      _ <- Either.cond(text.length <= _maximum_bytes, (), _byte_message)
      _ <- Either.cond(text.getBytes(StandardCharsets.UTF_8).length <= _maximum_bytes, (), _byte_message)
    } yield ()

  def validateText(text: String): Either[String, Unit] =
    validateBytes(text).flatMap(_ => _validate_depth(text))

  /* This scan admits resources only; the strict parser owns JSON grammar. */
  private def _validate_depth(text: String): Either[String, Unit] = {
    var index = 0
    var depth = 0
    var quoted = false
    var escaped = false
    while (index < text.length) {
      val character = text.charAt(index)
      if (quoted) {
        if (escaped) escaped = false
        else if (character == '\\') escaped = true
        else if (character == '"') quoted = false
      } else {
        character match {
          case '"' => quoted = true
          case '{' | '[' =>
            depth += 1
            if (depth > _maximum_depth) return Left(_depth_message)
          case '}' | ']' => depth = math.max(0, depth - 1)
          case _ => ()
        }
      }
      index += 1
    }
    Right(())
  }
}
