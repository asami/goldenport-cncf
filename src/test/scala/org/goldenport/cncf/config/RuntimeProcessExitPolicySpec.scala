package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeProcessExitPolicySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E2, rules:GCF09N-C1,C2,C3,C4, phase:55, slice:GCF-09N"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-runtime-process-exit-policy, example:E3, rules:GCF09N-C1,C3,C4, phase:55, slice:GCF-09N"
  )

  "Runtime process-exit policy" should {
    "project only admitted Global Boolean bindings" which {
      "E2 default absence, aliases, and direct CLI controls without raw configuration" must _e1 {
        "when one resolved Global collection is projected and adapter flags are consumed" in {
          Given("absent bindings, every decode-only alias, and direct process-exit switches")
          val absent = _take(RuntimeProcessExitPolicy.from(_take(_collection(Vector.empty))))
          val forcealiases = Vector(
            CncfConfigurationParameterCatalog.FORCE_EXIT_KEY,
            "textus.runtime.force-exit",
            "cncf.force-exit",
            "cncf.runtime.force-exit"
          )
          val noexitaliases = Vector(
            CncfConfigurationParameterCatalog.NO_EXIT_KEY,
            "textus.runtime.no-exit",
            "cncf.no-exit",
            "cncf.runtime.no-exit"
          )

          When("the typed projection and external-flag adapter are invoked")
          val forces = forcealiases.map(key => _take(RuntimeProcessExitPolicy.from(_take(_collection(Vector(key -> ConfigurationValue.StringValue("yes")))))))
          val noexits = noexitaliases.map(key => _take(RuntimeProcessExitPolicy.from(_take(_collection(Vector(key -> ConfigurationValue.BooleanValue(true)))))))
          val (direct, residual) = RuntimeProcessExitPolicy.admitArguments(
            _take(RuntimeProcessExitPolicy.from(_take(_collection(Vector(
              CncfConfigurationParameterCatalog.FORCE_EXIT_KEY -> ConfigurationValue.BooleanValue(false),
              CncfConfigurationParameterCatalog.NO_EXIT_KEY -> ConfigurationValue.BooleanValue(false)
            ))))),
            Array("--force-exit", "command", "--no-exit")
          )

          Then("absence defaults false, aliases share witnesses, flags only enable, and force exit dominates")
          absent shouldBe RuntimeProcessExitPolicy.default
          forces.foreach(_.forceExit shouldBe true)
          forces.foreach(_.noExit shouldBe false)
          noexits.foreach(_.forceExit shouldBe false)
          noexits.foreach(_.noExit shouldBe true)
          direct shouldBe RuntimeProcessExitPolicy(forceExit = true, noExit = true)
          residual.toVector shouldBe Vector("command")
          direct.disposition(0) shouldBe RuntimeProcessExitPolicy.Disposition.Exit
          RuntimeProcessExitPolicy(noExit = true).disposition(3) shouldBe RuntimeProcessExitPolicy.Disposition.Fail
          RuntimeProcessExitPolicy(noExit = true).disposition(0) shouldBe RuntimeProcessExitPolicy.Disposition.Return
        }
      }

      "E3 reject malformed, wrong-target, and canonical-alias-collision values structurally" must _e2 {
        "when process-exit input enters the catalog decoder" in {
          Given("invalid Boolean text, a Subsystem target, and one Global collision domain")

          When("each form is admitted")
          val malformed = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.FORCE_EXIT_KEY, ConfigurationValue.StringValue("sometimes"), CncfConfigurationTarget.Global, "malformed")
          ))
          val wrongtarget = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.NO_EXIT_KEY, ConfigurationValue.BooleanValue(true), _subsystem_target, "subsystem")
          ))
          val collision = CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            _batch(CncfConfigurationParameterCatalog.FORCE_EXIT_KEY, ConfigurationValue.BooleanValue(true), CncfConfigurationTarget.Global, "canonical", "process-exit"),
            _batch("cncf.force-exit", ConfigurationValue.BooleanValue(false), CncfConfigurationTarget.Global, "alias", "process-exit")
          ))

          Then("all invalid input fails before value-only policy projection")
          malformed.isSuccess shouldBe false
          wrongtarget.isSuccess shouldBe false
          collision.isSuccess shouldBe false
        }
      }
    }
  }

  private def _collection(
    entries: Vector[(String, ConfigurationValue)]
  ): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    for {
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
        CncfConfigurationDocumentBatch(
          CncfConfigurationDocumentLocation.Global,
          _take(ConfigurationSourceAdmission.create(
            ConfigurationOrigin.Home,
            "home",
            "runtime-process-exit-policy-spec",
            10,
            "runtime-process-exit-policy-spec",
            () => Consequence.success(ConfigurationDocument.Object(entries.map { case (key, value) =>
              ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
            }))
          ))
        )
      ))
      context <- CncfConfigurationResolutionContext.globalOnly
      collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
    } yield collection

  private def _subsystem_target: CncfConfigurationTarget.SubsystemInstance = {
    val identity = _take(SubsystemInstanceId.create("platform", "default"))
    _take(CncfConfigurationTarget.SubsystemInstance.create(identity))
  }

  private def _batch(
    key: String,
    value: ConfigurationValue,
    target: CncfConfigurationTarget,
    source: String,
    collisiondomain: String = "runtime-process-exit-policy-spec"
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      _location(target),
      _take(ConfigurationSourceAdmission.create(
        ConfigurationOrigin.Home,
        "home",
        source,
        10,
        collisiondomain,
        () => Consequence.success(ConfigurationDocument.Object(Vector(
          ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
        )))
      ))
    )

  private def _location(target: CncfConfigurationTarget): CncfConfigurationDocumentLocation =
    target match {
      case CncfConfigurationTarget.Global => CncfConfigurationDocumentLocation.Global
      case value: CncfConfigurationTarget.SubsystemInstance =>
        new CncfConfigurationDocumentLocation.SubsystemInstance(value)
      case _ => fail("process-exit policy test only requires Global and Subsystem locations")
    }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
