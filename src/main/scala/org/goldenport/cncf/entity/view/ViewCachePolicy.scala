package org.goldenport.cncf.entity.view

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
enum ViewQueryCacheScope(val name: String) {
  case Shared extends ViewQueryCacheScope("shared")
  case Principal extends ViewQueryCacheScope("principal")
  case Disabled extends ViewQueryCacheScope("disabled")
}

final case class ViewCachePolicy(
  maxEntities: Int = 10000,
  maxQueries: Int = 512,
  queryChunkSize: Int = 1000,
  queryScope: ViewQueryCacheScope = ViewQueryCacheScope.Shared,
  metricsName: String = "view"
) {
  def normalized: ViewCachePolicy =
    if (maxQueries <= 0 && queryScope != ViewQueryCacheScope.Disabled)
      copy(queryScope = ViewQueryCacheScope.Disabled)
    else
      this
}
