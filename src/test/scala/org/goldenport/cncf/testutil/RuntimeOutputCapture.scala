package org.goldenport.cncf.testutil

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

import org.goldenport.cncf.log.LogBackendHolder

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object RuntimeOutputCapture {
  /**
   * Captures output for one sequential CNCF test scope. Test-owned asynchronous
   * work must finish or be cancelled before this scope returns.
   */
  final case class Captured[A](value: A, stdout: String, stderr: String)

  def capture[A](body: => A): Captured[A] = {
    val originalout = System.out
    val originalerr = System.err
    val originalbackend = LogBackendHolder.backend
    val stdoutbytes = new ByteArrayOutputStream()
    val stderrbytes = new ByteArrayOutputStream()
    val out = new PrintStream(stdoutbytes, true, StandardCharsets.UTF_8)
    val err = new PrintStream(stderrbytes, true, StandardCharsets.UTF_8)

    try {
      System.setOut(out)
      System.setErr(err)
      val value = Console.withOut(out) {
        Console.withErr(err) {
          body
        }
      }
      out.flush()
      err.flush()
      Captured(
        value,
        new String(stdoutbytes.toByteArray, StandardCharsets.UTF_8),
        new String(stderrbytes.toByteArray, StandardCharsets.UTF_8)
      )
    } finally {
      System.setOut(originalout)
      System.setErr(originalerr)
      originalbackend match {
        case Some(backend) => LogBackendHolder.install(backend)
        case None => LogBackendHolder.reset()
      }
      out.close()
      err.close()
    }
  }
}
