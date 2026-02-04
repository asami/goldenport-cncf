package org.goldenport.cncf.collaborator

import java.lang.{Boolean => JBoolean, Byte => JByte, Character => JCharacter, Double => JDouble, Float => JFloat, Integer => JInteger, Long => JLong, Short => JShort}

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.collaborator.api.DefaultActionCall
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Jan. 30, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Collaborator() {
  def execute(
    ctx: ExecutionContext,
    request: Request
  ): Consequence[Response]
}

object Collaborator {
  case class Core(
    collaborator: api.Collaborator
  )
  object Core {
    trait Holder {
      def core: Core

      def collaborator = core.collaborator
    }
  }

  case class Instance(core: Core) extends Collaborator with Core.Holder {
    def execute(ctx: ExecutionContext, request: Request): Consequence[Response] = {
      val ccall = toCollaborator(request)
      try {
        val r = collaborator.invoke(ccall)
        fromCollaborator(r)
      } catch {
        case NonFatal(e) => Consequence.failure(e) // TODO
      }
    }
  }

  def toCollaborator(p: Request): api.ActionCall = {
    val arguments = p.arguments.map(arg => arg.name -> _argumentValueAsAnyRef(arg.value)).toMap
    val javaArgs = arguments.asJava
    new DefaultActionCall(p.operation, javaArgs)
  }

  def fromCollaborator(p: api.Consequence): Consequence[Response] = {
    if (p.isSuccess) {
      Consequence.success(Response.Scalar(p.value().toString))
    } else {
      val observation = Option(p.observation())
      val message = observation.flatMap(obs => Option(obs.message())).getOrElse("collaborator failure")
      Consequence.failure(new RuntimeException(message))
    }
  }

  // TODO: stabilize this wrapper API once the runtime and API collaborators converge.
  def apply(apiCollaborator: api.Collaborator): Collaborator =
    Instance(Core(apiCollaborator))


  private def _argumentValueAsAnyRef(value: Any): AnyRef = {
    if (value == null) {
      null
    } else {
      value match {
        case ref: AnyRef => ref
        case v: Int => JInteger.valueOf(v)
        case v: Long => JLong.valueOf(v)
        case v: Short => JShort.valueOf(v)
        case v: Byte => JByte.valueOf(v)
        case v: Double => JDouble.valueOf(v)
        case v: Float => JFloat.valueOf(v)
        case v: Boolean => JBoolean.valueOf(v)
        case v: Char => JCharacter.valueOf(v)
        case other => other.toString
      }
    }
  }

/*
    val call: JActionCall = new DefaultActionCall(operationName, arguments.mapValues(_.asInstanceOf[Object]).asJava)
    val consequence: JConsequence = resolveCollaborator().invoke(call)
    if (consequence.isSuccess) {
      Consequence.success(OperationResponse.Scalar(consequence.value().toString))
    } else {
      Consequence.failure(new RuntimeException("collaborator failure"))
    }
 */ 
}
