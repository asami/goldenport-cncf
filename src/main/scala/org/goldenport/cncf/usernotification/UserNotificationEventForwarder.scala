package org.goldenport.cncf.usernotification

import java.util.concurrent.ConcurrentHashMap
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, EventDispatchHandler, EventId, EventLane, EventRecord, EventSubscription, ReceptionDomainEvent}
import org.goldenport.cncf.subsystem.{GenericSubsystemUserNotificationEventForwardingBinding, Subsystem}

/*
 * Event-to-user-notification bridge.
 *
 * Jobs and other producers only emit events. User notifications are produced
 * here when subsystem notification forwarding rules match those events.
 *
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object UserNotificationEventForwarder:
  private val _sent = ConcurrentHashMap.newKeySet[String]()
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
        def dispatch(event: org.goldenport.cncf.event.DomainEvent): Consequence[Unit] = {
          event match {
            case reception: ReceptionDomainEvent =>
              _forward_one(subsystem, reception)
            case _ =>
              ()
          }
          Consequence.unit
        }
      }
    )

  private def _forward_one(
    subsystem: Subsystem,
    event: ReceptionDomainEvent
  ): Unit =
    _rules(subsystem).filter(_.matches(event)).foreach { rule =>
      _base_execution_context(subsystem).foreach { base =>
        _request(event, rule) match {
          case Some(request) =>
            val key = request.dedupeKey.getOrElse(s"cncf.user-notification:${event.name}:${System.identityHashCode(event)}")
            if (_sent.add(key)) {
              _provider(base, rule.provider) match {
                case Some(provider) =>
                  provider.notify(request)(using base) match {
                    case Consequence.Success(result) =>
                      _append_forwarding_diagnostic(
                        subsystem,
                        "user-notification.forwarding.sent",
                        event,
                        request,
                        result.notificationId.orElse(result.providerNotificationId).getOrElse("")
                      )
                    case Consequence.Failure(conclusion) =>
                      _append_forwarding_diagnostic(
                        subsystem,
                        "user-notification.forwarding.failed",
                        event,
                        request,
                        conclusion.show
                      )
                  }
                case None =>
                  _append_forwarding_diagnostic(
                    subsystem,
                    "user-notification.forwarding.failed",
                    event,
                    request,
                    "No user-notification provider is configured for the current subsystem."
                  )
              }
            }
          case None =>
            _append_forwarding_diagnostic(
              subsystem,
              "user-notification.forwarding.skipped",
              event,
              None,
              "missing recipient or app visibility metadata"
            )
        }
      }
    }

  private final case class _Rule(
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

  private object _Rule:
    def from(binding: GenericSubsystemUserNotificationEventForwardingBinding): _Rule =
      _Rule(
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

    def default(event: String): _Rule =
      _Rule(
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

  private def _rules(subsystem: Subsystem): Vector[_Rule] = {
    val configured = subsystem.descriptor.toVector
      .flatMap(_.runtime.toVector)
      .flatMap(_.userNotification.toVector)
      .flatMap(_.eventForwarding)
      .map(_Rule.from)
    if (configured.nonEmpty)
      configured
    else if (subsystem.resolvedSecurityWiring.userNotification.enabledProviders.nonEmpty)
      _job_terminal_events.toVector.sorted.map(_Rule.default)
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
    rule: _Rule
  ): Option[UserNotificationRequest] = {
    val recipient = _string(event, "submitter-principal-id").filter(_.trim.nonEmpty)
    val jobid = _string(event, "job-id").filter(_.trim.nonEmpty)
    recipient.flatMap { user =>
      jobid.map { id =>
        val trigger = rule.trigger(event)
        val app = _string(event, "web.app").filter(_.trim.nonEmpty)
        val service = _string(event, "web.service").filter(_.trim.nonEmpty)
        val operation = _string(event, "web.operation").filter(_.trim.nonEmpty)
        val target = Vector(app, service, operation).flatten.mkString(".")
        val status = if (trigger == "recovery-required") "recoveryRequired" else _string(event, "status").getOrElse(trigger)
        val titleTarget = if (target.nonEmpty) s": $target" else ""
        val message = _string(event, "message").orElse(_string(event, "result-summary")).getOrElse(status)
        UserNotificationRequest(
          recipientUserId = user,
          notificationType = rule.notificationType,
          channel = rule.channel.getOrElse("in-app"),
          title = s"Job $status$titleTarget",
          body = s"Job $id is $status. $message".trim,
          priority = rule.priority.orElse(if (trigger == "failed" || trigger == "recovery-required") Some("high") else None),
          status = "Queued",
          dedupeKey = Some(_dedupe_key(rule, id, trigger)),
          actionUrl = app.map(a => s"/web/$a/jobs/$id"),
          metadata = Map(
            "jobId" -> id,
            "trigger" -> trigger,
            "jobStatus" -> status,
            "sourceEventName" -> event.name
          ) ++ app.map("app" -> _) ++
            service.map("service" -> _) ++
            operation.map("operation" -> _) ++
            _string(event, "recovery-required").map("recoveryRequired" -> _) ++
            _string(event, "message").map("message" -> _),
          correlationId = _string(event, "correlation-id")
        )
      }
    }
  }

  private def _dedupe_key(rule: _Rule, jobid: String, trigger: String): String =
    rule.dedupeKey
      .map(_.replace("${jobId}", jobid).replace("${trigger}", trigger))
      .getOrElse(s"cncf.job:$jobid:$trigger")

  private def _trigger(name: String): String =
    name.stripPrefix("job.").trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")

  private def _string(event: ReceptionDomainEvent, key: String): Option[String] =
    event.payload.get(key).map(_.toString).orElse(event.attributes.get(key))

  private def _normalize(s: String): String =
    Option(s).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")

  private def _is_async_job_event(event: ReceptionDomainEvent): Boolean =
    _string(event, "job-run-mode").exists(v => _normalize(v) == "async")

  private def _append_forwarding_diagnostic(
    subsystem: Subsystem,
    name: String,
    source: ReceptionDomainEvent,
    request: UserNotificationRequest,
    note: String
  ): Unit =
    _append_forwarding_diagnostic(subsystem, name, source, Some(request), note)

  private def _append_forwarding_diagnostic(
    subsystem: Subsystem,
    name: String,
    source: ReceptionDomainEvent,
    request: Option[UserNotificationRequest],
    note: String
  ): Unit = {
    val payload = Map(
      "source-event-name" -> source.name,
      "job-id" -> _string(source, "job-id").getOrElse(""),
      "note" -> note
    ) ++ request.toVector.flatMap { r =>
      Vector(
        "recipient-user-id" -> r.recipientUserId,
        "dedupe-key" -> r.dedupeKey.getOrElse("")
      )
    }.toMap
    val _ = subsystem.eventStore.append(
      Vector(
        EventRecord(
          id = EventId.generate(),
          name = name,
          kind = name,
          payload = payload,
          attributes = Map("cncf.userNotification.forwarding" -> "true"),
          createdAt = java.time.Instant.now(),
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = EventLane.NonTransactional
        )
      )
    )
  }
