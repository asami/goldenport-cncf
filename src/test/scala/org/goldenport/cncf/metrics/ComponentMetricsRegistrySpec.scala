package org.goldenport.cncf.metrics

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentMetricsRegistrySpec extends AnyWordSpec with Matchers {
  "ComponentMetricsRegistry" should {
    "aggregate only contract-approved bounded metric series" in {
      val definition = ComponentMetricDefinition(
        "semantic.provider.request",
        Vector("component", "provider", "outcome"),
        maxSeries = 1
      )
      val registry = ComponentMetricsRegistry.create()

      registry.record(definition, Map("component" -> "textus-sie", "provider" -> "Fuseki", "outcome" -> "success"), durationmillis = Some(12L)) shouldBe true
      registry.record(definition, Map("component" -> "textus-sie", "provider" -> "fuseki", "outcome" -> "success"), error = true, durationmillis = Some(8L)) shouldBe true
      registry.record(definition, Map("component" -> "textus-sie", "provider" -> "chroma", "outcome" -> "success")) shouldBe false
      registry.record(definition, Map("component" -> "textus-sie", "provider" -> "fuseki", "query" -> "secret text")) shouldBe false

      val entry = registry.snapshot().head
      entry.name shouldBe "semantic.provider.request"
      entry.labels shouldBe Map("component" -> "textus-sie", "provider" -> "fuseki", "outcome" -> "success")
      entry.count shouldBe 2L
      entry.errorCount shouldBe 1L
      entry.durationCount shouldBe 2L
      entry.durationTotalMillis shouldBe 20L
      entry.durationMinMillis shouldBe Some(8L)
      entry.durationMaxMillis shouldBe Some(12L)
    }

    "require component identity in every metric declaration" in {
      an[IllegalArgumentException] shouldBe thrownBy {
        ComponentMetricDefinition("semantic.provider.request", Vector("provider", "outcome"))
      }
    }
  }
}
