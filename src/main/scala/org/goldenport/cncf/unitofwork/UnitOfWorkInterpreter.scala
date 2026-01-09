package org.goldenport.cncf.unitofwork

import cats.free.Free
import cats.~>
import cats.Id
import org.goldenport.{Consequence, Conclusion}

/*
 * Interpreter for UnitOfWorkOp.
 *
 * This bridges declarative UoW programs (Free) and
 * concrete UnitOfWork execution.
 */
/*
 * @since   Jan. 10, 2026
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkInterpreter(uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver) {

  private val step: UnitOfWorkOp ~> Id =
    new (UnitOfWorkOp ~> Id) {
      def apply[A](op: UnitOfWorkOp[A]): Id[A] =
        execute(op)
    }

  def run[A](program: ExecUowM[A]): Consequence[A] =
    try {
      val result: Consequence[A] = program.value.foldMap(step)
      result.flatMap { value =>
        uow.commit().map(_ => value)
      }
    } catch {
      case e: Throwable =>
        uow.abort()
        Consequence.Failure(Conclusion.from(e))
    }

  def executeDirect[A](op: UnitOfWorkOp[A]): A =
    execute(op)

  private def execute[A](op: UnitOfWorkOp[A]): A = op match {
    case UnitOfWorkOp.HttpGet(path) =>
      http.get(path)

    case UnitOfWorkOp.HttpPost(path, body, headers) =>
      http.post(path, body, headers)

    case UnitOfWorkOp.DataStoreLoad(id) =>
      // TODO: delegate to DataStore
      throw new NotImplementedError("DataStore not wired: DataStoreLoad")

    case UnitOfWorkOp.DataStoreSave(id, record) =>
      // TODO: delegate to DataStore
      ()

    case UnitOfWorkOp.DataStoreDelete(id) =>
      // TODO: delegate to DataStore
      ()
  }
}
