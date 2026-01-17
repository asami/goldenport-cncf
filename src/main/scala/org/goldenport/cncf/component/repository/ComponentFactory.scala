package org.goldenport.cncf.component.repository

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import scala.util.control.NonFatal

/*
 * @since   Jan. 12, 2026
 * @version Jan. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentFactory {
  def build(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: String
  ): Consequence[Vector[ComponentSource]] = {
    val results: Vector[Consequence[Option[ComponentSource]]] =
      classNames.toVector.map { cn =>
        _to_source(cn, loader, origin)
      }

    _sequence(results) match {
      case Consequence.Success(opts) =>
        Consequence.Success(opts.flatten)
      case Consequence.Failure(conclusion) =>
        Consequence.Failure(conclusion)
    }
  }

  private def _to_source(
    className: String,
    loader: ClassLoader,
    origin: String
  ): Consequence[Option[ComponentSource]] =
    _load_class(className, loader) match {
      case Consequence.Success(cls) =>
        if (_is_component_class(cls) && !_is_module_class(className)) {
          Consequence.Success(
            Some(
              ComponentSource.ClassDef(
                cls.asInstanceOf[Class[_ <: Component]],
                origin
              )
            )
          )
        } else {
          Consequence.Success(None)
        }
      case Consequence.Failure(conclusion) =>
        Consequence.Failure(conclusion)
    }

  private def _load_class(
    className: String,
    loader: ClassLoader
  ): Consequence[Class[_]] = {
    try {
      Consequence.success(Class.forName(className, false, loader))
    } catch {
      case e: ClassNotFoundException => _failure(e)
      case e: NoClassDefFoundError => _failure(e)
      case e: LinkageError => _failure(e)
      case NonFatal(e) => _failure(e)
    }
  }

  private def _is_component_class(cls: Class[_]): Boolean = {
    classOf[Component].isAssignableFrom(cls)
  }

  private def _is_module_class(className: String): Boolean = {
    className.endsWith("$")
  }

  private def _failure[A](e: Throwable): Consequence[A] = {
    val message = Option(e.getMessage).getOrElse("")
    val wrapped = new RuntimeException(s"${e.getClass.getName}: ${message}".trim, e)
    Consequence.failure(wrapped)
  }

  private def _sequence[A](
    xs: Vector[Consequence[A]]
  ): Consequence[Vector[A]] = {
    xs.foldLeft(Consequence.success(Vector.empty[A])) { (acc, x) =>
      acc match {
        case Consequence.Success(values) =>
          x match {
            case Consequence.Success(value) => Consequence.Success(values :+ value)
            case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
          }
        case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
      }
    }
  }
}
