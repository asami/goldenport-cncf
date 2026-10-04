package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * Native retained Job capacity, independent of runtime and Blob budgets.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobStorageResourcePolicy(
  maxstoredjobs: Int = 4096,
  maxcanonicalbytes: Long = 67108864L
) {
  def isValid: Boolean =
    maxstoredjobs > 0 && maxstoredjobs <= 4096 &&
      maxcanonicalbytes > 0L && maxcanonicalbytes <= 67108864L

  def validate: Consequence[Unit] =
    if (isValid) Consequence.unit else DurableJobStorageResourcePolicy.invalid
}

private[job] object DurableJobStorageResourcePolicy {
  val default: DurableJobStorageResourcePolicy = DurableJobStorageResourcePolicy()
  val maximumJobIdBytes = 512
  val maximumRecordBytes = 1048576L
  val pageSize = 32
  val maximumPending = 8
  val maximumAttempts = 8
  val maximumLedgerBytes = 16777216
  val maximumLedgerDepth = 16
  val invalidLabel = "Durable job storage quota invalid"
  val capacityLabel = "Durable job storage capacity unavailable"
  val unavailableLabel = "Durable job storage quota unavailable"
  val confirmationLabel = "Durable job storage quota confirmation unavailable"

  def validId(jobId: String): Boolean =
    jobId != null && jobId.length <= maximumJobIdBytes &&
      !jobId.isBlank && jobId.getBytes(StandardCharsets.UTF_8).length <= maximumJobIdBytes

  def validateId(jobId: String): Consequence[Unit] =
    if (validId(jobId)) Consequence.unit else invalid

  def invalid[A]: Consequence[A] = Consequence.stateInvalid(invalidLabel)
  def capacity[A]: Consequence[A] = Consequence.stateInvalid(capacityLabel)
  def unavailable[A]: Consequence[A] = Consequence.stateInvalid(unavailableLabel)
  def confirmation[A]: Consequence[A] = Consequence.stateInvalid(confirmationLabel)

  def protect[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => unavailable }
}
