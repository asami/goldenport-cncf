package org.goldenport.cncf.cli

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Comparator
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  6, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeLaunchFailureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-12-art-scene-runtime-matrix, example:$example, rules:$rules, phase:12, slice:12C-1")

  "CNCF legacy runtime launch" should {
    "E1 render a structured configuration failure when the shared runtime datastore path is missing" must _metadata("E1", "P12-AF-F1") {
      "when a local runtime datastore omits its required path" in {
        Given("Spec: phase-12-art-scene-runtime-matrix; Rules: P12-AF-F1; Example: E1; an isolated test descriptor selecting a local runtime datastore without a path")
        Files.createDirectories(Paths.get("target"))
        val root = Files.createTempDirectory(Paths.get("target"), "cncf-runtime-launch-failure-")
        try {
        val descriptor = root.resolve("test.yaml")
        Files.writeString(
          descriptor,
          """kind: test-descriptor
            |config:
            |  textus.datastore.kind: local
            |""".stripMargin
        )
        val propertynames = Vector(
          "org.slf4j.simpleLogger.logFile",
          "org.slf4j.simpleLogger.defaultLogLevel",
          "org.slf4j.simpleLogger.showDateTime",
          "org.slf4j.simpleLogger.dateTimeFormat",
          "org.slf4j.simpleLogger.log.com.zaxxer.hikari",
          "org.slf4j.simpleLogger.log.org.sqlite"
        )
        val previous = propertynames.map(name => name -> Option(System.getProperty(name))).toMap

        try {
          When("the legacy runWithExtraComponents boundary is invoked")
          val err = new ByteArrayOutputStream()
          val errps = new PrintStream(err)
          val code =
            Console.withErr(errps) {
              CncfRuntime.runWithExtraComponents(
                Array(s"--textus.test.descriptor=${descriptor.toString}", "command"),
                _ => Nil
              )
            }
          errps.flush()
          val message = err.toString("UTF-8")

          Then("startup returns a failure code and prints the structured actionable conclusion")
          code should not be 0
          message should include ("configuration.invalid")
          message should include ("textus.datastore.path is required when textus.datastore.kind is local or sqlite")
        } finally {
          previous.foreach {
            case (name, Some(value)) => System.setProperty(name, value)
            case (name, None) => System.clearProperty(name)
          }
          Files.deleteIfExists(descriptor)
          Files.deleteIfExists(root)
        }
        } finally {
          _delete_tree(root)
        }
      }
    }

    "E2 reject conflicting fixed-user profiles before a configured SQLite datastore changes" must _metadata("E2", "P12-AF-F2") {
      "when canonical standalone assembly admission finds conflicting Textus and CNCF HOME identities" in {
        Given("Spec: phase-12-art-scene-runtime-matrix; Rules: P12-AF-F2; Example: E2; a valid seeded SQLite datastore, a standalone assembly descriptor, and conflicting fixed-user HOME profiles")
        Files.createDirectories(Paths.get("target"))
        val root = Files.createTempDirectory(Paths.get("target"), "cncf-runtime-fixed-user-launch-").toAbsolutePath.normalize
        try {
        val home = root.resolve("home")
        val datastore = root.resolve("runtime.sqlite")
        val descriptor = root.resolve("test.yaml")
        val assembly = root.resolve("assembly.yaml")
        Files.createDirectories(home.resolve(".textus"))
        Files.createDirectories(home.resolve(".cncf"))
        Files.writeString(
          home.resolve(".textus/user-profile.yaml"),
          "apiVersion: textus/v1\nkind: StandaloneUserProfile\nuser:\n  id: textus-fixed-user\n"
        )
        Files.writeString(
          home.resolve(".cncf/user-profile.yaml"),
          "apiVersion: textus/v1\nkind: StandaloneUserProfile\nuser:\n  id: cncf-fixed-user\n"
        )
        Files.writeString(
          descriptor,
          s"""kind: test-descriptor
             |config:
             |  textus.datastore.kind: sqlite
             |  textus.datastore.path: ${datastore.toString}
             |""".stripMargin
        )
        Files.writeString(
          assembly,
          """subsystem: phase12-fixed-user-launch
            |components:
            |  - name: phase12-fixed-user-fixture
            |security:
            |  authentication:
            |    local_subject:
            |      id: descriptor-fixed-subject
            |config:
            |  textus.subsystem.user-mode: standalone
            |""".stripMargin
        )
        val connection = DriverManager.getConnection(s"jdbc:sqlite:${datastore.toString}")
        try {
          val statement = connection.createStatement()
          try {
            statement.execute("CREATE TABLE preserved_content (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            statement.execute("INSERT INTO preserved_content (id, value) VALUES (1, 'phase12-stable')")
          } finally {
            statement.close()
          }
        } finally {
          connection.close()
        }
        val before = _sha256(datastore)
        val propertynames = Vector(
          "user.home",
          "org.slf4j.simpleLogger.logFile",
          "org.slf4j.simpleLogger.defaultLogLevel",
          "org.slf4j.simpleLogger.showDateTime",
          "org.slf4j.simpleLogger.dateTimeFormat",
          "org.slf4j.simpleLogger.log.com.zaxxer.hikari",
          "org.slf4j.simpleLogger.log.org.sqlite"
        )
        val previous = propertynames.map(name => name -> Option(System.getProperty(name))).toMap

        try {
          System.setProperty("user.home", home.toString)
          val runtime = new CncfRuntime()

          try {
            When("the canonical embedding boundary admits the standalone profile configuration")
            val result = runtime.initializeForEmbedding(
              cwd = root,
              args = Array(
                s"--textus.test.descriptor=${descriptor.toString}",
                s"--textus.subsystem.file=${assembly.toString}",
                s"--textus.assembly.descriptor=${assembly.toString}",
                "command"
              ),
              modeHint = Some(RunMode.Command),
              extraComponents = _ => Nil
            )
            val conclusion = result match {
              case Consequence.Failure(value) => value
              case Consequence.Success(_) => fail("fixed-user identity conflict must fail")
            }
            val message = conclusion.observation.cause.getEffectiveMessage.getOrElse(conclusion.display)

            Then("the structured migration-safe failure leaves the original SQLite datastore byte-identical")
            result shouldBe a[Consequence.Failure[_]]
            message should include ("explicit data migration")
            message should include ("isolated datastore")
            message should include ("silent data reuse is not admitted")
            message should not include "textus-fixed-user"
            message should not include "cncf-fixed-user"
            message should not include "descriptor-fixed-subject"
            message should not include home.toString
            message should not include datastore.toString
            _sha256(datastore) shouldBe before
          } finally {
            runtime.closeEmbedding()
          }
        } finally {
          previous.foreach {
            case (name, Some(value)) => System.setProperty(name, value)
            case (name, None) => System.clearProperty(name)
          }
          _delete_tree(root)
        }
        } finally {
          _delete_tree(root)
        }
      }
    }

    "E3 render malformed standalone HOME profile admission through the legacy command boundary" must _metadata("E3", "P12-AF-F3") {
      "when a standalone assembly selects a malformed fixed-user profile" in {
        Given("Spec: phase-12-art-scene-runtime-matrix; Rules: P12-AF-F3; Example: E3; an isolated HOME profile without apiVersion and a standalone assembly descriptor with a local subject")
        Files.createDirectories(Paths.get("target"))
        val root = Files.createTempDirectory(Paths.get("target"), "cncf-runtime-malformed-profile-launch-").toAbsolutePath.normalize
        try {
        val home = root.resolve("home")
        val descriptor = root.resolve("test.yaml")
        val assembly = root.resolve("assembly.yaml")
        Files.createDirectories(home.resolve(".textus"))
        Files.writeString(
          home.resolve(".textus/user-profile.yaml"),
          "kind: StandaloneUserProfile\nuser:\n  id: malformed-profile-user\n"
        )
        Files.writeString(
          descriptor,
          """kind: test-descriptor
            |execution:
            |  profile: controlled
            |  key: phase12-malformed-profile-launch
            |  time:
            |    mode: manual
            |    start-at: 2026-08-06T00:00:00Z
            |  random:
            |    mode: seeded
            |    seed: phase12-malformed-profile-launch
            |  ids:
            |    mode: deterministic
            |  scheduler:
            |    mode: manual
            |  ordering:
            |    mode: deterministic
            |""".stripMargin
        )
        Files.writeString(
          assembly,
          """subsystem: phase12-malformed-profile-launch
            |components:
            |  - name: phase12-malformed-profile-fixture
            |security:
            |  authentication:
            |    local_subject:
            |      id: descriptor-local-subject
            |config:
            |  textus.subsystem.user-mode: standalone
            |""".stripMargin
        )
        val propertynames = Vector(
          "user.home",
          "org.slf4j.simpleLogger.logFile",
          "org.slf4j.simpleLogger.defaultLogLevel",
          "org.slf4j.simpleLogger.showDateTime",
          "org.slf4j.simpleLogger.dateTimeFormat",
          "org.slf4j.simpleLogger.log.com.zaxxer.hikari",
          "org.slf4j.simpleLogger.log.org.sqlite"
        )
        val previous = propertynames.map(name => name -> Option(System.getProperty(name))).toMap
        val err = new ByteArrayOutputStream()
        val errps = new PrintStream(err)

        try {
          System.setProperty("user.home", home.toString)

          When("the public legacy runWithExtraComponents command path is invoked")
          val code =
            Console.withErr(errps) {
              CncfRuntime.runWithExtraComponents(
                Array(
                  s"--textus.test.descriptor=${descriptor.toString}",
                  s"--textus.subsystem.file=${assembly.toString}",
                  s"--textus.assembly.descriptor=${assembly.toString}",
                  "command"
                ),
                _ => Nil
              )
            }
          errps.flush()
          val message = err.toString("UTF-8")

          Then("the original structured configuration conclusion is rendered with a nonzero exit code")
          code should not be 0
          message should include ("configuration.invalid")
          message should include ("StandaloneUserProfile")
          message should include ("requires 'apiVersion'")
        } finally {
          new CncfRuntime().closeEmbedding()
          errps.close()
          previous.foreach {
            case (name, Some(value)) => System.setProperty(name, value)
            case (name, None) => System.clearProperty(name)
          }
          _delete_tree(root)
        }
        } finally {
          _delete_tree(root)
        }
      }
    }
  }

  private def _sha256(path: java.nio.file.Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map { value =>
      f"${value & 0xff}%02x"
    }.mkString

  private def _delete_tree(path: java.nio.file.Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(Comparator.reverseOrder()).forEach((entry: java.nio.file.Path) => Files.deleteIfExists(entry))
      } finally {
        stream.close()
      }
    }
}
