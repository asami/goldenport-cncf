package org.goldenport.cncf.tag

import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.builtin.tag.TagComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.{WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for hierarchical Tag master and TagAttachment.
 *
 * @since   May.  5, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class TagModelSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "TagRepository" should {
    "create a tag tree per tag space and expand descendants" in {
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()

      val root = _success(repository.create(TagCreate(None, "phase20root", None, tagSpace = TagSpace.Blog, title = Some("Phase 20 Root"))))
      val child = _success(repository.create(TagCreate(None, "scala", Some(root.id), tagSpace = TagSpace.Blog, usageKind = TagUsageKind.Cms)))
      val otherSpace = _success(repository.create(TagCreate(None, "scala", None, tagSpace = TagSpace.Operational)))

      val blogTree = _success(repository.tree(TagSpace.Blog))

      blogTree.resolve("phase20root").map(_.id) shouldBe Consequence.success(root.id)
      blogTree.resolve("phase20root.scala").map(_.id) shouldBe Consequence.success(child.id)
      blogTree.descendantsOf(root).map(_.id) shouldBe Vector(root.id, child.id)
      blogTree.tags.map(_.id) should not contain otherSpace.id
      child.toRecord.getString("tagSpace") shouldBe Some(TagSpace.Blog)
      child.toRecord.getString("path") shouldBe Some("phase20root.scala")
    }

    "reject duplicate sibling keys and missing parents" in {
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "dup-root", None, tagSpace = "dup-space")))
      _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "dup-space")))

      repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "dup-space")) shouldBe a[Consequence.Failure[_]]
      repository.create(TagCreate(None, "bad child", None, tagSpace = "dup-space")) shouldBe a[Consequence.Failure[_]]
    }

    "publish master descriptor with tag-specific resident tree handled outside entity working set" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val tag = subsystem.findComponent(TagComponent.name).getOrElse(fail("Tag component is missing"))
      val descriptor = tag.entityRuntimeDescriptor("tag").getOrElse(fail("Tag runtime descriptor is missing"))

      descriptor.entityKind.label shouldBe "master"
      descriptor.effectiveWorkingSetPolicy shouldBe Some(WorkingSetPolicy.Disabled)
      descriptor.effectiveWorkingSetPolicySource shouldBe Some(WorkingSetPolicySource.Code)
    }
  }

  "TaggingWorkflow" should {
    "attach tags idempotently and search descendants through TagAttachment" in {
      given ExecutionContext = ExecutionContext.test()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "search-root", None, tagSpace = "search-space")))
      val child = _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "search-space")))
      val workflow = TaggingWorkflow(tagSpace = "search-space")

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

      first.targetEntityId shouldBe child.id.value
      second.targetEntityId shouldBe child.id.value
      stored.size shouldBe 1
      matched should contain ("entity-1")
      direct should not contain "entity-1"
    }

    "merge explicit workflow tag space with execution context tag spaces" in {
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

      _success(workflow.attach("entity-blog", blog.path))
      _success(workflow.attach("entity-ops", operational.path))

      _success(workflow.searchSourceIds(blog.path)) should contain ("entity-blog")
      _success(workflow.searchSourceIds(operational.path)) should contain ("entity-ops")
    }

    "generic tag_search_entities should load visible source entities through EntityStore" in {
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
      val sourceId = source.getString("id").getOrElse(fail("source tag id is missing"))
      val classifierPath = classifier.getString("path").getOrElse(fail("classifier tag path is missing"))
      _success(subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", sourceId),
        Argument("tagRef", classifierPath),
        Argument("tagSpace", "tag-search-op")
      )))
      _success(subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", "not-an-entity-id"),
        Argument("tagRef", classifierPath),
        Argument("tagSpace", "tag-search-op")
      )))
      val listed = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_list_entity_tags",
        Argument("sourceEntityId", sourceId),
        Argument("tagSpace", "tag-search-op")
      ))))

      val response = _record(_success(subsystem.executeOperationResponse(Request.of(
        component = "tag",
        service = "tag",
        operation = "tag_search_entities",
        arguments = List(
          Argument("component", "tag"),
          Argument("entity", "tag"),
          Argument("tagSpace", "tag-search-op"),
          Argument("tagRef", classifierPath)
        )
      ))))
      val data = response.getVector("data").getOrElse(Vector.empty)

      withClue(s"listed=$listed response=$response") {
        data should have size 1
        data.head.asInstanceOf[Record].getString("id") shouldBe Some(sourceId)
      }
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
