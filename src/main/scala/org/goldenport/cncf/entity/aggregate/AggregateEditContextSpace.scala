package org.goldenport.cncf.entity.aggregate

import java.time.{Duration, Instant}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Jun. 14, 2026
 *  version Jun. 18, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateEditContextSpace(
  ttl: Duration = Duration.ofHours(2)
) {
  private val _contexts = new ConcurrentHashMap[String, AggregateEditContext[?]]()
  private val _leases = new ConcurrentHashMap[AggregateEditContextSpace.LeaseKey, String]()

  def begin[A](
    timestamp: Instant,
    contextid: String,
    aggregatename: String,
    aggregateid: EntityId,
    basetoken: String,
    aggregate: A,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous,
    lockscope: AggregateEditLockScope = AggregateEditLockScope.Principal,
    metadata: Record = Record.empty
  ): Consequence[AggregateEditContext[A]] = synchronized {
    val effectivecontextid = contextid.trim
    if (effectivecontextid.isEmpty)
      return Consequence.argumentMissing("contextId")
    expire(timestamp)
    val key = AggregateEditContextSpace.LeaseKey(aggregatename, aggregateid.print)
    Option(_leases.get(key)).flatMap(id => Option(_contexts.get(id))) match {
      case Some(existing) if _same_owner(existing, owner) =>
        val touched = existing.asInstanceOf[AggregateEditContext[A]].touch(timestamp)
        _contexts.put(touched.contextId, touched)
        return Consequence.success(touched)
      case Some(existing) =>
        return Consequence.operationConflict(
          "aggregate_edit_context_begin",
          Seq(Descriptor.Facet.Message(
            s"Aggregate is already being edited: aggregate=$aggregatename, aggregateId=${aggregateid.print}, owner=${_owner_label(existing)}"
          ))
        )
      case None =>
    }
    if (_contexts.containsKey(effectivecontextid))
      return Consequence.stateConflict(s"aggregate edit context already exists: $effectivecontextid")
    val context = AggregateEditContext[A](
      contextId = effectivecontextid,
      aggregateName = aggregatename,
      aggregateId = aggregateid,
      baseToken = basetoken,
      owner = owner,
      lockScope = lockscope,
      workingAggregate = aggregate,
      dirty = false,
      createdAt = timestamp,
      updatedAt = timestamp,
      metadata = metadata
    )
    _contexts.put(context.contextId, context)
    _leases.put(key, context.contextId)
    Consequence.success(context)
  }

  def get[A](
    timestamp: Instant,
    contextid: String,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous
  ): Consequence[AggregateEditContext[A]] = synchronized {
    _context[A](timestamp, contextid, owner).map { context =>
      val touched = context.touch(timestamp)
      _contexts.put(contextid, touched)
      touched
    }
  }

  def update[A](
    timestamp: Instant,
    contextid: String,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous
  )(
    action: A => Consequence[A]
  ): Consequence[AggregateEditContext[A]] = synchronized {
    _context[A](timestamp, contextid, owner).flatMap { context =>
      action(context.workingAggregate).map { updated =>
        val next = context.update(updated, timestamp)
        _contexts.put(contextid, next)
        next
      }
    }
  }

  def view[A, B](
    timestamp: Instant,
    contextid: String,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous
  )(
    action: AggregateEditContext[A] => Consequence[B]
  ): Consequence[B] = synchronized {
    get[A](timestamp, contextid, owner).flatMap(action)
  }

  def save[A, B](
    timestamp: Instant,
    contextid: String,
    currentbasetoken: Option[String] = None,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous
  )(
    action: A => Consequence[B]
  ): Consequence[B] = synchronized {
    _context[A](timestamp, contextid, owner).flatMap { context =>
      currentbasetoken match {
        case Some(token) if token != context.baseToken =>
          Consequence.operationConflict(
            "aggregate_edit_context_save",
            Seq(Descriptor.Facet.Message(
              s"Aggregate edit context conflict: contextId=${context.contextId}, aggregate=${context.aggregateName}, aggregateId=${context.aggregateId.print}"
            ))
          )
        case _ =>
          action(context.workingAggregate).map { result =>
            _contexts.remove(contextid)
            _leases.remove(_lease_key(context))
            result
          }
      }
    }
  }

  def discard(
    timestamp: Instant,
    contextid: String,
    owner: AggregateEditOwner = AggregateEditOwner.anonymous
  ): Consequence[Boolean] = synchronized {
    expire(timestamp)
    _context[Any](timestamp, contextid, owner) match {
      case Consequence.Success(context) =>
        _contexts.remove(contextid)
        _leases.remove(_lease_key(context))
        Consequence.success(true)
      case Consequence.Failure(conclusion) if Option(_contexts.get(contextid)).isEmpty =>
        Consequence.success(false)
      case failure: Consequence.Failure[AggregateEditContext[Any]] =>
        Consequence.Failure(failure.conclusion)
    }
  }

  def expire(timestamp: Instant): Unit = synchronized {
    _contexts.asScala.foreach {
      case (id, context) if _expired(context, timestamp) =>
        _contexts.remove(id)
        _leases.remove(_lease_key(context))
      case _ =>
    }
  }

  def size(timestamp: Instant): Int = synchronized {
    expire(timestamp)
    _contexts.size()
  }

  private def _context[A](
    timestamp: Instant,
    contextid: String,
    owner: AggregateEditOwner
  ): Consequence[AggregateEditContext[A]] = {
    expire(timestamp)
    Option(_contexts.get(contextid)) match {
      case Some(context) if _same_owner(context, owner) =>
        Consequence.success(context.asInstanceOf[AggregateEditContext[A]])
      case Some(context) =>
        Consequence.operationConflict(
          "aggregate_edit_context_owner",
          Seq(Descriptor.Facet.Message(
            s"Aggregate edit context is owned by another owner: contextId=$contextid, owner=${_owner_label(context)}"
          ))
        )
      case None =>
        Consequence.entityNotFound(s"AggregateEditContext not found: $contextid")
    }
  }

  private def _expired(context: AggregateEditContext[?], timestamp: Instant): Boolean =
    context.updatedAt.plus(ttl).isBefore(timestamp)

  private def _lease_key(context: AggregateEditContext[?]): AggregateEditContextSpace.LeaseKey =
    AggregateEditContextSpace.LeaseKey(context.aggregateName, context.aggregateId.print)

  private def _same_owner(
    current: AggregateEditContext[?],
    requested: AggregateEditOwner
  ): Boolean =
    current.lockScope.ownerKey(current.owner) == current.lockScope.ownerKey(requested)

  private def _owner_label(context: AggregateEditContext[?]): String =
    context.lockScope.ownerKey(context.owner).getOrElse("")
}

object AggregateEditContextSpace {
  private final case class LeaseKey(
    aggregatename: String,
    aggregateid: String
  )
}
