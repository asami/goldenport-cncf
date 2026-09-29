package org.goldenport.cncf.http

/*
 * Pure admission/state policy for the bounded progressive detail refresher.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object JobExperiencePolling {
  val intervalMillis: Long = 5000L
  val timeoutMillis: Long = 5000L
  val maximumAttempts: Int = 60

  final case class State(
    attempts: Int = 0,
    inFlight: Boolean = false,
    paused: Boolean = false,
    stopped: Boolean = false
  )

  final case class Observation(
    active: Boolean,
    hidden: Boolean = false,
    unloading: Boolean = false,
    responseAccepted: Boolean = true,
    terminal: Boolean = false,
    paused: Boolean = false
  )

  def mayPoll(state: State, observation: Observation): Boolean =
    observation.active && !observation.hidden && !observation.unloading &&
      observation.responseAccepted && !observation.terminal && !observation.paused &&
      !state.inFlight && !state.paused && !state.stopped && state.attempts < maximumAttempts

  def started(state: State): State = state.copy(attempts = state.attempts + 1, inFlight = true)

  def resumed(state: State): State =
    if (state.stopped) state else state.copy(paused = false)

  def observed(state: State, observation: Observation): State =
    state.copy(
      inFlight = false,
      paused = state.paused || observation.paused,
      stopped = state.stopped || !observation.responseAccepted || observation.terminal || observation.hidden || observation.unloading || state.attempts >= maximumAttempts
    )
}
