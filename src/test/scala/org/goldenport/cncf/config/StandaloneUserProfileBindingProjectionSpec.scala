package org.goldenport.cncf.config

import java.time.ZoneId
import java.nio.file.Path
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingResolver, ConfigurationOrigin}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class StandaloneUserProfileBindingProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-gcf07e-standalone-user-profile-catalog-projection, example:E1, rules:GCF07E-R1,R2,R3, phase:55, slice:GCF-07E")
  private val _e2 = afterWord("in spec:phase-55-gcf07e-standalone-user-profile-catalog-projection, example:E2, rules:GCF07E-R1,R4, phase:55, slice:GCF-07E")
  private val _e3 = afterWord("in spec:phase-55-gcf07e-standalone-user-profile-catalog-projection, example:E3, rules:GCF07E-R2,R3, phase:55, slice:GCF-07E")

  "StandaloneUserProfile binding projection" should {
    "E1 project admitted HOME documents without rereading and resolve layer/target precedence" must _e1 {
      "when Textus common values, a Textus subsystem override, and a CNCF common override are admitted" in {
        Given("two already-parsed HOME documents for one stable Subsystem")
        val subsystem = SubsystemInstanceId.create("shop", "default").getOrElse(fail("identity"))
        val home = Path.of("gcf07e-home").toAbsolutePath.normalize
        val admitted = Vector(
          StandaloneUserProfileResolver.Admitted(
            StandaloneUserProfileResolver.Layer.TextusHome, home.resolve(".textus/user-profile.yaml"), ConfigurationOrigin.Home,
            StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(id = Some("textus"), displayName = Some("Textus"), locale = Some("ja-JP"), timezone = Some("Asia/Tokyo"))), Map("shop" -> StandaloneUserProfile.User(id = Some("local"))))),
          StandaloneUserProfileResolver.Admitted(
            StandaloneUserProfileResolver.Layer.CncfHome, home.resolve(".cncf/user-profile.yaml"), ConfigurationOrigin.Home,
            StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(displayName = Some("CNCF"), locale = Some("en-US"), timezone = Some("UTC"))))
          )
        )

        When("the projection creates candidates and the existing generic resolver resolves them")
        val candidates = StandaloneUserProfileBindingProjection.candidates(admitted, subsystem).getOrElse(fail("candidates"))
        val context = CncfConfigurationResolutionContext.forSubsystem(subsystem).getOrElse(fail("context"))
        val resolved = ConfigurationBindingResolver.resolve(candidates, context.generic).getOrElse(fail("resolved"))

        Then("selected Subsystem overrides same-document Global and later CNCF overrides Textus per field")
        resolved.value(CncfConfigurationParameterCatalog.fixedUserId).toOption.flatten shouldBe Some("local")
        resolved.value(CncfConfigurationParameterCatalog.fixedUserDisplayName).toOption.flatten shouldBe Some("CNCF")
        resolved.value(CncfConfigurationParameterCatalog.fixedUserLocale).toOption.flatten shouldBe Some(Locale.forLanguageTag("en-US"))
        resolved.value(CncfConfigurationParameterCatalog.fixedUserTimezone).toOption.flatten shouldBe Some(ZoneId.of("UTC"))
        val id = resolved.binding(CncfConfigurationParameterCatalog.fixedUserId).toOption.flatten.getOrElse(fail("id binding"))
        val locale = resolved.binding(CncfConfigurationParameterCatalog.fixedUserLocale).toOption.flatten.getOrElse(fail("locale binding"))
        val timezone = resolved.binding(CncfConfigurationParameterCatalog.fixedUserTimezone).toOption.flatten.getOrElse(fail("timezone binding"))
        id.provenance.inputPath shouldBe Some("subsystems.shop.user.id")
        id.target shouldBe CncfConfigurationTarget.SubsystemInstance.create(subsystem).getOrElse(fail("local target"))
        locale.target shouldBe CncfConfigurationTarget.Global
        locale.provenance.layer shouldBe "cncf"
        locale.provenance.origin shouldBe ConfigurationOrigin.Home
        locale.provenance.sourceType shouldBe Some("file")
        locale.provenance.sourceRank shouldBe 11
        locale.provenance.sourceOrdinal shouldBe 1
        locale.provenance.evidence shouldBe Vector("phase-53: StandaloneUserProfile HOME admission")
        timezone.provenance.inputPath shouldBe Some("user.timezone")
      }
    }

    "E2 reject forged, malformed, mismatched, duplicate, and reversed admissions" must _e2 {
      "when a caller bypasses the resolver's canonical admission contract" in {
        Given("one selected Subsystem identity and forged profile admissions")
        val subsystem = SubsystemInstanceId.create("shop", "default").getOrElse(fail("identity"))
        val document = StandaloneUserProfile.Document()
        val project = Vector(StandaloneUserProfileResolver.Admitted(
          StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Project, document
        ))
        val mismatch = Vector(StandaloneUserProfileResolver.Admitted(
          StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".cncf/user-profile.yaml"), ConfigurationOrigin.Home, document
        ))
        val reversed = Vector(
          StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.CncfHome, Path.of(".cncf/user-profile.yaml"), ConfigurationOrigin.Home, document),
          StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Home, document)
        )
        val duplicate = Vector(
          StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Home, document),
          StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Home, document)
        )
        val nulllayer = Vector(StandaloneUserProfileResolver.Admitted(null, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Home, document))
        val rootpath = Vector(StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.TextusHome, Path.of("/"), ConfigurationOrigin.Home, document))
        val nulldocument = Vector(StandaloneUserProfileResolver.Admitted(StandaloneUserProfileResolver.Layer.TextusHome, Path.of(".textus/user-profile.yaml"), ConfigurationOrigin.Home, null))
        val nullfields = Vector(StandaloneUserProfileResolver.Admitted(
          StandaloneUserProfileResolver.Layer.TextusHome,
          Path.of(".textus/user-profile.yaml"),
          ConfigurationOrigin.Home,
          StandaloneUserProfile.Document(null, null)
        ))

        When("the projection validates its public admission inputs")
        val results = Vector(project, mismatch, reversed, duplicate, nulllayer, rootpath, nulldocument, nullfields).map(
          StandaloneUserProfileBindingProjection.candidates(_, subsystem)
        )

        Then("each forged source contract fails structurally before candidate construction")
        results.foreach(_ shouldBe a[Consequence.Failure[_]])
      }
    }

    "E3 keep the profile-only catalog separate from the closed runtime catalog" must _e3 {
      "when profile witness identities and codecs are inspected" in {
        Given("the closed runtime catalog and the standalone profile-only catalog")

        When("the profile witnesses and their strict value codecs are resolved")
        val profileids = CncfConfigurationParameterCatalog.standaloneUserProfile.definitions.map(_.parameterId.value)
        val closedprofile = CncfConfigurationParameterCatalog.closed.schema.definition(CncfConfigurationParameterCatalog.FIXED_USER_ID_KEY)

        Then("all four profile keys remain absent from runtime catalog admission and retain typed codecs")
        profileids shouldBe Vector(
          CncfConfigurationParameterCatalog.FIXED_USER_ID_KEY,
          CncfConfigurationParameterCatalog.FIXED_USER_DISPLAY_NAME_KEY,
          CncfConfigurationParameterCatalog.FIXED_USER_LOCALE_KEY,
          CncfConfigurationParameterCatalog.FIXED_USER_TIMEZONE_KEY
        )
        closedprofile.isSuccess shouldBe false
        CncfConfigurationParameterCatalog.fixedUserLocale.codec.decode(org.goldenport.configuration.ConfigurationValue.StringValue("en-US")).toOption shouldBe Some(Locale.forLanguageTag("en-US"))
        CncfConfigurationParameterCatalog.fixedUserLocale.codec.decode(org.goldenport.configuration.ConfigurationValue.StringValue("und")).isSuccess shouldBe false
        CncfConfigurationParameterCatalog.fixedUserLocale.codec.decode(org.goldenport.configuration.ConfigurationValue.StringValue("en-US-")).isSuccess shouldBe false
        CncfConfigurationParameterCatalog.fixedUserTimezone.codec.decode(org.goldenport.configuration.ConfigurationValue.StringValue("Asia/Tokyo")).toOption shouldBe Some(ZoneId.of("Asia/Tokyo"))
        CncfConfigurationParameterCatalog.fixedUserTimezone.codec.decode(org.goldenport.configuration.ConfigurationValue.StringValue("not/a-timezone")).isSuccess shouldBe false
      }
    }
  }
}
