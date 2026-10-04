package org.goldenport.cncf.job

import java.time.Duration
import org.goldenport.Consequence

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class JobResourcePolicy(
  maxretainedjobs: Int = 4096,
  maxpendingwork: Int = 1024,
  maxexecutingwork: Int = 64,
  maxtaskspersubmission: Int = 1024,
  maxretryattempts: Int = 32
) {
  def validate: Consequence[Unit] =
    if (
      maxretainedjobs < 1 || maxretainedjobs > 4096 ||
        maxpendingwork < 1 || maxpendingwork > 1024 ||
        maxexecutingwork < 1 || maxexecutingwork > 64 ||
        maxtaskspersubmission < 1 || maxtaskspersubmission > 1024 ||
        maxretryattempts < 1 || maxretryattempts > 32
    ) JobResourcePolicy.refused
    else Consequence.unit

  def validateEngine(
    scheduler: InMemoryJobEngine.SchedulerConfig,
    retry: InMemoryJobEngine.RetrySchedule
  ): Consequence[Unit] =
    validate.flatMap { _ =>
      if (
        scheduler == null || retry == null ||
          scheduler.workerCount < 1 || scheduler.workerCount > maxexecutingwork ||
          retry.maxRetries < 0 || retry.maxRetries > maxretryattempts ||
          retry.delayedRetryDelays == null || retry.delayedRetryDelays.size > maxretryattempts ||
          retry.delayedRetryDelays.exists(delay =>
            delay == null || delay.isZero || delay.isNegative || delay.compareTo(Duration.ofHours(24)) > 0
          )
      ) JobResourcePolicy.refused
      else Consequence.unit
    }
}

private[job] object JobResourcePolicy {
  val default: JobResourcePolicy = JobResourcePolicy()

  def refused[A]: Consequence.Failure[A] =
    Consequence.serviceUnavailable("JobEngine resource capacity unavailable")
}

private[job] final case class JobResourceSnapshot(
  retained: Int,
  pending: Int,
  executing: Int,
  policy: JobResourcePolicy
)
