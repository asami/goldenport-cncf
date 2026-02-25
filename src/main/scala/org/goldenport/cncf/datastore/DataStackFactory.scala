package org.goldenport.cncf.datastore

import org.goldenport.configuration.Configuration
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.entity.EntityStore // TODO

/*
 * @since   Jan.  6, 2026
 * @version Feb. 22, 2026
 * @author  ASAMI, Tomoharu
 */
object DataStackFactory {
  def create(config: Configuration): UnitOfWork = {
    val backend = config.string("datastore.backend").getOrElse("memory")

    backend match {
      case "memory" =>
        val ds = DataStore.inMemorySearchable()
        val es = EntityStore.noop() // TODO
        UnitOfWork.simple(ds, es)
      case other =>
        throw new IllegalArgumentException(s"Unsupported datastore backend: ${other}")
    }
  }
}
