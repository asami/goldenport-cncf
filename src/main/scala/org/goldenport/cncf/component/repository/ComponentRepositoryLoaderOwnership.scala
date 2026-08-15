package org.goldenport.cncf.component.repository

import java.nio.file.Path
import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentLocalFirstClassLoader}

/*
 * Component development loaders become subsystem-owned only after discovery
 * has fully materialized its successful result.  Failures and exceptions
 * before that transfer retain local cleanup responsibility.
 *
 * @since   Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[repository] object ComponentRepositoryLoaderOwnership {
  def retainDevelopmentComponentC[A](
    loader: ComponentLocalFirstClassLoader,
    params: ComponentCreate,
    log: BootstrapLog,
    artifact: String
  )(materialize: => Consequence[A]): Consequence[A] = {
    var retained = false
    try {
      val result = materialize
      result match {
        case Consequence.Success(_) =>
          params.subsystem.registerComponentClassLoader(loader)
          retained = true
        case Consequence.Failure(_) => ()
      }
      result
    } finally {
      if (!retained)
        try loader.close()
        catch {
          case NonFatal(e) =>
            log.warn(s"[component-dev-dir] artifact=$artifact component loader cleanup failed cause=${e.getMessage}")
      }
    }
  }

  def discoverDevelopment(
    loader: ComponentLocalFirstClassLoader,
    params: ComponentCreate,
    log: BootstrapLog,
    base: Path
  )(materialize: => Consequence[Vector[Component]]): Vector[Component] =
    retainDevelopmentComponentC(loader, params, log, base.getFileName.toString)(materialize) match {
      case Consequence.Success(components) => components
      case Consequence.Failure(conclusion) =>
        log.warn(s"[component-dev-dir] discovery failed cause=${conclusion.show}")
        Vector.empty
    }
}
