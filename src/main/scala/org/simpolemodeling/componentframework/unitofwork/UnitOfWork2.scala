package org.simplemodeling.componentframework.unitofwork

import org.simplemodeling.componentframework.*
import org.simplemodeling.componentframework.entity.EntityStore
import org.simplemodeling.componentframework.entity.EntityStore.*

class UnitOfWork2 {
  import UnitOfWork2.*

  def create[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
    ???
  }

  def get[T](store: EntityStore)(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
    ???
  }

  def list[T](store: EntityStore, directive: ListDirective)(using instance: EntityInstance[T]): Consequence[ListResult[T]] = {
    ???
  }

  def update[T](store: EntityStore, id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
    ???
  }

  def delete[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
    ???
  }

  def commit(): Consequence[CommitResult] = {
    ???
  }

  def abort(): Consequence[AbortResult] = {
    ???
  }
}

object UnitOfWork2 {
  import cats._
  import cats.data._
  import cats.free._
  import cats.implicits._

  type CommitResult = Unit
  type AbortResult = Unit

  sealed trait UnitOfWorkOp[T]
  case class CreateEntity[ENTITY](store: EntityStore, data: Record, instance: EntityInstance[ENTITY]) extends UnitOfWorkOp[Consequence[CreateResult[ENTITY]]]

  type UnitOfWorkFM[T] = Free[UnitOfWorkOp, T]

  def create[A](store: EntityStore, data: Record)(using instance: EntityInstance[A]): UnitOfWorkFM[Consequence[CreateResult[A]]] =
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
    def create(): UnitOfWorkInterpreter = {
      val uow = new UnitOfWork(???)
      new UnitOfWorkInterpreter(uow)
    }
  }

  def program[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): UnitOfWorkFM[Consequence[CreateResult[T]]] =
    for {
      r <- create(store, data) 
    } yield r

  case class Product()

  def z: Consequence[CreateResult[Product]] = {
    val store: EntityStore = ???
    val data: Record = Map.empty

    implicit val instance = new EntityInstance[Product] {
    }

    program[Product](store, data).foldMap(x)
    program[Product](store, data).foldMap(UnitOfWorkInterpreter.create())
  }

  trait ServiceOperation[T] {
    def execute() = {
      val uowi = UnitOfWorkInterpreter.create()
      val program = operation_program
      val r = program.foldMap(uowi)
      r match {
        case Consequence.Success(s) =>
          uowi.commit()
          s
        case Consequence.Error(e) =>
          uowi.abort()
          e
      }
    }

    protected def operation_program: UnitOfWorkFM[Consequence[T]]
  }

  class XOperation extends ServiceOperation[CreateResult[Product]] {
    protected def operation_program: UnitOfWorkFM[Consequence[CreateResult[Product]]] = {
      val store: EntityStore = ???
      val data: Record = Map.empty

      implicit val instance = new EntityInstance[Product] {
      }

      for {
        r <- create(store, data)
      } yield r
    }
  }
}
