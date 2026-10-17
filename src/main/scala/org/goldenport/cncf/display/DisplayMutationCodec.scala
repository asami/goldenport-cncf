package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Pure standalone mutation codecs; operation and semantic authority remain supplied. */
object DisplayMutationCodec {
  import DisplayMutation.{Operation, Request, Selection}
  import DisplayMutationBinding.{Binding, Descriptor}
  import DisplayMutationResult.Result
  import GeneratedLogicalUiAbi.AdmittedDefinitions

  def encodeRequestC(request: Request): Consequence[String] =
    DisplayMutationRequestCodec._encode(request).map(DisplayMutationJson._render)
  def decodeRequestC(expectedSelection: Selection, operation: Operation, json: String): Consequence[Request] =
    DisplayMutationRequestCodec._decode(expectedSelection, operation, json)
  def encodeDescriptorC[P](binding: Binding[P], descriptor: Descriptor): Consequence[String] =
    DisplayMutationDescriptorCodec._encode(binding, descriptor).map(DisplayMutationJson._render)
  def decodeDescriptorC[P](binding: Binding[P], json: String): Consequence[Descriptor] =
    DisplayMutationDescriptorCodec._decode(binding, json)
  def encodeResultC(result: Result): Consequence[String] =
    DisplayMutationResultCodec._encode(result).map(DisplayMutationJson._render)
  def decodeResultC(expectedSelection: Selection, definitions: AdmittedDefinitions, request: Request,
    observedRevision: Option[EntityRevision], json: String): Consequence[Result] =
    DisplayMutationResultCodec._decode(expectedSelection, definitions, request, observedRevision, json)
}
