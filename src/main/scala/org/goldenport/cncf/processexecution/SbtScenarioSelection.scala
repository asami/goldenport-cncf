package org.goldenport.cncf.processexecution

import org.goldenport.Consequence

/** Exact framework coordinates, admitted again against the current host allowlist.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SbtScenarioSelection(suiteClassName: String, testName: String) {
  def validateC: Consequence[SbtScenarioSelection] =
    if (SbtTestSelectionPolicy.validClasses(Vector(suiteClassName)) &&
        testName != null && testName.nonEmpty && testName == testName.trim &&
        testName.length <= 512 && !testName.exists(_.isControl) &&
        !testName.exists(x => x == '"' || x == '\\' || x == ';'))
      Consequence.success(this)
    else
      Consequence.argumentPolicyViolation("scenario", "sbt.execution.scenario",
        "exact admitted class and bounded literal test name without quote, backslash or semicolon", "invalid")
}

object SbtScenarioSelection {
  private[cncf] def validateOptionC(
    scenario: Option[SbtScenarioSelection],
    operation: SbtOperation
  ): Consequence[Unit] =
    if (scenario == null || scenario.exists(_ == null))
      Consequence.argumentPolicyViolation("scenario", "sbt.execution.scenario", "nonnull optional selection", "invalid")
    else scenario match {
      case None => Consequence.unit
      case Some(selection) if operation == SbtOperation.TestOnly => selection.validateC.map(_ => ())
      case Some(_) => Consequence.argumentPolicyViolation("scenario", "sbt.execution.scenario-operation",
        "scenario selection only for TestOnly", "invalid")
    }
}
