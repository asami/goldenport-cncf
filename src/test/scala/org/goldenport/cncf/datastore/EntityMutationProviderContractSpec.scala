package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationExecutionPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 26, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityMutationProviderContractSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Entity mutation provider contract" should {
    "define capabilities and readback contracts" which {
      "classify ordinary direct and guarded fallback paths without changing policy" in {
        Given("an ordinary None plus AlwaysWrite mutation and providers with and without direct update")
        val request = _request()
        val direct =
          EntityMutationProviderCapabilities.guardedBaseline.copy(
            features =
              EntityMutationProviderCapabilities.guardedBaseline.features +
                EntityMutationProviderFeature.DirectAlwaysWrite
          )
        val guarded = EntityMutationProviderCapabilities.guardedBaseline

        When("the same mutation is planned for each provider")
        val directpath = EntityMutationPathPlanner.selectC(direct, request)
        val fallbackpath = EntityMutationPathPlanner.selectC(guarded, request)

        Then("direct support selects the fast path and the current baseline remains an explicit guarded fallback")
        directpath shouldBe Consequence.success(
          EntityMutationExecutionPath.DirectAlwaysWrite
        )
        fallbackpath shouldBe Consequence.success(
          EntityMutationExecutionPath.GuardedVersionedFallback
        )
      }

      "require an authoritative-result feature only when the caller requests readback" in {
        Given("a direct provider that does not return an authoritative record")
        val capabilities =
          EntityMutationProviderCapabilities(
            Set(EntityMutationProviderFeature.DirectAlwaysWrite)
          )

        When("ordinary mutation is planned without and with authoritative readback")
        val withoutreadback =
          EntityMutationPathPlanner.selectC(
            capabilities,
            _request()
          )
        val withreadback =
          EntityMutationPathPlanner.selectC(
            capabilities,
            _request(
              readbackrequirement =
                EntityMutationReadbackRequirement.AuthoritativeRecord
            )
          )

        Then("the direct acknowledgment path succeeds and unsupported readback fails structurally")
        withoutreadback shouldBe Consequence.success(
          EntityMutationExecutionPath.DirectAlwaysWrite
        )
        withreadback shouldBe a[Consequence.Failure[?]]
      }

      "represent provider acknowledgment independently from authoritative readback" in {
        Given("applied provider results with omitted and authoritative records")
        val omitted =
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Omitted
          )
        val record = Record.data("id" -> "entity-1")
        val authoritative =
          EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Authoritative(record)
          )

        When("the provider results are validated against each readback contract")
        val omittedwithoutreadback =
          EntityMutationProviderResult.validateReadbackC(
            omitted,
            EntityMutationReadbackRequirement.None
          )
        val omittedwithreadback =
          EntityMutationProviderResult.validateReadbackC(
            omitted,
            EntityMutationReadbackRequirement.AuthoritativeRecord
          )
        val authoritativewithreadback =
          EntityMutationProviderResult.validateReadbackC(
            authoritative,
            EntityMutationReadbackRequirement.AuthoritativeRecord
          )

        Then("acknowledgment is admitted only when readback is optional")
        omittedwithoutreadback shouldBe Consequence.success(omitted)
        omittedwithreadback shouldBe a[Consequence.Failure[?]]
        authoritativewithreadback shouldBe Consequence.success(authoritative)
      }

    }

    "select paths without weakening requested semantics" which {
      "prefer native compare-and-set while retaining guarded optimistic fallback" in {
        Given("guarded and native-CAS providers plus one optimistic mutation")
        val guarded = EntityMutationProviderCapabilities.guardedBaseline
        val native =
          guarded.copy(
            features =
              guarded.features +
                EntityMutationProviderFeature.OptimisticCompareAndSet
          )
        val optimistic =
          _request(
            policy = EntityMutationExecutionPolicy(
              concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
              observedRevision = Some(EntityRevision.INITIAL)
            )
          )

        When("the optimistic mutation is planned for each provider")
        val guardedpath =
          EntityMutationPathPlanner.selectC(guarded, optimistic)
        val nativepath =
          EntityMutationPathPlanner.selectC(native, optimistic)

        Then("native CAS is selected only when declared and guarded execution remains safe")
        guardedpath shouldBe Consequence.success(
          EntityMutationExecutionPath.GuardedVersionedFallback
        )
        nativepath shouldBe Consequence.success(
          EntityMutationExecutionPath.OptimisticCompareAndSet
        )
      }

      "select stronger guarded paths for write-if-changed and side-effect semantics" in {
        Given("the current guarded provider baseline and stronger mutation policies")
        val capabilities = EntityMutationProviderCapabilities.guardedBaseline
        val writeifchanged =
          _request(
            policy = EntityMutationExecutionPolicy(
              writePolicy = EntityWritePolicy.WriteIfChanged
            )
          )
        val optimisticwriteifchanged =
          _request(
            policy = EntityMutationExecutionPolicy(
              concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
              writePolicy = EntityWritePolicy.WriteIfChanged,
              observedRevision = Some(EntityRevision.INITIAL)
            )
          )
        val sideeffect = _request(hassideeffects = true)

        When("the planner resolves each policy independently")
        val comparisonpath =
          EntityMutationPathPlanner.selectC(capabilities, writeifchanged)
        val optimisticcomparisonpath =
          EntityMutationPathPlanner.selectC(
            capabilities,
            optimisticwriteifchanged
          )
        val sideeffectpath =
          EntityMutationPathPlanner.selectC(capabilities, sideeffect)

        Then("business comparison and atomic side-effect paths remain distinct")
        comparisonpath shouldBe Consequence.success(
          EntityMutationExecutionPath.GuardedBusinessStateComparison
        )
        optimisticcomparisonpath shouldBe Consequence.success(
          EntityMutationExecutionPath.GuardedBusinessStateComparison
        )
        sideeffectpath shouldBe Consequence.success(
          EntityMutationExecutionPath.AtomicSideEffectMutation
        )
      }

      "compose optimistic comparison side-effect and readback capabilities" in {
        Given("one mutation that requests every stronger provider semantic")
        val request =
          _request(
            policy = EntityMutationExecutionPolicy(
              concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
              writePolicy = EntityWritePolicy.WriteIfChanged,
              observedRevision = Some(EntityRevision.INITIAL)
            ),
            readbackrequirement =
              EntityMutationReadbackRequirement.AuthoritativeRecord,
            hassideeffects = true
          )
        val complete = EntityMutationProviderCapabilities.guardedBaseline
        val withoutguard =
          complete.copy(
            features =
              complete.features -
                EntityMutationProviderFeature.GuardedVersionedMutation
          )
        val withoutcomparison =
          complete.copy(
            features =
              complete.features -
                EntityMutationProviderFeature.BusinessStateComparison
          )
        val withoutreadback =
          complete.copy(
            features =
              complete.features -
                EntityMutationProviderFeature.AuthoritativeRecordResult
          )

        When("the complete and partial providers are planned")
        val selected = EntityMutationPathPlanner.selectC(complete, request)
        val rejected =
          Vector(withoutguard, withoutcomparison, withoutreadback).map(
            EntityMutationPathPlanner.selectC(_, request)
          )

        Then("the guarded atomic path retains every independently requested capability")
        selected shouldBe Consequence.success(
          EntityMutationExecutionPath.AtomicSideEffectMutation
        )
        all(rejected) shouldBe a[Consequence.Failure[?]]
      }

    }

    "preserve path invariants for generated capability sets" which {
      "reject missing strong capabilities instead of weakening mutation semantics" in {
        Given("a provider with only ordinary direct-update support")
        val capabilities =
          EntityMutationProviderCapabilities(
            Set(EntityMutationProviderFeature.DirectAlwaysWrite)
          )
        val optimistic =
          _request(
            policy = EntityMutationExecutionPolicy(
              concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
              observedRevision = Some(EntityRevision.INITIAL)
            )
          )
        val comparison =
          _request(
            policy = EntityMutationExecutionPolicy(
              writePolicy = EntityWritePolicy.WriteIfChanged
            )
          )
        val sideeffect = _request(hassideeffects = true)

        When("stronger semantics are requested")
        val results =
          Vector(optimistic, comparison, sideeffect).map(
            EntityMutationPathPlanner.selectC(capabilities, _)
          )

        Then("every unsupported request remains a structured failure")
        all(results) shouldBe a[Consequence.Failure[?]]
      }

      "never select a path whose required feature is absent for generated capability sets" in {
        Given("generated provider feature sets and ordinary readback requirements")
        val featuregen =
          Gen.someOf(EntityMutationProviderFeature.values.toVector).map(_.toSet)
        val readbackgen =
          Gen.oneOf(EntityMutationReadbackRequirement.values.toVector)
        val property = Prop.forAll(featuregen, readbackgen) {
          (features, readbackrequirement) =>
            val capabilities = EntityMutationProviderCapabilities(features)
            val result =
              EntityMutationPathPlanner.selectC(
                capabilities,
                _request(readbackrequirement = readbackrequirement)
              )
            result.toOption.forall {
              case EntityMutationExecutionPath.DirectAlwaysWrite =>
                features.contains(
                  EntityMutationProviderFeature.DirectAlwaysWrite
                ) &&
                  (
                    readbackrequirement ==
                      EntityMutationReadbackRequirement.None ||
                    features.contains(
                      EntityMutationProviderFeature.AuthoritativeRecordResult
                    )
                  )
              case EntityMutationExecutionPath.GuardedVersionedFallback =>
                features.contains(
                  EntityMutationProviderFeature.GuardedVersionedMutation
                ) &&
                  (
                    readbackrequirement ==
                      EntityMutationReadbackRequirement.None ||
                    features.contains(
                      EntityMutationProviderFeature.AuthoritativeRecordResult
                    )
                  )
              case _ =>
                false
            }
        }

        When("the capability invariant is checked")
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then("every successful ordinary path is backed by its declared features")
        checked.passed shouldBe true
      }

      "never select native compare-and-set without its provider feature" in {
        Given("generated provider feature sets and one optimistic mutation")
        val featuregen =
          Gen.someOf(EntityMutationProviderFeature.values.toVector).map(_.toSet)
        val request =
          _request(
            policy = EntityMutationExecutionPolicy(
              concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
              observedRevision = Some(EntityRevision.INITIAL)
            )
          )
        val property = Prop.forAll(featuregen) { features =>
          val capabilities = EntityMutationProviderCapabilities(features)
          EntityMutationPathPlanner
            .selectC(capabilities, request)
            .toOption
            .forall {
              case EntityMutationExecutionPath.OptimisticCompareAndSet =>
                features.contains(
                  EntityMutationProviderFeature.OptimisticCompareAndSet
                )
              case EntityMutationExecutionPath.GuardedVersionedFallback =>
                features.contains(
                  EntityMutationProviderFeature.GuardedVersionedMutation
                )
              case _ =>
                false
            }
        }

        When("the native-versus-guarded invariant is checked")
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then("every selected optimistic path is backed by its declared feature")
        checked.passed shouldBe true
      }

    }

    "expose installed provider capabilities through runtime space" which {
      "expose the installed provider contract through DataStoreSpace" in {
        Given("one standard versioned datastore and one datastore without the capability")
        val supportedspace = new DataStoreSpace()
          .useDataStore(DataStore.inMemorySearchable())
        val unsupportedspace = new DataStoreSpace()
          .useDataStore(DataStore.noop())
        val collection = DataStore.CollectionId("provider_contract")

        When("the runtime queries provider capabilities")
        val supported =
          supportedspace.entityMutationProviderCapabilities(collection)
        val unsupported =
          unsupportedspace.entityMutationProviderCapabilities(collection)

        Then("the in-memory native provider and explicit empty contract are reported")
        supported shouldBe Consequence.success(
          EntityMutationProviderCapabilities.guardedBaseline.copy(
            features =
              EntityMutationProviderCapabilities.guardedBaseline.features ++
                Set(
                  EntityMutationProviderFeature.DirectAlwaysWrite,
                  EntityMutationProviderFeature.OptimisticCompareAndSet
                )
          )
        )
        unsupported shouldBe Consequence.success(
          EntityMutationProviderCapabilities.empty
        )
      }
    }
  }

  private def _request(
    policy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default,
    readbackrequirement: EntityMutationReadbackRequirement =
      EntityMutationReadbackRequirement.None,
    hassideeffects: Boolean = false
  ): EntityMutationPathRequest =
    EntityMutationPathRequest(
      policy,
      readbackrequirement,
      hassideeffects
    )
}
