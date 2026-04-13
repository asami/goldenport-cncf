package org.goldenport.cncf.statemachine

import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object MvelEvaluator {
  def evalBoolean(expression: String, context: Map[String, Any]): Consequence[Boolean] =
    try {
      val cls = Class.forName("org.mvel2.MVEL")
      val method = cls.getMethod("eval", classOf[String], classOf[java.util.Map[?, ?]])
      val javactx = new java.util.HashMap[String, AnyRef]()
      context.foreach { case (k, v) => javactx.put(k, _to_anyref(v)) }
      val result = method.invoke(null, expression, javactx)
      _to_boolean(expression, result)
    } catch {
      case _: ClassNotFoundException =>
        Consequence.serviceUnavailable("MVEL engine is not available on classpath")
      case NonFatal(e) =>
        Consequence.operationInvalid(s"MVEL expression evaluation failed: ${e.getMessage}")
    }

  private def _to_anyref(p: Any): AnyRef =
    p match {
      case null => null
      case m: java.lang.Boolean => m
      case m: java.lang.Integer => m
      case m: java.lang.Long => m
      case m: java.lang.Double => m
      case m: java.lang.Float => m
      case m: java.lang.Short => m
      case m: java.lang.Byte => m
      case m: java.lang.Character => m
      case m: AnyRef => m
      case m => m.toString
    }

  private def _to_boolean(expression: String, p: Any): Consequence[Boolean] =
    p match {
      case m: java.lang.Boolean => Consequence.success(m.booleanValue)
      case m: Boolean => Consequence.success(m)
      case _ =>
        Consequence.operationInvalid(s"MVEL expression did not return Boolean: '$expression'")
    }
}
