package org.goldenport.cncf.finding

import org.goldenport.Consequence

/** Representation-independent durable finding decisions and reinspection.
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object FindingDispositionV1 {
  val ABI_VERSION: Int = 1

  enum Kind(val name: String) {
    case Fix extends Kind("fix")
    case Accept extends Kind("accept")
    case Defer extends Kind("defer")
    case Configure extends Kind("configure")
  }

  enum Status {
    case Unresolved, Accepted, Deferred, Corrected, Configured
  }

  final case class Target(scope: String, identity: String)
  final case class Key(rule: String, finding: String, target: Target)
  final case class Finding(key: Key, message: String)
  final case class Declaration(
    key: Key,
    kind: Kind,
    reason: String,
    provenance: String,
    abiVersion: Int = ABI_VERSION
  )

  /** A completed reinspection explicitly names the rule/target coverage.
    * The adapter, rather than an annotation, owns these observations.
    */
  final case class Coverage(rule: String, target: Target)
  final case class Inspection(revision: String, findings: Vector[Finding], coverage: Set[Coverage])
  final case class ChangeEvidence(key: Key, before: String, after: String, reference: String)
  final case class RuleAlias(previous: String, current: String, deprecatedSince: String)
  final case class Result(finding: Finding, status: Status, decision: Option[Declaration]) {
    def actionable: Boolean = status == Status.Unresolved
  }
  final case class Report(results: Vector[Result]) {
    def actionableBacklog: Vector[Finding] = results.filter(_.actionable).map(_.finding)
    def deferredBacklog: Vector[Finding] = results.filter(_.status == Status.Deferred).map(_.finding)
  }

  /** An open rule ID is data. Aliases explicitly retain renamed rule identity. */
  def canonicalRuleC(rule: String, aliases: Vector[RuleAlias] = Vector.empty): Consequence[String] =
    _canonical_rule(rule, aliases).fold(Consequence.argumentInvalid(_), Consequence.success)

  def validateC(declaration: Declaration): Consequence[Declaration] =
    _declaration(declaration).fold(Consequence.argumentInvalid(_), Consequence.success)

  /** Pure interpretation; this operation neither admits decisions nor edits sources.
    * A missing old finding remains actionable until explicit corrective evidence
    * and completed coverage establish the correction/configuration outcome.
    */
  def interpretC(
    before: Inspection,
    after: Inspection,
    decisions: Vector[Declaration],
    changes: Vector[ChangeEvidence] = Vector.empty,
    aliases: Vector[RuleAlias] = Vector.empty
  ): Consequence[Report] = {
    val result = for {
      _ <- _alias_map(aliases)
      previous <- _inspection(before, aliases)
      current <- _inspection(after, aliases)
      declarations <- _sequence(Option(decisions).toRight("decisions must be present").map(_.map { value =>
        _declaration(value).flatMap(d => _key(d.key, aliases).map(k => d.copy(key = k)))
      }))
      _ <- _unique(declarations.map(_.key), "conflicting or duplicate dispositions")
      evidence <- _sequence(Option(changes).toRight("changes must be present").map(_.map { value =>
        if (value == null) Left("change evidence must be present")
        else for {
          key <- _key(value.key, aliases)
          _ <- _text(value.before, "change before revision")
          _ <- _text(value.after, "change after revision")
          _ <- _text(value.reference, "change evidence reference")
          _ <- Either.cond(value.before != value.after, (), "correction requires changed source/configuration revision")
        } yield value.copy(key = key)
      }))
      _ <- _unique(evidence.map(_.key), "duplicate change evidence")
    } yield {
      val decisionmap = declarations.map(d => d.key -> d).toMap
      val evidencemap = evidence.map(e => e.key -> e).toMap
      val currentmap = current.findings.map(f => f.key -> f).toMap
      val all = previous.findings.map(f => f.key -> f).toMap ++ currentmap
      val results = all.values.toVector.sortBy(f => (f.key.target.scope, f.key.target.identity, f.key.rule, f.key.finding)).map { finding =>
        val key = finding.key
        val decision = decisionmap.get(key)
        val present = currentmap.contains(key)
        val covered = current.coverage.contains(Coverage(key.rule, key.target))
        val changed = evidencemap.get(key).exists(e => e.before == previous.revision && e.after == current.revision)
        val status = decision match {
          case Some(d) if present && d.kind == Kind.Accept => Status.Accepted
          case Some(d) if present && d.kind == Kind.Defer => Status.Deferred
          case Some(d) if !present && covered && changed && d.kind == Kind.Fix => Status.Corrected
          case Some(d) if !present && covered && changed && d.kind == Kind.Configure => Status.Configured
          case _ => Status.Unresolved
        }
        Result(finding, status, decision)
      }
      Report(results)
    }
    result.fold(Consequence.argumentInvalid(_), Consequence.success)
  }

  private def _text(value: String, field: String): Either[String, String] =
    Either.cond(value != null && value.nonEmpty && value == value.trim && !value.exists(_.isControl),
      value, s"$field must be nonempty trimmed text without controls")

  private def _target(value: Target): Either[String, Target] =
    if (value == null) Left("target must be present")
    else for {
      _ <- _text(value.scope, "target scope")
      _ <- _text(value.identity, "target identity")
    } yield value

  private def _key(value: Key, aliases: Vector[RuleAlias]): Either[String, Key] =
    if (value == null) Left("finding key must be present")
    else for {
      rule <- _canonical_rule(value.rule, aliases)
      _ <- _text(value.finding, "finding identity")
      _ <- _target(value.target)
    } yield value.copy(rule = rule)

  private def _declaration(value: Declaration): Either[String, Declaration] =
    if (value == null) Left("disposition must be present")
    else for {
      _ <- Either.cond(value.abiVersion == ABI_VERSION, (), "unsupported Finding Disposition ABI version")
      _ <- _key(value.key, Vector.empty)
      _ <- Either.cond(value.kind != null, (), "disposition kind must be present")
      _ <- _text(value.reason, "disposition reason")
      _ <- _text(value.provenance, "disposition provenance")
    } yield value

  private def _canonical_rule(rule: String, aliases: Vector[RuleAlias]): Either[String, String] = for {
    _ <- _text(rule, "rule identity")
    mapping <- _alias_map(aliases)
    result <- _resolve_rule(rule, mapping, Set.empty)
  } yield result

  private def _alias_map(aliases: Vector[RuleAlias]): Either[String, Map[String, String]] = for {
    entries <- Option(aliases).toRight("rule compatibility declarations must be present")
    valid <- _sequence(Right(entries.map { entry =>
      if (entry == null) Left("rule alias must be present")
      else for {
        _ <- _text(entry.previous, "previous rule identity")
        _ <- _text(entry.current, "current rule identity")
        _ <- _text(entry.deprecatedSince, "rule deprecation provenance")
      } yield entry
    }))
    _ <- _unique(valid.map(_.previous), "ambiguous rule alias")
    mapping = valid.map(a => a.previous -> a.current).toMap
    _ <- _sequence(Right(valid.map(a => _resolve_rule(a.previous, mapping, Set.empty))))
  } yield mapping

  @scala.annotation.tailrec
  private def _resolve_rule(current: String, mapping: Map[String, String], visited: Set[String]): Either[String, String] =
    if (visited.contains(current)) Left("cyclic rule compatibility declaration")
    else mapping.get(current) match {
      case None => Right(current)
      case Some(next) => _resolve_rule(next, mapping, visited + current)
    }

  private def _inspection(value: Inspection, aliases: Vector[RuleAlias]): Either[String, Inspection] =
    if (value == null) Left("inspection must be present")
    else for {
      _ <- _text(value.revision, "inspection revision")
      findings <- _sequence(Option(value.findings).toRight("findings must be present").map(_.map { finding =>
        if (finding == null) Left("finding must be present")
        else for {
          key <- _key(finding.key, aliases)
          _ <- _text(finding.message, "finding message")
        } yield finding.copy(key = key)
      }))
      _ <- _unique(findings.map(_.key), "duplicate finding identity")
      coverage <- _sequence(Option(value.coverage).toRight("inspection coverage must be present").map(_.toVector.map { entry =>
        if (entry == null) Left("coverage entry must be present")
        else for {
          rule <- _canonical_rule(entry.rule, aliases)
          _ <- _target(entry.target)
        } yield entry.copy(rule = rule)
      }))
      _ <- Either.cond(findings.forall(f => coverage.contains(Coverage(f.key.rule, f.key.target))), (),
        "every observed finding requires its rule/target inspection coverage")
    } yield value.copy(findings = findings, coverage = coverage.toSet)

  private def _unique[A](values: Vector[A], detail: String): Either[String, Unit] =
    Either.cond(values.distinct.size == values.size, (), detail)

  private def _sequence[A](values: Either[String, Vector[Either[String, A]]]): Either[String, Vector[A]] =
    values.flatMap(_.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (prior, next) =>
      for { previous <- prior; value <- next } yield previous :+ value
    })
}
