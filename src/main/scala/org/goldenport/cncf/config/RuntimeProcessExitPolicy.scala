package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * Value-only process-adapter policy. It deliberately carries neither raw
 * configuration nor binding/provenance metadata across the runtime boundary.
 *
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeProcessExitPolicy(
  forceExit: Boolean = false,
  noExit: Boolean = false
) {
  def disposition(exitCode: Int): RuntimeProcessExitPolicy.Disposition =
    if (forceExit)
      RuntimeProcessExitPolicy.Disposition.Exit
    else if (noExit && exitCode != 0)
      RuntimeProcessExitPolicy.Disposition.Fail
    else
      RuntimeProcessExitPolicy.Disposition.Return
}

object RuntimeProcessExitPolicy {
  enum Disposition {
    case Exit
    case Fail
    case Return
  }

  def default: RuntimeProcessExitPolicy = RuntimeProcessExitPolicy()

  def from(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[RuntimeProcessExitPolicy] =
    if (bindings == null)
      Consequence.configurationInvalid("runtime process-exit policy bindings are required")
    else
      for {
        forceexit <- bindings.value(CncfConfigurationParameterCatalog.forceExit)
        noexit <- bindings.value(CncfConfigurationParameterCatalog.noExit)
      } yield RuntimeProcessExitPolicy(
        forceExit = forceexit.getOrElse(false),
        noExit = noexit.getOrElse(false)
      )

  /**
   * Consume only the external CLI adapter switches. They can enable either
   * field but never become another internal configuration authority.
   */
  def admitArguments(
    policy: RuntimeProcessExitPolicy,
    args: Array[String]
  ): (RuntimeProcessExitPolicy, Array[String]) = {
    val forceexit = args.contains("--force-exit")
    val noexit = args.contains("--no-exit")
    val residual = args.filterNot(arg => arg == "--force-exit" || arg == "--no-exit")
    (
      policy.copy(
        forceExit = policy.forceExit || forceexit,
        noExit = policy.noExit || noexit
      ),
      residual
    )
  }
}
