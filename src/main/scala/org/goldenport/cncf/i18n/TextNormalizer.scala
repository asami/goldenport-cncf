package org.goldenport.cncf.i18n

import java.text.Normalizer
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextNormalizationRequest(
  text: String,
  profile: String = TextNormalizer.DisplaySafeV1,
  locale: Option[Locale] = None,
  collectProvenance: Boolean = false
)

final case class TextNormalizationResult(
  text: String,
  profile: String,
  locale: Locale,
  transforms: Vector[String]
)

object TextNormalizer {
  val DisplaySafeV1 = "display-safe-v1"

  private val _unicode_nfc = "unicode-nfc"
  private val _unicode_spacing = "unicode-spacing"
  private val _whitespace_collapse = "whitespace-collapse"
  private val _invisible_format_removal = "invisible-format-removal"
  private val _japanese_ascii_width = "ja-ascii-width"
  private val _japanese_katakana_width = "ja-katakana-width"

  def normalize(
    request: TextNormalizationRequest
  )(using ctx: ExecutionContext): Consequence[TextNormalizationResult] =
    request.profile match {
      case DisplaySafeV1 =>
        val locale = request.locale.getOrElse(ctx.core.locale)
        val (text, transforms) = _display_safe(request.text, locale)
        Consequence.success(
          TextNormalizationResult(
            text = text,
            profile = DisplaySafeV1,
            locale = locale,
            transforms = if (request.collectProvenance) transforms else Vector.empty
          )
        )
      case other =>
        Consequence.argumentInvalid(s"unknown text normalization profile: $other")
    }

  private def _display_safe(source: String, locale: Locale): (String, Vector[String]) = {
    val steps = Vector.newBuilder[String]
    var text = source

    text = _apply(text, Normalizer.normalize(text, Normalizer.Form.NFC), _unicode_nfc, steps)
    text = _apply(text, _normalize_spacing(text), _unicode_spacing, steps)
    text = _apply(text, _remove_invisible_formatting(text), _invisible_format_removal, steps)
    text = _apply(text, _collapse_whitespace(text), _whitespace_collapse, steps)
    if (_is_japanese(locale)) {
      text = _apply(text, _normalize_ascii_width(text), _japanese_ascii_width, steps)
      text = _apply(text, _normalize_katakana_width(text), _japanese_katakana_width, steps)
    }
    (text, steps.result())
  }

  private def _apply(
    current: String,
    normalized: String,
    transform: String,
    steps: scala.collection.mutable.Builder[String, Vector[String]]
  ): String = {
    if (current != normalized)
      steps += transform
    normalized
  }

  private def _normalize_spacing(source: String): String =
    source.codePoints().toArray.map { codepoint =>
      if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) ' '.toInt
      else codepoint
    }.foldLeft(new StringBuilder) { (z, codepoint) =>
      z.appendAll(Character.toChars(codepoint))
    }.result()

  private def _collapse_whitespace(source: String): String =
    source.trim.replaceAll(" +", " ")

  private def _remove_invisible_formatting(source: String): String =
    source.filterNot(character =>
      character == '\ufeff' || character == '\u200b' || character == '\u2060'
    )

  private def _is_japanese(locale: Locale): Boolean =
    locale.getLanguage.equalsIgnoreCase(Locale.JAPANESE.getLanguage)

  private def _normalize_ascii_width(source: String): String =
    source.map {
      case character if character >= '\uff10' && character <= '\uff19' => (character - 0xfee0).toChar
      case character if character >= '\uff21' && character <= '\uff3a' => (character - 0xfee0).toChar
      case character if character >= '\uff41' && character <= '\uff5a' => (character - 0xfee0).toChar
      case character => character
    }

  private def _normalize_katakana_width(source: String): String = {
    val result = new StringBuilder
    val katakana = new StringBuilder
    def flush(): Unit =
      if (katakana.nonEmpty) {
        result.append(Normalizer.normalize(katakana.result(), Normalizer.Form.NFKC))
        katakana.clear()
      }
    source.foreach { character =>
      if (character >= '\uff61' && character <= '\uff9f')
        katakana.append(character)
      else {
        flush()
        result.append(character)
      }
    }
    flush()
    result.result()
  }
}
