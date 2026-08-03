package org.goldenport.cncf.subsystem

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.config.{CncfConfigurationTarget, CncfRuntimeConfigurationProjection, SubsystemInstanceId}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationOrigin, ConfigurationResolver, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.file.FileConfigLoader
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemRuntimeCatalogCollectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(example: String, rules: String) = afterWord(
    s"in spec:phase-55-gcf07d-runtime-catalog-collection-authority, example:$example, rules:$rules, phase:55, slice:GCF-07D"
  )

  "Subsystem runtime catalog collection authority" should {
    "E1 keep an admitted empty collection authoritative over a later legacy value" must _metadata("E1", "GCF07D-R1,R2") {
      "when the compatibility configuration has multi-user but the snapshot collection has no catalog key" in {
        Given("a fixed-user Subsystem and an empty authoritative runtime collection")
        val subsystem = _fixed_subsystem("multi-user")
        val empty = ConfigurationBindingCollection.empty[CncfConfigurationTarget]

        When("the collection is admitted before the first user-mode evaluation")
        subsystem.admitRuntimeConfigurationBindingsC(empty).isSuccess shouldBe true
        val result = subsystem.subsystemUserModeC

        Then("the absent snapshot key follows the existing direct admission rule and does not revive multi-user")
        result shouldBe a[Consequence.Failure[_]]
      }
    }

    "E2 reject duplicate and late runtime collection admission" must _metadata("E2", "GCF07D-R1,R3") {
      "when a collection is replaced before or after the first user-mode evaluation" in {
        Given("two fixed-user Subsystems and one empty collection")
        val empty = ConfigurationBindingCollection.empty[CncfConfigurationTarget]
        val duplicate = _fixed_subsystem("standalone")
        val late = _fixed_subsystem("standalone")

        When("the first collection is admitted")
        duplicate.admitRuntimeConfigurationBindingsC(empty).isSuccess shouldBe true

        Then("a second admission fails structurally")
        duplicate.admitRuntimeConfigurationBindingsC(empty) shouldBe a[Consequence.Failure[_]]

        When("user-mode evaluation occurs before any admission")
        late.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)

        Then("a late collection admission fails structurally")
        late.admitRuntimeConfigurationBindingsC(empty) shouldBe a[Consequence.Failure[_]]
      }
    }

    "E3 use the admitted catalog collection instead of a disagreeing legacy value" must _metadata("E3", "GCF07D-R1,R2,R4") {
      "when the retained snapshot says standalone and the compatibility view says multi-user" in {
        Given("one fixed-user Subsystem, an incompatible legacy value, and a snapshot-derived collection")
        val source = ConfigurationSource.File(
          ConfigurationOrigin.Cwd,
          Path.of(".textus", "config.conf"),
          ConfigurationSource.Rank.Cwd,
          new FileConfigLoader {
            override def load(path: Path): Consequence[Configuration] =
              Consequence.success(Configuration(Map(
                SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone")
              )))
          }
        )
        val snapshot = ConfigurationResolver.default.resolveSnapshot(Vector(source)).getOrElse(fail("snapshot is required"))
        val identity = SubsystemInstanceId.default("gcf07d-runtime").getOrElse(fail("identity is required"))
        val collection = CncfRuntimeConfigurationProjection.forSubsystem(snapshot, identity).getOrElse(fail("collection is required"))
        val subsystem = _fixed_subsystem("multi-user")

        When("the snapshot-derived collection is admitted before evaluation")
        subsystem.admitRuntimeConfigurationBindingsC(collection).isSuccess shouldBe true

        Then("the exact typed witness selects standalone rather than the legacy multi-user value")
        subsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
      }
    }

    "E4 preserve security validation for typed collection bindings" must _metadata("E4", "GCF07D-R2,R4") {
      "when fixed and authenticated Subsystems receive matching or mismatched typed user-mode bindings" in {
        Given("one collection for each mode and fixed/authenticated Subsystems")
        val standalone = _collection("standalone")
        val multiuser = _collection("multi-user")
        val fixedstandalone = _subsystem("multi-user", authenticated = false)
        val authenticatedmulti = _subsystem("standalone", authenticated = true)
        val fixedmulti = _subsystem("standalone", authenticated = false)
        val authenticatedstandalone = _subsystem("multi-user", authenticated = true)

        When("each typed collection is admitted before evaluation")
        Vector(
          fixedstandalone -> standalone,
          authenticatedmulti -> multiuser,
          fixedmulti -> multiuser,
          authenticatedstandalone -> standalone
        ).foreach { case (subsystem, collection) =>
          subsystem.admitRuntimeConfigurationBindingsC(collection).isSuccess shouldBe true
        }

        Then("matching security wiring succeeds and both mismatches fail closed")
        fixedstandalone.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
        authenticatedmulti.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.MultiUser)
        fixedmulti.subsystemUserModeC shouldBe a[Consequence.Failure[_]]
        authenticatedstandalone.subsystemUserModeC shouldBe a[Consequence.Failure[_]]
      }
    }

  }

  private def _collection(value: String): ConfigurationBindingCollection[CncfConfigurationTarget] = {
    val source = ConfigurationSource.File(
      ConfigurationOrigin.Cwd,
      Path.of(".textus", s"$value.conf"),
      ConfigurationSource.Rank.Cwd,
      new FileConfigLoader {
        override def load(path: Path): Consequence[Configuration] =
          Consequence.success(Configuration(Map(
            SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue(value)
          )))
      }
    )
    val snapshot = ConfigurationResolver.default.resolveSnapshot(Vector(source)).getOrElse(fail("snapshot is required"))
    val identity = SubsystemInstanceId.default("gcf07d-runtime").getOrElse(fail("identity is required"))
    CncfRuntimeConfigurationProjection.forSubsystem(snapshot, identity).getOrElse(fail("collection is required"))
  }

  private def _fixed_subsystem(value: String): Subsystem =
    _subsystem(value, authenticated = false)

  private def _subsystem(value: String, authenticated: Boolean): Subsystem =
    Subsystem(
      "gcf07d-runtime",
      configuration = ResolvedConfiguration(
        Configuration(Map(SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue(value))),
        ConfigurationTrace.empty
      )
    ).withDescriptor(
      GenericSubsystemDescriptor(
        path = Path.of("gcf07d-runtime.car"),
        subsystemName = "gcf07d-runtime",
        security = Some(GenericSubsystemSecurityBinding(authentication = Some(
          if (authenticated)
            GenericSubsystemAuthenticationBinding(providers = Vector(
              GenericSubsystemAuthenticationProviderBinding(
                "gcf07d-runtime",
                "gcf07d-runtime",
                enabled = Some(true)
              )
            ))
          else
            GenericSubsystemAuthenticationBinding(
              localSubject = Some(GenericSubsystemLocalSubjectBinding("gcf07d-runtime"))
            )
        )))
      )
    )
}
