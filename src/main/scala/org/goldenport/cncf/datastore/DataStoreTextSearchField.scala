package org.goldenport.cncf.datastore

import java.util.Locale
import org.goldenport.cncf.directive.Query

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final case class DataStoreTextSearchField(path: String, caseInsensitive: Boolean = false) {
  require(path != null && path.trim.nonEmpty && path == path.trim,
    "a search field requires a nonblank, unpadded declared path")

  def toStoreValue(value: String): String = _version_prefix + _encode(value)

  def contains(value: String): Query.Expr =
    Query.Contains(path, _encode(value), caseInsensitive = false)

  def startsWith(value: String): Query.Expr =
    Query.StartsWith(path, _version_prefix + _encode(value), caseInsensitive = false)

  def endsWith(value: String): Query.Expr =
    Query.EndsWith(path, _encode(value), caseInsensitive = false)

  private val _version_prefix = "u16x1:"
  private val _hex_digits = "0123456789abcdef"

  private def _encode(value: String): String = {
    require(value != null, "search text must be nonnull")
    val normalized = if (caseInsensitive) value.toLowerCase(Locale.ROOT) else value
    val encoded = new java.lang.StringBuilder()
    var index = 0
    while (index < normalized.length) {
      val unit = normalized.charAt(index).toInt
      encoded.append('x')
      encoded.append(_hex_digits.charAt((unit >>> 12) & 15))
      encoded.append(_hex_digits.charAt((unit >>> 8) & 15))
      encoded.append(_hex_digits.charAt((unit >>> 4) & 15))
      encoded.append(_hex_digits.charAt(unit & 15))
      index += 1
    }
    encoded.toString
  }
}
