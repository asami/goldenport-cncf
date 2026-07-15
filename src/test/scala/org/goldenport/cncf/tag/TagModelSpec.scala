package org.goldenport.cncf.tag

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.builtin.tag.TagComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.{WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for hierarchical Tag master and TagAttachment.
 *
 * @since   May.  5, 2026
 *  version May.  5, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class TagModelSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "TagRepository" should {
    "create a tag tree per tag space and expand descendants" in {
      Given("a repository with tags in two tag spaces")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()

      When("a hierarchy is created and the blog tree is loaded")
      val root = _success(repository.create(TagCreate(None, "phase20root", None, tagSpace = TagSpace.Blog, title = Some("Phase 20 Root"))))
      val child = _success(repository.create(TagCreate(None, "scala", Some(root.id), tagSpace = TagSpace.Blog, usageKind = TagUsageKind.Cms)))
      val otherspace = _success(repository.create(TagCreate(None, "scala", None, tagSpace = TagSpace.Operational)))
      val blogtree = _success(repository.tree(TagSpace.Blog))

      Then("resolution and descendants stay within the requested tag space")
      blogtree.resolve("phase20root").map(_.id) shouldBe Consequence.success(root.id)
      blogtree.resolve("phase20root.scala").map(_.id) shouldBe Consequence.success(child.id)
      blogtree.descendantsOf(root).map(_.id) shouldBe Vector(root.id, child.id)
      blogtree.tags.map(_.id) should not contain otherspace.id
      child.toRecord.getString("tagSpace") shouldBe Some(TagSpace.Blog)
      child.toRecord.getString("path") shouldBe Some("phase20root.scala")
    }

    "reject duplicate sibling keys and invalid keys" in {
      Given("one existing child under a valid parent")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "dup-root", None, tagSpace = "dup-space")))
      _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "dup-space")))

      When("a duplicate sibling and an invalid key are requested")
      val duplicate = repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "dup-space"))
      val invalid = repository.create(TagCreate(None, "bad child", None, tagSpace = "dup-space"))

      Then("both invalid creations fail")
      duplicate shouldBe a[Consequence.Failure[_]]
      invalid shouldBe a[Consequence.Failure[_]]
    }

    "update mutable metadata without changing path" in {
      Given("an existing nested Tag")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "update-root", None, tagSpace = "update-space")))
      val tag = _success(repository.create(TagCreate(None, "target", Some(root.id), tagSpace = "update-space", usageKind = TagUsageKind.General)))

      When("its mutable metadata is updated")
      val updated = _success(repository.update(tag.path, TagUpdate(
        title = Some("Updated title"),
        description = Some("Updated description"),
        usageKind = Some(TagUsageKind.Navigation),
        sortOrder = Some(7),
        attributes = Some(Map("scope" -> "admin"))
      )))
      val tree = _success(repository.tree("update-space"))

      Then("metadata changes while identity and hierarchy remain stable")
      updated.id shouldBe tag.id
      updated.key shouldBe "target"
      updated.parentTagId shouldBe Some(root.id)
      updated.path shouldBe "update-root.target"
      updated.title shouldBe Some("Updated title")
      updated.description shouldBe Some("Updated description")
      updated.usageKind shouldBe TagUsageKind.Navigation
      updated.sortOrder shouldBe Some(7)
      updated.attributes should contain ("scope" -> "admin")
      tree.resolve("update-root.target").map(_.title) shouldBe Consequence.success(Some("Updated title"))
    }

    "derive update and move timestamps from the operational execution clock" in {
      Given("generated fixed operational clock instants")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val epoch = 1_700_000_000L + Math.floorMod(epochoffset.toLong, 1_000_000L)
        val instant = Instant.ofEpochSecond(epoch)
        given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))
        val repository = TagRepository.entityStore()
        val space = s"clock$epoch"
        val root = _success(repository.create(TagCreate(None, "root", None, tagSpace = space)))
        val target = _success(repository.create(TagCreate(None, "target", Some(root.id), tagSpace = space)))
        val destination = _success(repository.create(TagCreate(None, "destination", None, tagSpace = space)))

        val updated = _success(repository.update(target.id.value, TagUpdate(title = Some("fixed"))))
        val moved = _success(repository.move(target.id.value, Some(destination.id.value), None))

        updated.updatedAt == instant && moved.updatedAt == instant
      }

      When("each Tag is updated and moved")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("both component-visible timestamps always equal the selected execution time")
      checked.passed shouldBe true
    }

    "move a tag and recompute descendant paths" in {
      Given("a Tag hierarchy with a child and grandchild")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "move-root", None, tagSpace = "move-space")))
      val next = _success(repository.create(TagCreate(None, "next-root", None, tagSpace = "move-space")))
      val child = _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "move-space")))
      val grandchild = _success(repository.create(TagCreate(None, "leaf", Some(child.id), tagSpace = "move-space")))

      When("the child is moved and renamed")
      val moved = _success(repository.move(child.id.value, Some(next.path), Some("renamed")))
      val tree = _success(repository.tree("move-space"))

      Then("the moved Tag and all descendant paths are recomputed")
      moved.id shouldBe child.id
      moved.parentTagId shouldBe Some(next.id)
      moved.key shouldBe "renamed"
      moved.path shouldBe "next-root.renamed"
      tree.resolve("next-root.renamed").map(_.id) shouldBe Consequence.success(child.id)
      tree.resolve("next-root.renamed.leaf").map(_.id) shouldBe Consequence.success(grandchild.id)
    }

    "reject invalid tag moves" in {
      Given("Tag trees containing a descendant, another tag space, and a duplicate key")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "invalid-move-root", None, tagSpace = "invalid-move-space")))
      val other = _success(repository.create(TagCreate(None, "other-root", None, tagSpace = "invalid-move-space")))
      val foreign = _success(repository.create(TagCreate(None, "foreign-root", None, tagSpace = "foreign-move-space")))
      val child = _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "invalid-move-space")))
      _success(repository.create(TagCreate(None, "duplicate", Some(other.id), tagSpace = "invalid-move-space")))

      When("cycle, cross-space, duplicate, and invalid-key moves are requested")
      val cycle = repository.move(root.id.value, Some(child.id.value), None)
      val crossspace = repository.move(child.id.value, Some(foreign.id.value), None)
      val duplicate = repository.move(child.id.value, Some(other.id.value), Some("duplicate"))
      val invalid = repository.move(child.id.value, Some(other.id.value), Some("bad key"))

      Then("every invalid move fails")
      cycle shouldBe a[Consequence.Failure[_]]
      crossspace shouldBe a[Consequence.Failure[_]]
      duplicate shouldBe a[Consequence.Failure[_]]
      invalid shouldBe a[Consequence.Failure[_]]
    }

    "publish master descriptor with tag-specific resident tree handled outside entity working set" in {
      Given("the default subsystem with its built-in Tag component")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val tag = subsystem.findComponent(TagComponent.name).getOrElse(fail("Tag component is missing"))

      When("the Tag runtime descriptor is inspected")
      val descriptor = tag.entityRuntimeDescriptor("tag").getOrElse(fail("Tag runtime descriptor is missing"))

      Then("Tag remains a master with its generic entity working set disabled")
      descriptor.entityKind.label shouldBe "master"
      descriptor.effectiveWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.effectiveWorkingSetPolicySource shouldBe Some(WorkingSetPolicySource.Code)
    }
  }

  "TaggingWorkflow" should {
    "attach tags idempotently and search descendants through TagAttachment" in {
      Given("an Entity, a Tag hierarchy, and a TaggingWorkflow")
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "search-root", None, tagSpace = "search-space")))
      val child = _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "search-space")))
      val workflow = TaggingWorkflow(tagSpace = "search-space")

      When("the same Tag is attached twice and searched through its parent")
      val first = _success(workflow.attach("entity-1", child.path))
      val second = _success(workflow.attach("entity-1", child.path))
      val matched = _success(workflow.searchSourceIds(root.path, includeDescendants = true, role = Some("tag")))
      val direct = _success(workflow.searchSourceIds(root.path, includeDescendants = false, role = Some("tag")))
      val stored = _success(AssociationRepository.entityStore(AssociationStoragePolicy.tagAttachmentDefault).list(
        AssociationFilter(
          domain = AssociationDomain.TagAttachment,
          sourceEntityId = Some("entity-1"),
          targetKind = Some("tag"),
          role = Some("tag")
        )
      ))

      Then("one association remains and descendant search finds the Entity")
      first.targetEntityId shouldBe child.id.value
      second.targetEntityId shouldBe child.id.value
      stored.size shouldBe 1
      matched should contain ("entity-1")
      direct should not contain "entity-1"
    }

    "merge explicit workflow tag space with execution context tag spaces" in {
      Given("explicit and execution-context Tag spaces")
      val base = ExecutionContext.test()
      val ctx = ExecutionContext.withTagSpaces(
        base,
        ExecutionContext.TagSpaceContext(
          subsystem = Vector(TagSpace.Operational),
          component = Vector("component-space"),
          user = Vector("user.alice")
        )
      )
      given ExecutionContext = ctx
      val repository = TagRepository.entityStore()
      val blog = _success(repository.create(TagCreate(None, "blog-only", None, tagSpace = TagSpace.Blog)))
      val operational = _success(repository.create(TagCreate(None, "ops-only", None, tagSpace = TagSpace.Operational)))
      val workflow = TaggingWorkflow(tagSpace = TagSpace.Blog)

      When("Tags from both effective spaces are attached and searched")
      _success(workflow.attach("entity-blog", blog.path))
      _success(workflow.attach("entity-ops", operational.path))
      val blogmatches = _success(workflow.searchSourceIds(blog.path))
      val operationalmatches = _success(workflow.searchSourceIds(operational.path))

      Then("the workflow resolves both explicit and context-provided spaces")
      blogmatches should contain ("entity-blog")
      operationalmatches should contain ("entity-ops")
    }

    "load visible source entities through generic tag_search_entities" in {
      Given("a source Tag associated with a classifier Tag and one invalid source ID")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val source = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "tag-search-source"),
        Argument("tagSpace", "tag-search-op")
      ))))
      val classifier = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "tag-search-classifier"),
        Argument("tagSpace", "tag-search-op")
      ))))
      val sourceid = source.getString("id").getOrElse(fail("source tag id is missing"))
      val classifierpath = classifier.getString("path").getOrElse(fail("classifier tag path is missing"))
      _success(subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", sourceid),
        Argument("tagRef", classifierpath),
        Argument("tagSpace", "tag-search-op")
      )))
      _success(subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", "not-an-entity-id"),
        Argument("tagRef", classifierpath),
        Argument("tagSpace", "tag-search-op")
      )))
      val listed = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_list_entity_tags",
        Argument("sourceEntityId", sourceid),
        Argument("tagSpace", "tag-search-op")
      ))))

      When("generic tag_search_entities loads matching source Entities")
      val response = _record(_success(subsystem.executeOperationResponse(Request.of(
        component = "tag",
        service = "tag",
        operation = "tag_search_entities",
        arguments = List(
          Argument("component", "tag"),
          Argument("entity", "tag"),
          Argument("tagSpace", "tag-search-op"),
          Argument("tagRef", classifierpath)
        )
      ))))
      val data = response.getVector("data").getOrElse(Vector.empty)

      Then("only the valid visible source Entity is returned")
      withClue(s"listed=$listed response=$response") {
        data should have size 1
        data.head.asInstanceOf[Record].getString("id") shouldBe Some(sourceid)
      }
    }

    "scope tag_update and tag_move by requested tagSpace" in {
      Given("ambiguous Tag paths in blog and operational spaces")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "same"),
        Argument("tagSpace", TagSpace.Blog)
      ))))
      _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "same"),
        Argument("tagSpace", TagSpace.Operational)
      ))))
      _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "newparent"),
        Argument("tagSpace", TagSpace.Blog)
      ))))

      When("update and move explicitly select the blog Tag space")
      val updated = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_update",
        Argument("tagPath", "same"),
        Argument("tagSpace", TagSpace.Blog),
        Argument("title", "Blog scoped")
      ))))
      val moved = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_move",
        Argument("tagPath", "same"),
        Argument("tagSpace", TagSpace.Blog),
        Argument("newParentTagRef", "newparent"),
        Argument("newKey", "renamed")
      ))))

      Then("only the blog Tag is changed")
      updated.getString("title") shouldBe Some("Blog scoped")
      moved.getString("tagSpace") shouldBe Some(TagSpace.Blog)
      moved.getString("path") shouldBe Some("newparent.renamed")
    }
  }

  private def _tag_request(operation: String, arguments: Argument*): Request =
    Request.of(
      component = "tag",
      service = "tag",
      operation = operation,
      arguments = arguments.toList
    )

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"unexpected response: $other")
    }
}
