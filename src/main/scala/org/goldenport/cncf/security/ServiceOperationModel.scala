package org.goldenport.cncf.security

/*
 * Coarse operation/service model used to derive UnitOfWork authorization
 * defaults. This keeps common business-app cases declarative while preserving
 * explicit low-level override hooks.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
enum ServiceOperationModel {
  case PublicApi, BusinessService, InternalService, SystemTask
}

object ServiceOperationModel {
  val default: ServiceOperationModel = ServiceOperationModel.BusinessService

  def parse(text: String): ServiceOperationModel =
    text.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-") match
      case "public-api" | "public" => PublicApi
      case "internal-service" | "service-internal" | "internal" => InternalService
      case "system-task" | "system" | "batch" | "migration" => SystemTask
      case _ => BusinessService
}
