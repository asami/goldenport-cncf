package org.goldenport.cncf.repository

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait Repository[A] {
  protected final def unit_of_work(
    using ctx: ExecutionContext
  ): UnitOfWork =
    ctx.unitOfWork
}
