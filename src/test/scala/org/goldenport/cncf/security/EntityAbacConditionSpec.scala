package org.goldenport.cncf.security

import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityAbacConditionSpec
  extends AnyWordSpec
  with Matchers {

  "EntityAbacCondition" should {
    "parse subject attribute equality with access kinds" in {
      val condition = EntityAbacCondition.parse("tenantId=subject.tenantId:read,search/list")

      condition.map(_.entityAttribute) shouldBe Some("tenantId")
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

    "parse multiple conditions separated by semicolon or newline" in {
      val conditions = EntityAbacCondition.parseList(
        "tenantId=subject.tenantId:read;postStatus=Published:read,search/list\nvisibility=Public:read"
      )

      conditions.map(_.entityAttribute) shouldBe Vector("tenantId", "postStatus", "visibility")
    }
  }
}
