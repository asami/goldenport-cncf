package org.goldenport.cncf.association

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the generic Association runtime foundation.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class AssociationRepositorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "AssociationRepository" should {
    "persist and query associations through domain-specific storage" in {
      Given("an AssociationRepository with Blob attachment storage policy")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)

      When("creating a Blob attachment association")
      val created = _success(repository.create(
        AssociationCreate(
          id = None,
          associationId = "assoc-1",
          sourceEntityId = "product-1",
          targetEntityId = "blob-1",
          targetKind = Some("blob"),
          role = "mainImage",
          associationDomain = AssociationDomain.BlobAttachment,
          sortOrder = Some(1),
          collectionId = AssociationStoragePolicy.BlobAttachmentCollection
        )
      ))

      Then("the association is persisted as a framework entity")
      created.associationId shouldBe "assoc-1"
      created.id.collection shouldBe AssociationStoragePolicy.BlobAttachmentCollection

      When("querying by source and role")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some("product-1"),
          targetKind = Some("blob"),
          role = Some("mainImage")
        )
      ))

      Then("the created association is returned")
      values.map(_.targetEntityId) shouldBe Vector("blob-1")
    }

    "return all matching associations from the store" in {
      Given("an AssociationRepository with Blob attachment storage policy")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)

      When("creating multiple associations for the same source")
      (1 to 3).foreach { i =>
        _success(repository.create(
          AssociationCreate(
            id = None,
            associationId = s"assoc-ws-$i",
            sourceEntityId = "article-1",
            targetEntityId = s"blob-ws-$i",
            targetKind = Some("blob"),
            role = "galleryImage",
            associationDomain = AssociationDomain.BlobAttachment,
            sortOrder = Some(i),
            collectionId = AssociationStoragePolicy.BlobAttachmentCollection
          )
        ))
      }

      Then("store-backed lookup returns every matching association")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some("article-1"),
          targetKind = Some("blob"),
          role = Some("galleryImage")
        )
      ))
      values.map(_.targetEntityId) shouldBe Vector("blob-ws-1", "blob-ws-2", "blob-ws-3")
    }

    "delete only the association row" in {
      Given("an existing association")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val created = _success(repository.create(
        AssociationCreate(
          id = None,
          associationId = "assoc-delete",
          sourceEntityId = "product-delete",
          targetEntityId = "blob-delete",
          targetKind = Some("blob"),
          role = "attachment",
          associationDomain = AssociationDomain.BlobAttachment,
          sortOrder = None,
          collectionId = AssociationStoragePolicy.BlobAttachmentCollection
        )
      ))

      When("deleting the association")
      _success(repository.delete(created))

      Then("the association is no longer visible in normal list queries")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some("product-delete"),
          targetKind = Some("blob"),
          role = Some("attachment")
        )
      ))
      values shouldBe Vector.empty
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
