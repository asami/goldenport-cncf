package org.goldenport.cncf.tag

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.builtin.tag.TagComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport
}
import org.goldenport.cncf.entity.runtime.{WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for hierarchical Tag master and TagAttachment.
 *
 * @since   May.  5, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class TagModelSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "TagRepository" should {
    "create a tag tree per tag space and expand descendants" in {
      Given("a repository with tags in two tag spaces")
      given ExecutionContext = _execution_context()
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
      given ExecutionContext = _execution_context()
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
      given ExecutionContext = _execution_context()
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
        given ExecutionContext =
          _execution_context(Clock.fixed(instant, ZoneOffset.UTC))
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
      given ExecutionContext = _execution_context()
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
      given ExecutionContext = _execution_context()
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

    "reject foreign canonical ids rather than rebinding them to the Tag collection" in {
      Given("a Tag record and repository lookup with a foreign exact EntityId")
      given ExecutionContext = _execution_context()
      val foreign = EntityId("cncf", "foreign_tag", EntityCollectionId("cncf", "builtin", "blob"))
      val record = Record.dataAuto(
        "id" -> foreign.value,
        "key" -> "foreign",
        "tagSpace" -> "identity-space",
        "path" -> "foreign",
        "createdAt" -> "2026-07-30T00:00:00Z",
        "updatedAt" -> "2026-07-30T00:00:00Z"
      )

      When("the Tag codec and repository receive that id")
      val decoded = TagRecordCodec.fromStoreRecord(record)
      val loaded = TagRepository.entityStore().load(foreign)

      Then("both fail instead of changing its collection to tag")
      decoded shouldBe a[Consequence.Failure[_]]
      loaded shouldBe a[Consequence.Failure[_]]
    }

    "reject a present malformed parent Tag reference instead of dropping it" in {
      Given("a canonical Tag record with a malformed optional parent reference")
      val id = EntityId(
        TagEntityCollections.Tag.major,
        TagEntityCollections.Tag.minor,
        TagEntityCollections.Tag,
        entropy = Some("malformed_parent")
      )
      val record = Record.dataAuto(
        "id" -> id.value,
        "key" -> "malformed-parent",
        "tagSpace" -> "identity-space",
        "path" -> "malformed-parent",
        "parentTagId" -> "legacy-parent",
        "createdAt" -> "2026-07-30T00:00:00Z",
        "updatedAt" -> "2026-07-30T00:00:00Z"
      )

      When("the Tag codec decodes the stored optional reference")
      val decoded = TagRecordCodec.fromStoreRecord(record)

      Then("it fails instead of treating malformed input as no parent")
      decoded shouldBe a[Consequence.Failure[_]]
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
      tag
        .entity[Tag]("tag")
        .descriptor
        .revisionBinding
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
    }
  }

  "TaggingWorkflow" should {
    "attach tags idempotently and search descendants through TagAttachment" in {
      Given("an Entity, a Tag hierarchy, and a TaggingWorkflow")
      given ExecutionContext = _execution_context()
      val repository = TagRepository.entityStore()
      val root = _success(repository.create(TagCreate(None, "search-root", None, tagSpace = "search-space")))
      val child = _success(repository.create(TagCreate(None, "child", Some(root.id), tagSpace = "search-space")))
      val workflow = TaggingWorkflow(tagSpace = "search-space")
      val sourceid = _source_id("entity_1")

      When("the same Tag is attached twice and searched through its parent")
      val first = _success(workflow.attach(sourceid, child.path))
      val second = _success(workflow.attach(sourceid, child.path))
      val matched = _success(workflow.searchSourceIds(root.path, includeDescendants = true, role = Some("tag")))
      val direct = _success(workflow.searchSourceIds(root.path, includeDescendants = false, role = Some("tag")))
      val stored = _success(AssociationRepository.entityStore(AssociationStoragePolicy.tagAttachmentDefault).list(
        AssociationFilter(
          domain = AssociationDomain.TagAttachment,
          sourceEntityId = Some(sourceid),
          targetKind = Some("tag"),
          role = Some("tag")
        )
      ))

      Then("one association remains and descendant search finds the Entity")
      first.targetEntityId shouldBe child.id.value
      second.targetEntityId shouldBe child.id.value
      stored.size shouldBe 1
      matched should contain (sourceid)
      direct should not contain sourceid
    }

    "merge explicit workflow tag space with execution context tag spaces" in {
      Given("explicit and execution-context Tag spaces")
      val base = _execution_context()
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
      val blogsourceid = _source_id("entity_blog")
      val operationalsourceid = _source_id("entity_ops")

      When("Tags from both effective spaces are attached and searched")
      _success(workflow.attach(blogsourceid, blog.path))
      _success(workflow.attach(operationalsourceid, operational.path))
      val blogmatches = _success(workflow.searchSourceIds(blog.path))
      val operationalmatches = _success(workflow.searchSourceIds(operational.path))

      Then("the workflow resolves both explicit and context-provided spaces")
      blogmatches should contain (blogsourceid)
      operationalmatches should contain (operationalsourceid)
    }

    "load visible source entities through generic tag_search_entities" in {
      Given("a canonical source Tag associated with a classifier Tag")
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

    "reject noncanonical Entity IDs at Tag operation ingress" in {
      Given("a Tag service with a classifier Tag and scalar Entity ID inputs")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val classifier = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "tag-ingress-classifier"),
        Argument("tagSpace", "tag-ingress")
      ))))
      val classifierpath = classifier.getString("path").getOrElse(fail("classifier tag path is missing"))

      When("Tag operations receive a noncanonical Entity ID")
      val attach = subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", "not-an-entity-id"),
        Argument("tagRef", classifierpath),
        Argument("tagSpace", "tag-ingress")
      ))
      val detach = subsystem.executeOperationResponse(_tag_request(
        "tag_detach",
        Argument("sourceEntityId", "not-an-entity-id"),
        Argument("tagRef", classifierpath),
        Argument("tagSpace", "tag-ingress")
      ))
      val listed = subsystem.executeOperationResponse(_tag_request(
        "tag_list_entity_tags",
        Argument("sourceEntityId", "not-an-entity-id"),
        Argument("tagSpace", "tag-ingress")
      ))
      val generated = subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "invalid-explicit-id"),
        Argument("id", "not-an-entity-id"),
        Argument("tagSpace", "tag-ingress")
      ))
      val parent = subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "invalid-parent-id"),
        Argument("parentTagId", "not-an-entity-id"),
        Argument("tagSpace", "tag-ingress")
      ))

      Then("every explicit scalar input is rejected instead of being persisted or treated as absent")
      attach shouldBe a[Consequence.Failure[_]]
      detach shouldBe a[Consequence.Failure[_]]
      listed shouldBe a[Consequence.Failure[_]]
      generated shouldBe a[Consequence.Failure[_]]
      parent shouldBe a[Consequence.Failure[_]]
    }

    "reject a same-name foreign canonical id at generic tag source ingress" in {
      Given("a Tag association whose source id has the tag collection name but a foreign namespace")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val source = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "tag-search-foreign-source"),
        Argument("tagSpace", "tag-search-foreign")
      ))))
      val classifier = _record(_success(subsystem.executeOperationResponse(_tag_request(
        "tag_create",
        Argument("key", "tag-search-foreign-classifier"),
        Argument("tagSpace", "tag-search-foreign")
      ))))
      val sourceid = source.getString("id").getOrElse(fail("source tag id is missing"))
      val parsed = EntityId.parse(sourceid).toOption.getOrElse(fail("source tag id must be canonical"))
      val foreign = EntityId(
        "foreign",
        parsed.minor,
        EntityCollectionId("foreign", parsed.minor, "tag"),
        parsed.timestamp,
        parsed.entropy
      )
      val classifierpath = classifier.getString("path").getOrElse(fail("classifier tag path is missing"))
      _success(subsystem.executeOperationResponse(_tag_request(
        "tag_attach",
        Argument("sourceEntityId", foreign.value),
        Argument("tagRef", classifierpath),
        Argument("tagSpace", "tag-search-foreign")
      )))

      When("tag_search_entities resolves the source against the local tag collection")
      val result = subsystem.executeOperationResponse(Request.of(
        component = "tag",
        service = "tag",
        operation = "tag_search_entities",
        arguments = List(
          Argument("component", "tag"),
          Argument("entity", "tag"),
          Argument("tagSpace", "tag-search-foreign"),
          Argument("tagRef", classifierpath)
        )
      ))

      Then("it rejects the foreign canonical id instead of rewriting it to the local tag collection")
      result shouldBe a[Consequence.Failure[_]]
    }

    "scope tag_update and tag_move by requested tagSpace" in {
      Given("ambiguous Tag paths in blog and operational spaces")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val tagcomponent =
        subsystem
          .findComponent(TagComponent.name)
          .getOrElse(fail("Tag component is missing"))
      val probe = _tag_request(
        "tag_create",
        Argument("key", "probe"),
        Argument("tagSpace", "probe")
      )
      (tagcomponent.logic.component eq tagcomponent) shouldBe true
      tagcomponent
        .entity[Tag]("tag")
        .descriptor
        .collectionId shouldBe TagEntityCollections.Tag
      val resolvedcontext =
        _success(
          IngressSecurityResolver.resolve(
            tagcomponent.logic.executionContext(),
            probe
          )
        ).executionContext
      tagcomponent
        .logic
        .executionContext()
        .entitySpace
        .entityOption(TagEntityCollections.Tag)
        .flatMap(_.descriptor.revisionBinding)
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
      resolvedcontext.entitySpace
        .entityOption(TagEntityCollections.Tag)
        .flatMap(_.descriptor.revisionBinding)
        .map(_.representation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
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

  private def _execution_context(
    clock: Clock = Clock.systemUTC()
  ): ExecutionContext = {
    import TagRepository.given

    val context = ExecutionContext.create(clock)
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      TagEntityCollections.Tag,
      summon[org.goldenport.cncf.entity.EntityPersistent[Tag]],
      EntityRevisionRepresentation.Detached
    )
    context
  }

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

  private def _source_id(entropy: String): String = {
    val collection = EntityCollectionId("test", "tag", "source")
    EntityId(
      collection.major,
      collection.minor,
      collection,
      entropy = Some(entropy)
    ).value
  }
}
