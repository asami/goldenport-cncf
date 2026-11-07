package org.goldenport.cncf.validation

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.config.OperationMode

/** Shared declaration normalization and child ConfigMap consumption.
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 */
object ValidationExecutionModeV1 {
  val modeKey: String = "cncf.validation.mode"
  val providerKey: String = "cncf.validation.provider"
  val observationPrefix: String = "CNCF_VALIDATION_MODE "

  def normalizeC(values: Set[String]): Consequence[Set[String]] =
    if (values == null || values.isEmpty || values.exists(value => value == null || OperationMode.from(value).isEmpty))
      Consequence.argumentPolicyViolation("executionModes", "validation.execution-modes", "nonempty supported OperationMode set", "invalid")
    else Consequence.success(values.map(value => OperationMode.from(value).get.name))

  def variantsC(required: Option[Set[String]], defaults: Set[String] = Set("develop")):
      Consequence[Vector[String]] =
    if (required == null) Consequence.configurationInvalid("Execution modes must be absent or a nonempty declaration")
    else normalizeC(required.getOrElse(defaults)).map(_.toVector.sorted)

  final case class Observation(suite: String, test: String, mode: String, provider: String) {
    def line: String = observationPrefix + Json.obj("suite" -> Json.fromString(suite),
      "test" -> Json.fromString(test), "mode" -> Json.fromString(mode), "provider" -> Json.fromString(provider)).noSpaces
  }

  /** The child supplies its actual adapter choice; configuration cannot attest it. */
  def consumeC(config: Map[String, Any], suite: String, test: String,
    actualMode: OperationMode, actualProvider: String): Consequence[Observation] =
    if (config == null || actualMode == null || !Set("fixture", "live")(actualProvider) ||
        !config.get(modeKey).contains(actualMode.name) || !config.get(providerKey).contains(actualProvider) ||
        suite == null || suite.isEmpty || test == null || test.isEmpty)
      Consequence.stateConflict("Child mode/provider differs from its exact managed ConfigMap selection")
    else Consequence.success(Observation(suite, test, actualMode.name, actualProvider))
}
