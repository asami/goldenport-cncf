package org.goldenport.cncf.observability

import org.goldenport.observation.calltree.{CallTree, CallTreeBuilder}

/*
 * @since   Feb.  7, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait CallTreeContext {
  def isEnabled: Boolean
  def enter(label: String): Unit
  def leave(): Unit
  def build(): Option[CallTree]
  def clear(): Unit
}

object CallTreeContext {
  object Disabled extends CallTreeContext {
    def isEnabled: Boolean = false
    def enter(label: String): Unit = ()
    def leave(): Unit = ()
    def build(): Option[CallTree] = None
    def clear(): Unit = ()
  }

  final class Enabled() extends CallTreeContext {
    private var _builder: CallTreeBuilder = CallTreeBuilder()
    private val _stack = scala.collection.mutable.Stack.empty[String]

    def isEnabled: Boolean = true

    def enter(label: String): Unit = {
      _stack.push(label)
      _builder.enter(label)
    }

    def leave(): Unit = {
      if (_stack.nonEmpty) {
        val label = _stack.pop()
        _builder.exit(label)
      }
    }

    def build(): Option[CallTree] = Some(_builder.build())

    def clear(): Unit = {
      _stack.clear()
      _builder = CallTreeBuilder()
    }
  }

  def enabled: CallTreeContext = new Enabled()
}
