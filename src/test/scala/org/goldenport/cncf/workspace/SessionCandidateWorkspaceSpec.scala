package org.goldenport.cncf.workspace

import java.nio.file.Files
import org.goldenport.cncf.processexecution.{GitExecutionSpecSupport, LocalProcessExecutionDriver}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkEffectClass, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkOperationClassifier}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SessionCandidateWorkspaceSpec extends AnyWordSpec with Matchers {
  "Session candidate workspace" should {
    "require host policy and preserve the retained logical selection across reacquisition" in {
      val driver = new LocalProcessExecutionDriver()
      val fixture = GitExecutionSpecSupport.create(driver = Some(driver))
      val support = GitExecutionSpecSupport
      val parent = WorkspaceId.parseC("candidate-parent").toOption.get
      val alias = WorkspaceId.parseC("candidate-alias").toOption.get
      val managed = Files.createDirectory(fixture.root.resolve("managed"))
      val grants = Vector(support.workspaceId, parent, alias).map(id => WorkspaceGrant(support.owner, id, WorkspacePermission.Manage))
      val roots = Vector(support.workspaceId -> fixture.checkout, parent -> managed, alias -> fixture.checkout)
      val admission = WorkspaceExecutionAdmission.createC(roots, grants,
        Vector(SessionCandidateWorkspacePolicy(support.owner, parent, alias))).toOption.get
      val context = support.contextFor(fixture.root, Some(admission), Some(fixture.admission), driver)
      val op = UnitOfWorkOp.SessionCandidatePrepare(support.workspaceId, parent, alias, "session", "candidate")
      def prepare(ctx: org.goldenport.cncf.context.ExecutionContext) = {
        val uow = new UnitOfWork(ctx)
        try new UnitOfWorkInterpreter(uow).interpret(op) finally uow.dispose()
      }
      try {
        UnitOfWorkOperationClassifier.classify(op) shouldBe UnitOfWorkEffectClass.External
        prepare(fixture.context).isFaillure shouldBe true
        val selected = prepare(context).toOption.get
        val record = selected.reference.record
        val restored = SessionCandidateWorkspaceReference.fromRecordC(record).toOption.get
        val fresh = support.contextFor(fixture.root, Some(admission), Some(fixture.admission), driver)
        SessionCandidateWorkspace.reacquireC(fresh, restored, record, SessionCandidateWorkspaceState.Empty).isSuccess shouldBe true
        SessionCandidateWorkspace.reacquireC(fresh, restored, record.copy(session = "other"), SessionCandidateWorkspaceState.Empty).isFaillure shouldBe true
        SessionCandidateWorkspace.reacquireC(fresh, restored, record.copy(candidate = "other"), SessionCandidateWorkspaceState.Empty).isFaillure shouldBe true
        SessionCandidateWorkspace.reacquireC(fresh, restored, record, SessionCandidateWorkspaceState.Unknown).isFaillure shouldBe true
        prepare(fresh).isFaillure shouldBe true
        val path = selected.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get
        Files.writeString(path.resolve("foreign"), "occupied")
        SessionCandidateWorkspace.reacquireC(fresh, restored, record, SessionCandidateWorkspaceState.Empty).isFaillure shouldBe true
        Files.delete(path.resolve("foreign"))
        Files.delete(path)
        Files.createSymbolicLink(path, fixture.checkout)
        SessionCandidateWorkspace.reacquireC(fresh, restored, record, SessionCandidateWorkspaceState.Empty).isFaillure shouldBe true
      } finally {
        driver.close()
        fixture.close()
      }
    }
  }
}
