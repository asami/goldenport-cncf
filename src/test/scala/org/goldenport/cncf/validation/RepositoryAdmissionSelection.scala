package org.goldenport.cncf.validation

import java.nio.file.Path
import org.scalatest.DoNotDiscover
import annotation.{ValidationScenario, ValidationSpecification}
import ValidationDiscoveryV1.*
import ValidationMetadataV1.Purpose

/** Build adapter over canonical metadata; legacy FULL/HEAVY selection stays intact.
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
object RepositoryAdmissionSelection {
  def select(root: String, revision: String, suites: Array[String]): Array[Array[String]] = {
    val loader = getClass.getClassLoader
    // Unannotated repository tests keep their existing classification. Restrict
    // discovery to declared namespaces rather than constructing every suite.
    val negativeFixturePrefix = "org.goldenport.cncf.validation.fixtures.phase103.invalid."
    val candidates = suites.toSet.filterNot(_.startsWith(negativeFixturePrefix)).filter { name =>
      val cls = Class.forName(name, false, loader)
      !cls.isAnnotationPresent(classOf[DoNotDiscover]) &&
        (cls.isAnnotationPresent(classOf[ValidationSpecification]) ||
          cls.getMethods.exists(_.isAnnotationPresent(classOf[ValidationScenario])))
    }
    val packages = candidates.map(name => name.substring(0, name.lastIndexOf('.')))
    val prefixes = packages.filterNot(name => packages.exists(parent => name.startsWith(parent + ".")))
    val artifacts = prefixes.toVector.sorted.map(prefix => ArtifactScope(
      "component:goldenport-cncf", "cncf-tests:" + prefix, revision, "working-tree",
      Path.of(root).toRealPath(), prefix))
    val inventory = discoverC(DiscoveryScope("cncf-admission", revision, artifacts), loader).fold(
      failure => throw new IllegalStateException(s"Admission discovery failed: $failure"), identity)
    val relevant = inventory.operations.filter(op => candidates(op.selector.suiteClassName))
    // Namespace discovery also encounters deliberate negative metadata fixtures.
    // Only their exact namespace may be ignored; loss of enumeration or diagnostics
    // for a declared candidate cannot establish a complete selection.
    val diagnostics = inventory.diagnostics ++ inventory.operations.flatMap(_.diagnostics)
    val unsafe = diagnostics.filterNot(_.source.startsWith(negativeFixturePrefix))
    val missing = candidates -- relevant.map(_.selector.suiteClassName).toSet
    if (inventory.completeness == Completeness.UNKNOWN ||
        (inventory.completeness != Completeness.COMPLETE && diagnostics.isEmpty) || unsafe.nonEmpty || missing.nonEmpty)
      throw new IllegalStateException(s"Admission discovery unavailable: ${inventory.completeness}; " +
        s"${unsafe.size} relevant diagnostics; ${missing.size} missing suites")
    val excludedTags = Set("org.goldenport.tags.ManualSpec", "org.goldenport.tags.TimingSpec")
    val selected = selectC(inventory.copy(operations = relevant),
      SelectionQuery(Set(Purpose.ADMISSION), standardFramework = true)).fold(
      failure => throw new IllegalStateException(s"Admission selection failed: $failure"), identity)
      .filterNot(op => op.framework.exists(_.tags.exists(excludedTags)))
    require(selected.nonEmpty, "No ADMISSION operation selected; an empty run is not PASS")
    selected.map(op => Array(op.selector.suiteClassName, op.selector.testName)).toArray
  }
}

/** Emits identities from actual ScalaTest success events, not selected arguments. */
class AdmissionSuccessReporter extends org.scalatest.Reporter {
  def apply(event: org.scalatest.events.Event): Unit = event match {
    case success: org.scalatest.events.TestSucceeded =>
      def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      success.suiteClassName.foreach { suite =>
        Console.println("CNCF-ADMISSION-V1 succeeded " + token(suite) + ":" + token(success.testName))
      }
    case _ => ()
  }
}
