package org.goldenport.cncf.component

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.util.Using
import io.circe.Json
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentStyleCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-component-style-catalog, example:$example, rules:$rules, phase:53, slice:CS-02D")

  "ComponentStyleCatalog" should {
    "E1 load and expand the built-in full-fledged standalone style" must _metadata("E1", "CS02A-R1,CS02A-R3") {
      "when the framework resolves the built-in catalog snapshot" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1, CS02A-R3; Example: E1; the framework-owned CNCF component-style catalog")
      val catalog = ComponentStyleCatalog.loadC().toOption.get
      val style = ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get

      When("the built-in standalone style is resolved and expanded")
      val definition = catalog.resolveC(style).toOption.get
      val snapshot = catalog.expandC(definition).toOption.get

      Then("the canonical snapshot exposes the complete deterministic capability contract")
      catalog.apiVersion shouldBe "cncf.textus/v1"
      catalog.provider.canonical shouldBe "cncf"
      definition.id shouldBe style
      snapshot.bundles.map(_.canonical) shouldBe Vector("domain.full@1")
      snapshot.capabilities.map(_.canonical) shouldBe Vector(
        "user.fixed-context-compatible@1",
        "user.multi-user@1"
      )
      snapshot.effectiveCapabilities.map(_.canonical) shouldBe Vector(
        "domain.aggregate@1",
        "domain.command@1",
        "domain.domain-event@1",
        "domain.entity@1",
        "domain.optimistic-concurrency@1",
        "domain.persistence@1",
        "domain.projection@1",
        "domain.query@1",
        "domain.transaction@1",
        "user.fixed-context-compatible@1",
        "user.multi-user@1"
      )
      ComponentStyleSnapshot.toJson(snapshot).hcursor.keys.get.toList shouldBe List(
        "apiVersion", "provider", "id", "version", "parameterSchema", "parameters", "provides", "requires"
      )
      ComponentStyleCatalog.snapshotC(ComponentStyleSnapshot.toJson(snapshot), catalog).toOption shouldBe Some(snapshot)
    }
    }

    "validate every capability definition graph" which {
      _graph_variants(_catalog_text).zipWithIndex.foreach { case ((label, catalogtext, expected), index) =>
        s"E${index + 2} reject a $label capability graph" must _metadata(s"E${index + 2}", "CS02D-R1,CS02D-R2") {
          "when framework catalog admission evaluates the altered graph" in {
          Given(s"Spec: docs/journal/2026/07/2026-07-31-phase-53-cs02d-capability-graph-validation.md; Rules: CS02D-R1, CS02D-R2; Example: E${index + 2}; a catalog with a $label violation")

          When("the framework parses the catalog")
          val diagnostic = ComponentStyleCatalog.parse(catalogtext).left.toOption.getOrElse(fail("invalid catalog was accepted"))

          Then(s"the $label violation has its stable rejection diagnostic")
          diagnostic should include(expected)
          }
        }
      }

      "E8 reject every generated unreferenced diamond with a duplicated terminal capability" must _metadata("E8", "CS02D-R1,CS02D-R2") {
      "when framework admission evaluates arbitrary unreachable nested graphs" in {
      Given("Spec: docs/journal/2026/07/2026-07-31-phase-53-cs02d-capability-graph-validation.md; Rules: CS02D-R1, CS02D-R2; Example: E8; arbitrary canonical suffixes for unreachable nested bundle graphs")
      val catalogtext = _catalog_text
      val property = Prop.forAll(Gen.chooseNum(1, 96)) { suffix =>
        ComponentStyleCatalog.parse(_catalog_with_unreferenced_diamond(catalogtext, suffix.toString)).isLeft
      }

      When("each nested diamond is validated as part of the complete catalog graph")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("none can hide duplicate terminals outside a selected style closure")
      checked.passed shouldBe true
    }
    }
    }

    "E9 reject an unapproved catalog provider" must _metadata("E9", "CS02A-R1") {
      "when the catalog provider differs from CNCF" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E9; a catalog whose provider is not the approved CNCF provider")
      val approvedcatalog = ComponentStyleCatalog.loadC().toOption.get
      val catalogtext = _catalog_text
      val shadowtext = catalogtext.replace("\"provider\" : \"cncf\"", "\"provider\" : \"shadow\"")

      When("the framework parses the altered catalog")
      val parsed = ComponentStyleCatalog.parse(shadowtext).toOption

      Then("only the approved provider catalog is admitted")
      parsed shouldBe None
      approvedcatalog.provider.canonical shouldBe "cncf"
    }
    }

    "E10 ignore a context-classloader catalog shadow" must _metadata("E10", "CS02A-R1") {
      "when a context classloader supplies an unapproved catalog shadow" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R1; Example: E10; a context classloader that supplies an unapproved catalog shadow")
      val approvedcatalog = ComponentStyleCatalog.loadC().toOption.get
      val catalogtext = _catalog_text
      val previousloader = Thread.currentThread.getContextClassLoader
      val shadowtext = catalogtext.replace("\"provider\" : \"cncf\"", "\"provider\" : \"shadow\"")
      val shadowloader = new ClassLoader(null) {
        override def getResourceAsStream(name: String) =
          if (name == ComponentStyleCatalog.RESOURCE_PATH)
            new ByteArrayInputStream(shadowtext.getBytes(StandardCharsets.UTF_8))
          else
            null
      }

      When("the framework loads its catalog while the shadow is active")
      Thread.currentThread.setContextClassLoader(shadowloader)
      try {
        Then("the framework-owned resource remains authoritative")
        ComponentStyleCatalog.loadC().toOption shouldBe Some(approvedcatalog)
      } finally {
        Thread.currentThread.setContextClassLoader(previousloader)
      }
    }
    }

    "E11 reject snapshots with mode parameters" must _metadata("E11", "CS02A-R3") {
      "when a valid snapshot carries an added mode parameter" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R3; Example: E11; a valid built-in snapshot with an added mode parameter")
      val catalog = ComponentStyleCatalog.default
      val style = ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get
      val snapshot = catalog.expandC(catalog.resolveC(style).toOption.get).toOption.get
      val nonemptyparameters = ComponentStyleSnapshot.toJson(snapshot).mapObject(_.add(
        "parameters",
        Json.obj("mode" -> Json.fromString("fixed-user"))
      ))

      When("snapshot admission evaluates the parameter payload")
      val admitted = ComponentStyleCatalog.snapshotC(nonemptyparameters, catalog).toOption

      Then("the mode-bearing snapshot is rejected")
      admitted shouldBe None
    }
    }

    "E12 admit canonical catalog identity boundaries" must _metadata("E12", "CS02A-R2") {
      "when framework parsing receives canonical ASCII identities at both major boundaries" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E12; qualified and unqualified ASCII identities at canonical major boundaries")
      val styleidentities = Vector(
        "full-fledged-with-standalone@1" -> true,
        "vendor.full-fledged-with-standalone@1" -> true,
        "vendor.full-fledged-with-standalone@2147483647" -> true
      )
      val capabilityidentities = Vector(
        "domain.entity@1" -> true,
        "domain.entity@2147483647" -> true
      )

      When("the framework parses every catalog identity")
      val styleadmission = styleidentities.map { case (identity, _) =>
        identity -> ComponentStyleId.parseC(identity).toOption.isDefined
      }
      val capabilityadmission = capabilityidentities.map { case (identity, _) =>
        identity -> ComponentCapabilityId.parseC(identity).toOption.isDefined
      }

      Then("the canonical identity boundaries are admitted")
      styleadmission shouldBe styleidentities
      capabilityadmission shouldBe capabilityidentities
    }
    }

    "E14 reject a Unicode style identity" must _metadata("E14", "CS02A-R2") {
      "when framework parsing receives a Unicode style name" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E14; a style identity with a Unicode part")

      When("the style identity is parsed")
      val parsed = ComponentStyleId.parseC("vendor.é@1").toOption

      Then("the non-ASCII style is rejected")
      parsed shouldBe None
      }
    }

    "E15 reject a leading-zero style major" must _metadata("E15", "CS02A-R2") {
      "when framework parsing receives a leading-zero major" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E15; a style identity with a leading-zero major")

      When("the style identity is parsed")
      val parsed = ComponentStyleId.parseC("vendor.full-fledged-with-standalone@01").toOption

      Then("the noncanonical major is rejected")
      parsed shouldBe None
      }
    }

    "E16 reject an overflowing style major" must _metadata("E16", "CS02A-R2") {
      "when framework parsing receives a major above Int.MaxValue" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E16; a style identity whose major exceeds Int.MaxValue")

      When("the style identity is parsed")
      val parsed = ComponentStyleId.parseC("vendor.full-fledged-with-standalone@2147483648").toOption

      Then("the overflowing major is rejected")
      parsed shouldBe None
      }
    }

    "E17 reject a Unicode capability identity" must _metadata("E17", "CS02A-R2") {
      "when framework parsing receives a Unicode capability name" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E17; a capability identity with a Unicode part")

      When("the capability identity is parsed")
      val parsed = ComponentCapabilityId.parseC("domain.é@1").toOption

      Then("the non-ASCII capability is rejected")
      parsed shouldBe None
      }
    }

    "E13 reject generated catalog identity major overflows" must _metadata("E13", "CS02A-R2") {
      "when framework parsing receives generated decimal suffixes beyond Int.MaxValue" in {
      Given("Spec: docs/notes/phase-53-cs02a-versioned-component-style-contract.md; Rules: CS02A-R2; Example: E13; canonical identity prefixes and arbitrary decimal suffixes beyond Int.MaxValue")
      val property = Prop.forAll(Gen.chooseNum(0, 96)) { suffix =>
        val overflow = s"2147483648$suffix"
        ComponentStyleId.parseC(s"vendor.style@$overflow").toOption.isEmpty &&
        ComponentCapabilityId.parseC(s"domain.entity@$overflow").toOption.isEmpty
      }

      When("the framework parses every overflowing major")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("no major outside the shared signed-Int range is admitted")
      checked.passed shouldBe true
    }
    }
  }

  private def _catalog_text: String =
    Using.resource(
      Option(getClass.getClassLoader.getResourceAsStream(ComponentStyleCatalog.RESOURCE_PATH)).get
    )(stream => new String(stream.readAllBytes(), StandardCharsets.UTF_8))

  private def _graph_variants(catalogtext: String): Vector[(String, String, String)] =
    Vector(
      "duplicate bundle" -> catalogtext.replaceFirst("(?s)(\\\"capabilityBundles\\\"\\s*:\\s*\\[)", "$1{\\\"id\\\":\\\"domain.full@1\\\",\\\"bundles\\\":[],\\\"capabilities\\\":[]},") -> "Duplicate component capability bundle identities",
      "unknown bundle reference" -> catalogtext.replace("\"bundles\" : []", "\"bundles\" : [\"domain.missing@1\"]") -> "Unknown component capability bundle: domain.missing@1",
      "cyclic bundle reference" -> catalogtext.replace("\"bundles\" : []", "\"bundles\" : [\"domain.loop@1\"]").replace("\n    }\n  ],\n  \"componentStyles\"", "\n    },\n    {\n      \"id\" : \"domain.loop@1\",\n      \"bundles\" : [\"domain.full@1\"],\n      \"capabilities\" : []\n    }\n  ],\n  \"componentStyles\"") -> "Cyclic component capability bundle reference",
      "capability major conflict" -> catalogtext.replace("\"domain.entity@1\",", "\"domain.entity@1\", \"domain.entity@2\",") -> "version-incompatible identities: domain.entity",
      "nested bundle major conflict" -> _catalog_with_nested_bundle_major_conflict(catalogtext) -> "version-incompatible identities: domain.nested-major-root",
      "sibling bundle major conflict" -> _catalog_with_sibling_bundle_major_conflict(catalogtext) -> "version-incompatible identities: domain.shared",
      "unreferenced diamond" -> _catalog_with_unreferenced_diamond(catalogtext, "static") -> "Duplicate component capability bundle domain.unusedstatic@1 closure identities",
      "required subsystem major conflict" -> catalogtext.replace("\"user-context.current@1\"", "\"user-context.current@1\", \"user-context.current@2\"") -> "subsystem capabilities contains version-incompatible identities: user-context.current"
    ).map { case ((label, text), expected) => (label, text, expected) }

  private def _catalog_with_unreferenced_diamond(catalogtext: String, suffix: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      s"""
         |    },
         |    {
         |      "id" : "domain.unused$suffix@1",
         |      "bundles" : ["domain.left$suffix@1", "domain.right$suffix@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.left$suffix@1",
         |      "bundles" : [],
         |      "capabilities" : ["domain.entity@1"]
         |    },
         |    {
         |      "id" : "domain.right$suffix@1",
         |      "bundles" : [],
         |      "capabilities" : ["domain.entity@1"]
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )

  private def _catalog_with_nested_bundle_major_conflict(catalogtext: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      """
         |    },
         |    {
         |      "id" : "domain.nested-major-root@1",
         |      "bundles" : ["domain.nested-major-middle@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.nested-major-middle@1",
         |      "bundles" : ["domain.nested-major-root@2"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.nested-major-root@2",
         |      "bundles" : [],
         |      "capabilities" : []
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )

  private def _catalog_with_sibling_bundle_major_conflict(catalogtext: String): String =
    catalogtext.replace(
      "\n    }\n  ],\n  \"componentStyles\"",
      """
         |    },
         |    {
         |      "id" : "domain.branch-root@1",
         |      "bundles" : ["domain.branch-left@1", "domain.branch-right@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.branch-left@1",
         |      "bundles" : ["domain.shared@1"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.branch-right@1",
         |      "bundles" : ["domain.shared@2"],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.shared@1",
         |      "bundles" : [],
         |      "capabilities" : []
         |    },
         |    {
         |      "id" : "domain.shared@2",
         |      "bundles" : [],
         |      "capabilities" : []
         |    }
         |  ],
         |  "componentStyles"""".stripMargin
    )
}
