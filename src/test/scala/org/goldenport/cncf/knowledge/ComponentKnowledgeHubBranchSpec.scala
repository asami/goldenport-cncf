package org.goldenport.cncf.knowledge

import scala.collection.mutable
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.testutil.ComponentKnowledgeFixtures.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeHubBranchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e7 = afterWord("in spec:component-knowledge-contribution-hub, example:E7, rules:R1,R3,R4,R8, phase:73")
  private val _e8 = afterWord("in spec:component-knowledge-contribution-hub, example:E8, rules:R2,R3,R4,R8, phase:73")
  private val _e9 = afterWord("in spec:component-knowledge-contribution-hub, example:E9, rules:R1,R3,R8, phase:73")
  private val _e10 = afterWord("in spec:component-knowledge-contribution-hub, example:E10, rules:R2,R4,R7, phase:73")
  private val _e11 = afterWord("in spec:component-knowledge-contribution-hub, example:E11, rules:R4,R6,R8, phase:73")

  "Opaque contribution branch contracts" should {
    "distinguish discovery and complete availability" which {
      "E7 distinguishes absent, ready-empty, unavailable and failed" must _e7 {
        "return successful absence without reading an unrelated supplier" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R4,R8; Example: E7; one installed supplier of another exact domain")
          val provider = new MutableSupplier(snapshot(declaration().copy(knowledgeDomainId = "other-domain")))
          withInstalled(Vector(provider)) { (_, hub) =>
            When("the queried domain is discovered")
            val result = hub.discoverC(domainId)
            Then("absence is successful None and invokes no supplier read")
            result.toOption shouldBe Some(None)
            provider.readCount shouldBe 0
          }
        }

        "deliver a complete attributed empty snapshot" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R4,R8; Example: E7; an installed Ready supplier with an empty opaque vector")
          val provider = new MutableSupplier(snapshot(payload = Vector.empty[Byte]))
          withInstalled(Vector(provider)) { (_, hub) =>
            When("the empty contribution is discovered and read")
            val handle = hub.discoverC(domainId).toOption.flatten.get
            val result = hub.readC(handle, schema)
            Then("empty data remains a valid complete snapshot with trusted attribution")
            result.toOption shouldBe Some(ComponentKnowledgeSnapshot(
              ComponentKnowledgeDescriptor(provider.instanceId, declaration()), Vector.empty[Byte]
            ))
            provider.readCount shouldBe 1
          }
        }

        Vector(KnowledgeContributionAvailability.Unavailable -> "unavailable", KnowledgeContributionAvailability.Failed -> "failed").foreach { (availability, code) =>
          s"keep $availability discoverable but reject its read before the callback" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R4,R8; Example: E7; installed $availability metadata")
            val declared = declaration(availability = availability)
            val provider = new MutableSupplier(snapshot(declared))
            withInstalled(Vector(provider)) { (_, hub) =>
              When("the unavailable contribution is discovered")
              val discovery = hub.discoverC(domainId)
              Then("its complete declaration remains discoverable without a read")
              discovery.toOption.flatten.map(_.descriptor.declaration) shouldBe Some(declared)
              provider.readCount shouldBe 0
              When("the captured descriptor is read")
              val result = hub.readC(discovery.toOption.flatten.get, schema)
              Then("the distinct fixed availability failure precedes the supplier callback")
              _require_failure(result, code, Some(provider.instanceId.toString))
              provider.readCount shouldBe 0
            }
          }
        }
      }
    }

    "admit exactly contract version one and exact schema" which {
      "E8 rejects incompatible caller schemas and malformed metadata" must _e8 {
        Vector("unequal id" -> KnowledgeContributionSchema("other-schema", "1"), "unequal version" -> KnowledgeContributionSchema(schema.id, "2")).foreach { (label, expected) =>
          s"reject a valid $label before invoking the supplier" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; an installed Ready supplier and valid $label")
            val provider = new MutableSupplier()
            withInstalled(Vector(provider)) { (_, hub) =>
              val handle = hub.discoverC(domainId).toOption.flatten.get
              When("the caller requests that incompatible exact schema")
              val result = hub.readC(handle, expected)
              Then("the hub reports schema-incompatible without a callback")
              _require_failure(result, "schema-incompatible", Some(provider.instanceId.toString))
              provider.readCount shouldBe 0
            }
          }
        }

        Vector(
          "null schema" -> null,
          "null schema id" -> KnowledgeContributionSchema(null, "1"),
          "null schema version" -> KnowledgeContributionSchema(schema.id, null),
          "blank schema id" -> KnowledgeContributionSchema(" ", "1"),
          "blank schema version" -> KnowledgeContributionSchema(schema.id, " ")
        ).foreach { (label, expected) =>
          s"reject $label as an invalid caller argument" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; an installed Ready supplier and $label")
            val provider = new MutableSupplier()
            withInstalled(Vector(provider)) { (_, hub) =>
              val handle = hub.discoverC(domainId).toOption.flatten.get
              When("the caller requests a malformed schema")
              val result = hub.readC(handle, expected)
              Then("argument-invalid precedes the supplier callback")
              _require_failure(result, "argument-invalid", Some(provider.instanceId.toString))
              provider.readCount shouldBe 0
            }
          }
        }

        "reject a null hub owner" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; no owning Subsystem")
          When("a facade is requested for null")
          val result = ComponentKnowledgeHub.createC(null)
          Then("creation fails argument-invalid without constructing a facade or callback")
          _require_failure(result, "argument-invalid", None)
        }

        Vector("null query" -> null, "blank query" -> " ").foreach { (label, query) =>
          s"reject $label without discovery or read callbacks" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; an installed supplier and $label")
            val provider = new MutableSupplier(declarationsOverride = Some(() => throw new IllegalStateException(failureText)))
            withInstalled(Vector(provider)) { (_, hub) =>
              When("the malformed domain is queried")
              val result = hub.discoverC(query)
              Then("argument-invalid wins before the throwing getter can participate")
              _require_failure(result, "argument-invalid", None)
              provider.readCount shouldBe 0
            }
          }
        }

        _malformed_declarations.foreach { (label, declarations) =>
          s"reject $label in installed supplier declarations" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; an installed opted-in supplier advertising $label")
            val provider = new MutableSupplier(declarationsOverride = Some(() => declarations))
            withInstalled(Vector(provider)) { (_, hub) =>
              When("the domain is discovered")
              val result = hub.discoverC(domainId)
              Then("declaration-invalid admits no contribution and invokes no read")
              _require_failure(result, "declaration-invalid", Some(provider.instanceId.toString))
              provider.readCount shouldBe 0
            }
          }
        }

        "fail queried discovery when an unrelated installed opted-in getter is malformed" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R3,R4,R8; Example: E8; a unique queried supplier and an unrelated malformed supplier")
          val selected = new MutableSupplier()
          val unrelated = new MutableSupplier(declarationsOverride = Some(() => Vector(declaration().copy(knowledgeDomainId = "other-domain", schema = null))))
          withInstalled(Vector(selected, unrelated)) { (_, hub) =>
            When("the selected domain is queried")
            val result = hub.discoverC(domainId)
            Then("whole-discovery admission rejects unrelated malformed metadata without reads")
            _require_failure(result, "declaration-invalid", Some(unrelated.instanceId.toString))
            selected.readCount shouldBe 0
            unrelated.readCount shouldBe 0
          }
        }
      }
    }

    "select only a unique exact-domain declaration" which {
      "E9 refuses every duplicate without ranking or fallback" must _e9 {
        Vector(false, true).foreach { reversed =>
          s"reject distinct providers in ${if (reversed) "reverse" else "forward"} admission order" in {
            Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R8; Example: E9; two supplier objects advertising the same domain with different availability and generations")
            val first = new MutableSupplier()
            val second = new MutableSupplier(snapshot(declaration(secondGeneration, KnowledgeContributionAvailability.Unavailable)))
            val providers = Vector[Component](first, second)
            withInstalled(if (reversed) providers.reverse else providers) { (_, hub) =>
              When("the duplicate domain is discovered")
              val result = hub.discoverC(domainId)
              Then("no readiness, generation or admission order chooses a winner")
              _require_failure(result, "conflict", None)
              first.readCount shouldBe 0
              second.readCount shouldBe 0
            }
          }
        }

        "reject two advertised generations from one supplier" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R8; Example: E9; one supplier advertises both g1 and g2 for the same domain")
          val provider = new MutableSupplier(declarationsOverride = Some(() => Vector(declaration(), declaration(secondGeneration))))
          withInstalled(Vector(provider)) { (_, hub) =>
            When("the ambiguous domain is discovered")
            val result = hub.discoverC(domainId)
            Then("duplicate generations conflict without invoking the supplier")
            _require_failure(result, "conflict", None)
            provider.readCount shouldBe 0
          }
        }

        "bind only the unique queried supplier among unrelated declarations" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R1,R3,R8; Example: E9; one queried supplier, one valid unrelated supplier and a non-opt-in component")
          val selected = new MutableSupplier()
          val unrelated = new MutableSupplier(snapshot(declaration().copy(knowledgeDomainId = "other-domain"), secondPayload))
          withInstalled(Vector(unrelated, new Component() {}, selected)) { (_, hub) =>
            When("the unique exact domain is discovered and read")
            val handle = hub.discoverC(domainId).toOption.flatten.get
            val result = hub.readC(handle, schema)
            Then("the queried installed provider alone supplies the full attributed bytes")
            result.toOption shouldBe Some(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(selected.instanceId, declaration()), firstPayload))
            selected.readCount shouldBe 1
            unrelated.readCount shouldBe 0
          }
        }
      }
    }

    "retain exact immutable generations for captured readers" which {
      "E10 publishes complete replacements and makes retention explicit" must _e10 {
        "deliver independent complete g1 and g2 snapshots after atomic publication" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R4,R7; Example: E10; an installed supplier with complete g1 and a complete g2 candidate with new bytes, schema version and provenance")
          val provider = new MutableSupplier()
          val candidate = snapshot(declaration(secondGeneration, provenance = secondProvenance).copy(schema = KnowledgeContributionSchema(schema.id, "2")), secondPayload)
          withInstalled(Vector(provider)) { (_, hub) =>
            val oldhandle = hub.discoverC(domainId).toOption.flatten.get
            val stored = hub.readC(oldhandle, schema).toOption.get
            When("the supplier publishes complete g2 and old and new readers read independently")
            val publication = provider.publishC(candidate)
            val newhandle = hub.discoverC(domainId).toOption.flatten.get
            val newread = hub.readC(newhandle, candidate.declaration.schema)
            val oldread = hub.readC(oldhandle, schema)
            Then("g2 replaces all advertised metadata and bytes without merging while captured g1 remains complete")
            publication.toOption shouldBe Some(())
            newhandle.descriptor.declaration shouldBe candidate.declaration
            newread.toOption shouldBe Some(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(provider.instanceId, candidate.declaration), secondPayload))
            oldread.toOption shouldBe Some(ComponentKnowledgeSnapshot(ComponentKnowledgeDescriptor(provider.instanceId, declaration()), firstPayload))
            stored shouldBe oldread.toOption.get
            provider.readCount shouldBe 3
          }
        }

        "leave complete g1 unchanged after candidate preparation fails" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R4,R7; Example: E10; a captured complete g1 and fault injection before g2 publication")
          val provider = new MutableSupplier()
          val candidate = snapshot(declaration(secondGeneration, provenance = secondProvenance), secondPayload)
          withInstalled(Vector(provider)) { (_, hub) =>
            val captured = hub.discoverC(domainId).toOption.flatten.get
            val stored = hub.readC(captured, schema).toOption.get
            When("preparation fails before state mutation and captured and rediscovered readers request data")
            val publication = provider.publishC(candidate, failPreparation = true)
            val rediscovered = hub.discoverC(domainId).toOption.flatten.get
            val capturedread = hub.readC(captured, schema)
            val currentread = hub.readC(rediscovered, schema)
            Then("failure leaves original advertised metadata and retained bytes intact with no partial replacement")
            publication shouldBe a[Consequence.Failure[?]]
            rediscovered.descriptor shouldBe captured.descriptor
            capturedread.toOption shouldBe Some(stored)
            currentread.toOption shouldBe Some(stored)
            stored.payload shouldBe firstPayload
            stored.descriptor.declaration shouldBe declaration()
            provider.readCount shouldBe 3
          }
        }

        "fail an explicitly retired captured generation without current-generation fallback" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R2,R4,R7; Example: E10; captured/read g1 and published/read g2 retained independently")
          val provider = new MutableSupplier()
          val candidate = snapshot(declaration(secondGeneration, provenance = secondProvenance), secondPayload)
          withInstalled(Vector(provider)) { (_, hub) =>
            val oldhandle = hub.discoverC(domainId).toOption.flatten.get
            val stored = hub.readC(oldhandle, schema).toOption.get
            provider.publishC(candidate).toOption shouldBe Some(())
            val newhandle = hub.discoverC(domainId).toOption.flatten.get
            val newstored = hub.readC(newhandle, schema).toOption.get
            When("the supplier explicitly retires g1 and old/new readers try their captured generations")
            provider.retire(firstGeneration)
            val oldread = hub.readC(oldhandle, schema)
            val newread = hub.readC(newhandle, schema)
            val rediscoveredread = hub.readC(hub.discoverC(domainId).toOption.flatten.get, schema)
            Then("old read fails with trusted attribution, current g2 succeeds and returned snapshots remain immutable")
            _require_failure(oldread, "provider-failed", Some(provider.instanceId.toString))
            newread.toOption shouldBe Some(newstored)
            rediscoveredread.toOption shouldBe Some(newstored)
            stored.payload shouldBe firstPayload
            stored.descriptor.declaration shouldBe declaration()
            newstored.payload shouldBe secondPayload
            provider.readCount shouldBe 5
          }
        }
      }
    }

    "sanitize supplier boundaries and preserve exact snapshot binding" which {
      "E11 discards all supplier secrets from public causal forms" must _e11 {
        "sanitize a throwing installed declaration getter without reading" in {
          Given("Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R6,R8; Example: E11; a getter throwing fake exception, payload, domain, generation, schema, URL and private-path sentinels")
          val provider = new MutableSupplier(declarationsOverride = Some(() => throw new IllegalStateException(failureText)))
          withInstalled(Vector(provider)) { (_, hub) =>
            When("discovery invokes the throwing getter")
            val result = hub.discoverC(domainId)
            Then("fixed declaration-invalid and trusted identity retain no supplier exception or sentinel in public causal forms")
            _require_failure(result, "declaration-invalid", Some(provider.instanceId.toString))
            provider.readCount shouldBe 0
          }
        }

        Vector[(String, KnowledgeContributionDeclaration => Consequence[KnowledgeContributionSnapshot])](
          "thrown NonFatal" -> (_ => throw new IllegalStateException(failureText)),
          "supplier Failure" -> (_ => Consequence.serviceUnavailable(failureText)),
          "null Consequence" -> (_ => null)
        ).foreach { (label, callback) =>
          s"sanitize $label from the selected read callback" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R6,R8; Example: E11; installed Ready supplier returning $label with fake private sentinels")
            val provider = new MutableSupplier(readOverride = Some(callback))
            withInstalled(Vector(provider)) { (_, hub) =>
              val handle = hub.discoverC(domainId).toOption.flatten.get
              When("the hub invokes that selected supplier once")
              val result = hub.readC(handle, schema)
              Then("fixed provider-failed with trusted identity has no source exception/conclusion in recursive public forms")
              _require_failure(result, "provider-failed", Some(provider.instanceId.toString))
              provider.readCount shouldBe 1
            }
          }
        }

        _malformed_snapshots.foreach { (label, returned) =>
          s"reject supplier success containing $label" in {
            Given(s"Spec: docs/spec/component-knowledge-contribution-hub.md; Rules: R4,R6,R8; Example: E11; a Ready supplier returning successful $label")
            val provider = new MutableSupplier(readOverride = Some(_ => Consequence.success(returned)))
            withInstalled(Vector(provider)) { (_, hub) =>
              val handle = hub.discoverC(domainId).toOption.flatten.get
              When("the supplier callback returns that malformed bound snapshot")
              val result = hub.readC(handle, schema)
              Then("snapshot-binding-invalid delivers no snapshot or private supplier causal value")
              _require_failure(result, "snapshot-binding-invalid", Some(provider.instanceId.toString))
              provider.readCount shouldBe 1
            }
          }
        }
      }
    }
  }

  private def _malformed_declarations: Vector[(String, Vector[KnowledgeContributionDeclaration])] = {
    val declared = declaration()
    Vector(
      "null declaration vector" -> null,
      "null declaration member" -> Vector(null),
      "null domain" -> Vector(declared.copy(knowledgeDomainId = null)),
      "blank domain" -> Vector(declared.copy(knowledgeDomainId = " ")),
      "null generation" -> Vector(declared.copy(generation = null)),
      "blank generation" -> Vector(declared.copy(generation = " ")),
      "null schema" -> Vector(declared.copy(schema = null)),
      "null schema id" -> Vector(declared.copy(schema = KnowledgeContributionSchema(null, "1"))),
      "blank schema id" -> Vector(declared.copy(schema = KnowledgeContributionSchema(" ", "1"))),
      "null schema version" -> Vector(declared.copy(schema = KnowledgeContributionSchema(schema.id, null))),
      "blank schema version" -> Vector(declared.copy(schema = KnowledgeContributionSchema(schema.id, " "))),
      "null provenance vector" -> Vector(declared.copy(provenance = null)),
      "null provenance member" -> Vector(declared.copy(provenance = Vector(null))),
      "null availability" -> Vector(declared.copy(availability = null)),
      "unsupported contract version zero" -> Vector(declared.copy(contractVersion = 0)),
      "unsupported contract version two" -> Vector(declared.copy(contractVersion = 2))
    )
  }

  private def _malformed_snapshots: Vector[(String, KnowledgeContributionSnapshot)] = {
    val declared = declaration()
    Vector(
      "null snapshot" -> null,
      "null payload" -> snapshot(payload = null),
      "null declaration" -> snapshot(null),
      "changed domain" -> snapshot(declared.copy(knowledgeDomainId = "sentinel-changed-domain")),
      "changed generation" -> snapshot(declared.copy(generation = secondGeneration)),
      "changed schema id" -> snapshot(declared.copy(schema = KnowledgeContributionSchema("sentinel-changed-schema", "1"))),
      "changed schema version" -> snapshot(declared.copy(schema = KnowledgeContributionSchema(schema.id, "2"))),
      "changed provenance" -> snapshot(declared.copy(provenance = secondProvenance)),
      "changed availability" -> snapshot(declared.copy(availability = KnowledgeContributionAvailability.Failed)),
      "changed contract version" -> snapshot(declared.copy(contractVersion = 2))
    )
  }

  private def _require_failure(result: Consequence[?], code: String, provider: Option[String]): Unit = {
    result shouldBe a[Consequence.Failure[?]]
    result.toOption shouldBe empty
    val conclusion = result.asInstanceOf[Consequence.Failure[?]].conclusion
    val expected = "knowledge-contribution." + code + provider.fold("")(identity => s" provider=$identity")
    conclusion.show should include(expected)
    val conclusions = _public_causal_conclusions(Vector(conclusion))
    conclusions should not be empty
    conclusions.foreach { value =>
      value.causes.foreach(_.getException shouldBe empty)
      _public_forms(value).foreach { text =>
        failureMarkers.foreach(marker => text should not include marker)
      }
    }
  }

  private def _public_causal_conclusions(roots: Vector[Conclusion]): Vector[Conclusion] = {
    val seen = new java.util.IdentityHashMap[AnyRef, java.lang.Boolean]()
    val results = mutable.ArrayBuffer.empty[Conclusion]
    def _collect_(value: Any): Unit = value match {
      case null => ()
      case reference: AnyRef if seen.containsKey(reference) => ()
      case conclusion: Conclusion =>
        seen.put(conclusion, java.lang.Boolean.TRUE)
        results += conclusion
        conclusion.causes.foreach(_collect_)
      case option: Option[?] => option.foreach(_collect_)
      case optional: java.util.Optional[?] if optional.isPresent => _collect_(optional.get)
      case values: Iterable[?] => values.foreach(_collect_)
      case values: Array[?] => values.foreach(_collect_)
      case reference: AnyRef =>
        seen.put(reference, java.lang.Boolean.TRUE)
        reference.getClass.getMethods.iterator
          .filter(method => method.getParameterCount == 0 && method.getName.toLowerCase.contains("conclusion"))
          .foreach { method =>
            try _collect_(method.invoke(reference))
            catch { case NonFatal(_) => () }
          }
      case _ => ()
    }
    roots.foreach(_collect_)
    results.toVector
  }

  private def _public_forms(conclusion: Conclusion): Vector[String] = {
    val values = Vector[AnyRef](conclusion) ++ conclusion.causes.map(_.asInstanceOf[AnyRef])
    Vector(conclusion.display, conclusion.show, conclusion.toString) ++
      conclusion.causes.flatMap(value => Vector(value.show, value.toString)) ++
      values.flatMap { value =>
        Vector("display", "toJsonString", "toRecord").flatMap { name =>
          value.getClass.getMethods.iterator
            .find(method => method.getName == name && method.getParameterCount == 0)
            .flatMap { method =>
              try Option(method.invoke(value)).map(_.toString)
              catch { case NonFatal(_) => None }
            }
        }
      }
  }
}
