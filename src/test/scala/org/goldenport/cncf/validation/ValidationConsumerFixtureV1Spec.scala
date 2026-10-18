package org.goldenport.cncf.validation

import java.nio.file.Path
import scala.io.Source
import scala.util.Using
import io.circe.{Decoder, Json}
import io.circe.parser.parse
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import ValidationDiscoveryV1.*
import ValidationMetadataV1.*
import ValidationCmlMappingV1.{DurationUnit, DurationValue, Properties}

/** Behavioral consumption of the packaged synthetic interchange example.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ValidationConsumerFixtureV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ValidationConsumerFixtureV1Spec.*

  "Shared static consumer fixture" should {
    "expose the sole ADMISSION removal within complete comparable scope" in {
      Given("the packaged synthetic baseline and candidate with the same explicit logical population")
      val fixture = _read()
      val baseline = fixture.snapshots(fixture.comparison.baseline)
      val candidate = fixture.snapshots(fixture.comparison.candidate)
      When("public queries select ADMISSION and exact resolution compares the same logical operation")
      val before = selectC(baseline.inventory, SelectionQuery(Set(Purpose.ADMISSION))).toOption.get
      val after = selectC(candidate.inventory, SelectionQuery(Set(Purpose.ADMISSION))).toOption.get
      val original = before.head
      val changed = ValidationDiscoveryV1.resolveC(candidate.inventory, original.reference).toOption.get
      Then("the comparable complete inventories expose one membership becoming zero while the operation remains FULL")
      baseline.inventory.completeness shouldBe Completeness.COMPLETE
      candidate.inventory.completeness shouldBe Completeness.COMPLETE
      baseline.inventory.scope.identity shouldBe fixture.comparison.scopeidentity
      candidate.inventory.scope.identity shouldBe fixture.comparison.scopeidentity
      baseline.populationrevision shouldBe fixture.comparison.populationrevision
      candidate.populationrevision shouldBe fixture.comparison.populationrevision
      before should have size 1
      after shouldBe empty
      changed.reference shouldBe original.reference
      changed.selector shouldBe original.selector
      changed.metadata shouldBe original.metadata.map(_.copy(purposes = Set(Purpose.FULL)))
      changed.provenance.artifactRevision should not be original.provenance.artifactRevision
      changed.provenance.sourceRevision should not be original.provenance.sourceRevision
      changed.provenance.operationRevision should not be original.provenance.operationRevision
      baseline.inventory.operations.map(_.reference).toSet shouldBe candidate.inventory.operations.map(_.reference).toSet
      baseline.inventory.operations.filterNot(_.reference == original.reference).foreach { row =>
        ValidationDiscoveryV1.resolveC(candidate.inventory, row.reference).toOption.get.metadata shouldBe row.metadata
      }
    }

    "preserve incomplete scope facts when empty selections cannot establish absence" in {
      Given("the synthetic PARTIAL and UNKNOWN variants with explicit enumeration gaps")
      val fixture = _read()
      val variants = Vector(fixture.snapshots("candidate-partial"), fixture.snapshots("candidate-unknown"))
      val facts = variants.map(x => (x.inventory.scope, x.inventory.completeness, x.inventory.diagnostics))
      When("the same public ADMISSION query selects no visible operation")
      val selections = variants.map(x => selectC(x.inventory, SelectionQuery(Set(Purpose.ADMISSION))).toOption.get)
      Then("no matching rows leave the incompleteness and diagnostics available to the consumer")
      selections.foreach(_ shouldBe empty)
      variants.map(_.inventory.completeness) shouldBe Vector(Completeness.PARTIAL, Completeness.UNKNOWN)
      variants.foreach(_.inventory.diagnostics should not be empty)
      variants.map(x => (x.inventory.scope, x.inventory.completeness, x.inventory.diagnostics)) shouldBe facts
      variants.head.inventory.operations should have size 2
      variants.last.inventory.operations shouldBe empty
    }

    "query feature unions against an explicit target population including an uncovered feature" in {
      Given("three target features, two explicit feature mappings and a FOCUSED operation covering two features")
      val fixture = _read()
      val inventory = fixture.snapshots("baseline").inventory
      val approval = FeatureReference("component:orders", "approval")
      val review = FeatureReference("slice:orders", "review")
      val uncovered = FeatureReference("component:orders", "uncovered-export")
      When("the public query combines FOCUSED with either target feature")
      val selected = selectC(inventory, SelectionQuery(Set(Purpose.FOCUSED), Set(approval, review))).toOption.get
      val missing = selectC(inventory, SelectionQuery(features = Set(uncovered))).toOption.get
      Then("query coverage is distinct from the declared target population and revisioned mappings")
      selected.map(_.reference.operation) shouldBe Vector("focused:review")
      selected.head.metadata.get.features shouldBe Set(approval, review)
      missing shouldBe empty
      fixture.population.features shouldBe Set(approval, review, uncovered)
      fixture.population.identity shouldBe "example:orders-features"
      fixture.population.revision shouldBe "features-1"
      fixture.mappings.map(_.feature).toSet shouldBe Set(approval, review)
      fixture.mappings.map(_.revision).toSet shouldBe Set("mapping-1", "mapping-2")
      fixture.mappings.foreach(_.targetrevision should not be empty)
      fixture.runtimeevidence shouldBe empty
    }

    "retain visible unannotated operations without assigning any purpose or runtime success" in {
      Given("the valid unannotated row in the packaged baseline")
      val fixture = _read()
      val inventory = fixture.snapshots("baseline").inventory
      When("the empty query and exact supplemental resolution inspect the same row")
      val all = selectC(inventory, SelectionQuery()).toOption.get
      val plain = all.find(_.reference.operation == "plain:registration").get
      val resolved = ValidationDiscoveryV1.resolveC(inventory, plain.reference).toOption.get
      Then("absence of declarations remains valid metadata with no inferred purpose or execution observation")
      all should have size 3
      resolved shouldBe plain
      plain.metadata.get.origins shouldBe Origins(false, false)
      plain.metadata.get.purposes shouldBe empty
      plain.metadata.get.features shouldBe empty
      fixture.runtimeevidence shouldBe empty
    }

    "preserve purpose OR, feature OR and cross-dimension AND for generated queries" in {
      Given("the shared baseline, candidate and incomplete variants normalized through public declaration mapping")
      val fixture = _read()
      val inventories = fixture.snapshots.values.map(_.inventory).toVector
      val purposes = Purpose.values.toVector
      val features = fixture.population.features.toVector
      When("generated purpose and feature subsets query every example inventory")
      val property = Prop.forAll(Gen.someOf(purposes), Gen.someOf(features)) { (selectedpurposes, selectedfeatures) =>
        inventories.forall { inventory =>
          val query = SelectionQuery(selectedpurposes.toSet, selectedfeatures.toSet)
          val selected = selectC(inventory, query).toOption.get
          val purposeonly = selectC(inventory, SelectionQuery(purposes = query.purposes)).toOption.get
          val featureonly = selectC(inventory, SelectionQuery(features = query.features)).toOption.get
          val all = selectC(inventory, SelectionQuery()).toOption.get
          val featureunion = if (query.features.isEmpty) all.toSet else query.features.flatMap { feature =>
            selectC(inventory, SelectionQuery(features = Set(feature))).toOption.get
          }
          selected.toSet == purposeonly.toSet.intersect(featureonly.toSet) &&
            featureonly.toSet == featureunion && selected.map(_.reference).distinct.size == selected.size
        }
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      Then("the fixture observes the same pure membership semantics without an observed-tag population")
      checked.passed shouldBe true
    }
  }
}

object ValidationConsumerFixtureV1Spec {
  private final case class Comparison(scopeidentity: String, populationrevision: String, baseline: String, candidate: String)
  private final case class FeaturePopulation(identity: String, revision: String, features: Set[FeatureReference])
  private final case class FeatureMapping(identity: String, revision: String, feature: FeatureReference,
    targetidentity: String, targetrevision: String)
  private final case class Snapshot(inventory: Inventory, populationrevision: String)
  private final case class Example(comparison: Comparison, population: FeaturePopulation,
    mappings: Vector[FeatureMapping], snapshots: Map[String, Snapshot], runtimeevidence: Vector[String])

  /** Private reader for this example only: no production inventory codec or discovery. */
  private def _read(): Example = {
    val resource = getClass.getResourceAsStream("/META-INF/cncf/validation/v1/consumer-fixture.json")
    val json = Using.resource(Source.fromInputStream(resource, "UTF-8"))(source => parse(source.mkString).toOption.get)
    val comparison = _field(json, "comparison")
    val population = _field(json, "featurePopulation")
    val snapshots = _array(_field(json, "snapshots")).map(x => _get[String](x, "name") -> _snapshot(x)).toMap
    val variants = _array(_field(json, "variants")).map { variant =>
      val original = snapshots(_get[String](variant, "snapshot"))
      val included = _get[Vector[String]](variant, "includedOperations").toSet
      val inventory = original.inventory.copy(
        scope = original.inventory.scope.copy(revision = _get[String](variant, "scopeRevision")),
        completeness = Completeness.valueOf(_get[String](variant, "completeness")),
        operations = original.inventory.operations.filter(x => included.contains(x.reference.operation)),
        diagnostics = _diagnostics(_field(variant, "diagnostics")))
      _get[String](variant, "name") -> Snapshot(inventory, original.populationrevision)
    }.toMap
    Example(Comparison(_get[String](comparison, "scopeIdentity"), _get[String](comparison, "populationRevision"),
      _get[String](comparison, "baseline"), _get[String](comparison, "candidate")),
      FeaturePopulation(_get[String](population, "identity"), _get[String](population, "revision"),
        _features(_field(population, "features"))),
      _array(_field(json, "featureMappings")).map { mapping =>
        FeatureMapping(_get[String](mapping, "identity"), _get[String](mapping, "revision"),
          _feature(_field(mapping, "feature")), _get[String](mapping, "targetIdentity"), _get[String](mapping, "targetRevision"))
      }, snapshots ++ variants, _get[Vector[String]](json, "runtimeEvidence"))
  }

  private def _snapshot(value: Json): Snapshot = {
    val scope = _field(value, "scope")
    val artifacts = _array(_field(scope, "artifacts")).map { artifact =>
      val locator = _get[String](artifact, "artifactLocator").stripPrefix("example-artifact:")
      ArtifactScope(_get[String](artifact, "componentIdentity"), _get[String](artifact, "artifactIdentity"),
        _get[String](artifact, "artifactRevision"), _get[String](artifact, "sourceRevision"),
        Path.of("/synthetic/validation", locator), _get[String](artifact, "packagePrefix"))
    }
    val operations = _array(_field(value, "operations")).map { operation =>
      val reference = _field(operation, "reference")
      val provenance = _field(operation, "provenance")
      val selector = _field(operation, "selector")
      val metadata = ValidationCmlMappingV1.resolveC(_properties(_field(operation, "specification")),
        _properties(_field(operation, "operation"))).toOption.get
      Operation(OperationReference(_get[String](reference, "component"), _get[String](reference, "specification"),
        _get[String](reference, "operation")), Provenance(_get[String](provenance, "artifactIdentity"),
        _get[String](provenance, "artifactRevision"), _get[String](provenance, "sourceRevision"),
        _get[String](provenance, "operationRevision"), _get[Int](provenance, "abiVersion")),
        ScalaTestSelector(_get[String](selector, "suiteClassName"), _get[String](selector, "testName")),
        Some(metadata), Vector.empty)
    }
    Snapshot(Inventory(DiscoveryScope(_get[String](scope, "identity"), _get[String](scope, "revision"), artifacts),
      Completeness.valueOf(_get[String](value, "completeness")), operations, _diagnostics(_field(value, "diagnostics"))),
      _get[String](scope, "populationRevision"))
  }

  private def _properties(value: Json): Option[Properties] =
    if (value.isNull) None
    else {
      val duration = _field(value, "expectedDuration")
      val requirement = _field(value, "executionRequirement")
      Some(Properties(abiVersion = value.hcursor.get[Int]("abiVersion").getOrElse(1),
        purposes = value.hcursor.get[Set[String]]("purposes").toOption,
        features = if (_field(value, "features").isNull) Set.empty else _features(_field(value, "features")),
        expectedDuration = if (duration.isNull) None else Some(DurationValue(_get[Long](duration, "amount"),
          DurationUnit.valueOf(_get[String](duration, "unit")))),
        durationClass = value.hcursor.get[String]("durationClass").toOption,
        executionRequirement = if (requirement.isNull) None else Some(ExecutionRequirement(
          _get[Vector[String]](requirement, "capabilities").map(CapabilityRequirement.apply),
          RiskLevel(_get[String](requirement, "risk")),
          ReasoningLevel.values.find(_.value == _get[String](requirement, "reasoning")).get,
          _get[Boolean](requirement, "reviewRequired"))),
        requiredSemantics = value.hcursor.get[Set[String]]("requiredSemantics").getOrElse(Set.empty)))
    }

  private def _diagnostics(value: Json): Vector[Diagnostic] = _array(value).map { diagnostic =>
    Diagnostic(_get[String](diagnostic, "code"), _get[String](diagnostic, "source"), _get[String](diagnostic, "message"))
  }
  private def _features(value: Json): Set[FeatureReference] = _array(value).map(_feature).toSet
  private def _feature(value: Json): FeatureReference = FeatureReference(_get[String](value, "scope"), _get[String](value, "identity"))
  private def _field(value: Json, field: String): Json = value.hcursor.downField(field).focus.getOrElse(Json.Null)
  private def _array(value: Json): Vector[Json] = value.asArray.get.toVector
  private def _get[A: Decoder](value: Json, field: String): A = value.hcursor.get[A](field).toOption.get
}
