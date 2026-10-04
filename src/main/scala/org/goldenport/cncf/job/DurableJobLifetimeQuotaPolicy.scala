package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * Native lifetime policy and closed failure vocabulary.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobLifetimeQuotaPolicy(
  lifetime: JobLifetimePolicy = JobLifetimePolicy.default,
  maxledgerjobs: Int = 4096,
  maxledgerbytes: Long = 67108864L
) {
  def isValid: Boolean = lifetime != null && lifetime.validate.isSuccess &&
    maxledgerjobs > 0 && maxledgerjobs <= 4096 &&
    maxledgerbytes > 0L && maxledgerbytes <= DurableJobLifetimeQuotaPolicy.maximumLedgerBytes

  def validate: Consequence[Unit] =
    if (isValid) Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
}

private[job] object DurableJobLifetimeQuotaPolicy {
  val default: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy()
  val maximumIdBytes = 512
  val maximumLedgerBytes = 67108864
  val maximumLedgerDepth = 16
  val maximumAttempts = 8
  val entryOverheadBytes = 4096L
  val bytesPerChargedTask = 4096L
  val invalidLabel = "Durable job lifetime quota invalid"
  val unavailableLabel = "Durable job lifetime quota unavailable"
  val confirmationLabel = "Durable job lifetime quota confirmation unavailable"

  def validId(value: String): Boolean = value != null && value.length <= maximumIdBytes &&
    !value.isBlank && value.getBytes(StandardCharsets.UTF_8).length <= maximumIdBytes

  def hex(value: String, size: Int): Boolean = value != null && value.length == size &&
    value.forall(character => character >= '0' && character <= '9' || character >= 'a' && character <= 'f')

  def invalid[A]: Consequence[A] = Consequence.stateInvalid(invalidLabel)
  def unavailable[A]: Consequence[A] = Consequence.stateInvalid(unavailableLabel)
  def confirmation[A]: Consequence[A] = Consequence.stateInvalid(confirmationLabel)

  def protect[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => unavailable }

  def pure[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => invalid }
}
