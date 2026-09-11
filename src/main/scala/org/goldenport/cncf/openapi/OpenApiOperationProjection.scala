package org.goldenport.cncf.openapi

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
/** Explicit, opt-in HTTP method projection for one protocol operation. */
enum OpenApiHttpMethod {
  case GET
  case POST
  case PUT
}

/**
 * Marks an operation whose generated OpenAPI method must not use legacy name
 * inference. Unmarked operations retain the projector's established fallback.
 */
trait OpenApiOperationProjection {
  def openApiHttpMethod: OpenApiHttpMethod
}
