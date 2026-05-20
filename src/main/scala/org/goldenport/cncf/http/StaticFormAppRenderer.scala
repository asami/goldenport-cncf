package org.goldenport.cncf.http

/*
 * @since   Apr. 12, 2026
 *  version Apr. 30, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppRenderer {
  type Page = StaticFormAppRendererSupport.Page
  val Page = StaticFormAppRendererSupport.Page
  type PageRequest = StaticFormAppRendererSupport.PageRequest
  val PageRequest = StaticFormAppRendererSupport.PageRequest
  type FormPageProperties = StaticFormAppRendererSupport.FormPageProperties
  val FormPageProperties = StaticFormAppRendererSupport.FormPageProperties
  type FormResultProperties = StaticFormAppRendererSupport.FormResultProperties
  val FormResultProperties = StaticFormAppRendererSupport.FormResultProperties
  type TableColumn = StaticFormAppRendererSupport.TableColumn
  val TableColumn = StaticFormAppRendererSupport.TableColumn
  type FormValidationResult = StaticFormAppRendererSupport.FormValidationResult
  val FormValidationResult = StaticFormAppRendererSupport.FormValidationResult
  type FormValidationMessage = StaticFormAppRendererSupport.FormValidationMessage
  val FormValidationMessage = StaticFormAppRendererSupport.FormValidationMessage
  type DocumentLink = StaticFormAppRendererSupport.DocumentLink
  val DocumentLink = StaticFormAppRendererSupport.DocumentLink

  val defaultPageViewContextValues: Map[String, String] =
    StaticFormAppRendererSupport.defaultPageViewContextValues

  def apply(
    config: StaticFormAppRendererConfig = StaticFormAppRendererConfig.default
  ): StaticFormAppRenderer =
    new StaticFormAppRenderer(config)

  def isHiddenFormContextKey(key: String): Boolean =
    StaticFormAppRendererSupport.isHiddenFormContextKey(key)

  def isHtmlDocumentTemplate(template: String): Boolean =
    StaticFormAppRendererSupport.isHtmlDocumentTemplate(template)

  def hasTextusMarkup(template: String): Boolean =
    StaticFormAppRendererSupport.hasTextusMarkup(template)

  def tableColumnKey(
    path: String,
    entity: String,
    view: String
  ): String =
    StaticFormAppRendererSupport.tableColumnKey(path, entity, view)
}

final class StaticFormAppRenderer(
  val config: StaticFormAppRendererConfig = StaticFormAppRendererConfig.default
) extends StaticFormAppRendererSupport(config)
    with StaticFormAppRendererCorePart
    with StaticFormAppRendererTemplatePart
    with StaticFormAppRendererFormPart
    with StaticFormAppRendererFormResultPart
    with StaticFormAppRendererSystemAdminPart
    with StaticFormAppRendererComponentAdminPart
    with StaticFormAppRendererBlobTagPart
    with StaticFormAppRendererJobPart
    with StaticFormAppRendererObservabilityPart
    with StaticFormAppRendererKnowledgePart
    with StaticFormAppRendererInformationPart
