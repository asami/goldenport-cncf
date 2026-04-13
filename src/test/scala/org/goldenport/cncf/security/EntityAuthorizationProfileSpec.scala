package org.goldenport.cncf.security

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityAuthorizationProfileSpec
  extends AnyWordSpec
  with Matchers {

  "EntityAuthorizationProfile" should {
    "derive service-internal access only from internal service operation model" in {
      val profile = EntityAuthorizationProfile.derive(
        operationKind = EntityOperationKind.Resource,
        applicationDomain = EntityApplicationDomain.Business,
        operationModel = ServiceOperationModel.InternalService,
        explicitRelations = Vector.empty
      )

      profile.accessMode shouldBe EntityAccessMode.ServiceInternal
    }

    "derive system access only from system task operation model" in {
      val profile = EntityAuthorizationProfile.derive(
        operationKind = EntityOperationKind.Resource,
        applicationDomain = EntityApplicationDomain.Business,
        operationModel = ServiceOperationModel.SystemTask,
        explicitRelations = Vector.empty
      )

      profile.accessMode shouldBe EntityAccessMode.System
    }

    "keep task entities under user permission for business service operations" in {
      val profile = EntityAuthorizationProfile.derive(
        operationKind = EntityOperationKind.Task,
        applicationDomain = EntityApplicationDomain.Business,
        operationModel = ServiceOperationModel.BusinessService,
        explicitRelations = Vector.empty
      )

      profile.accessMode shouldBe EntityAccessMode.UserPermission
    }
  }
}
