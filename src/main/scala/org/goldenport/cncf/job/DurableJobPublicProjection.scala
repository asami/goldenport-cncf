package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal

import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DurablePublicValue(
  storage: String,
  inlineMetadata: Option[DurableInlineMetadata],
  externalReference: Option[DurableExternalReference]
)

final case class DurablePublicInput(name: String, value: DurablePublicValue)

final case class DurablePublicResult(
  outcome: String,
  value: Option[DurablePublicValue],
  failure: Option[DurableFailureSummary]
)

final case class DurableJobPublicProjection(
  identity: DurableJobIdentity,
  lifecycle: DurableJobLifecycle,
  authorization: DurableJobAuthorization,
  tasks: Vector[DurableTaskDescriptor],
  inputs: Vector[DurablePublicInput],
  result: DurablePublicResult,
  timeline: Vector[DurableTimelineEvent],
  diagnostics: Vector[DurableDiagnosticSummary],
  calltreeReference: Option[DurableExternalReference],
  definitionSnapshot: DurableDefinitionSnapshot,
  retention: DurableRetentionState
)

/*
 * Closed startup admission fact.  It intentionally exposes neither a decode
 * failure nor an authorization detail across the recovery boundary.
 */
private[job] enum DurableJobRecordStartupAdmission {
  case Admitted(record: DurableJobRecord)
  case Refused
  case Corrupt
}
