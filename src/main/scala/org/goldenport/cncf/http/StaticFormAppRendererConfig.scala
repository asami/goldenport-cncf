package org.goldenport.cncf.http

/*
 * Renderer volume and debug display tuning.
 *
 * @since   May. 18, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class StaticFormAppRendererConfig(
  defaultPageSize: Int = 20,
  adminPageSize: Int = 100,
  adminFilterFieldLimit: Int = 6,
  previewLimit: Int = 50,
  debugBodyPreviewChars: Int = 12000,
  callTreeInitialOpenDepth: Int = 1
) {
  def validationError: Option[String] =
    Vector(
      "default-page-size" -> defaultPageSize,
      "admin-page-size" -> adminPageSize,
      "admin-filter-field-limit" -> adminFilterFieldLimit,
      "preview-limit" -> previewLimit,
      "debug-body-preview-chars" -> debugBodyPreviewChars,
      "calltree.initial-open-depth" -> callTreeInitialOpenDepth
    ).collectFirst {
      case (name, value) if value <= 0 =>
        s"textus.web.renderer.${name} must be positive: ${value}"
    }
}

object StaticFormAppRendererConfig {
  val default: StaticFormAppRendererConfig =
    StaticFormAppRendererConfig()
}
