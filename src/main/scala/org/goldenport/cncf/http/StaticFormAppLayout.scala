package org.goldenport.cncf.http

/*
 * @since   Apr. 12, 2026
 * @version Apr. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppLayout {
  final case class AssetCompletionOptions(
    autoComplete: Boolean = true,
    requiresBootstrap: Boolean = false,
    requiresTextusWidgets: Boolean = false,
    declaredCss: Vector[String] = Vector.empty,
    declaredJs: Vector[String] = Vector.empty
  )

  final case class Options(
    title: String,
    subtitle: String,
    body: String,
    extraHead: String = "",
    extraHeadHtml: String = "",
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
       |  <link href="/web/assets/textus-widgets.css" rel="stylesheet">
       |${options.extraHeadHtml}
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
       |  <script src="/web/assets/textus-widgets.js"></script>
       |${options.extraScript}
       |</body>
       |</html>
       |""".stripMargin

  def completeWidgetAssets(
    html: String,
    options: AssetCompletionOptions
  ): String = {
    val withFramework =
      if (!options.autoComplete || (!options.requiresBootstrap && !options.requiresTextusWidgets))
        html
      else {
      val withBootstrapCss = _insert_css_if_needed(
        html,
        options.requiresBootstrap,
        _has_bootstrap_css(html, options.declaredCss),
        """/web/assets/bootstrap.min.css"""
      )
      val withTextusCss = _insert_css_if_needed(
        withBootstrapCss,
        options.requiresTextusWidgets,
        _has_textus_widgets_css(withBootstrapCss, options.declaredCss),
        """/web/assets/textus-widgets.css"""
      )
      val withBootstrapJs = _insert_js_if_needed(
        withTextusCss,
        options.requiresBootstrap,
        _has_bootstrap_js(withTextusCss, options.declaredJs),
        """/web/assets/bootstrap.bundle.min.js"""
      )
      _insert_js_if_needed(
        withBootstrapJs,
        options.requiresTextusWidgets,
        _has_textus_widgets_js(withBootstrapJs, options.declaredJs),
        """/web/assets/textus-widgets.js"""
      )
    }
    completeDeclaredAssets(withFramework, options)
  }

  def completeDeclaredAssets(
    html: String,
    options: AssetCompletionOptions
  ): String = {
    val withCss = options.declaredCss.distinct.foldLeft(html) { (z, href) =>
      _insert_css_asset_if_absent(z, href)
    }
    options.declaredJs.distinct.foldLeft(withCss) { (z, src) =>
      _insert_js_asset_if_absent(z, src)
    }
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
  ): Boolean = {
    val _ = declaredCss
    html.contains("/web/assets/bootstrap.min.css") ||
      html.toLowerCase(java.util.Locale.ROOT).contains("bootstrap.min.css")
  }

  private def _has_bootstrap_js(
    html: String,
    declaredJs: Vector[String]
  ): Boolean = {
    val _ = declaredJs
    html.contains("/web/assets/bootstrap.bundle.min.js") ||
      html.toLowerCase(java.util.Locale.ROOT).contains("bootstrap.bundle.min.js")
  }

  private def _has_textus_widgets_css(
    html: String,
    declaredCss: Vector[String]
  ): Boolean = {
    val _ = declaredCss
    _has_asset(html, "textus-widgets.css")
  }

  private def _has_textus_widgets_js(
    html: String,
    declaredJs: Vector[String]
  ): Boolean = {
    val _ = declaredJs
    _has_asset(html, "textus-widgets.js")
  }

  private def _has_asset(
    html: String,
    assetName: String
  ): Boolean = {
    val name = assetName.toLowerCase(java.util.Locale.ROOT)
    html.toLowerCase(java.util.Locale.ROOT).contains(name)
  }

  private def _insert_css_if_needed(
    html: String,
    required: Boolean,
    alreadySupplied: Boolean,
    href: String
  ): String =
    if (!required || alreadySupplied)
      html
    else
      _insert_before(
        html,
        "</head>",
        s"""  <link href="${href}" rel="stylesheet">
           |""".stripMargin
      )

  private def _insert_js_if_needed(
    html: String,
    required: Boolean,
    alreadySupplied: Boolean,
    src: String
  ): String =
    if (!required || alreadySupplied)
      html
    else
      _insert_before(
        html,
        "</body>",
        s"""  <script src="${src}"></script>
           |""".stripMargin
      )

  private def _insert_css_asset_if_absent(
    html: String,
    href: String
  ): String = {
    val asset = href.trim
    if (asset.isEmpty || _has_declared_asset(html, asset))
      html
    else
      _insert_before(
        html,
        "</head>",
        s"""  <link href="${escape(asset)}" rel="stylesheet">
           |""".stripMargin
      )
  }

  private def _insert_js_asset_if_absent(
    html: String,
    src: String
  ): String = {
    val asset = src.trim
    if (asset.isEmpty || _has_declared_asset(html, asset))
      html
    else
      _insert_before(
        html,
        "</body>",
        s"""  <script src="${escape(asset)}"></script>
           |""".stripMargin
      )
  }

  private def _has_declared_asset(
    html: String,
    asset: String
  ): Boolean =
    html.toLowerCase(java.util.Locale.ROOT).contains(asset.toLowerCase(java.util.Locale.ROOT))

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
