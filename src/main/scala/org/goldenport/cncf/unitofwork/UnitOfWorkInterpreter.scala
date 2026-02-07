package org.goldenport.cncf.unitofwork

import cats.free.Free
import cats.~>
import org.goldenport.{Consequence, Conclusion, ConsequenceT}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.process.ShellCommandExecutor

/*
 * Interpreter for UnitOfWorkOp.
 *
 * This bridges declarative UoW programs (Free) and
 * concrete UnitOfWork execution.
 */
/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkInterpreter(uow: UnitOfWork) {

  private val step: UnitOfWorkOp ~> Consequence =
    new (UnitOfWorkOp ~> Consequence) {
      def apply[A](op: UnitOfWorkOp[A]): Consequence[A] =
        _execute(op)
    }

  def run[R](program: ExecUowM[R]): Consequence[R] = {
    val result =
      try {
        program.value.foldMap(step)
      } catch {
        case e: Throwable =>
          uow.abort()
          return Consequence.Failure(Conclusion.from(e))
      }
    result match {
      case Consequence.Success(inner) =>
        inner match {
          case Consequence.Success(value) =>
            uow.commit().map(_ => value)
          case failure: Consequence.Failure[_] =>
            uow.abort()
            failure.asInstanceOf[Consequence[R]]
        }
      case failure: Consequence.Failure[_] =>
        uow.abort()
        failure.asInstanceOf[Consequence[R]]
    }
  }

  def this(uow: UnitOfWork, http: HttpDriver) = {
    this(uow.withHttpDriver(Some(http)))
  }

  def execute[A](op: UnitOfWorkOp[A]): A =
    run(ConsequenceT.liftF(Free.liftF(op))).TAKE

  private def _execute[A](op: UnitOfWorkOp[A]): Consequence[A] = op match {
    case UnitOfWorkOp.HttpGet(path) =>
      Consequence(_http_driver_().get(path))

    case UnitOfWorkOp.HttpPost(path, body, headers) =>
      Consequence(_http_driver_().post(path, body, headers))

    case UnitOfWorkOp.HttpPut(path, body, headers) =>
      Consequence(_http_driver_().put(path, body, headers))

    case UnitOfWorkOp.DataStoreLoad(id) =>
      Consequence.failure("DataStore not wired: DataStoreLoad")

    case UnitOfWorkOp.DataStoreSave(id, record) =>
      Consequence.failure("DataStore not wired: DataStoreSave")

    case UnitOfWorkOp.DataStoreDelete(id) =>
      Consequence.failure("DataStore not wired: DataStoreDelete")

    case UnitOfWorkOp.ShellCommandExec(command) =>
      _shell_command_executor_().execute(command)
  }

  private def _http_driver_(): HttpDriver =
    uow.http_driver.getOrElse {
      throw new IllegalStateException("http driver not configured")
    }

  private def _shell_command_executor_(): ShellCommandExecutor =
    uow.shellCommandExecutor
}
