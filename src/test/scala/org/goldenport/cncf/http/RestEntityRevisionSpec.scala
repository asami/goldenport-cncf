package org.goldenport.cncf.http

import cats.effect.IO
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationAdapterDefaults,
  EntityMutationPolicyResolver,
  EntityRevisionTransport,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.http.HttpRequest
import org.goldenport.record.Record
import org.http4s.{Header, Method, Request, Response, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.typelevel.ci.CIString

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class RestEntityRevisionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "REST Entity revision transport" should {
    "select WriteIfChanged with managed revision for an idempotent PUT" in {
      Given("a canonical REST PUT without a strict validator")
      val server = _server
      val request = Request[IO](
        Method.PUT,
        Uri.unsafeFromString("/rest/v1/admin/entity/update")
      )

      When("the REST adapter normalizes the request")
      val normalized =
        server._rest_mutation_request(request, _core_request).toOption
      val profile = normalized.flatMap(
        _.header.getString(
          EntityMutationAdapterDefaults.profilePropertyName
        )
      )
      val selection = EntityMutationPolicyResolver.resolveC(
        EntityConcurrencyPolicy.Optimistic,
        adapterDefault = EntityMutationAdapterDefaults.forProfile(profile)
      )

      Then("the idempotent route deduplicates state with managed revision")
      profile shouldBe
        Some(EntityMutationAdapterDefaults.idempotentRestProfile)
      selection.toOption.map(_.writePolicy) shouldBe
        Some(EntityWritePolicy.WriteIfChanged)
      selection.toOption.map(_.preconditionPolicy) shouldBe
        Some(RevisionPreconditionPolicy.Managed)
    }

    "carry a strong revision validator into strict observed metadata" in {
      Given("a canonical REST PUT with the current strong Entity revision validator")
      val revision = EntityRevision.createC(7L).toOption.getOrElse(
        fail("revision fixture is invalid")
      )
      val request = Request[IO](
        Method.PUT,
        Uri.unsafeFromString("/rest/v1/admin/entity/update")
      ).putHeaders(
        Header.Raw(
          CIString(EntityRevisionTransport.ifMatchHeaderName),
          EntityRevisionTransport.entityTag(revision)
        )
      )

      When("the REST adapter normalizes the strict request")
      val normalized =
        _server._rest_mutation_request(request, _core_request).toOption

      Then("the adapter selects strict policy and carries only framework revision metadata")
      normalized.flatMap(
        _.header.getString(
          EntityMutationAdapterDefaults.profilePropertyName
        )
      ) shouldBe Some(EntityMutationAdapterDefaults.strictRestProfile)
      normalized.flatMap(
        _.header.getString(
          EntityRevisionTransport.observedRevisionPropertyName
        )
      ) shouldBe Some("7")
      normalized.flatMap(_.form.getAny("revision")) shouldBe None
      normalized.flatMap(_.form.getAny("version")) shouldBe None
    }

    "override client-supplied mutation metadata with the REST adapter decision" in {
      Given("a strict Entity PUT whose pre-adapter request contains conflicting framework metadata")
      val request = Request[IO](
        Method.PUT,
        Uri.unsafeFromString("/rest/v1/admin/entity/update")
      ).putHeaders(
        Header.Raw(
          CIString(EntityRevisionTransport.ifMatchHeaderName),
          "\"revision-7\""
        )
      )
      val injected = _core_request.copy(
        header = Record.data(
          EntityMutationAdapterDefaults.profilePropertyName ->
            EntityMutationAdapterDefaults.idempotentRestProfile,
          EntityRevisionTransport.observedRevisionPropertyName -> "3"
        )
      )

      When("the REST adapter applies the method and If-Match contract")
      val normalized =
        _server._rest_mutation_request(request, injected).toOption

      Then("only the adapter-owned strict profile and observed revision remain effective")
      normalized.flatMap(
        _.header.getString(
          EntityMutationAdapterDefaults.profilePropertyName
        )
      ) shouldBe Some(EntityMutationAdapterDefaults.strictRestProfile)
      normalized.flatMap(
        _.header.getString(
          EntityRevisionTransport.observedRevisionPropertyName
        )
      ) shouldBe Some("7")
      normalized.toVector
        .flatMap(_.header.fields)
        .count(
          _.key == EntityMutationAdapterDefaults.profilePropertyName
        ) shouldBe 1
      normalized.toVector
        .flatMap(_.header.fields)
        .count(
          _.key == EntityRevisionTransport.observedRevisionPropertyName
        ) shouldBe 1
    }

    "reject malformed or weak validators deterministically" in {
      Given("REST PUT requests with malformed and weak If-Match values")
      val malformed = Vector(
        "7",
        "\"7\"",
        "W/\"revision-7\"",
        "\"revision-0\"",
        "\"revision-many\""
      )

      When("the validator grammar is parsed")
      val outcomes = malformed.map(EntityRevisionTransport.parseEntityTagC)

      Then("none of the invalid values is admitted as an observed revision")
      outcomes.forall(_.toOption.isEmpty) shouldBe true
    }

    "leave non-PUT REST methods outside Entity mutation policy selection" in {
      Given("a REST POST carrying an If-Match header")
      val request = Request[IO](
        Method.POST,
        Uri.unsafeFromString("/rest/v1/admin/entity/update")
      ).putHeaders(
        Header.Raw(
          CIString(EntityRevisionTransport.ifMatchHeaderName),
          "\"revision-3\""
        )
      )

      When("the REST adapter normalizes the non-idempotent method")
      val normalized =
        _server._rest_mutation_request(request, _core_request).toOption

      Then("no Entity mutation adapter profile or observed revision is injected")
      normalized.flatMap(
        _.header.getAny(
          EntityMutationAdapterDefaults.profilePropertyName
        )
      ) shouldBe None
      normalized.flatMap(
        _.header.getAny(
          EntityRevisionTransport.observedRevisionPropertyName
        )
      ) shouldBe None
    }

    "leave unrelated REST PUT operations outside Entity revision handling" in {
      Given("an unrelated REST PUT with a malformed Entity validator")
      val request = Request[IO](
        Method.PUT,
        Uri.unsafeFromString("/rest/v1/catalog/price/update")
      ).putHeaders(
        Header.Raw(
          CIString(EntityRevisionTransport.ifMatchHeaderName),
          "not-an-entity-validator"
        )
      )

      When("the REST adapter normalizes the request")
      val normalized =
        _server._rest_mutation_request(request, _core_request)

      Then("the unrelated operation is neither rejected nor assigned an Entity mutation profile")
      normalized.toOption shouldBe Some(_core_request)
      normalized.toOption.flatMap(
        _.header.getAny(
          EntityMutationAdapterDefaults.profilePropertyName
        )
      ) shouldBe None
    }

    "derive response validators from actual embedded or detached Entity response shapes" in {
      Given("Entity REST response bodies with nested embedded revision and explicit detached version")
      val request = Request[IO](
        Method.GET,
        Uri.unsafeFromString("/rest/v1/admin/entity/read")
      )
      val embeddedbody =
        """{"record":{"id":"entity-1","revision":9}}"""
      val detachedbody =
        """{"record":{"id":"entity-2","revision":99},"version":"11"}"""

      When("the REST response adapter projects strong validators")
      val embedded = _server._with_entity_revision_validator(
        Response[IO](),
        Some(request),
        embeddedbody
      )
      val detached = _server._with_entity_revision_validator(
        Response[IO](),
        Some(request),
        detachedbody
      )

      Then("both admitted revision projections use the canonical ETag grammar")
      _header(embedded, "ETag") shouldBe Some("\"revision-9\"")
      _header(detached, "ETag") shouldBe Some("\"revision-11\"")
    }

    "never project Entity validators on unrelated REST responses" in {
      Given("an unrelated REST response whose business payload has a version field")
      val request = Request[IO](
        Method.GET,
        Uri.unsafeFromString("/rest/v1/catalog/price/read")
      )

      When("the generic REST response adapter renders the business response")
      val response = _server._with_entity_revision_validator(
        Response[IO](),
        Some(request),
        """{"version":12,"name":"summer-price"}"""
      )

      Then("the business version is not reinterpreted as an Entity validator")
      _header(response, "ETag") shouldBe None
    }
  }

  private def _server: Http4sHttpServer =
    new Http4sHttpServer(
      HttpExecutionEngine.Factory.engine()
    )

  private def _core_request: HttpRequest =
    HttpRequest.fromPath(
      method = HttpRequest.PUT,
      path = "/admin/entity/update",
      form = Record.data(
        "component" -> "sample",
        "entity" -> "notice",
        "id" -> "notice-1"
      )
    )

  private def _header(
    response: Response[IO],
    name: String
  ): Option[String] =
    response.headers.headers
      .find(_.name.toString.equalsIgnoreCase(name))
      .map(_.value)
}
