package org.goldenport.cncf.observability

import org.goldenport.observation.calltree.{CallTree, CallTreeBuilder}

/*
 * @since   Feb.  7, 2026
 * @version May.  8, 2026
 * @author  ASAMI, Tomoharu
 */
trait CallTreeContext {
  def isEnabled: Boolean
  def enter(label: String): Unit =
    enter(label, Map.empty)
  def enter(label: String, attributes: Map[String, String]): Unit
  def leave(): Unit
  def mark(label: String, attributes: Map[String, String] = Map.empty): Unit
  def failure(label: String, message: String, attributes: Map[String, String] = Map.empty): Unit
  def build(): Option[CallTree]
  def clear(): Unit
}

object CallTreeContext {
  object Disabled extends CallTreeContext {
    def isEnabled: Boolean = false
    def enter(label: String, attributes: Map[String, String]): Unit = ()
    def leave(): Unit = ()
    def mark(label: String, attributes: Map[String, String]): Unit = ()
    def failure(label: String, message: String, attributes: Map[String, String]): Unit = ()
    def build(): Option[CallTree] = None
    def clear(): Unit = ()
  }

  final class Enabled() extends CallTreeContext {
    private var _builder: CallTreeBuilder = CallTreeBuilder()
    private val _stack = scala.collection.mutable.Stack.empty[String]

    def isEnabled: Boolean = true

    def enter(label: String, attributes: Map[String, String]): Unit = {
      _stack.push(label)
      _builder.enter(label, attributes)
    }

    def leave(): Unit = {
      if (_stack.nonEmpty) {
        val label = _stack.pop()
        _builder.leave(label)
      }
    }

    def mark(label: String, attributes: Map[String, String]): Unit = {
      enter(label, attributes)
      leave()
    }

    def failure(label: String, message: String, attributes: Map[String, String]): Unit =
      _builder.failure(label, message, attributes)

    def build(): Option[CallTree] = Some(_builder.build())

    def clear(): Unit = {
      _stack.clear()
      _builder = CallTreeBuilder()
    }
  }

  def enabled: CallTreeContext = new Enabled()
}
