package org.goldenport.cncf.http

import scala.io.Source

/*
 * @since   Apr. 12, 2026
 * @version Apr. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppAssets {
  val bootstrapVersion: String = "5.3.3"

  val bootstrapCss: String =
    _resource_text("org/goldenport/cncf/http/assets/bootstrap.min.css")

  val bootstrapBundleJs: String =
    _resource_text("org/goldenport/cncf/http/assets/bootstrap.bundle.min.js")

  private def _resource_text(path: String): String = {
    val stream = Option(Thread.currentThread.getContextClassLoader.getResourceAsStream(path))
      .orElse(Option(getClass.getClassLoader.getResourceAsStream(path)))
      .getOrElse(throw new IllegalStateException(s"Static Form App asset not found: ${path}"))
    val source = Source.fromInputStream(stream, "UTF-8")
    try source.mkString
    finally source.close()
  }
}
