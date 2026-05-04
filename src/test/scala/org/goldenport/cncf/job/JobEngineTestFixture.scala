package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import scala.collection.mutable.ListBuffer
import org.scalatest.{BeforeAndAfterEach, Suite}

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
trait JobEngineTestFixture extends BeforeAndAfterEach { this: Suite =>
  private val _job_engines = ListBuffer.empty[JobEngine]

  protected def registerJobEngine[A <: JobEngine](engine: A): A =
    synchronized {
      _job_engines += engine
      engine
    }

  protected def createJobEngine(
    schedulerConfig: InMemoryJobEngine.SchedulerConfig = InMemoryJobEngine.SchedulerConfig.default
  ): InMemoryJobEngine =
    registerJobEngine(InMemoryJobEngine.create(schedulerConfig))

  protected def createInMemoryJobEngine(
    runtimeState: InMemoryJobEngine.State = InMemoryJobEngine.State(),
    retrySchedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule.default,
    schedulerConfig: InMemoryJobEngine.SchedulerConfig = InMemoryJobEngine.SchedulerConfig.default,
    timeSource: JobTimeSource = JobTimeSource.system,
    timer: Option[JobTimer] = None
  ): InMemoryJobEngine =
    registerJobEngine(
      new InMemoryJobEngine(
        runtimeState = runtimeState,
        retrySchedule = retrySchedule,
        schedulerConfig = schedulerConfig,
        timeSource = timeSource,
        timer = timer
      )(scala.concurrent.ExecutionContext.global)
    )

  protected def createManualJobEngine(
    schedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule(
      Vector(Duration.ofMillis(25L), Duration.ofMillis(50L), Duration.ofMillis(75L))
    ),
    state: InMemoryJobEngine.State = InMemoryJobEngine.State(),
    initialTime: Instant = Instant.parse("2026-05-04T00:00:00Z")
  ): JobEngineTestFixture.ManualFixture = {
    val fixture = JobEngineTestFixture.manual(schedule, state, initialTime)
    registerJobEngine(fixture.engine)
    fixture
  }

  protected def createManualInMemoryJobEngine(
    state: InMemoryJobEngine.State,
    schedule: InMemoryJobEngine.RetrySchedule,
    clock: ManualJobTimeSource,
    timer: InMemoryJobEngine.ManualJobTimer
  ): InMemoryJobEngine =
    registerJobEngine(JobEngineTestFixture.manualEngine(state, schedule, clock, timer))

  protected def withJobEngine[A](
    schedulerConfig: InMemoryJobEngine.SchedulerConfig = InMemoryJobEngine.SchedulerConfig.default
  )(body: InMemoryJobEngine => A): A = {
    val engine = createJobEngine(schedulerConfig)
    body(engine)
  }

  protected def shutdownRegisteredJobEngines(): Unit = {
    val engines =
      synchronized {
        val xs = _job_engines.toVector
        _job_engines.clear()
        xs
      }
    engines.reverse.foreach(_.shutdown())
  }

  abstract override protected def afterEach(): Unit =
    try {
      shutdownRegisteredJobEngines()
    } finally {
      super.afterEach()
    }
}

object JobEngineTestFixture {
  final case class ManualFixture(
    engine: InMemoryJobEngine,
    clock: ManualJobTimeSource,
    timer: InMemoryJobEngine.ManualJobTimer
  )

  def manual(
    schedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule(
      Vector(Duration.ofMillis(25L), Duration.ofMillis(50L), Duration.ofMillis(75L))
    ),
    state: InMemoryJobEngine.State = InMemoryJobEngine.State(),
    initialTime: Instant = Instant.parse("2026-05-04T00:00:00Z")
  ): ManualFixture = {
    val clock = new ManualJobTimeSource(initialTime)
    val timer = new InMemoryJobEngine.ManualJobTimer(clock)
    ManualFixture(
      new InMemoryJobEngine(
        runtimeState = state,
        retrySchedule = schedule,
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
        timeSource = clock,
        timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global),
      clock,
      timer
    )
  }

  def manualEngine(
    state: InMemoryJobEngine.State,
    schedule: InMemoryJobEngine.RetrySchedule,
    clock: ManualJobTimeSource,
    timer: InMemoryJobEngine.ManualJobTimer
  ): InMemoryJobEngine =
    new InMemoryJobEngine(
      runtimeState = state,
      retrySchedule = schedule,
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
      timeSource = clock,
      timer = Some(timer)
    )(scala.concurrent.ExecutionContext.global)
}
