package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, Command, ResourceAccess}
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  4, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class InMemoryJobEngineSpec extends AnyWordSpec with Matchers {
  "InMemoryJobEngine" should {
    "execute a Command as a Job and store the result" in {
      val action = new Command() {
        // val name = "test"
        val request = Request.ofOperation("test")
        override def createCall(core: ActionCall.Core): ActionCall = {
          val actionself = this
          val _core_ = core
          new ActionCall {
            override val core: ActionCall.Core = _core_
            override def action: Action = actionself
            def execute(): Consequence[OperationResponse] =
              Consequence.success(OperationResponse.Scalar("ok"))
          }
        }
      }

      val actionEngine = ActionEngine.create()
      val jobEngine = InMemoryJobEngine.create()
      val ctx = ExecutionContext.test()
      val task = ActionTask(ActionId.generate(), action, actionEngine, None)

      val jobid = jobEngine.submit(List(task), ctx)
      val result = _await_result_(jobEngine, jobid)

      result.isDefined shouldBe true
      result.get match {
        case JobResult.Success(res) =>
          res.toResponse shouldBe Response.Scalar("ok")
        case JobResult.Failure(c) =>
          fail(c.toString)
      }
    }
  }

  private def _await_result_(
    engine: JobEngine,
    jobid: JobId
  ): Option[JobResult] = {
    val deadline = System.currentTimeMillis() + 2000L
    var result: Option[JobResult] = None
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      result = engine.getResult(jobid)
      if (result.isEmpty) {
        Thread.sleep(10)
      }
    }
    result
  }
}
