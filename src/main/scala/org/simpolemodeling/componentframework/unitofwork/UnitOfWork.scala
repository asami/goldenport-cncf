package org.simplemodeling.componentframework.unitofwork

import scala.util.{Try, Success, Failure}
import java.io.File
import org.simplemodeling.componentframework.*
import org.simplemodeling.componentframework.entity.EntityStore
import org.simplemodeling.componentframework.entity.EntityStore.*

class UnitOfWork(
  context: ExecutionContext
) {
  import UnitOfWork.*

  def create[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Try[CreateResult[T]] = ???

  def get[T](store: EntityStore)(using instance: EntityInstance[T]): Try[GetResult[T]] = ???

  def list[T](store: EntityStore, directive: ListDirective)(using instance: EntityInstance[T]): Try[ListResult[T]] = ???

  def update[T](store: EntityStore, id: EntityId, data: Record)(using instance: EntityInstance[T]): Try[UpdateResult[T]] = ???

  def delete[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Try[DeleteResult[T]] = ???

  def createFile(file: File, data: String): Try[Unit] = ???

  def sendMessage(msg: Message): Try[Unit] = ???

  def commit(): Try[CommitResult] = ???

  def abort(): Try[AbortResult] = ???
}

object UnitOfWork {
  import cats._
  import cats.data._
  import cats.free._
  import cats.implicits._

  type CommitResult = Unit
  type AbortResult = Unit
  type Message = String

  sealed trait UnitOfWorkOp[T]
  case class CreateEntity[ENTITY](store: EntityStore, data: Record, instance: EntityInstance[ENTITY]) extends UnitOfWorkOp[Try[CreateResult[ENTITY]]]

  type UnitOfWorkFM[T] = Free[UnitOfWorkOp, T]

  type UnitOfWorkEitherFM[T] = EitherT[UnitOfWorkFM, Throwable, T]

  type Config = ExecutionContext
  type Log = List[String]
  type State = Map[String, Any]

  type UnitOfWorkRWSEitherFM[T] = RWST[UnitOfWorkEitherFM, Config, Log, State, T]

  def create[A](store: EntityStore, data: Record)(using instance: EntityInstance[A]): UnitOfWorkFM[Try[CreateResult[A]]] =
    Free.liftF(CreateEntity(store, data, instance))

  // def interpreterUnitOfWork[ENTITY, T](unitofwork: UnitOfWorkFM[T]): T = {
  //   unitofwork.foldMap {
  //     case m: CreateEntity[ENTITY] => ???
  //   }
  // }

  def x: UnitOfWorkOp ~> Id = new (UnitOfWorkOp ~> Id) {
    def apply[A](fa: UnitOfWorkOp[A]): Id[A] = fa match {
      case m: CreateEntity[_] => ???
    }
  }

  class UnitOfWorkInterpreter(unitofwork: UnitOfWork) extends (UnitOfWorkOp ~> Id) {
    def apply[A](fa: UnitOfWorkOp[A]): Id[A] = fa match {
      case m: CreateEntity[_] => ???
    }

    def commit() = unitofwork.commit()
    def abort() = unitofwork.abort()
  }
  object UnitOfWorkInterpreter {
    def create(ctx: ExecutionContext): UnitOfWorkInterpreter = {
      val uow = new UnitOfWork(ctx)
      new UnitOfWorkInterpreter(uow)
    }
  }

  def program[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): UnitOfWorkFM[Try[CreateResult[T]]] =
    for {
      r <- create(store, data) 
    } yield r

  case class Product()

  def z: Try[CreateResult[Product]] = {
    val store: EntityStore = ???
    val data: Record = Map.empty

    val ctx = ExecutionContext.create()

    implicit val instance = new EntityInstance[Product] {
    }

    program[Product](store, data).foldMap(x)
    program[Product](store, data).foldMap(UnitOfWorkInterpreter.create(ctx))
  }

  trait ServiceOperation[T] {
    def execute() = {
      val ctx = ExecutionContext.create()
      val uowi = UnitOfWorkInterpreter.create(ctx)
      val program = operation_program
      val r = program.foldMap(uowi)
      r match {
        case Success(s) =>
          uowi.commit()
          s
        case Failure(e) =>
          uowi.abort()
          e
      }
    }

    protected def operation_program: UnitOfWorkFM[Try[T]]
  }

  class XOperation extends ServiceOperation[CreateResult[Product]] {
    protected def operation_program: UnitOfWorkFM[Try[CreateResult[Product]]] = {
      val store: EntityStore = ???
      val data: Record = Map.empty

      implicit val instance = new EntityInstance[Product] {
      }

      for {
        r <- create(store, data)
      } yield r
    }
  }

  trait LogicOperation[T] {
    def apply()(using ctx: ExecutionContext) = {
      ???
    }

    protected def operation_program: UnitOfWorkFM[Try[T]]
  }

  given EntityInstance[Product] with {
  }

  def x(using ctx: ExecutionContext) = {
    val store: EntityStore = ???
    val data: Record = Map.empty

    val program = for {
      r <- create(store, data)
    } yield r
    program.foldMap(ctx.unitOfWorkInterpreter)
  }
}

object Tryout {
  import cats._
  import cats.data._
  import cats.free._
  import cats.implicits._

  sealed trait TryoutOp[T]
  case class Plus(lhs: Int, rhs: Int) extends TryoutOp[Int]
  case class PlusTry(lhs: Int, rhs: Int) extends TryoutOp[Try[Int]]

  type TryoutFM[T] = Free[TryoutOp, T]
  type TryoutEitherFM[T] = EitherT[TryoutFM, Throwable, T]

  type Config = Map[String, String]
  type Log = List[String]
  type State = Map[String, String]
  type XMonad[T] = RWST[Id, Config, Log, State, TryoutEitherFM[T]]

  type YMonad[T] = RWST[TryoutFM, Config, Log, State, Try[T]]

  type ZMonad[T] = RWST[TryoutFM, Config, Log, State, Either[Throwable, T]]

  type WMonad[T] = RWST[TryoutEitherFM, Config, Log, State, T]

  def wwi: TryoutOp ~> Id = new (TryoutOp ~> Id) {
    def apply[T](fa: TryoutOp[T]): Id[T] = fa match {
      case Plus(lhs, rhs) => ???
      case PlusTry(lhs, rhs) => ???
    }
  }

  def ww: Try[Int] = {
    val program = for {
      r <- www
    } yield r
    val a = program.run(Map.empty, Map.empty).value.foldMap(wwi)
    a match {
      case Right((log, state, value)) => Success(value)
      case Left(e) => Failure(e)
    }
  }

  def yy = {
    // def _f_(p: Int): YMonad[Int] = RWST { (config, state) =>
    //   ???
    //   (result, state, config)
    // }

//     val program = for {
//       a <- yyy
// //      r <- _f_(a)
//     } yield r
    //    program.run()
    ???
  }

  def zz = {
    val interpreter = ???

    val program = for {
      _ <- zzz
    } yield ()
    // program.run()
    ???
  }

  def xxx: XMonad[Int] = {
    IndexedReaderWriterStateT.liftF(EitherT.right(Free.liftF(Plus(1, 2))))
  }

  def yyy: YMonad[Int] = {
    IndexedReaderWriterStateT.liftF(Free.liftF(PlusTry(1, 2)))
  }

  def zzz: ZMonad[Int] = {
    IndexedReaderWriterStateT.liftF(Free.liftF(PlusTry(1, 2)).map(_.toEither))
  }

  def www: WMonad[Int] = RWST { (config, state) =>
//    val result: EitherT[TryoutFM, Throwable, Int] = ???
    val result: EitherT[TryoutFM, Throwable, (Log, Config, Int)] = EitherT(
      Free.liftF(PlusTry(1, 2)).map(_.toEither.map(x => (Nil, config, x)))
    )
    // (result, state, config)
    result
  }

  def www2: WMonad[Int] = RWST { (config, state) =>
    EitherT {
      Free.liftF(PlusTry(1, 2)).map(_.toEither.map(x => (Nil, config, x)))
    }
  }
}
