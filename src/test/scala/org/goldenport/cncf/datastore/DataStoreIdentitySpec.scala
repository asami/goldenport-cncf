package org.goldenport.cncf.datastore

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.sql.SqlDataStoreIdentity
import org.goldenport.cncf.datastore.sql.SqlDataStoreIdentity.{Credential, HmacKey, Ownership, Provenance}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class DataStoreIdentitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _key = HmacKey("phase-54-dsp02-test-key".getBytes(StandardCharsets.UTF_8))
  private val _credential = Credential.reference("catalog", "v1")

  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp02, example:$example, rules:DSP02-R8-R16, phase:54, slice:DSP-02")

  "Managed datastore identity" when {
    "E1 normalize equivalent SQLite paths without resolving symlinks" must _metadata("E1") {
      "make relative and absolute spellings equal" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E1")
        val relative = SqlDataStoreIdentity.sqliteC("target/../target/dsp02.db", _credential, _key)
        val absolute = SqlDataStoreIdentity.sqliteC(java.nio.file.Path.of("target/dsp02.db").toAbsolutePath.toString, _credential, _key)

        When("the two filesystem paths are canonicalized")
        val identities = Vector(relative.toOption, absolute.toOption).flatten

        Then("they identify the same managed datastore")
        identities.size shouldBe 2
        identities.head shouldBe identities(1)
      }
    }

    "E2 preserve named shared SQLite memory and reject private memory" must _metadata("E2") {
      "require a stable shared-memory name" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E2")
        val shared = SqlDataStoreIdentity.sqliteC("file:node-cache?mode=memory&cache=shared", _credential, _key)
        val privatememory = SqlDataStoreIdentity.sqliteC(":memory:", _credential, _key)

        When("managed SQLite identities are constructed")
        val sharedidentity = shared.toOption

        Then("only the named shared-memory target is admitted")
        sharedidentity should not be empty
        privatememory.toOption shouldBe None
      }
    }

    "E3 normalize MySQL, MariaDB, and PostgreSQL standard cloud endpoints" must _metadata("E3") {
      "ignore host case default ports and query ordering" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E3")
        val postgresone = _jdbc("jdbc:postgresql://DB.EXAMPLE.RDS.AMAZONAWS.COM/catalog?sslmode=require&currentSchema=app")
        val postgrestwo = _jdbc("jdbc:postgresql://db.example.rds.amazonaws.com:5432/catalog?currentSchema=app&sslmode=require")
        val mysql = _jdbc("jdbc:mysql://10.10.0.4/app?useSSL=true")
        val mariadb = _jdbc("jdbc:mariadb://cloudsql.example.internal/app?useSSL=true")

        When("AWS RDS and Google Cloud SQL style endpoints are structurally parsed")
        val parsed = Vector(postgresone, postgrestwo, mysql, mariadb).map(_.toOption)

        Then("PostgreSQL spellings share identity and each admitted dialect has an identity")
        parsed.flatten.size shouldBe 4
        parsed(0) shouldBe parsed(1)
        parsed(2) should not be parsed(3)
      }
    }

    "E4 distinguish credential rotation and ignore logical provenance" must _metadata("E4") {
      "separate identity fields from binding metadata" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E4")
        val first = SqlDataStoreIdentity.jdbcC("jdbc:postgresql://db.example/catalog", Credential.reference("catalog", "v1"), _key, provenance = Provenance(Some("orders"), Some("component-a")))
        val same = SqlDataStoreIdentity.jdbcC("jdbc:postgresql://db.example/catalog", Credential.reference("catalog", "v1"), _key, provenance = Provenance(Some("orders-copy"), Some("component-b")))
        val rotated = SqlDataStoreIdentity.jdbcC("jdbc:postgresql://db.example/catalog", Credential.reference("catalog", "v2"), _key)

        When("only provenance or credential version changes")
        val values = Vector(first.toOption, same.toOption, rotated.toOption).flatten

        Then("provenance is excluded while credential rotation changes canonical identity")
        values(0) shouldBe values(1)
        values(0) should not be values(2)
      }
    }

    "E5 redact raw credentials HMAC keys and forbidden URL material" must _metadata("E5") {
      "never retain secrets in observable identity output" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E5")
        val secret = "p@ssw0rd-token"
        val raw = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", secret, _key)
        val userinfo = SqlDataStoreIdentity.jdbcC(s"jdbc:postgresql://alice:$secret@db.example/catalog", _credential, _key)
        val parameter = SqlDataStoreIdentity.jdbcC(s"jdbc:mysql://db.example/catalog?password=$secret", _credential, _key)

        When("raw and prohibited credential inputs are handled")
        val rendered = raw.toOption.map(_.toString).getOrElse("")
        val failures = Vector(userinfo, parameter).collect { case Consequence.Failure(conclusion) => conclusion.display }

        Then("successful identities and structured failures omit the secret")
        rendered should not include secret
        _key.toString should not include secret
        failures.foreach(_ should not include secret)
      }
    }

    "E6 reject deferred dialects and preserve deterministic ordering" must _metadata("E6") {
      "not use raw JDBC text as a fallback identity" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E6")
        val sqlserver = _jdbc("jdbc:sqlserver://db.example;databaseName=catalog")
        val first = _jdbc("jdbc:postgresql://db-a.example/catalog")
        val second = _jdbc("jdbc:postgresql://db-b.example/catalog")

        When("unsupported and supported targets are compared")
        val ordered = Vector(first.toOption.get, second.toOption.get).sorted

        Then("the deferred dialect fails structurally and sorting is insertion independent")
        sqlserver.toOption shouldBe None
        ordered shouldBe Vector(second.toOption.get, first.toOption.get).sorted
      }
    }

    "E7 make raw-credential fingerprints stable per injected key and rotate safely" must _metadata("E7") {
      "separate raw credential and key rotations" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E7")
        val otherkey = HmacKey("phase-54-dsp02-other-key".getBytes(StandardCharsets.UTF_8))
        val stableone = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", "one", _key)
        val stabletwo = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", "one", _key)
        val rotatedcredential = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", "two", _key)
        val rotatedkey = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", "one", otherkey)

        When("the explicit key or supplied credential rotates")
        val values = Vector(stableone, stabletwo, rotatedcredential, rotatedkey).map(_.toOption.get)

        Then("only equal credential material under one injected key reuses the identity")
        values(0) shouldBe values(1)
        values(0) should not be values(2)
        values(0) should not be values(3)
      }
    }

    "E8 retain safe SQLite URI properties and verify canonical properties" must _metadata("E8") {
      "distinguish URI configuration while accepting reordered equivalent spelling" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E8")
        val first = SqlDataStoreIdentity.sqliteC("file:node-cache?mode=memory&cache=shared&busy_timeout=1&foreign_keys=on", _credential, _key)
        val same = SqlDataStoreIdentity.sqliteC("file:node-cache?foreign_keys=on&busy_timeout=1&cache=shared&mode=memory", _credential, _key)
        val different = SqlDataStoreIdentity.sqliteC("file:node-cache?mode=memory&cache=shared&busy_timeout=2&foreign_keys=on", _credential, _key)
        val secretparameter = SqlDataStoreIdentity.sqliteC("file:node-cache?mode=memory&cache=shared&password=unsafe", _credential, _key)

        When("shared-memory URI properties are structurally normalized")
        val identities = Vector(first, same, different).map(_.toOption.get)

        Then("safe configuration contributes to equality and credential material is rejected")
        identities(0) shouldBe identities(1)
        identities(0) should not be identities(2)
        secretparameter.toOption shouldBe None
      }
    }

    "E9 property-check canonical equivalence non-collision and raw-credential byte fidelity" must _metadata("E9") {
      "exercise generated PostgreSQL targets and whitespace-distinct credentials" in {
        Given("Spec: docs/phase/phase-54.md; Rules: DSP02-R8-R16; Example: E9")
        val names = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)
        val property = Prop.forAll(names, names) { (hostpart, database) =>
          val first = SqlDataStoreIdentity.jdbcC(s"jdbc:postgresql://${hostpart.toUpperCase}.example/$database?sslmode=require&currentSchema=app", _credential, _key)
          val second = SqlDataStoreIdentity.jdbcC(s"jdbc:postgresql://$hostpart.example:5432/$database?currentSchema=app&sslmode=require", _credential, _key)
          val distinct = SqlDataStoreIdentity.jdbcC(s"jdbc:postgresql://$hostpart.example:5432/${database}x?currentSchema=app&sslmode=require", _credential, _key)
          first.toOption == second.toOption && first.toOption != distinct.toOption
        }
        val redactionproperty = Prop.forAll(names) { suffix =>
          val secret = s"token-$suffix"
          val identity = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", secret, _key)
          val rejected = SqlDataStoreIdentity.jdbcC(s"jdbc:postgresql://db.example/catalog?password=$secret", _credential, _key)
          identity.toOption.exists(identity => !identity.toString.contains(secret)) &&
            (rejected match {
              case Consequence.Failure(conclusion) => !conclusion.display.contains(secret)
              case Consequence.Success(_) => false
            })
        }
        val rawone = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", "secret", _key)
        val rawspaced = SqlDataStoreIdentity.jdbcRawC("jdbc:postgresql://db.example/catalog", " secret ", _key)

        When("generated equivalent and distinct canonical inputs are evaluated")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(25), property)
        val redactionchecked = Test.check(Test.Parameters.default.withMinSuccessfulTests(25), redactionproperty)

        Then("equivalence, non-collision, and byte-sensitive raw credential rotation hold")
        checked.passed shouldBe true
        redactionchecked.passed shouldBe true
        rawone.toOption should not be rawspaced.toOption
      }
    }
  }

  private def _jdbc(url: String): Consequence[SqlDataStoreIdentity] =
    SqlDataStoreIdentity.jdbcC(url, _credential, _key, ownership = Ownership.Managed)
}
