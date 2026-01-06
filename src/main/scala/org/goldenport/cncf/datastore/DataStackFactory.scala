package org.goldenport.cncf.datastore

import org.goldenport.cncf.config.model.Config
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object DataStackFactory {
  def create(config: Config): UnitOfWork = {
    val backend = config.string("datastore.backend").getOrElse("memory")

    backend match {
      case "memory" =>
        val ds = DataStore.inMemorySelectable()
        UnitOfWork.simple(ds)
      case other =>
        throw new IllegalArgumentException(s"Unsupported datastore backend: ${other}")
    }
  }
}
