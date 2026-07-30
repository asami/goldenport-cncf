package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for exact Job persistence collection identity.
 *
 * @since   Jul. 30, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobEntityCollectionIdentitySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Job persistence codecs" should {
    "reject foreign canonical ids rather than rebinding job and job-definition records" in {
      Given("records whose ids belong to the foreign Blob collection")
      val foreign = EntityId("cncf", "foreign_job", EntityCollectionId("cncf", "builtin", "blob"))
      val job = Record.dataAuto("id" -> foreign.value)
      val definition = Record.dataAuto(
        "id" -> foreign.value,
        "key" -> "foreign-job",
        "jclSource" -> "job foreign-job {}",
        "jclFormat" -> JobBatchDefinition.DefaultFormatName
      )

      When("the job codecs decode the records")
      val decodedJob = JobEntity.fromRecord(job)
      val decodedDefinition = JobDefinitionEntity.fromRecord(definition)

      Then("both fail instead of replacing their exact collection identity")
      decodedJob shouldBe a[Consequence.Failure[_]]
      decodedDefinition shouldBe a[Consequence.Failure[_]]
    }
  }
}
