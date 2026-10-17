package org.goldenport.cncf.display

import java.time.Instant
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext, SubjectKind}
import scala.util.control.NonFatal

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Restores server authority once per new Operation; projection performs no lookup. */
object PresentationContextRuntime {
  final class Binding private (
    val componentInstanceId: ComponentInstanceId,
    val principalId: PrincipalId,
    val sessionId: String
  )
  object Binding {
    def createC(componentInstanceId: ComponentInstanceId, principalId: PrincipalId,
      sessionId: String): Consequence[Binding] = {
      if (componentInstanceId == null || componentInstanceId.sharedIdentity == null ||
        principalId == null || !_identifier(principalId.value) || !_identifier(sessionId))
        Consequence.argumentInvalid("invalid presentation binding")
      else Consequence.success(new Binding(componentInstanceId, principalId, sessionId))
    }
  }

  final class References private (val displayInstance: String, val correlation: String)
  object References {
    def createC(displayInstance: String, correlation: String): Consequence[References] =
      PresentationContext.Flow.createC(displayInstance, correlation, Instant.EPOCH)
        .map(_ => new References(displayInstance, correlation))
  }

  final class Resolution private (
    val binding: Binding,
    val variant: Option[String],
    val flow: Option[PresentationContext.Flow]
  )
  object Resolution {
    def createC(binding: Binding, variant: Option[String] = None,
      flow: Option[PresentationContext.Flow] = None): Consequence[Resolution] = {
      if (binding == null) Consequence.argumentInvalid("invalid presentation resolution")
      else for {
        checkedbinding <- Binding.createC(binding.componentInstanceId, binding.principalId, binding.sessionId)
        context <- PresentationContext.createC(variant = variant, flow = flow)
        _ <- if (context.variant.nonEmpty && context.flow.isEmpty)
          Consequence.argumentInvalid[Unit]("presentation variant requires server flow") else Consequence.unit
        _ <- context.flow match {
          case Some(value) => PresentationContext.Flow.createC(value.displayInstance, value.correlation, value.expiresAt).map(_ => ())
          case None => Consequence.unit
        }
      } yield new Resolution(checkedbinding, context.variant, context.flow)
    }
  }

  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _version_pattern = "([0-9]+)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?".r

  def restoreC(componentInstanceId: ComponentInstanceId, candidate: Option[String],
    ctx: ExecutionContext): Consequence[ExecutionContext] = {
    if (ctx == null || candidate == null || candidate.contains(null))
      Consequence.argumentInvalid("invalid presentation input")
    else {
      val cleared = ExecutionContext._with_presentation_context(ctx, None)
      candidate match {
        case None => Consequence.success(cleared)
        case Some(raw) => PresentationContextCodec.decodeC(raw).flatMap { context =>
          // The strict codec has already bounded and validated this entire tree.
          if (_unsupported_flow(raw)) Consequence.success(cleared)
          else {
            val now = ctx.clock.instant()
            _trusted_binding(componentInstanceId, cleared, now) match {
              case None => Consequence.success(cleared)
              case Some(binding) =>
                val references = context.flow.map(value =>
                  References.createC(value.displayInstance, value.correlation).toOption.get)
                _resolve(cleared, binding, references, now)
            }
          }
        }
      }
    }
  }

  def project(ctx: ExecutionContext): Option[PresentationContext] = {
    if (ctx == null) None
    else {
      val now = ctx.clock.instant()
      for {
        resolution <- Option(ctx.presentationContext).flatten
        component <- _component(ctx.scope)
        binding <- _trusted_binding(component.instanceId, ctx, now)
        if _same_binding(binding, resolution.binding)
        checked <- Resolution.createC(resolution.binding, resolution.variant, resolution.flow).toOption
        if checked.flow.forall(_.expiresAt.isAfter(now))
        locale <- Option(ctx.core.locale).map(_.toLanguageTag)
        projected <- PresentationContext.createC(Some(locale), checked.variant, checked.flow).toOption
      } yield projected
    }
  }

