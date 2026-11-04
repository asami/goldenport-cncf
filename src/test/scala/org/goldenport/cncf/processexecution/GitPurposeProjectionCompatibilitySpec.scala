package org.goldenport.cncf.processexecution

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  4, 2026
 * @version Nov.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitPurposeProjectionCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Purpose projection JVM compatibility" should afterWord(
    "in spec:typed-git-execution, example:E1, rules:Purpose-specific-candidates, phase:sync-runtime-20261104-08") {
    "preserve the legacy three-argument JVM calls while retaining an explicit projection on copy" in {
      Given("the exact constructor, companion apply and copy signatures used by already compiled callers")
      val path = GitPath.parseC("selected.txt").toOption.get
      val parameters = Array(classOf[GitPath], classOf[Option[?]], classOf[Option[?]])
      val constructor = classOf[GitPurposeChange].getConstructor(parameters*)
      val applymethod = GitPurposeChange.getClass.getMethod("apply", parameters*)
      val copymethod = classOf[GitPurposeChange].getMethod("copy", parameters*)
      val projected = new GitPurposeChange(path, Some("captured\n"), Some("current\n"),
        Some(GitPurposeWorktreeProjection(Some("integrated\n"))))
      When("those legacy JVM signatures are invoked directly rather than recompiled with a fourth default argument")
      val constructed = constructor.newInstance(path, Some("captured\n"), Some("current\n"))
      val applied = applymethod.invoke(GitPurposeChange, path, Some("captured\n"), Some("current\n"))
        .asInstanceOf[GitPurposeChange]
      val copied = copymethod.invoke(projected, path, Some("captured\n"), Some("corrected\n"))
        .asInstanceOf[GitPurposeChange]
      Then("legacy creation means absent projection and an existing explicit projection survives legacy copying")
      constructed.worktreeProjection shouldBe None
      applied shouldBe constructed
      copied.content shouldBe Some("corrected\n")
      copied.worktreeProjection shouldBe projected.worktreeProjection
      classOf[GitPurposeChange].getMethod("copy$default$1").invoke(projected) shouldBe path
      classOf[GitPurposeChange].getMethod("copy$default$2").invoke(projected) shouldBe projected.expectedWorktree
      classOf[GitPurposeChange].getMethod("copy$default$3").invoke(projected) shouldBe projected.content
      val unapplymethod = GitPurposeChange.getClass.getMethod("unapply", classOf[GitPurposeChange])
      unapplymethod.getReturnType shouldBe classOf[GitPurposeChange]
      unapplymethod.invoke(GitPurposeChange, projected) shouldBe projected
    }
  }
}
