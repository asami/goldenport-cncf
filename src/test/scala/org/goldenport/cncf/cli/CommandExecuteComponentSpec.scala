package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{RuntimeMetadata, RuntimeMetadataInfo}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Request
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Jan. 16, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandExecuteComponentSpec extends AnyWordSpec with Matchers {

  "CncfRuntime.parseCommandArgs" should {
    "parse component service operation form" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin", "system", "ping")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "parse component.service.operation form" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      CncfRuntime.parseCommandArgs(subsystem, Array("admin.system.ping")) match {
        case Consequence.Success(req: Request) =>
          req.component.getOrElse(fail("missing component")).shouldBe("admin")
          req.service.getOrElse(fail("missing service")).shouldBe("system")
          req.operation.shouldBe("ping")
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }

  "server-emulator normalization" should {
    "execute via Subsystem.executeHttp" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin", "system", "ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpReq = normalized.flatMap(HttpRequest.fromCurlLike)
      httpReq match {
        case Consequence.Success(req: HttpRequest) =>
          val res = subsystem.executeHttp(req)
          val expected = RuntimeMetadata.format(
            RuntimeMetadataInfo("server", RuntimeMetadata.SubsystemName, CncfVersion.current, CncfVersion.current)
          )
          res.code.shouldBe(200)
          res.getString.getOrElse("").shouldBe(expected)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }

    "execute via dot-form input" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val normalized = CncfRuntime.normalizeServerEmulatorArgs(
        Seq("admin.system.ping"),
        RuntimeConfig.default.serverEmulatorBaseUrl
      )
      val httpReq = normalized.flatMap(HttpRequest.fromCurlLike)
      httpReq match {
        case Consequence.Success(req: HttpRequest) =>
          val res = subsystem.executeHttp(req)
          val expected = RuntimeMetadata.format(
            RuntimeMetadataInfo("server", RuntimeMetadata.SubsystemName, CncfVersion.current, CncfVersion.current)
          )
          res.code.shouldBe(200)
          res.getString.getOrElse("").shouldBe(expected)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: ${c}")
      }
    }
  }
}
