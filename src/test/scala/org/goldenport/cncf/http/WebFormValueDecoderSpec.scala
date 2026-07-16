package org.goldenport.cncf.http

import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebFormValueDecoderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "WebFormValueDecoder" should {
    "decode configured JSON controls into records" in {
      Given("a Web form with one JSON control")
      val descriptor = _descriptor("metadata", "json")
      val form = Record.dataAuto(
        "title" -> "Notice",
        "metadata" -> "{\"source\":\"admin\",\"attempt\":2}"
      )

      When("the submitted form is decoded")
      val decoded = WebFormValueDecoder.decode(descriptor, "notice", "notification", "create", form).TAKE

      Then("the JSON object becomes a structured Record and other fields remain unchanged")
      decoded.getString("title") shouldBe Some("Notice")
      val metadata = decoded.getRecord("metadata").getOrElse(fail(s"metadata record is missing: $decoded"))
      metadata.getString("source") shouldBe Some("admin")
      metadata.getString("attempt") shouldBe Some("2")
    }

    "remove an empty optional record control from the submitted form" in {
      Given("an empty JSON control")
      val descriptor = _descriptor("metadata", "record")
      val form = Record.dataAuto("title" -> "Notice", "metadata" -> "  ")

      When("the submitted form is decoded")
      val decoded = WebFormValueDecoder.decode(descriptor, "notice", "notification", "create", form).TAKE

      Then("the absent structured value is not passed as an empty string")
      decoded.getAny("metadata") shouldBe empty
      decoded.getString("title") shouldBe Some("Notice")
    }

    "reject malformed JSON without extracting or guessing a record" in {
      Given("a malformed JSON control value")
      val descriptor = _descriptor("metadata", "json")
      val form = Record.dataAuto("metadata" -> "{source:admin")

      When("the submitted form is decoded")
      val result = WebFormValueDecoder.decode(descriptor, "notice", "notification", "create", form)

      Then("decoding fails deterministically")
      result.isFaillure shouldBe true
    }

    "leave non-JSON controls and pre-structured records unchanged" in {
      Given("one ordinary control and one already structured JSON control")
      val descriptor = WebDescriptor(form = Map(
        WebDescriptor.formSelector("notice", "notification", "create") -> WebDescriptor.Form(
          controls = Map(
            "title" -> WebDescriptor.FormControl(controlType = Some("text")),
            "metadata" -> WebDescriptor.FormControl(controlType = Some("json"))
          )
        )
      ))
      val metadata = Record.dataAuto("source" -> "api")
      val form = Record.dataAuto("title" -> "Notice", "metadata" -> metadata)

      When("the submitted form is decoded")
      val decoded = WebFormValueDecoder.decode(descriptor, "notice", "notification", "create", form).TAKE

      Then("the decoder preserves both values")
      decoded.getString("title") shouldBe Some("Notice")
      decoded.getRecord("metadata") shouldBe Some(metadata)
    }
  }

  private def _descriptor(name: String, controltype: String): WebDescriptor =
    WebDescriptor(form = Map(
      WebDescriptor.formSelector("notice", "notification", "create") -> WebDescriptor.Form(
        controls = Map(name -> WebDescriptor.FormControl(controlType = Some(controltype)))
      )
    ))
}
