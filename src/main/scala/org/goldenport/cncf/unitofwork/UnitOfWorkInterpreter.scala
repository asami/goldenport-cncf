package org.goldenport.cncf.unitofwork

import cats.free.Free
import cats.~>
import cats.Id
import org.goldenport.{Consequence, Conclusion, ConsequenceT}
import org.goldenport.cncf.client.{ClientHttpActionCall, ClientHttpGetCall, ClientHttpPostCall}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.operation.OperationResponse

/*
 * Interpreter for UnitOfWorkOp.
 *
 * This bridges declarative UoW programs (Free) and
 * concrete UnitOfWork execution.
 */
/*
 * @since   Jan. 10, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkInterpreter(uow: UnitOfWork) {

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

  def this(uow: UnitOfWork, http: HttpDriver) = {
    this(uow.withHttpDriver(Some(http)))
  }

  def executeDirect[A](op: UnitOfWorkOp[A]): A =
    execute(op)

  def execute(call: ClientHttpActionCall): org.goldenport.http.HttpResponse = call match {
    case ClientHttpGetCall(_, request) =>
      execute(UnitOfWorkOp.HttpGet(_path_(request)))
    case ClientHttpPostCall(_, request) =>
      execute(UnitOfWorkOp.HttpPost(_path_(request), _body_(request), _headers_(request)))
  }

  def executeUowM(call: ClientHttpActionCall): ExecUowM[OperationResponse] = {
    val response = execute(call)
    ConsequenceT.liftF(Free.pure(OperationResponse.Http(response)))
  }

  private def execute[A](op: UnitOfWorkOp[A]): A = op match {
    case UnitOfWorkOp.HttpGet(path) =>
      _http_driver_().get(path)

    case UnitOfWorkOp.HttpPost(path, body, headers) =>
      _http_driver_().post(path, body, headers)

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

  private def _path_(request: HttpRequest): String =
    request.path.asString

  private def _body_(request: HttpRequest): Option[String] = {
    val _ = request
    None
  }

  private def _headers_(request: HttpRequest): Map[String, String] =
    request.header.asMap.map { case (k, v) => k -> v.toString }

  private def _http_driver_(): HttpDriver =
    uow.http_driver.getOrElse {
      throw new IllegalStateException("http driver not configured")
    }
}
