package org.goldenport.cncf.observability

import java.util.Locale

import org.goldenport.observation.calltree.{CallTree, CallTreeBuilder}

/*
 * @since   Feb.  7, 2026
 *  version May. 10, 2026
 * @version Jul.  4, 2026
 * @author  ASAMI, Tomoharu
 */
trait CallTreeContext {
  def isEnabled: Boolean
  def currentLabels: Vector[String] = Vector.empty
  def hasOpenLabelPrefix(prefix: String): Boolean =
    currentLabels.exists(_.startsWith(prefix))
  def enter(label: String): Unit =
    enter(label, Map.empty)
  def enter(label: String, attributes: Map[String, String]): Unit
  def leave(): Unit =
    leave(Map.empty)
  def leave(attributes: Map[String, String]): Unit
  def mark(label: String, attributes: Map[String, String] = Map.empty): Unit
  def failure(label: String, message: String, attributes: Map[String, String] = Map.empty): Unit
  def build(): Option[CallTree]
  def clear(): Unit
}

object CallTreeContext {
  object Disabled extends CallTreeContext {
    def isEnabled: Boolean = false
    def enter(label: String, attributes: Map[String, String]): Unit = ()
    def leave(attributes: Map[String, String]): Unit = ()
    def mark(label: String, attributes: Map[String, String]): Unit = ()
    def failure(label: String, message: String, attributes: Map[String, String]): Unit = ()
    def build(): Option[CallTree] = None
    def clear(): Unit = ()
  }

  final class Enabled() extends CallTreeContext {
    private var _builder: CallTreeBuilder = CallTreeBuilder()
    private val _stack = scala.collection.mutable.Stack.empty[String]

    def isEnabled: Boolean = true

    override def currentLabels: Vector[String] =
      _stack.reverse.toVector

    def enter(label: String, attributes: Map[String, String]): Unit = {
      _stack.push(label)
      _builder.enter(label, _sanitize(attributes))
    }

    def leave(attributes: Map[String, String]): Unit = {
      if (_stack.nonEmpty) {
        val label = _stack.pop()
        _builder.leave(label, _sanitize(attributes))
      }
    }

    def mark(label: String, attributes: Map[String, String]): Unit = {
      enter(label, attributes)
      leave()
    }

    def failure(label: String, message: String, attributes: Map[String, String]): Unit =
      _builder.failure(label, _redact_sensitive_text(message), _sanitize(attributes))

    def build(): Option[CallTree] = Some(_builder.build())

    def clear(): Unit = {
      _stack.clear()
      _builder = CallTreeBuilder()
    }

    private def _sanitize(attributes: Map[String, String]): Map[String, String] =
      attributes.map { case (key, value) =>
        val sanitized =
          if (_is_sensitive_key(key))
            "***"
          else
            _redact_sensitive_text(value)
        key -> sanitized
      }

    private def _is_sensitive_key(key: String): Boolean = {
      val normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
      val sensitivetokenkeys = Set(
        "token",
        "accesstoken",
        "refreshtoken",
        "idtoken",
        "authorization",
        "cookie"
      )
      normalized.contains("password") ||
        normalized.contains("passwd") ||
        normalized.contains("secret") ||
        normalized.contains("session") ||
        normalized.contains("credential") ||
        normalized.contains("apikey") ||
        normalized.contains("privatekey") ||
        sensitivetokenkeys.contains(normalized)
    }

    private def _redact_sensitive_text(value: String): String = {
      val sensitive = """password|passwd|secret|token|access[-_]?session[-_]?id|refresh[-_]?session[-_]?id|session[-_]?id|session|authorization|cookie|credential|api[-_]?key|private[-_]?key"""
      val jsonlike = s"""(?i)("(?:$sensitive)"\\s*:\\s*)"[^"]*"""".r
      val yamllike = s"""(?im)^([ \\t]*(?:$sensitive)[ \\t]*:[ \\t]*).+$$""".r
      val lineequalslike = s"""(?im)^([ \\t]*(?:$sensitive)[ \\t]*=[ \\t]*).+$$""".r
      val formlike = s"""(?i)(^|[?&\\s,;])($sensitive)(\\s*[=:]\\s*)([^&\\s,;]+)""".r
      val jsonredacted = jsonlike.replaceAllIn(value, m => s"""${m.group(1)}"***"""")
      val yamlredacted = yamllike.replaceAllIn(jsonredacted, m => s"${m.group(1)}***")
      val lineequalsredacted = lineequalslike.replaceAllIn(yamlredacted, m => s"${m.group(1)}***")
      formlike.replaceAllIn(lineequalsredacted, m => s"${m.group(1)}${m.group(2)}${m.group(3)}***")
    }
  }

  def enabled: CallTreeContext = new Enabled()
}
