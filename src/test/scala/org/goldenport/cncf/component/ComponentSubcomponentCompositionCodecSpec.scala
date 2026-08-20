package org.goldenport.cncf.component

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Failing-first executable acceptance specification for
 * RSC02-IDENTITY-CODEC (Phase 58.1 / RSC-02 / RSC02-A).
 *
 * The wire vocabulary used here is deliberately kept inside the successor
 * boundary.  The schema identity and codec entry points are the only
 * production vocabulary frozen by RSC01-B for this pass.
 *
 * @since   Aug. 20, 2026
 * @version Aug. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentSubcomponentCompositionCodecSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _schema = "cncf.component-subcomponent-composition.v1"
  private val _parent_id = "org.goldenport.cncf.phase58.RscParent"
  private val _child_id = "org.goldenport.cncf.phase58.RscDocumentation"
  private val _source_id = "org.goldenport.cncf.phase58.RscSourceCode"
  private val _release = "0.1.0-SNAPSHOT"
  private val _logical_resource = "urn:cncf:resource:phase58/documentation-guide"
  private val _logical_path = "logical/documentation-guide"
  private val _physical_path = "repository/components/rsc-documentation-0.1.0.car"
  private val _artifact_coordinate = "org.example:rsc-documentation-car:0.1.0"
  private val _parent_physical_path = "repository/components/rsc-parent-0.1.0.car"
  private val _parent_artifact_coordinate = "org.example:rsc-parent-car:0.1.0"
  private val _repository = "https://repo.example.invalid/cncf/rsc"
  private val _sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  private val _signature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="

  private def _car_json(
    classification: String,
    artifactcoordinate: String,
    physicalpath: String,
    logicalsource: String,
    sha256: String = _sha256,
    signature: String = _signature,
    repository: String = _repository
  ): String =
    s"""{
       |        "classification":"$classification",
       |        "artifact":{
       |          "coordinate":"$artifactcoordinate",
       |          "sha256":"$sha256",
       |          "signature":"$signature",
       |          "repository":"$repository",
       |          "physicalPath":"$physicalpath"
       |        },
       |        "provenance":{
       |          "logicalSource":"$logicalsource",
       |          "physicalSource":"repository:rsc",
       |          "physicalPath":"$physicalpath"
       |        }
       |      }""".stripMargin

  private def _parent_car_json: String =
    _car_json(
      classification = "primary",
      artifactcoordinate = _parent_artifact_coordinate,
      physicalpath = _parent_physical_path,
      logicalsource = "composition-registry:rsc-parent"
    )

  private def _member_json(
    componentid: String = _child_id,
    logicalrelease: String = _release,
    required: Boolean = true,
    role: String = "Documentation",
    implementationtechnology: String = "Markdown",
    logicalresource: String = _logical_resource,
    logicalpath: String = _logical_path,
    artifactcoordinate: String = _artifact_coordinate,
    physicalpath: String = _physical_path,
    repository: String = _repository,
    sha256: String = _sha256,
    signature: String = _signature,
    deploymentplatform: String = "documentation-delivery",
    deploymentmode: String = "external",
    requiresexplicitplatformaction: Boolean = true,
    activationauthority: Boolean = false,
    operationauthority: Boolean = false,
    mcpauthority: Boolean = false,
    disclosureauthority: Boolean = false,
    deploymentauthority: Boolean = false
  ): String =
    s"""{
       |      "componentId":"$componentid",
       |      "logicalRelease":"$logicalrelease",
       |      "required":$required,
       |      "role":"$role",
       |      "implementationTechnology":"$implementationtechnology",
       |      "logicalResource":"$logicalresource",
       |      "logicalPath":"$logicalpath",
       |      "subcomponentCar":${_car_json(
         "subcomponent",
         artifactcoordinate,
         physicalpath,
         "composition-registry:rsc-documentation",
         sha256,
         signature,
         repository
       )},
       |      "payload":{
       |        "authoritative":false,
       |        "executable":false
       |      },
       |      "authorization":{"state":"not-granted"},
       |      "integrity":{"state":"verified"},
       |      "availability":{"state":"available"},
       |      "deployment":{
       |        "platform":"$deploymentplatform",
       |        "mode":"$deploymentmode",
       |        "requiresExplicitPlatformAction":$requiresexplicitplatformaction,
       |        "authority":{
       |          "activation":$activationauthority,
       |          "operation":$operationauthority,
       |          "mcp":$mcpauthority,
       |          "disclosure":$disclosureauthority,
       |          "deployment":$deploymentauthority
       |        }
       |      },
       |      "access":{"visibility":"described"},
       |      "disclosure":{"mode":"metadata-only"},
       |      "license":{"spdx":"Apache-2.0"},
       |      "media":{"type":"text/markdown"},
       |      "profile":{"id":"parent-documentation-source"}
       |    }""".stripMargin

  private def _composition_json(members: String*): String =
    s"""{
       |  "schema":"$_schema",
       |  "membershipKind":"parent-child",
       |  "parent":{
       |    "componentId":"$_parent_id",
       |    "logicalRelease":"$_release",
       |    "primaryCar":$_parent_car_json
       |  },
       |  "members":[
       |    ${members.mkString(",\n    ")}
       |  ]
       |}""".stripMargin

  private def _decoded(json: String) =
    ComponentSubcomponentCompositionCodec.decodeC(json).toOption.get

  private def _encoded(json: String): String =
    ComponentSubcomponentCompositionCodec.encode(_decoded(json))

  private def _rejected(json: String) =
    ComponentSubcomponentCompositionCodec.decodeC(json).toOption

  "E1 canonical parent/child membership round-trips without duplicated identity" should afterWord("in spec:component-subcomponent-composition-codec, example:E1, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "decode and encode one canonical parent/child membership without copying mutable child identity" in {
      Given("a v1 parent composition with one independently identified child release")
      val json = _composition_json(_member_json())

      When("the registered composition codec decodes and re-encodes the manifest")
      val encoded = _encoded(json)
      val decodedagain = ComponentSubcomponentCompositionCodec.decodeC(encoded).toOption

      Then("the canonical child identity and logical release survive exactly once")
      decodedagain should not be empty
      encoded should include (_schema)
      encoded should include (_child_id)
      encoded.split(java.util.regex.Pattern.quote(_child_id), -1).length - 1 shouldBe 1
      encoded should include ("\"membershipKind\":\"parent-child\"")
      encoded should include ("\"required\":true")
      encoded should include (s"\"logicalRelease\":\"$_release\"")
      encoded should include ("\"primaryCar\":{\"classification\":\"primary\"")
      encoded should include ("\"subcomponentCar\":{\"classification\":\"subcomponent\"")
      encoded should include (s"\"coordinate\":\"$_parent_artifact_coordinate\"")
      encoded should include (s"\"physicalPath\":\"$_parent_physical_path\"")
      ComponentSubcomponentCompositionCodec.encode(decodedagain.get) shouldBe encoded
    }

  }

  "E2 canonical membership supports fifty successful property round-trips" should afterWord("in spec:component-subcomponent-composition-codec, example:E2, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "run at least fifty successful canonical membership round-trip cases" in {
      Given("safe canonical child identities, logical releases, resources, and artifacts")
      val cases = Gen.zip(Gen.chooseNum(1, 1000000), Gen.chooseNum(1, 999))
      val property = Prop.forAll(cases) { case (identitynumber, releasenumber) =>
        val childid = s"org.goldenport.cncf.phase58.RscChild$identitynumber"
        val logicalrelease = s"0.1.$releasenumber"
        val logicalresource = s"urn:cncf:resource:phase58/child-$identitynumber"
        val logicalpath = s"logical/child-$identitynumber"
        val artifactcoordinate = s"org.example:rsc-child-$identitynumber:$logicalrelease"
        val physicalpath = s"repository/components/rsc-child-$identitynumber.car"
        val json = _composition_json(_member_json(
          componentid = childid,
          logicalrelease = logicalrelease,
          logicalresource = logicalresource,
          logicalpath = logicalpath,
          artifactcoordinate = artifactcoordinate,
          physicalpath = physicalpath
        ))

        ComponentSubcomponentCompositionCodec.decodeC(json).toOption match {
          case Some(composition) =>
            val encoded = ComponentSubcomponentCompositionCodec.encode(composition)
            ComponentSubcomponentCompositionCodec.decodeC(encoded).toOption.nonEmpty
          case None => false
        }
      }

      When("the canonical codec property is checked with fifty minimum successful cases")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("every generated canonical membership remains decodable after encoding")
      checked.passed shouldBe true
    }
  }

  "E3 optional membership round-trips its required fact" should afterWord("in spec:component-subcomponent-composition-codec, example:E3, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "retain an optional membership as required false" in {
      Given("a valid v1 composition with an optional independently identified child")
      val json = _composition_json(_member_json(required = false))

      When("the composition is decoded and encoded")
      val encoded = _encoded(json)

      Then("the optional membership fact remains explicit on the wire")
      encoded should include ("\"required\":false")
    }
  }

  "E4 role and technology remain separate" should afterWord("in spec:component-subcomponent-composition-codec, example:E4, rules:RSC02-AC-02, phase:58.1, slice:RSC-02") {
    "retain the information role independently from implementation technology" in {
      Given("a Documentation child implemented with Markdown")
      val json = _composition_json(_member_json(role = "Documentation", implementationtechnology = "Markdown"))

      When("the composition is decoded and encoded")
      val encoded = _encoded(json)

      Then("role and implementation technology remain distinct fields")
      encoded should include ("\"role\":\"Documentation\"")
      encoded should include ("\"implementationTechnology\":\"Markdown\"")
      encoded should not include ("\"role\":\"Markdown\"")
    }
  }

  "E5 logical and physical identities remain separate" should afterWord("in spec:component-subcomponent-composition-codec, example:E5, rules:RSC02-AC-02, phase:58.1, slice:RSC-02") {
    "retain logical release, artifact, digest, path, repository, and provenance evidence independently" in {
      Given("a child logical release with distinct physical artifact and provenance evidence")
      val json = _composition_json(_member_json())

      When("the composition is decoded and encoded")
      val encoded = _encoded(json)

      Then("logical identity and all physical source evidence remain distinguishable")
      encoded should include (s"\"logicalRelease\":\"$_release\"")
      encoded should include (s"\"coordinate\":\"$_artifact_coordinate\"")
      encoded should include (s"\"sha256\":\"$_sha256\"")
      encoded should include (s"\"physicalPath\":\"$_physical_path\"")
      encoded should include (s"\"repository\":\"$_repository\"")
      encoded should include ("\"logicalSource\":\"composition-registry:rsc-documentation\"")
      encoded should include ("\"physicalSource\":\"repository:rsc\"")
      encoded should not include (s"\"logicalRelease\":\"$_artifact_coordinate\"")
    }
  }

  "E6 external-platform deployment remains explicit" should afterWord("in spec:component-subcomponent-composition-codec, example:E6, rules:RSC02-AC-02, phase:58.1, slice:RSC-02") {
    "retain explicit platform deployment without activation, operation, MCP, disclosure, or deployment authority" in {
      Given("a presentation child whose artifact belongs to an external web platform")
      val json = _composition_json(_member_json(
        role = "presentation",
        implementationtechnology = "React",
        deploymentplatform = "web",
        deploymentmode = "external"
      ))

      When("the composition is decoded and encoded")
      val encoded = _encoded(json)

      Then("external deployment stays explicit and the CAR payload remains non-authoritative")
      encoded should include ("\"platform\":\"web\"")
      encoded should include ("\"mode\":\"external\"")
      encoded should include ("\"requiresExplicitPlatformAction\":true")
      encoded should include ("\"authoritative\":false")
      encoded should include ("\"executable\":false")
      encoded should include ("\"authorization\":{\"state\":\"not-granted\"}")
      encoded should include ("\"integrity\":{\"state\":\"verified\"}")
      encoded should include ("\"availability\":{\"state\":\"available\"}")
      encoded should include ("\"activation\":false")
      encoded should include ("\"operation\":false")
      encoded should include ("\"mcp\":false")
      encoded should include ("\"disclosure\":false")
      encoded should include ("\"deployment\":false")
      encoded should not include ("\"requiresExplicitPlatformAction\":false")
    }
  }

  "E7 duplicate role/release coordinates reject" should afterWord("in spec:component-subcomponent-composition-codec, example:E7, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject duplicate child membership coordinates" in {
      Given("a parent registry containing the same child and logical release twice")
      val json = _composition_json(_member_json(), _member_json())

      When("the codec decodes the duplicate membership registry")
      val result = _rejected(json)

      Then("duplicate role/release membership is rejected")
      result shouldBe None
    }
  }

  "E33 duplicate child identity across releases rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E33, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "reject one canonical child ComponentId assigned distinct valid releases, roles, and logical resources" in {
      Given("a parent registry that repeats one canonical child identity with distinct allowed membership evidence")
      val json = _composition_json(
        _member_json(),
        _member_json(
          logicalrelease = "0.2.0-SNAPSHOT",
          role = "SourceCode",
          implementationtechnology = "Scala",
          logicalresource = "urn:cncf:resource:phase58/source-code",
          logicalpath = "logical/source-code",
          artifactcoordinate = "org.example:rsc-source-code-car:0.2.0-SNAPSHOT",
          physicalpath = "repository/components/rsc-source-code-0.2.0.car"
        )
      )

      When("the codec decodes the repeated child identity at distinct logical releases")
      val result = _rejected(json)

      Then("the repeated canonical child ComponentId is rejected independently of its other membership evidence")
      result shouldBe None
    }
  }

  "E8 self-cycle rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E8, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a child membership that points back to its parent identity" in {
      Given("a parent composition whose child identity is the parent identity")
      val json = _composition_json(_member_json(componentid = _parent_id))

      When("the codec decodes the cyclic registry")
      val result = _rejected(json)

      Then("the parent/child self-cycle is rejected")
      result shouldBe None
    }
  }

  "E9 logical-resource conflict rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E9, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject two independent children claiming one logical resource" in {
      Given("two distinct children that share one canonical logical resource")
      val json = _composition_json(
        _member_json(),
        _member_json(
          componentid = _source_id,
          role = "SourceCode",
          implementationtechnology = "Scala",
          logicalresource = _logical_resource,
          logicalpath = "logical/source-code",
          artifactcoordinate = "org.example:rsc-source-code-car:0.1.0"
        )
      )

      When("the codec decodes the conflicting resource registry")
      val result = _rejected(json)

      Then("the logical-resource conflict is rejected")
      result shouldBe None
    }
  }

  "E10 inconsistent membership shape rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E10, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a membership that omits canonical child identity and logical release" in {
      Given("a membership object with required identity fields removed")
      val member = _member_json()
        .replace("\"componentId\":\"" + _child_id + "\",", "")
        .replace("\"logicalRelease\":\"" + _release + "\",", "")
      val json = _composition_json(member)

      When("the codec decodes the inconsistent membership shape")
      val result = _rejected(json)

      Then("the invalid membership shape is rejected")
      result shouldBe None
    }
  }

  "E11 incompatible role/deployment rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E11, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject automatic CNCF deployment authority for a non-executable Documentation role" in {
      Given("a Documentation payload marked for automatic CNCF deployment and activation")
      val json = _composition_json(_member_json(
        role = "Documentation",
        deploymentplatform = "cncf-runtime",
        deploymentmode = "automatic",
        requiresexplicitplatformaction = false,
        activationauthority = true,
        operationauthority = true,
        mcpauthority = true,
        disclosureauthority = true,
        deploymentauthority = true
      ))

      When("the codec decodes the incompatible role/deployment combination")
      val result = _rejected(json)

      Then("the incompatible authority claim is rejected")
      result shouldBe None
    }
  }

  "E12 malformed SHA-256 rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E12, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a physical artifact with a malformed SHA-256 digest" in {
      Given("a child artifact whose SHA-256 value is not a 64-character hexadecimal digest")
      val json = _composition_json(_member_json(sha256 = "not-a-sha256"))

      When("the codec decodes the malformed digest")
      val result = _rejected(json)

      Then("the malformed SHA-256 evidence is rejected")
      result shouldBe None
    }
  }

  "E13 malformed signature rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E13, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a physical artifact with malformed signature evidence" in {
      Given("a child artifact whose signature is not valid encoded signature material")
      val json = _composition_json(_member_json(signature = "***"))

      When("the codec decodes the malformed signature")
      val result = _rejected(json)

      Then("the malformed signature evidence is rejected")
      result shouldBe None
    }
  }

  "E14 unsafe physical path rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E14, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject an artifact path that escapes its physical repository root" in {
      Given("a child artifact with a parent-traversal physical path")
      val json = _composition_json(_member_json(physicalpath = "../outside/rsc-documentation.car"))

      When("the codec decodes the unsafe physical path")
      val result = _rejected(json)

      Then("the physical path traversal is rejected")
      result shouldBe None
    }
  }

  "E15 unsafe logical path rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E15, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a logical resource path that escapes its logical namespace" in {
      Given("a child logical resource with a parent-traversal logical path")
      val json = _composition_json(_member_json(logicalpath = "../../outside"))

      When("the codec decodes the unsafe logical path")
      val result = _rejected(json)

      Then("the logical path traversal is rejected")
      result shouldBe None
    }
  }

  "E16 forward-compatible unknown-field preservation" should afterWord("in spec:component-subcomponent-composition-codec, example:E16, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "preserve an unknown field under the recognized v1 schema" in {
      Given("a valid v1 composition carrying an unknown future extension field")
      val json = _composition_json(_member_json())
        .replace("\"members\":[", "\"x-rsc02-future\":{\"retained\":\"yes\"},\"members\":[")
        .replace("\"parent\":{", "\"parent\":{\"x-rsc02-parent-future\":{\"retained\":\"parent\"},")
        .replace("\"subcomponentCar\":{", "\"subcomponentCar\":{\"x-rsc02-car-future\":{\"retained\":\"car\"},")
        .replace("\"payload\":{", "\"payload\":{\"x-rsc02-payload-future\":{\"retained\":\"payload\"},")

      When("the codec decodes and re-encodes the forward-compatible manifest")
      val encoded = _encoded(json)
      val decodedagain = ComponentSubcomponentCompositionCodec.decodeC(encoded).toOption

      Then("the unknown field and its value remain available after the round-trip")
      decodedagain should not be empty
      encoded should include ("\"x-rsc02-future\"")
      encoded should include ("\"retained\":\"yes\"")
      encoded should include ("\"x-rsc02-parent-future\"")
      encoded should include ("\"x-rsc02-car-future\"")
      encoded should include ("\"x-rsc02-payload-future\"")
      encoded should include ("\"retained\":\"parent\"")
      encoded should include ("\"retained\":\"car\"")
      encoded should include ("\"retained\":\"payload\"")
    }
  }

  "E17 encoder rejects authoritative payload mutations" should afterWord("in spec:component-subcomponent-composition-codec, example:E17, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "reject a publicly constructed authoritative payload before emitting JSON" in {
      Given("a decoded valid composition mutated into an authoritative payload")
      val composition = _decoded(_composition_json(_member_json()))
      val invalid = composition.copy(members = composition.members.map(member =>
        member.copy(payload = member.payload.copy(authoritative = true))
      ))

      When("the codec encodes the invalid public case-class value")
      val error = intercept[IllegalArgumentException](ComponentSubcomponentCompositionCodec.encode(invalid))

      Then("no invalid v1 wire manifest is emitted and the invariant reason is visible")
      error.getMessage should include ("non-authoritative and non-executable")
    }
  }

  "E18 encoder rejects unsafe physical-path mutations" should afterWord("in spec:component-subcomponent-composition-codec, example:E18, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "reject a publicly constructed physical path that escapes its repository root" in {
      Given("a decoded valid composition mutated with an unsafe artifact physical path")
      val composition = _decoded(_composition_json(_member_json()))
      val invalid = composition.copy(members = composition.members.map(member =>
        member.copy(subcomponentCar = member.subcomponentCar.copy(
          artifact = member.subcomponentCar.artifact.copy(physicalPath = "/outside/rsc-documentation.car")
        ))
      ))

      When("the codec encodes the invalid public case-class value")
      val error = intercept[IllegalArgumentException](ComponentSubcomponentCompositionCodec.encode(invalid))

      Then("no invalid v1 wire manifest is emitted and the path reason is visible")
      error.getMessage should include ("safe relative path")
    }
  }

  "E19 duplicate JSON keys reject" should afterWord("in spec:component-subcomponent-composition-codec, example:E19, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a v1 manifest with a duplicate schema key" in {
      Given("a canonical composition whose schema key is repeated")
      val json = _composition_json(_member_json()).replace(
        s"\"schema\":\"$_schema\",",
        s"\"schema\":\"$_schema\",\"schema\":\"$_schema\","
      )

      When("the codec decodes the hostile JSON object")
      val result = _rejected(json)

      Then("the duplicate key is rejected before manifest interpretation")
      result shouldBe None
    }
  }

  "E20 unsupported schema rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E20, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a manifest outside the v1 schema contract" in {
      Given("a composition with an unsupported schema identity")
      val json = _composition_json(_member_json()).replace(_schema, "cncf.component-subcomponent-composition.v2")

      When("the codec decodes the unsupported schema")
      val result = _rejected(json)

      Then("the schema mismatch is rejected")
      result shouldBe None
    }
  }

  "E21 unsupported membership kind rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E21, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a manifest with an unsupported membership kind" in {
      Given("a v1 schema manifest with a non-parent-child membership kind")
      val json = _composition_json(_member_json()).replace("\"membershipKind\":\"parent-child\"", "\"membershipKind\":\"peer\"")

      When("the codec decodes the unsupported membership relationship")
      val result = _rejected(json)

      Then("the membership kind mismatch is rejected")
      result shouldBe None
    }
  }

  "E22 empty members reject" should afterWord("in spec:component-subcomponent-composition-codec, example:E22, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a composition that has no parent-child memberships" in {
      Given("a v1 parent composition with an empty members array")
      val json = _composition_json()

      When("the codec decodes the empty membership registry")
      val result = _rejected(json)

      Then("the required parent-child membership is rejected as absent")
      result shouldBe None
    }
  }

  "E23 malformed ComponentId rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E23, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a malformed or noncanonical child ComponentId" in {
      Given("a membership with a whitespace-bearing ComponentId")
      val json = _composition_json(_member_json(componentid = _child_id + " "))

      When("the codec decodes the noncanonical child identity")
      val result = _rejected(json)

      Then("the ComponentId is rejected")
      result shouldBe None
    }
  }

  "E24 invalid logical release rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E24, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a membership without a valid logical release" in {
      Given("a membership whose logical release is empty")
      val json = _composition_json(_member_json(logicalrelease = ""))

      When("the codec decodes the invalid logical release")
      val result = _rejected(json)

      Then("the logical release is rejected")
      result shouldBe None
    }
  }

  "E25 repository user-info rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E25, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject repository URI user-info" in {
      Given("a membership artifact repository URI that embeds credentials")
      val json = _composition_json(_member_json(repository = "https://user:secret@repo.example.invalid/cncf/rsc"))

      When("the codec decodes the repository evidence")
      val result = _rejected(json)

      Then("the user-info-bearing repository URI is rejected")
      result shouldBe None
    }
  }

  "E26 absolute physical path rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E26, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject an absolute physical artifact path" in {
      Given("a membership with an absolute physical artifact path")
      val json = _composition_json(_member_json(physicalpath = "/repository/components/rsc-documentation.car"))

      When("the codec decodes the physical artifact evidence")
      val result = _rejected(json)

      Then("the absolute physical path is rejected")
      result shouldBe None
    }
  }

  "E27 Windows logical path rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E27, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject a Windows-style logical path" in {
      Given("a membership with a backslash-separated logical path")
      val json = _composition_json(_member_json(logicalpath = "logical\\\\documentation-guide"))

      When("the codec decodes the logical resource path")
      val result = _rejected(json)

      Then("the Windows-style logical path is rejected")
      result shouldBe None
    }
  }

  "E28 missing required membership field rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E28, rules:RSC02-AC-01, phase:58.1, slice:RSC-02") {
    "reject a membership that omits the mandatory required field" in {
      Given("a canonical membership with its required fact removed")
      val json = _composition_json(_member_json().replace("\"required\":true,", ""))

      When("the codec decodes the incomplete membership")
      val result = _rejected(json)

      Then("the missing required membership fact is rejected")
      result shouldBe None
    }
  }

  "E29 stale authorization state rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E29, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject an authorization state outside the explicit authorization vocabulary" in {
      Given("a membership with a stale authorization state")
      val json = _composition_json(_member_json().replace("\"authorization\":{\"state\":\"not-granted\"}", "\"authorization\":{\"state\":\"stale\"}"))

      When("the codec decodes authorization evidence")
      val result = _rejected(json)

      Then("the stale authorization state is rejected")
      result shouldBe None
    }
  }

  "E30 stale integrity state rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E30, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject an integrity state outside the explicit integrity vocabulary" in {
      Given("a membership with a stale integrity state")
      val json = _composition_json(_member_json().replace("\"integrity\":{\"state\":\"verified\"}", "\"integrity\":{\"state\":\"stale\"}"))

      When("the codec decodes integrity evidence")
      val result = _rejected(json)

      Then("the stale integrity state is rejected")
      result shouldBe None
    }
  }

  "E31 stale availability state rejects" should afterWord("in spec:component-subcomponent-composition-codec, example:E31, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "reject an availability state outside the explicit availability vocabulary" in {
      Given("a membership with a stale availability state")
      val json = _composition_json(_member_json().replace("\"availability\":{\"state\":\"available\"}", "\"availability\":{\"state\":\"stale\"}"))

      When("the codec decodes availability evidence")
      val result = _rejected(json)

      Then("the stale availability state is rejected")
      result shouldBe None
    }
  }

  "E32 alternate valid states accept" should afterWord("in spec:component-subcomponent-composition-codec, example:E32, rules:RSC02-AC-03, phase:58.1, slice:RSC-02") {
    "accept the alternate explicit authorization, integrity, and availability states" in {
      Given("a membership using granted, unverified, and unavailable state values")
      val json = _composition_json(_member_json()
        .replace("\"authorization\":{\"state\":\"not-granted\"}", "\"authorization\":{\"state\":\"granted\"}")
        .replace("\"integrity\":{\"state\":\"verified\"}", "\"integrity\":{\"state\":\"unverified\"}")
        .replace("\"availability\":{\"state\":\"available\"}", "\"availability\":{\"state\":\"unavailable\"}"))

      When("the codec decodes and encodes the alternate state evidence")
      val encoded = _encoded(json)

      Then("each independent alternate state remains valid and explicit")
      encoded should include ("\"authorization\":{\"state\":\"granted\"}")
      encoded should include ("\"integrity\":{\"state\":\"unverified\"}")
      encoded should include ("\"availability\":{\"state\":\"unavailable\"}")
    }
  }
}
