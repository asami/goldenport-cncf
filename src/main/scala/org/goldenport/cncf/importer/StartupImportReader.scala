package org.goldenport.cncf.importer

import java.nio.file.{Files, Path}
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
private[importer] final class StartupImportReader(runtimeConfig: RuntimeConfig) {
  private val _runtime_config = runtimeConfig

  def readFile(path: Path): Consequence[String] = {
    try {
      Using.resource(Files.newBufferedReader(path)) { reader =>
        val sb = new StringBuilder
        val buf = new Array[Char](8192)
        var read = reader.read(buf)
        while (read != -1) {
          sb.appendAll(buf, 0, read)
          read = reader.read(buf)
        }
        Consequence.success(sb.toString)
      }
    } catch {
      case e: Throwable if scala.util.control.NonFatal(e) =>
        Consequence.resourceInvalid(s"failed to load import text ${path}: ${e.getMessage}")
    }
  }

  def readUrl(url: String): Consequence[String] = {
    Consequence(_runtime_config.httpDriver.get(url)).flatMap { response =>
      if (response.code != 200) {
        Consequence.resourceInvalid(s"startup import URL fetch failed: ${url} (HTTP ${response.code})")
      } else {
        response.getString match {
          case Some(body) =>
            Consequence.success(body)
          case None =>
            Consequence.resourceInvalid(s"startup import URL returned empty body: ${url}")
        }
      }
    }
  }
}
