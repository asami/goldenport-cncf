package logicalui

import java.time.Instant
import org.goldenport.cncf.display.*
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure supplied consumer examples; no command, commit observer or reload is invoked. */
object DisplayConsumerHandoffFixture {
  import DisplayMutation.*
  import DisplayMutationResult.*
  import DisplayMutationContractFixture.{success, title, description}

  final case class MutationExample(fileName: String, request: Request,
    observedRevision: Option[EntityRevision], result: Result)

  val binding: DisplayMutationBinding.Binding[DisplayMutationContractFixture.Patch] =
    success(DisplayMutationContractFixture.bindingC(positive = true))
  val context: PresentationContext = success(PresentationContext.createC(
    locale = Some("en-US"), variant = Some("server-variant"),
    flow = Some(success(PresentationContext.Flow.createC("consumer-display-1",
      "consumer-correlation-1", Instant.parse("2026-10-17T00:30:00.123456789Z"))))))
  val createRequest: Request.Create = Request.Create(binding.selection, Vector(
    FieldInput(title, DisplayValue.Text("Created title 日本")),
    FieldInput(description, DisplayValue.Text("Created description"))))
  val updateRequest: Request.Update = Request.Update(binding.selection,
    success(TypedDisplayProjectionFixture.resourceC(1, 7)).entityId, Vector(
      FieldInput(title, DisplayValue.Text("Updated title 日本")),
      FieldInput(description, DisplayValue.Text("Updated description"))))
  val deleteRequest: Request.Delete = Request.Delete(binding.selection, updateRequest.resourceId)

  private def _example(filename: String, request: Request, kind: OutcomeKind,
    revision: Long, text: String, descriptiontext: String, pending: Boolean = false,
    contextual: Boolean = false): MutationExample = {
    val observed = if (kind == OutcomeKind.Created) None
      else Some(success(TypedDisplayProjectionFixture.resourceC(1, 7)).revision)
    val resource = success(TypedDisplayProjectionFixture.resourceC(1, revision))
    val receipt = success(receiptC(binding.selection, request, kind, resource, observed))
    val suppliedcontext = if (contextual) Some(context) else None
    val projection = if (kind == OutcomeKind.Deleted) None
      else if (pending) Some(ProjectionState.Pending(PendingReason.ViewNotCurrent))
      else {
        val capture = success(TypedDisplayProjectionFixture.captureC(1, revision))
          .copy(title = text, description = descriptiontext)
        val projector = success(TypedDisplayProjectionFixture.detailC(binding.definitions))
        val display = success(projector.projectC(capture))
        val contextualdisplay = success(Display.withPresentationContextC(display, suppliedcontext))
        Some(ProjectionState.Ready(ReloadedDisplay(binding.selection.source, contextualdisplay)))
      }
    MutationExample(filename, request, observed, success(createC(receipt, projection, suppliedcontext)))
  }

  val mutationExamples: Vector[MutationExample] = Vector(
    _example("mutation-created-ready.json", createRequest, OutcomeKind.Created, 1,
      "Created title 日本", "Created description"),
    _example("mutation-updated-ready.json", updateRequest, OutcomeKind.Updated, 8,
      "Updated title 日本", "Updated description"),
    _example("mutation-unchanged-ready.json", updateRequest, OutcomeKind.Unchanged, 7,
      "Updated title 日本", "Updated description"),
    _example("mutation-deleted.json", deleteRequest, OutcomeKind.Deleted, 7, "", ""),
    _example("mutation-updated-pending.json", updateRequest, OutcomeKind.Updated, 8,
      "Updated title 日本", "Updated description", pending = true),
    _example("mutation-updated-ready-context.json", updateRequest, OutcomeKind.Updated, 8,
      "Updated title 日本", "Updated description", contextual = true))
}
