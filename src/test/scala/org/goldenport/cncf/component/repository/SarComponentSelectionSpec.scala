package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentId}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class SarComponentSelectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:sar-component-selection, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")

  "SarComponentSelection" should {
    "preserve legacy and canonical SAR discovery semantics" which {
      "E1 preserve first occurrence ordering with legacy name deduplication for an empty request" must _metadata("E1") {
        "when exercising: select an empty request over duplicate discovered participant names" in {
          Given("two discovered participants with one duplicate name")
          val owner = ComponentId("org.example.Bundle")
          val first = _component(ComponentId("org.example.First"), owner, "1.0.0")
          val duplicate = _component(ComponentId("org.example.First"), owner, "2.0.0")
          val last = _component(ComponentId("org.example.Last"), owner, "1.0.0")

          When("no canonical descriptor is requested")
          val selected = SarComponentSelection.select(Vector(first, duplicate, last), Vector.empty)

          Then("the first occurrence of each legacy participant name is retained in discovery order")
          selected shouldBe Vector(first, last)
        }
      }

      "E2 fail closed when any requested descriptor is malformed" must _metadata("E2") {
        "when exercising: select a valid descriptor together with a malformed descriptor" in {
          Given("one discovered canonical participant and one malformed requested descriptor")
          val owner = ComponentId("org.example.Bundle")
          val discovered = Vector(_component(owner, owner, "1.0.0"))
          val malformed = ComponentDescriptor(
            name = Some("org.example.Malformed"),
            version = Some("1.0.0"),
            schemaVersion = Some(3)
          )

          When("the mixed request is selected")
          val selected = SarComponentSelection.select(
            discovered,
            Vector(_descriptor(owner, "1.0.0"), malformed)
          )

          Then("the entire request produces no selected participants")
          selected shouldBe Vector.empty
        }
      }

      "E3 return no participant when the requested owner release differs" must _metadata("E3") {
        "when exercising: select a valid owner at a nonmatching release" in {
          Given("one discovered owner participant at release 1.0.0")
          val owner = ComponentId("org.example.Bundle")
          val discovered = Vector(_component(owner, owner, "1.0.0"))

          When("the owner is requested at release 2.0.0")
          val selected = SarComponentSelection.select(discovered, Vector(_descriptor(owner, "2.0.0")))

          Then("no participant is selected")
          selected shouldBe Vector.empty
        }
      }

      "E4 retain primary and differently identified componentlet participants owned by one CAR" must _metadata("E4") {
        "when exercising: select an owner coordinate shared by primary and componentlet participants" in {
          Given("a primary and a componentlet with distinct Core IDs but one artifact owner coordinate")
          val owner = ComponentId("org.example.Bundle")
          val primary = _component(owner, owner, "1.0.0")
          val componentlet = _component(ComponentId("org.example.BundleAdmin"), owner, "1.0.0")

          When("the CAR owner coordinate is requested")
          val selected = SarComponentSelection.select(
            Vector(primary, componentlet),
            Vector(_descriptor(owner, "1.0.0"))
          )

          Then("both participants remain in discovery order")
          selected shouldBe Vector(primary, componentlet)
        }
      }

      "E5 retain duplicate exact primaries for downstream ambiguity rejection" must _metadata("E5") {
        "when exercising: select two exact primary candidates from one requested owner coordinate" in {
          Given("two discovered primary candidates with the same exact Core and artifact owner identity")
          val owner = ComponentId("org.example.Bundle")
          val first = _component(owner, owner, "1.0.0")
          val second = _component(owner, owner, "1.0.0")

          When("the canonical owner coordinate is requested")
          val selected = SarComponentSelection.select(
            Vector(first, second),
            Vector(_descriptor(owner, "1.0.0"))
          )

          Then("both exact candidates remain visible in discovery order")
          selected shouldBe Vector(first, second)
        }
      }
    }
  }

  private def _component(
    coreid: ComponentId,
    ownerid: ComponentId,
    release: String
  ): Component =
    TestComponentFactory.create(coreid.name, Protocol.empty).withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = ownerid.name,
        version = release,
        componentId = Some(ownerid)
      )
    )

  private def _descriptor(
    componentid: ComponentId,
    release: String
  ): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some(release),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )
}
