package org.goldenport.cncf.http

/*
 * @since   Apr. 12, 2026
 * @version Apr. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppLayout {
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

  def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
}
