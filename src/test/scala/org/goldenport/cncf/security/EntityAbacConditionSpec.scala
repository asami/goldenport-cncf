package org.goldenport.cncf.security

import java.time.Instant
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 * @version Apr. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityAbacConditionSpec
  extends AnyWordSpec
  with Matchers {

  "EntityAbacCondition" should {
    "parse subject attribute equality with access kinds" in {
      val condition = EntityAbacCondition.parse("tenantId=subject.tenantId:read,search/list")

      condition.map(_.entityAttribute) shouldBe Some("tenantId")
      condition.map(_.operator) shouldBe Some(EntityAbacCondition.Operator.Eq)
      condition.exists(_.allows("read")) shouldBe true
      condition.exists(_.allows("search/list")) shouldBe true
      condition.exists(_.allows("update")) shouldBe false
    }

    "match camelCase entity attributes against snake_case subject attributes" in {
      val condition = EntityAbacCondition.parse("tenantId=subject.tenantId:read").get
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Authenticated,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty,
        attributes = Map("tenant_id" -> "tenant-a")
      )
      val record = Record.dataAuto("tenantId" -> "tenant-a")

      condition.matches(record, subject) shouldBe true
    }

    "evaluate through authorization context" in {
      val condition = EntityAbacCondition.parse("tenantId=subject.tenantId:read").get
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Authenticated,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty,
        attributes = Map("tenant_id" -> "tenant-a")
      )
      val context = EntityAuthorizationContext(
        subject = subject,
        entity = Record.dataAuto("tenantId" -> "tenant-a"),
        operation = EntityAuthorizationContext.Operation("read", "domain", Some("Person"), Some("person"), EntityAccessMode.UserPermission, None, None, None),
        application = EntityAuthorizationContext.Application(Vector("Person")),
        environment = EntityAuthorizationContext.Environment("trace-1", Some("correlation-1"))
      )

      condition.matches(context) shouldBe true
      context.operation.resourceType shouldBe Some("Person")
      context.environment.correlationId shouldBe Some("correlation-1")
    }

    "match operation and application attributes from authorization context" in {
      val context = EntityAuthorizationContext(
        subject = SecuritySubject(
          subjectId = "u1",
          authenticationState = SecuritySubject.AuthenticationState.Authenticated,
          accessTokenPresent = false,
          primaryGroup = None,
          groups = Set.empty,
          roles = Set.empty,
          privileges = Set.empty,
          capabilities = Set.empty,
          securityLevel = Set.empty
        ),
        entity = Record.empty,
        operation = EntityAuthorizationContext.Operation(
          accessKind = "read",
          resourceFamily = "domain",
          resourceType = Some("Person"),
          collectionName = Some("person"),
          accessMode = EntityAccessMode.UserPermission,
          operationModel = Some(ServiceOperationModel.BusinessService),
          entityOperationKind = Some(EntityOperationKind.Resource),
          entityApplicationDomain = Some(EntityApplicationDomain.Business)
        ),
        application = EntityAuthorizationContext.Application(Vector("Person")),
        environment = EntityAuthorizationContext.Environment("trace-1", None)
      )

      EntityAbacCondition.parse("operation.operationModel=business-service:read").get.matches(context) shouldBe true
      EntityAbacCondition.parse("application.entityOperationKind=resource:read").get.matches(context) shouldBe true
      EntityAbacCondition.parse("application.entityApplicationDomain=business:read").get.matches(context) shouldBe true
    }

    "match literal values" in {
      val condition = EntityAbacCondition.parse("postStatus=Published:read").get
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )
      val record = Record.dataAuto("postStatus" -> "Published")

      condition.matches(record, subject) shouldBe true
    }

    "match enum formatted values by label or value" in {
      val bylabel = EntityAbacCondition.parse("postStatus=Published:read").get
      val byvalue = EntityAbacCondition.parse("postStatus=published:read").get
      val subject = SecuritySubject(
        subjectId = "anonymous",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )
      val record = Record.dataAuto("postStatus" -> "Enum(Published):published")

      bylabel.matches(record, subject) shouldBe true
      byvalue.matches(record, subject) shouldBe true
    }

    "parse multiple conditions separated by semicolon or newline" in {
      val conditions = EntityAbacCondition.parseList(
        "tenantId=subject.tenantId:read;postStatus=Published:read,search/list\nvisibility=Public:read"
      )

      conditions.map(_.entityAttribute) shouldBe Vector("tenantId", "postStatus", "visibility")
    }

    "parse publication time window comparisons" in {
      val publish = EntityAbacCondition.parse("publishAt<=now:read").get
      val close = EntityAbacCondition.parse("closeAt>now:read,search/list").get
      val escaped = EntityAbacCondition.parse("publishAt&lt;=now:read").get

      publish.entityAttribute shouldBe "publishAt"
      publish.operator shouldBe EntityAbacCondition.Operator.Lte
      publish.allows("read") shouldBe true
      close.entityAttribute shouldBe "closeAt"
      close.operator shouldBe EntityAbacCondition.Operator.Gt
      close.allows("search/list") shouldBe true
      escaped.entityAttribute shouldBe "publishAt"
      escaped.operator shouldBe EntityAbacCondition.Operator.Lte
    }

    "match publication time windows against now" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )
      val record = Record.dataAuto(
        "publishAt" -> Instant.parse("2026-04-13T00:00:00Z").toString,
        "closeAt" -> Instant.parse("2999-01-01T00:00:00Z").toString
      )

      EntityAbacCondition.parse("publishAt<=now:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("closeAt>now:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("publishAt>now:read").get.matches(record, subject) shouldBe false
    }

    "match CMS publication visibility attributes" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )
      val record = Record.dataAuto(
        "visibility" -> "Public",
        "publicAt" -> Instant.parse("2026-04-13T00:00:00Z").toString,
        "startAt" -> Instant.parse("2026-04-13T00:00:00Z").toString,
        "endAt" -> Instant.parse("2999-01-01T00:00:00Z").toString,
        "unpublishAt" -> Instant.parse("2999-01-01T00:00:00Z").toString
      )

      EntityAbacCondition.parse("visibility=Public:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("publicAt<=now:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("startAt<=now:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("endAt>now:read").get.matches(record, subject) shouldBe true
      EntityAbacCondition.parse("unpublishAt>now:read").get.matches(record, subject) shouldBe true
    }

    "explain missed conditions" in {
      val condition = EntityAbacCondition.parse("publishAt<=now:read").get
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Anonymous,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )
      val record = Record.dataAuto("publishAt" -> Instant.parse("2999-01-01T00:00:00Z").toString)

      val evaluation = condition.evaluate(record, subject)

      evaluation.matched shouldBe false
      evaluation.conditionText shouldBe "publishAt<=now"
      evaluation.message should include("publishAt<=now")
      evaluation.message should include("actual=2999-01-01T00:00:00Z")
    }
  }

  "EntityAccessRelation" should {
    "parse multiple relations separated by semicolon or newline" in {
      val relations = EntityAccessRelation.parseList(
        "customerId=subject.customerId:read,search/list;accountId=subject.accountId:read\nownerId=subject.subjectId:update"
      )

      relations.map(_.entityField) shouldBe Vector("customerId", "accountId", "ownerId")
      relations.map(_.subjectAttribute) shouldBe Vector("customerId", "accountId", "subjectId")
      relations(0).allows("search/list") shouldBe true
      relations(1).allows("update") shouldBe false
      relations(2).allows("update") shouldBe true
    }
  }
}
