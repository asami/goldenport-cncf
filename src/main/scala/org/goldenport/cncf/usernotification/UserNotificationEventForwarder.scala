package org.goldenport.cncf.usernotification

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, EventDispatchHandler, EventId, EventLane, EventRecord, EventSubscription, ReceptionDomainEvent}
import org.goldenport.cncf.job.JobExperienceScope
import org.goldenport.cncf.subsystem.{GenericSubsystemUserNotificationEventForwardingBinding, Subsystem}

/*
 * Event-to-user-notification bridge.
 *
 * Jobs and other producers only emit events. User notifications are produced
 * here when subsystem notification forwarding rules match those events.
 *
 * @since   May.  7, 2026
 *  version Jul. 16, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object UserNotificationEventForwarder:
  private val _job_terminal_events = Set(
    "job.succeeded",
    "job.failed",
    "job.cancelled",
    "job.recovery-required",
    "job.recoveryRequired"
  )

  def subscription(subsystem: Subsystem): EventSubscription =
    EventSubscription(
      name = "cncf.user-notification.event-forwarding",
      selector = Some {
        case event: ReceptionDomainEvent => _rules(subsystem).exists(_.matches(event))
        case _ => false
      },
      priority = 1000,
      handler = new EventDispatchHandler {
        def dispatch(event: DomainEvent): Consequence[Unit] = {
          _base_execution_context(subsystem).foreach(_dispatch(subsystem, event, _))
          Consequence.unit
        }

        override def dispatchAuthorized(
          event: DomainEvent
        )(using ctx: ExecutionContext): Consequence[Unit] = {
          _dispatch(subsystem, event, ctx)
          Consequence.unit
        }
      }
    )

  private def _dispatch(
    subsystem: Subsystem,
    event: DomainEvent,
    ctx: ExecutionContext
  ): Unit =
    event match {
      case reception: ReceptionDomainEvent =>
        _forward_one(subsystem, reception, ctx)
      case _ =>
        ()
    }

  private def _forward_one(
    subsystem: Subsystem,
    event: ReceptionDomainEvent,
    ctx: ExecutionContext
  ): Unit =
    _rules(subsystem).filter(_.matches(event)).foreach { rule =>
      _request(event, rule) match {
        case Some(request) =>
          _provider(ctx, rule.provider) match {
            case Some(provider) =>
              try {
                provider.notify(request)(using ctx) match {
                  case Consequence.Success(result) if result.accepted =>
                    _append_forwarding_diagnostic(
                      subsystem,
                      "user-notification.forwarding.sent",
                      event,
                      request,
                      result.notificationId.orElse(result.providerNotificationId).getOrElse("")
                    )(using ctx)
                  case Consequence.Success(_) =>
                    _append_forwarding_diagnostic(
                      subsystem,
                      "user-notification.forwarding.failed",
                      event,
                      request,
                      "The notification provider refused delivery."
                    )(using ctx)
                  case Consequence.Failure(conclusion) =>
                    val _ = conclusion
                    _append_forwarding_diagnostic(
                      subsystem,
                      "user-notification.forwarding.failed",
                      event,
                      request,
                      "The notification provider did not accept delivery."
                    )(using ctx)
                }
              } catch {
                case NonFatal(_) =>
                  _append_forwarding_diagnostic(
                    subsystem,
                    "user-notification.forwarding.failed",
                    event,
                    request,
                    "The notification provider failed while accepting delivery."
                  )(using ctx)
              }
            case None =>
                _append_forwarding_diagnostic(
                  subsystem,
                  "user-notification.forwarding.failed",
                  event,
                  request,
                  "No user-notification provider is configured for the current subsystem."
                )(using ctx)
          }
        case None =>
          _append_forwarding_diagnostic(
            subsystem,
            "user-notification.forwarding.skipped",
            event,
            None,
            "missing recipient or app visibility metadata"
          )(using ctx)
      }
    }

  private final case class ForwardingRule(
    event: String,
    provider: Option[String],
    channel: Option[String],
    enabled: Boolean,
    appVisibleOnly: Boolean,
    asyncOnly: Boolean,
    notificationType: String,
    priority: Option[String],
    dedupeKey: Option[String]
  ) {
    def matches(event: ReceptionDomainEvent): Boolean =
      enabled &&
        this.event == event.name &&
        (!appVisibleOnly || _string(event, "web.app").exists(_.trim.nonEmpty)) &&
        (!asyncOnly || _is_async_job_event(event))

    def trigger(event: ReceptionDomainEvent): String =
      _trigger(event.name)
  }

  private object ForwardingRule:
    def from(binding: GenericSubsystemUserNotificationEventForwardingBinding): ForwardingRule =
      ForwardingRule(
        event = binding.event,
        provider = binding.provider,
        channel = binding.channel,
        enabled = binding.enabled.getOrElse(true),
        appVisibleOnly = binding.appVisibleOnly.getOrElse(true),
        asyncOnly = binding.asyncOnly.getOrElse(false),
        notificationType = binding.notificationType.getOrElse("cncf.job"),
        priority = binding.priority,
        dedupeKey = binding.dedupeKey
      )

    def default(event: String): ForwardingRule =
      ForwardingRule(
        event = event,
        provider = None,
        channel = None,
        enabled = true,
        appVisibleOnly = true,
        asyncOnly = true,
        notificationType = "cncf.job",
        priority = None,
        dedupeKey = None
      )

  private def _rules(subsystem: Subsystem): Vector[ForwardingRule] = {
    val configured = subsystem.descriptor.toVector
      .flatMap(_.runtime.toVector)
      .flatMap(_.userNotification.toVector)
      .flatMap(_.eventForwarding)
      .map(ForwardingRule.from)
    if (configured.nonEmpty)
      configured
    else if (subsystem.resolvedSecurityWiring.userNotification.enabledProviders.nonEmpty)
      _job_terminal_events.toVector.sorted.map(ForwardingRule.default)
    else
      Vector.empty
  }

  private def _base_execution_context(subsystem: Subsystem): Option[ExecutionContext] =
    subsystem.components.headOption.map(_.logic.executionContext())

  private def _provider(
    base: ExecutionContext,
    name: Option[String]
  ): Option[UserNotificationProvider] = {
    val providers = UserNotificationProviderRuntime.providers(base)
    name match {
      case Some(n) => providers.find(p => _normalize(p.name) == _normalize(n))
      case None => providers.headOption
    }
  }

  private def _request(
    event: ReceptionDomainEvent,
    rule: ForwardingRule
  ): Option[UserNotificationRequest] = {
    val recipient = _string(event, "submitter-principal-id").filter(_.trim.nonEmpty)
    val jobid = _string(event, "job-id").filter(_.trim.nonEmpty)
    recipient.flatMap { user =>
      jobid.map { id =>
        val trigger = rule.trigger(event)
        val app = _string(event, "web.app").filter(_.trim.nonEmpty)
        val service = _string(event, "web.service").filter(_.trim.nonEmpty)
        val operation = _string(event, "web.operation").filter(_.trim.nonEmpty)
        val target = Vector(app, service, operation).flatten.map(_display(_, 128)).filter(_.nonEmpty).mkString(".")
        val status = _display(if (trigger == "recovery-required") "recoveryRequired" else trigger, 64)
        val titletarget = if (target.nonEmpty) s": $target" else ""
        val displayid = _display(id, 256)
        val actionurl = _action_url(app, id)
        UserNotificationRequest(
          recipientUserId = user,
          notificationType = rule.notificationType,
          channel = rule.channel.getOrElse("in-app"),
          title = _display(s"Job $status$titletarget", 256),
          body = _display(s"Job $displayid is $status. See the Job page for details.", 4096),
          priority = rule.priority.orElse(if (trigger == "failed" || trigger == "recovery-required") Some("high") else None),
          status = "Queued",
          dedupeKey = Some(_dedupe_key(rule, id, trigger)),
          actionUrl = actionurl,
          metadata = _metadata(
            "jobId" -> Some(id),
            "trigger" -> Some(trigger),
            "jobStatus" -> Some(status),
            "sourceEventName" -> Some(event.name),
            "app" -> app,
            "service" -> service,
            "operation" -> operation,
            "recoveryRequired" -> _string(event, "recovery-required")
          ),
          correlationId = _string(event, "correlation-id")
        )
      }
    }
  }

  private def _dedupe_key(rule: ForwardingRule, jobid: String, trigger: String): String =
    rule.dedupeKey
      .map(_.replace("${jobId}", jobid).replace("${trigger}", trigger))
      .getOrElse(s"cncf.job:$jobid:$trigger")

  private def _trigger(name: String): String =
    name.stripPrefix("job.").trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")

  private def _string(event: ReceptionDomainEvent, key: String): Option[String] =
    event.payload.get(key).map(_.toString).orElse(event.attributes.get(key))

  private def _normalize(s: String): String =
    Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")

  private def _path_segment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def _display(value: String, limit: Int): String =
    Option(value).getOrElse("").map(character => if (Character.isISOControl(character)) ' ' else character).take(limit)

  private def _metadata(entries: (String, Option[String])*): Map[String, String] =
    entries.collect {
      case (key, Some(value)) if _safe_reference(value) => key -> value
    }.toMap

  private def _safe_reference(value: String): Boolean =
    Option(value).exists(entry => entry.trim.nonEmpty && entry.length <= 256 && !entry.exists(Character.isISOControl))

  private def _safe_link_segment(value: String): Boolean =
    _safe_reference(value) && value != "." && value != ".." && !value.contains('/') && !value.contains('\\')

  private def _action_url(application: Option[String], id: String): Option[String] =
    for {
      original <- application
      if _safe_link_segment(original) && _safe_link_segment(id)
      normalized <- JobExperienceScope.normalizedApplication(original).toOption
      if normalized == original
      url = s"/web/${_path_segment(normalized)}/jobs/${_path_segment(id)}"
      if UserNotificationLinkPolicy.isSafe(url)
    } yield url

  private def _is_async_job_event(event: ReceptionDomainEvent): Boolean =
    _string(event, "job-run-mode").exists(v => _normalize(v) == "async")

  private def _append_forwarding_diagnostic(
    subsystem: Subsystem,
    name: String,
    source: ReceptionDomainEvent,
    request: UserNotificationRequest,
    note: String
  )(using ctx: ExecutionContext): Unit =
    _append_forwarding_diagnostic(subsystem, name, source, Some(request), note)(using ctx)

  private def _append_forwarding_diagnostic(
    subsystem: Subsystem,
    name: String,
    source: ReceptionDomainEvent,
    request: Option[UserNotificationRequest],
    note: String
  )(using ctx: ExecutionContext): Unit = {
    val record = diagnosticRecord(name, source, request, note)
    val _ = subsystem.eventStore.append(Vector(record))
  }

  private[usernotification] def diagnosticRecord(
    name: String,
    source: ReceptionDomainEvent,
    request: Option[UserNotificationRequest],
    note: String
  )(using ctx: ExecutionContext): EventRecord = {
    val createdat = ctx.clock.instant()
    val jobid = _string(source, "job-id").getOrElse("")
    val dedupekey = request.flatMap(_.dedupeKey).getOrElse("")
    val payload = Map(
      "source-event-name" -> source.name,
      "job-id" -> jobid,
      "note" -> note
    ) ++ request.toVector.flatMap { r =>
      Vector(
        "recipient-user-id" -> r.recipientUserId,
        "dedupe-key" -> r.dedupeKey.getOrElse("")
      )
    }.toMap
    val purpose = Vector(name, source.name, jobid, dedupekey).mkString(".")
    EventRecord(
      id = EventId.create(s"user-notification.forwarding.$purpose", createdat),
      name = name,
      kind = name,
      payload = payload,
      attributes = Map("cncf.userNotification.forwarding" -> "true"),
      createdAt = createdat,
      persistent = true,
      status = EventRecord.Status.Stored,
      lane = EventLane.NonTransactional
    )
  }
