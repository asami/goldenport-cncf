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
    val candidates = suites.toSet.filter { name =>
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
    // CNCF also contains deliberately malformed metadata fixtures. Canonical
    // selection excludes their invalid/ambiguous rows; partial discovery is not
    // proof of absence or complete repository coverage. Keep diagnostics visible.
    if (inventory.completeness != Completeness.COMPLETE)
      System.err.println(s"ADMISSION discovery: ${inventory.completeness}; " +
        s"${inventory.diagnostics.size} diagnostics retained; selecting valid declared operations only")
    val selected = selectC(inventory.copy(operations = relevant),
      SelectionQuery(Set(Purpose.ADMISSION))).fold(
      failure => throw new IllegalStateException(s"Admission selection failed: $failure"), identity)
    require(selected.nonEmpty, "No ADMISSION operation selected; an empty run is not PASS")
    selected.map(op => Array(op.selector.suiteClassName, op.selector.testName)).toArray
  }
}
