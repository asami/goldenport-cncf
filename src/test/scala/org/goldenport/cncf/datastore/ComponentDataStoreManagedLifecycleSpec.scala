package org.goldenport.cncf.datastore

import java.nio.file.Files
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentDataStoreManagedLifecycleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp04, example:$example, rules:DSP04-R1-R4, phase:54, slice:DSP-04")

  "ComponentDataStore managed resolution" when {
    "sharing a SystemNode registry" which {
    "E1 reuse one physical SQL resource across two resident bindings" must _metadata("E1") {
      "preserve non-owning datastore views" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP04-R1-R4; Example: E1")
      val database = Files.createTempDirectory("cncf-dsp04-managed").resolve("component.db")
      val environment = ComponentDataStore.Environment(
        ResolvedParameters.empty(),
        Some(_configuration("textus.component.art-scene.datastores.application.sqlite.path" -> database.toString))
      )
      val request = ComponentDataStore.Request("art-scene")
      val node = SystemNode.create()
      val firstbinding = node.bind()
      val secondbinding = node.bind()
      val firstlease = firstbinding.acquireLeaseC().toOption.get
      val secondlease = secondbinding.acquireLeaseC().toOption.get

      Given("two resident bindings with equal effective component datastore definitions")
      When("both bindings receive their managed datastore views")
      val first = ComponentDataStore.resolveManagedForDataStoreSpaceC(
        environment, request, firstbinding, firstlease, node.hmacKey
      ).toOption.get.get.asInstanceOf[SqlDataStore]
      val resource = first.managedResourceOption.get
      try {
        val second = ComponentDataStore.resolveManagedForDataStoreSpaceC(
          environment, request, secondbinding, secondlease, node.hmacKey
        ).toOption.get.get.asInstanceOf[SqlDataStore]
        first.closeC()

        Then("they share one node-owned resource and neither view closes it")
        (first.managedResourceOption.get eq second.managedResourceOption.get) shouldBe true
        resource.isOpen shouldBe true
      } finally {
        firstlease.release()
        secondlease.release()
        resource.closeC()
      }
      }
    }

    "E2 keep the legacy application alias on the same canonical managed resource" must _metadata("E2") {
      "normalize selection provenance away from physical ownership" in {
      info("Spec: docs/phase/phase-54.md; Rules: DSP04-R1-R4; Example: E2")
      val database = Files.createTempDirectory("cncf-dsp04-managed-alias").resolve("component.db")
      val canonicalenvironment = ComponentDataStore.Environment(
        ResolvedParameters.empty(),
        Some(_configuration("textus.component.art-scene.datastores.application.sqlite.path" -> database.toString))
      )
      val legacyenvironment = ComponentDataStore.Environment(
        ResolvedParameters.empty(),
        Some(_configuration("textus.component.art-scene.datastore.sqlite.path" -> database.toString))
      )
      val request = ComponentDataStore.Request("art-scene")
      val node = SystemNode.create()
      val canonicalbinding = node.bind()
      val legacybinding = node.bind()
      val canonicallease = canonicalbinding.acquireLeaseC().toOption.get
      val legacylease = legacybinding.acquireLeaseC().toOption.get

      Given("canonical and legacy application aliases for one SQLite target")
      When("each resident binding resolves the effective definition")
      val canonical = ComponentDataStore.resolveManagedForDataStoreSpaceC(
        canonicalenvironment, request, canonicalbinding, canonicallease, node.hmacKey
      ).toOption.get.get.asInstanceOf[SqlDataStore]
      val resource = canonical.managedResourceOption.get

      try {
        val legacy = ComponentDataStore.resolveManagedForDataStoreSpaceC(
          legacyenvironment, request, legacybinding, legacylease, node.hmacKey
        ).toOption.get.get.asInstanceOf[SqlDataStore]
        Then("selection aliases do not create a duplicate physical resource")
        (canonical.managedResourceOption.get eq legacy.managedResourceOption.get) shouldBe true
      } finally {
        canonicallease.release()
        legacylease.release()
        resource.closeC()
      }
      }
    }
    }
  }

  private def _configuration(values: (String, String)*): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.map { case (key, value) => key -> ConfigurationValue.StringValue(value) }.toMap),
      ConfigurationTrace.empty
    )
}
