package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{ComponentKnowledgeManifest, ComponentKnowledgeManifestConsumerContract}

/*
 * Internal, value-only Component Admin contract and model projection. It
 * retains the authoritative Phase 59 consumer contract without discovering,
 * reconstructing, or granting operational authority.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class ComponentAdminContractModelView(
  identityview: ComponentAdminViewModel,
  knowledgecontract: ComponentKnowledgeManifestConsumerContract
)

private[cncf] object ComponentAdminContractModelProjection {
  def projectC(
    identityView: ComponentAdminViewModel,
    manifest: ComponentKnowledgeManifest
  ): Consequence[ComponentAdminContractModelView] =
    ComponentAdminViewModel.validateC(identityView).flatMap { validated =>
      if (manifest == null) {
        Consequence.argumentInvalid("Component knowledge manifest is required")
      } else {
        ComponentKnowledgeManifestConsumerContract.fromManifestC(manifest).flatMap { contract =>
          if (validated.componentClass.value.componentId != contract.componentId) {
            Consequence.argumentInvalid("Component knowledge manifest component identity must match the Admin identity view")
          } else if (validated.selectedLogicalRelease.value.release != contract.logicalRelease) {
            Consequence.argumentInvalid("Component knowledge manifest logical release must match the Admin identity view")
          } else {
            Consequence.success(ComponentAdminContractModelView(validated, contract))
          }
        }
      }
    }
}
