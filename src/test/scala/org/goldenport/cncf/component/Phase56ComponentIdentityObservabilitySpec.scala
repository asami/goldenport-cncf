package org.goldenport.cncf.component

import java.util.concurrent.{Executors, TimeUnit}

import org.goldenport.Consequence
import org.goldenport.cncf.assembly.AssemblyReport
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityObservabilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-56-component-identity-observability, example:E1, rules:CID06C-R5,R8, phase:56, slice:CID-06C")
  private val _e2 = afterWord("in spec:phase-56-component-identity-observability, example:E2, rules:CID06C-R5,R8, phase:56, slice:CID-06C")
  private val _e3 = afterWord("in spec:phase-56-component-identity-observability, example:E3, rules:CID06C-R2,R4,R6, phase:56, slice:CID-06C")
  private val _e4 = afterWord("in spec:phase-56-component-identity-observability, example:E4, rules:CID06C-R4,R9, phase:56, slice:CID-06C")
  private val _e5 = afterWord("in spec:phase-56-component-identity-observability, example:E5, rules:CID06C-R3,R4, phase:56, slice:CID-06C")
  private val _e6 = afterWord("in spec:phase-56-component-identity-observability, example:E6, rules:CID06C-R7, phase:56, slice:CID-06C")
  private val _e7 = afterWord("in spec:phase-56-component-identity-observability, example:E7, rules:CID06C-R1,R6, phase:56, slice:CID-06C")
  private val _e8 = afterWord("in spec:phase-56-component-identity-observability, example:E8, rules:CID06C-R3,R4,R6, phase:56, slice:CID-06C")
  private val _e9 = afterWord("in spec:phase-56-component-identity-observability, example:E9, rules:CID06C-R8,R9, phase:56, slice:CID-06C")

  "Phase 56 Component identity observability" should {
    "retain typed admission notices" which {
    "E1 reject an assembly-binding alias without creating a report notice" must _e1 {
      "when a bare assembly alias is presented" in {
        Given("one admitted canonical ComponentId and an empty AssemblyReport")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()

        When("the retired assembly-binding surface receives Catalog")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "Catalog",
          Vector(_candidate(componentid)),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )

        Then("the selector fails closed and the report remains empty")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        report.warnings shouldBe empty
      }
    }

    "E2 reject descriptor-field alias input without creating a report notice" must _e2 {
      "when a legacy descriptor-field selector is presented" in {
        Given("one canonical candidate and an empty report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()

        When("the retired descriptor-field surface receives Catalog")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "Catalog",
          Vector(_candidate(componentid)),
          ComponentIdentityCompatibilityAdapter.Surface.DescriptorField
        )

        Then("no descriptor-field compatibility warning can be observed")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        report.warnings shouldBe empty
      }
    }
    }

    "resolve runtime selector compatibility" which {
    "E3 reject one presentation alias at the runtime selector" must _e3 {
      "when a display or artifact spelling has one canonical candidate" in {
        Given("one admitted alias candidate")
        val componentid = ComponentId("org.example.Catalog")
        When("the runtime selector resolves the artifact spelling")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        Then("the retired runtime selector has no accepted presentation alias")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E4 keep accepted aliases idempotent and canonical selections silent" must _e4 {
      "when the same accepted alias is observed repeatedly" in {
        Given("one unique presentation alias and report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()
        val candidates = Vector(_candidate(componentid, "catalog-ui"))
        When("the alias is resolved and observed twice, then canonical identity is resolved")
        val rejected = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", candidates, ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        val canonical = ComponentIdentityCompatibilityAdapter.resolveAliases(componentid.name, candidates, ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        Then("the rejected alias adds no notice and canonical resolution remains silent")
        rejected shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        report.warnings.size shouldBe 0
        canonical.toConsequence.map(_.notice) shouldBe Consequence.success(None)
      }
    }

    "E5 reject an alias shared by distinct canonical Components" must _e5 {
      "when admitted presentation aliases collide" in {
        Given("two canonical candidates with the same alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("the runtime selector resolves the shared alias")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        Then("the decision is rejected and cannot create an accepted-alias notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E6 advertise only uniquely accepted Help presentation aliases" must _e6 {
      "when Help checks a unique and an ambiguous alias through the adapter" in {
        Given("one unique alias and one ambiguous alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("Help evaluates selector acceptance without observation")
        val unique = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.HelpProjection)
        val ambiguous = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.HelpProjection)
        Then("Help accepts neither unique nor ambiguous presentation aliases")
        unique shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        ambiguous shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E7 reject unknown qualified and malformed Meta selectors without fallback" must _e7 {
      "when dotted selectors are not admitted canonical identities" in {
        Given("one admitted component alias candidate")
        val componentid = ComponentId("org.example.Catalog")
        When("Meta resolves unknown qualified and malformed dotted selectors")
        val unknown = ComponentIdentityCompatibilityAdapter.resolveAliases("org.example.Unknown", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.MetaProjection)
        val malformed = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui.bad", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.MetaProjection)
        Then("both remain rejected rather than falling back to presentation aliases")
        unknown shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        malformed shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E8 select Web paths only for one admitted Component identity" must _e8 {
      "when a Web alias is unique or ambiguous" in {
        Given("one unique and one colliding admitted Web alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("the Web path decision resolves the aliases")
        val unique = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        val ambiguous = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        Then("only the unique decision is accepted")
        unique.toConsequence.map(_.componentid) shouldBe Consequence.success(alpha)
        ambiguous shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }
    }

    "project compatibility warnings through assembly reports" which {
    "E9 expose compatibility warning fields through the existing AssemblyReport" must _e9 {
      "when an accepted runtime alias is observed" in {
        Given("a retained Web-path alias notice and existing assembly report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()
        When("bounded concurrent observers insert the same Web-path warning repeatedly")
        val notice = _adapted_notice("catalog-ui", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        val executor = Executors.newFixedThreadPool(8)
        (1 to 64).foreach { _ =>
          executor.submit(new Runnable {
            override def run(): Unit = ComponentIdentityCompatibilityObserver.observe(report, notice)
          })
        }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS) shouldBe true
        val record = report.toRecord
        Then("the canonical component, surface, alias kind, source alias, and message are exposed once")
        record.getString("status") shouldBe Some("warning")
        record.getInt("warningCount") shouldBe Some(1)
        report.warnings should have size 1
        report.warnings.head.kind shouldBe "component-identity-compatibility"
        report.warnings.head.componentName shouldBe componentid.name
        report.warnings.head.reason.getOrElse("") should include ("surface=web-path")
        report.warnings.head.reason.getOrElse("") should include ("alias-kind=presentation")
        report.warnings.head.reason.getOrElse("") should include ("alias=catalog-ui")
        report.warnings.head.message should include (s"canonical=${componentid.name}")
      }
    }
    }
  }

  private def _candidate(componentid: ComponentId, aliases: String*): ComponentIdentityCompatibilityAdapter.AliasCandidate =
    ComponentIdentityCompatibilityAdapter.AliasCandidate(componentid, aliases.toVector)

  private def _adapted_notice(
    alias: String,
    candidates: Vector[ComponentIdentityCompatibilityAdapter.AliasCandidate],
    surface: ComponentIdentityCompatibilityAdapter.Surface
  ): ComponentIdentityCompatibilityAdapter.Notice =
    ComponentIdentityCompatibilityAdapter.resolveAliases(alias, candidates, surface) match {
      case ComponentIdentityCompatibilityAdapter.Adapted(_, notice) => notice
      case result => throw new IllegalStateException(s"Expected accepted compatibility alias: $result")
    }
}
