package org.goldenport.cncf.spi.ai.runner

/** Provider-owned observations returned after a managed execution, not request
  * hints, execution permission or proof of context independence. Missing facts
  * stay absent. Consumers must not synthesize observations from profile config.
  * @since   Oct.  8, 2026
  * @version Oct.  9, 2026
  */
object AiManagedExecutionMetadata {
  val programIdentity = "ai.execution.program_identity"
  val providerProfile = "ai.execution.provider_profile"
  val reasoningEffort = "ai.execution.reasoning_effort"
  val durationMillis = "ai.execution.duration_millis"
  /** Provider control-channel context, when actually reported. This is not a
    * process/program identity, caller hint or independently authenticated actor.
    */
  val contextIdentity = "ai.execution.context_identity"
}
