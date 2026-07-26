package org.goldenport.cncf.spi.supervisor

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{ComponentSelector, SpiContract, SpiSelection, SpiSocket, StandardSpiSocketSet}

/*
 * Provider-neutral managed lifecycle SPI contract.
 *
 * A provider owns lifecycle execution and deployment ownership. Consumers use
 * opaque managed identifiers and never pass process, filesystem, shell, or
 * credential details through this boundary.
 *
 * @since   Jul. 24, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
trait Supervisor {
  def submit(request: SupervisorRequest)(using ExecutionContext): Consequence[SupervisorResult]
  def lookup(requestId: String)(using ExecutionContext): Consequence[Option[SupervisorResult]]
}

object Supervisor {
  val CONTRACT_NAME = "supervisor"
}

enum SupervisorAction {
  case Start
  case Stop
  case Restart
}

enum SupervisorState {
  case Queued
  case Accepted
  case Running
  case Stopped
  case Rejected
  case Failed
  case TimedOut
}

final case class SupervisorRequest(
  requestId: String,
  idempotencyKey: String,
  targetId: String,
  deploymentId: Option[String],
  action: SupervisorAction,
  operatorSubjectId: String,
  deadlineAt: Instant
)

final case class SupervisorResult(
  requestId: String,
  state: SupervisorState,
  diagnosticCode: Option[String],
  diagnostic: Option[String],
  supervisorId: String,
  instanceId: Option[String],
  acceptedAt: Option[Instant],
  completedAt: Option[Instant]
)

trait SupervisorSocket extends SpiSocket[Supervisor] {
  private var _supervisor: Option[Supervisor] = None

  final def supervisorC: Consequence[Supervisor] =
    _supervisor
      .map(Consequence.success)
      .getOrElse(
        Consequence.serviceUnavailable("Supervisor SPI is not installed")
      )

  final def supervisor: Supervisor =
    _supervisor.getOrElse(
      throw new IllegalStateException("Supervisor SPI is not installed.")
    )

  final def withSupervisor(spi: Supervisor): this.type = {
    _supervisor = Some(spi)
    this
  }

  override final def spiContract: SpiContract[Supervisor] =
    SpiContract(Supervisor.CONTRACT_NAME, classOf[Supervisor])

  override def spiSelection: SpiSelection = SpiSelection()

  override final def isSpiInstalled: Boolean = _supervisor.nonEmpty

  override final def installSpi(spi: Supervisor): Unit =
    withSupervisor(spi)
}

trait SupervisorSocketSet extends StandardSpiSocketSet[Supervisor] {
  final def supervisor(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[Supervisor] =
    resolve(selector)

  override final def spiContract: SpiContract[Supervisor] =
    SpiContract(Supervisor.CONTRACT_NAME, classOf[Supervisor])
}
