package org.goldenport.cncf.http

/*
 * @since   Apr. 12, 2026
 * @version Apr. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppLayout {
  final case class AssetCompletionOptions(
    requiresBootstrap: Boolean = false,
    declaredCss: Vector[String] = Vector.empty,
    declaredJs: Vector[String] = Vector.empty
  )

  final case class Options(
    title: String,
    subtitle: String,
    body: String,
    extraHead: String = "",
    extraScript: String = "",
    containerClass: String = "container-fluid px-4 py-4"
  )

  def bootstrapPage(options: Options): String =
    s"""<!doctype html>
       |<html lang="en">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${escape(options.title)}</title>
       |  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
       |  <style>
       |    body { background: #f6f7f9; color: #1f252b; }
       |    header { background: #ffffff; border-bottom: 1px solid #d9dee5; }
       |    code { background: #edf1f5; border-radius: 6px; padding: 2px 6px; }
       |    th:first-child, td:first-child { text-align: left; }
       |    ${options.extraHead}
       |  </style>
       |</head>
       |<body>
       |  <header class="py-4">
       |    <div class="container-fluid px-4">
       |      <h1 class="h3 mb-2">${escape(options.title)}</h1>
       |      <p class="text-secondary mb-0">${escape(options.subtitle)}</p>
       |    </div>
       |  </header>
       |  <main class="${escape(options.containerClass)}">
       |${options.body}
       |  </main>
       |  <script src="/web/assets/bootstrap.bundle.min.js"></script>
       |${options.extraScript}
       |</body>
       |</html>
       |""".stripMargin

  def completeWidgetAssets(
    html: String,
    options: AssetCompletionOptions
  ): String =
    if (!options.requiresBootstrap)
      html
    else {
      val withCss =
        if (_has_bootstrap_css(html, options.declaredCss))
          html
        else
          _insert_before(
            html,
            "</head>",
            """  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
              |""".stripMargin
          )
      if (_has_bootstrap_js(withCss, options.declaredJs))
        withCss
      else
        _insert_before(
          withCss,
          "</body>",
          """  <script src="/web/assets/bootstrap.bundle.min.js"></script>
            |""".stripMargin
        )
    }

  def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  private def _has_bootstrap_css(
    html: String,
    declaredCss: Vector[String]
  ): Boolean =
    html.contains("/web/assets/bootstrap.min.css") ||
      html.toLowerCase(java.util.Locale.ROOT).contains("bootstrap.min.css") ||
      declaredCss.exists(_.toLowerCase(java.util.Locale.ROOT).contains("bootstrap"))

  private def _has_bootstrap_js(
    html: String,
    declaredJs: Vector[String]
  ): Boolean =
    html.contains("/web/assets/bootstrap.bundle.min.js") ||
      html.toLowerCase(java.util.Locale.ROOT).contains("bootstrap.bundle.min.js") ||
      declaredJs.exists(_.toLowerCase(java.util.Locale.ROOT).contains("bootstrap"))

  private def _insert_before(
    html: String,
    marker: String,
    insertion: String
  ): String = {
    val lower = html.toLowerCase(java.util.Locale.ROOT)
    val index = lower.indexOf(marker.toLowerCase(java.util.Locale.ROOT))
    if (index >= 0)
      html.substring(0, index) + insertion + html.substring(index)
    else
      html + "\n" + insertion
  }
}
