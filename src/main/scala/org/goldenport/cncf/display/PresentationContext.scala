package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import scala.util.control.NonFatal

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
/** Inert client-safe candidates. Construction never restores runtime authority. */
final class PresentationContext private (
  val locale: Option[String],
  val variant: Option[String],
  val flow: Option[PresentationContext.Flow]
)

object PresentationContext {
  final case class Diagnostic(code: String, path: String, reason: String)
  final class ValidationException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  /** Opaque references and a claimed expiry, not a credential or an assignment. */
  final class Flow private (
    val displayInstance: String,
    val correlation: String,
    val expiresAt: Instant
  )

  object Flow {
    def createC(displayInstance: String, correlation: String, expiresAt: Instant): Consequence[Flow] = {
      Consequence.zip3(
        _text(displayInstance, 256, "displayInstance"),
        _text(correlation, 256, "correlation"),
        if (expiresAt == null) _failure[Instant]("expiresAt") else Consequence.success(expiresAt)
      ).map { case (instance, correlation, expiry) => new Flow(instance, correlation, expiry) }
    }
  }

  def createC(
    locale: Option[String] = None,
    variant: Option[String] = None,
    flow: Option[Flow] = None
  ): Consequence[PresentationContext] = {
    Consequence.zip3(
      _optional(locale, "locale")(_locale),
      _optional(variant, "variant")(_text(_, 128, "variant")),
      _optional(flow, "flow")(Consequence.success)
    ).map { case (language, variant, flow) => new PresentationContext(language, variant, flow) }
  }

  private def _optional[A](value: Option[A], path: String)(validate: A => Consequence[A]): Consequence[Option[A]] = {
    if (value == null || value.contains(null)) _failure(path)
    else value match {
      case None => Consequence.success(None)
      case Some(entry) => validate(entry).map(Some(_))
    }
  }

  private def _locale(text: String): Consequence[String] = {
    _text(text, 128, "locale").flatMap { value =>
      try {
        if (new Locale.Builder().setLanguageTag(value).build().toLanguageTag == value) Consequence.success(value)
        else _failure("locale")
      } catch { case NonFatal(_) => _failure("locale") }
    }
  }

  private def _text(text: String, limit: Int, path: String): Consequence[String] = {
    if (text == null || text.isEmpty || text.length > limit || text != text.trim) _failure(path)
    else {
      var index = 0
      var valid = true
      while (index < text.length && valid) {
        val char = text.charAt(index)
        if (Character.isHighSurrogate(char)) {
          if (index + 1 >= text.length || !Character.isLowSurrogate(text.charAt(index + 1))) valid = false
          else index += 1
        } else if (Character.isLowSurrogate(char) || Character.isISOControl(char)) valid = false
        index += 1
      }
      val first = text.codePointAt(0)
      val last = text.codePointBefore(text.length)
      if (!valid || Character.isWhitespace(first) || Character.isSpaceChar(first) ||
        Character.isWhitespace(last) || Character.isSpaceChar(last) ||
        text.getBytes(StandardCharsets.UTF_8).length > limit) _failure(path)
      else Consequence.success(text)
    }
  }

  private def _failure[A](path: String): Consequence[A] = {
    Consequence.fail(Taxonomy.argumentInvalid, new ValidationException(Diagnostic(
      "DSP96_CONTEXT_VALUE_INVALID", path, "expected canonical bounded nonnull presentation value")))
  }
}
