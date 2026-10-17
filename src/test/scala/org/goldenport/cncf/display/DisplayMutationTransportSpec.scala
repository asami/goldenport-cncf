package org.goldenport.cncf.display

import logicalui.DisplayMutationContractFixture
import org.goldenport.Consequence
import org.goldenport.cncf.entity.*
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class DisplayMutationTransportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutation.*
  import DisplayMutationContractFixture.*
  private def _taxonomy[A](result: Consequence[A]): Taxonomy = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.taxonomy
    case _ => fail("expected structured transport failure")
  }
  "E1 T1 strict header metadata" should afterWord("in spec:display-mutation, example:E1, rules:R3, phase:96") {
    "bind canonical observations and existing strict policies preserving ordered header evidence" in {
      Vector(Operation.Update, Operation.Delete).foreach { operation =>
        Given("docs/spec/display-mutation.md R3 E1: one real-header-compatible mixed-case strong tag with HTTP whitespace")
        val revision = resource(revision = Long.MaxValue).revision
        val tag = EntityRevisionTransport.entityTag(revision)
        val headers = Vector("Accept" -> "application/json", "iF-mAtCh" -> (" \t" + tag + "\t "), "X-Trace" -> "ignored")
        When("the pure Display adapter binds ordered headers")
        val metadata = success(DisplayMutationTransport.bindC(operation, headers))
        Then("one canonical property and exact observed-required optimistic policy are retained")
        metadata.operation shouldBe operation
        metadata.observedRevision shouldBe Some(revision)
        metadata.properties shouldBe Record.empty.upsertSingle(EntityRevisionTransport.observedRevisionPropertyName, revision.value)
        val policy = metadata.executionPolicy.get
        policy.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
        policy.writePolicy shouldBe (if (operation == Operation.Update) EntityWritePolicy.WriteIfChanged else EntityWritePolicy.AlwaysWrite)
        policy.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
        policy.observedRevision shouldBe Some(revision)
        policy.validateC.isSuccess shouldBe true
      }
      Given("docs/spec/display-mutation.md R3 E1: Create has only unrelated valid headers")
      When("Create metadata is bound")
      val created = success(DisplayMutationTransport.bindC(Operation.Create, Vector("Accept" -> "application/json")))
      Then("Create has no observation property or mutation execution policy")
      created.observedRevision shouldBe None
      created.executionPolicy shouldBe None
      created.properties shouldBe Record.empty
    }
  }
  "E2 T2 invalid header shapes" should afterWord("in spec:display-mutation, example:E2, rules:R3, phase:96") {
    "reject missing duplicate noncanonical weak and malformed observations without first-wins" in {
      Given("docs/spec/display-mutation.md R3 E2: Update and Delete without If-Match")
      When("both missing observations are bound")
      val missing = Vector(Operation.Update, Operation.Delete).map(operation => DisplayMutationTransport.bindC(operation, Vector.empty))
      Then("missing observation uses the existing missing taxonomy")
      missing.foreach(result => _taxonomy(result) shouldBe Taxonomy.argumentMissing)
      val values = Vector("W/\"revision-7\"", "*", "\"revision-7\", \"revision-8\"", "\"revision-07\"",
        "\"revision-0\"", "\"revision--1\"", "\"revision-9223372036854775808\"", "revision-7",
        "\"revision-+7\"", "\"revision- 7\"", "\n\"revision-7\"", "\"revision-7\"\r", "")
      values.foreach { value =>
        Given("docs/spec/display-mutation.md R3 E2: one malformed or noncanonical strong-tag candidate")
        When("Update and Delete bind that observation")
        val rejected = Vector(Operation.Update, Operation.Delete).map(operation =>
          DisplayMutationTransport.bindC(operation, Vector("If-Match" -> value)))
        Then("format failure rejects without parsing fallback or revision coercion")
        rejected.foreach(result => _taxonomy(result) shouldBe Taxonomy.argumentFormatError)
      }
      val headers = Vector(
        Vector("If-Match" -> "\"revision-7\"", "if-match" -> "\"revision-7\""),
        Vector("If-Match" -> "\"revision-7\"", "If-Match" -> "\"revision-8\""),
        null, Vector(null), Vector((null: String) -> "value"), Vector("If-Match" -> null),
        Vector("Bad Header" -> "value"), Vector("" -> "value"))
      headers.foreach { candidate =>
        Given("docs/spec/display-mutation.md R3 E2: duplicate or malformed ordered header container")
        When("the pure adapter binds the container")
        val rejected = DisplayMutationTransport.bindC(Operation.Update, candidate)
        Then("the exact container cannot become first-wins metadata")
        _taxonomy(rejected) shouldBe Taxonomy.argumentFormatError
      }
      Given("docs/spec/display-mutation.md R3 E2: null operation and prohibited Create observation")
      When("the two malformed requests are bound")
      val nulloperation = DisplayMutationTransport.bindC(null, Vector.empty)
      val createheader = DisplayMutationTransport.bindC(Operation.Create, Vector("If-Match" -> "\"revision-7\""))
      Then("both reject with structured format errors")
      _taxonomy(nulloperation) shouldBe Taxonomy.argumentFormatError
      _taxonomy(createheader) shouldBe Taxonomy.argumentFormatError
    }
  }
  "E3 T3 policy and operation separation" should afterWord("in spec:display-mutation, example:E3, rules:R1,R3, phase:96") {
    "exclude substituted operations and weakened managed defaults from prepared values" in {
      Given("docs/spec/display-mutation.md R1,R3 E3: strict admitted metadata and unchanged managed framework defaults")
      val trace = new Trace
      val binding = success(bindingC(Some(trace), positive = true))
      val request = update(binding, Vector(FieldInput(title, DisplayValue.Text("valid"))))
      val delete: Request.Delete = Request.Delete(binding.selection, resource().entityId)
      When("metadata is substituted between Update Delete and Create")
      val updatefromdelete = binding.prepareUpdateC(request, seed, resource(), transport(Operation.Delete), Availability.enabled, authorize())
      val deletefromupdate = binding.prepareDeleteC(delete, resource(), transport(), Availability.enabled, true, authorize())
      val updatefromcreate = binding.prepareUpdateC(request, seed, resource(),
        success(DisplayMutationTransport.bindC(Operation.Create, Vector.empty)), Availability.enabled, authorize())
      Then("each operation substitution rejects before converters or domain validation")
      Vector(updatefromdelete.isSuccess, deletefromupdate.isSuccess, updatefromcreate.isSuccess) shouldBe Vector(false, false, false)
      trace.calls shouldBe Vector.empty
      transport().executionPolicy should not be Some(EntityMutationExecutionPolicy.default)
      transport().executionPolicy.get.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
      EntityMutationExecutionPolicy.default.preconditionPolicy shouldBe RevisionPreconditionPolicy.Managed
      Given("docs/spec/display-mutation.md R1,R3 E3: Metadata has no candidate constructor or copy injection API")
      When("external compile-time probes try injecting arbitrary policies")
      val probes = verifyTypeBoundary()
      Then("private admitted Metadata prevents weakened nonoptimistic or managed policy entry")
      probes shouldBe (())
    }
  }
}