  private def _resolve(ctx: ExecutionContext, binding: Binding, references: Option[References],
    now: Instant): Consequence[ExecutionContext] = {
    given ExecutionContext = ctx
    val result = try {
      ctx.scope.presentationContextResolver.resolve(PresentationContextResolver.Request(binding, references, now))
    } catch { case NonFatal(_) => _provider_failure }
    if (result == null) _provider_invalid
    else result match {
      case _: Consequence.Failure[?] => _provider_failure
      case Consequence.Success(PresentationContextResolver.Result.Unavailable) => Consequence.success(ctx)
      case Consequence.Success(PresentationContextResolver.Result.Supported(resolution)) =>
        _checked_resolution(resolution).flatMap { checked =>
          if (!_same_binding(binding, checked.binding) || !references.forall { requested =>
            checked.flow.exists(value => value.displayInstance == requested.displayInstance && value.correlation == requested.correlation)
          }) _provider_invalid
          else if (checked.flow.exists(value => !value.expiresAt.isAfter(now))) Consequence.success(ctx)
          else Consequence.success(ExecutionContext._with_presentation_context(ctx, Some(checked)))
        }
      case _ => _provider_invalid
    }
  }

  private def _checked_resolution(resolution: Resolution): Consequence[Resolution] = {
    val checked = try {
      if (resolution == null) None
      else Resolution.createC(resolution.binding, resolution.variant, resolution.flow).toOption
    } catch { case NonFatal(_) => None }
    checked.map(Consequence.success).getOrElse(_provider_invalid)
  }

  private def _trusted_binding(component: ComponentInstanceId, ctx: ExecutionContext,
    now: Instant): Option[Binding] = {
    for {
      security <- Option(ctx.security)
      if Set(SubjectKind.User, SubjectKind.Service, SubjectKind.Subsystem).contains(security.subjectKind)
      principal <- Option(security.principal)
      session <- Option(security.session).flatten
      expiry <- Option(session.expiresAt)
      if !expiry.contains(null) && expiry.forall(_.isAfter(now))
      sessionid <- Option(session.sessionId).flatten
      binding <- Binding.createC(component, principal.id, sessionid).toOption
    } yield binding
  }

  private def _component(scope: ScopeContext): Option[Component] = {
    Option(scope).flatMap {
      case value: Component.Context => Option(value.component)
      case value => value.parent.flatMap(_component)
    }
  }

  private def _same_binding(left: Binding, right: Binding): Boolean =
    left != null && right != null && left.componentInstanceId == right.componentInstanceId &&
      left.principalId == right.principalId && left.sessionId == right.sessionId

  private def _identifier(text: String): Boolean =
    text != null && text.nonEmpty && text == text.trim && !text.exists(Character.isISOControl) &&
      !Character.isWhitespace(text.codePointAt(0)) && !Character.isSpaceChar(text.codePointAt(0)) &&
      !Character.isWhitespace(text.codePointBefore(text.length)) && !Character.isSpaceChar(text.codePointBefore(text.length))

  private def _unsupported_flow(raw: String): Boolean = {
    _parser.parse(raw).toOption.toVector.flatMap(_.hcursor.downField("families").focus.toVector)
      .flatMap(_.asArray.toVector.flatten).exists { envelope =>
        envelope.hcursor.get[String]("name").toOption.contains("cncf.display-flow") &&
          !envelope.hcursor.downField("version").focus.flatMap(_.asNumber).exists(value => _version_one(value.toString))
      }
  }

  private def _version_one(text: String): Boolean = text match {
    case _version_pattern(whole, fraction, exponent) =>
      val significant = (whole + Option(fraction).getOrElse("")).dropWhile(_ == '0')
      val zeros = significant.reverseIterator.takeWhile(_ == '0').length
      val scale = BigInt(Option(fraction).fold(0)(_.length)) - Option(exponent).map(BigInt.apply).getOrElse(BigInt(0))
      significant.dropRight(zeros) == "1" && scale == zeros
    case _ => false
  }

  private def _provider_failure[A]: Consequence[A] =
    Consequence.serviceUnavailable("presentation provider unavailable")
  private def _provider_invalid[A]: Consequence[A] =
    Consequence.stateInvalid("invalid presentation provider result")
}
