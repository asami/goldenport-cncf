package org.goldenport.cncf.security

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecuritySubjectSpec
  extends AnyWordSpec
  with Matchers {

  "SecuritySubject" should {
    "recognize create grants by resource type" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        primaryGroup = None,
        groups = Set.empty,
        roles = Set("createuseraccount"),
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = Some("UserAccount"),
        collectionName = None
      ) shouldBe true
    }

    "recognize create grants by collection name" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set("textususeraccountcreate"),
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = None,
        collectionName = Some("textus-user-account")
      ) shouldBe true
    }

    "return false when no create grant matches" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        primaryGroup = None,
        groups = Set.empty,
        roles = Set("reader"),
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = Some("UserAccount"),
        collectionName = Some("textus-user-account")
      ) shouldBe false
    }
  }
}
