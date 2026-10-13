package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class ComponentRecordStorageDeclarationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _owner = ComponentId("org.example.Workflow")
  private val _collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", "registrations"))
  private val _grant = ComponentRecordStorageDeclaration.CreateGrant(_owner, "workflow", _collection)
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Declaration rejected: $failure")
  }

  "Component record storage declaration" should {
    "retain only the exact declared Create scope without exposing physical storage" in {
      Given("an exact Component, logical store and entity collection declaration")
      val declaration = _take(ComponentRecordStorageDeclaration.createC(Vector(_grant)))
      When("trusted composition checks its exact tuple")
      val admitted = declaration.admitC(_owner, "workflow", _collection)
      Then("only immutable grant data is returned")
      _take(admitted) shouldBe _grant
      declaration.grants shouldBe Vector(_grant)
    }

    "reject incomplete, noncanonical and ambiguous physical collection ownership" in {
      Given("malformed grants and two owners targeting the same collection")
      val inputs = Vector(
        null, Vector.empty, Vector(null), Vector(_grant.copy(componentId = null)),
        Vector(_grant.copy(storeName = "Workflow")), Vector(_grant.copy(collection = null)),
        Vector(_grant, _grant.copy(componentId = ComponentId("org.example.Other"))),
        Vector.fill(ComponentRecordStorageDeclaration.MAX_GRANTS + 1)(_grant))
      When("host declaration data is validated without a provider")
      val results = inputs.map(ComponentRecordStorageDeclaration.createC)
      Then("every ambiguous or malformed declaration fails before storage can be accessed")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "exclude infrastructure collections independently of an otherwise valid grant" in {
      Given("a valid grant and host-owned reserved targets")
      val declaration = _take(ComponentRecordStorageDeclaration.createC(Vector(_grant)))
      val other = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", "instances"))
      When("composition checks both disjoint and overlapping target sets")
      val disjoint = declaration.validateReservedC(Vector(other))
      val overlapping = declaration.validateReservedC(Vector(other, _collection))
      Then("the reserved target cannot receive a component record Create grant")
      disjoint.isSuccess shouldBe true
      overlapping shouldBe a[Consequence.Failure[?]]
      declaration.validateReservedC(null) shouldBe a[Consequence.Failure[?]]
    }

    "reject sampled foreign owner, store and collection tuples without widening a grant" in {
      Given("one fixed declaration and distinct sampled foreign scopes")
      val declaration = _take(ComponentRecordStorageDeclaration.createC(Vector(_grant)))
      val property = Prop.forAll(Gen.chooseNum(1, 1000)) { suffix =>
        val foreign = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "records", s"foreign$suffix"))
        !declaration.admitC(ComponentId(s"org.example.Other$suffix"), "workflow", _collection).isSuccess &&
          !declaration.admitC(_owner, s"other$suffix", _collection).isSuccess &&
          !declaration.admitC(_owner, "workflow", foreign).isSuccess
      }
      When("the exact-scope property is checked")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)
      Then("each changed dimension remains outside the declaration")
      checked.passed shouldBe true
    }
  }
}
