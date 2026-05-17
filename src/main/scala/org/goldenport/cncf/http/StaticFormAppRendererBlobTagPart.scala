package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobQueryReadModel
import org.goldenport.cncf.knowledge.{KnowledgeNodeId, KnowledgeSpaceProjection}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, DiagnosticPayloadReference}
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.projection.{AuthorizationPolicyProjection, DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.cncf.search.{SearchMode, SearchPlanningProfile, WebSearchQueryPlanner}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataConfidentiality, Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   May. 18, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererBlobTagPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererSystemAdminPart =>
  import StaticFormAppRendererSupport.*

  def renderBlobAdmin(): Page =
    Page(simple_page(
      title = "Blob Admin",
      subtitle = "Blob metadata, associations, and store diagnostics",
      body =
        s"""${admin_card("Management",
             s"""<p>Use these pages to inspect Blob metadata, manage entity associations, and run controlled Blob admin actions.</p>
                |${admin_entry_cards(Vector(
             admin_entry_card("Blobs", "List Blob metadata rows and open detail pages.", "/web/blob/admin/blobs"),
             admin_entry_card("Associations", "Inspect, attach, and detach Blob-to-entity association records.", "/web/blob/admin/associations"),
             admin_entry_card("Store Status", "Inspect the active BlobStore backend status.", "/web/blob/admin/store"),
             admin_entry_card("Delete", "Open a Blob detail page to run controlled delete with optional force.", "/web/blob/admin/blobs")
           ))}""".stripMargin)}
           |${admin_card("Authorization requirements",
             """<p>Blob admin actions use the existing admin operation gate plus generic resource policies.</p>
               |<ul>
               |  <li><code>collection:blob:delete</code> controls Blob metadata delete.</li>
               |  <li><code>association:blob_attachment:create/delete/search/list</code> controls Blob attachment operations.</li>
               |  <li><code>store:blobstore:status</code> controls BlobStore status diagnostics.</li>
               |</ul>
               |<p class="mb-0"><a href="/web/system/document/specification#authorization-policies">View effective authorization policies</a></p>""".stripMargin
           )}""".stripMargin
    ))

  def renderBlobAdminBlobs(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_list_blobs", blob_admin_page_args(params), requestProperties).map { record =>
      val rows = record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${admin_empty_table_cell(9, "No Blob metadata rows are available.")}</tbody>"""
        else
          s"""<tbody>${rows.map(blob_admin_blob_row).mkString("\n")}</tbody>"""
      val nav = admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Associations" -> "/web/blob/admin/associations",
        "Store" -> "/web/blob/admin/store"
      ))
      val paging = blob_admin_paging("/web/blob/admin/blobs", params, record)
      Page(simple_page(
        title = "Blob Admin Blobs",
        subtitle = "Read-only Blob metadata inventory",
        body =
          s"""${nav}
             |${admin_card("Blobs",
               s"""<p>Showing metadata rows only. Payload bytes remain in BlobStore.</p>
                  |<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>ID</th><th>Kind</th><th>Source</th><th>Filename</th><th>Content Type</th><th>Bytes</th><th>Digest</th><th>Display URL</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${manual_raw_details("Raw Blob list", record)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDetail(
    subsystem: Subsystem,
    id: String,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_get_blob", Vector("id" -> id), requestProperties).map { record =>
      val nav = admin_nav_card(Vector(
        "Blobs" -> "/web/blob/admin/blobs",
        "Associations" -> s"/web/blob/admin/associations?id=${escapeQuery(id)}",
        "Blob admin" -> "/web/blob/admin"
      ))
      Page(simple_page(
        title = s"Blob ${escape(id)}",
        subtitle = "Blob metadata detail",
        body =
          s"""${nav}
             |${admin_card("Metadata", field_table(blob_admin_blob_fields(record)))}
             |${admin_card("Access URLs", field_table(blob_admin_url_fields(record)))}
             |${admin_card("Actions", s"""<div class="admin-action-row d-flex flex-wrap gap-2"><a class="btn btn-outline-danger" href="/web/blob/admin/blobs/${escape_path_segment(id)}/delete">Delete Blob</a></div>""")}
             |${manual_raw_details("Raw Blob metadata", record)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDelete(
    subsystem: Subsystem,
    id: String,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    for {
      blob <- blob_admin_record(subsystem, "admin_get_blob", Vector("id" -> id), requestProperties)
      associations <- blob_admin_record(subsystem, "admin_list_blob_associations", Vector("id" -> id, "offset" -> "0", "limit" -> renderer_config.adminPageSize.toString), requestProperties)
    } yield {
      val count = associations.getInt("fetchedCount").getOrElse(record_seq(associations.asMap.get("data")).size)
      val warning =
        if (count > 0)
          s"""<div class="alert alert-warning">This Blob has ${count} association(s). Default delete will fail unless <code>force</code> is enabled.</div>"""
        else
          """<div class="alert alert-info">This Blob has no visible associations. Default delete is expected to remove metadata and managed payload if present.</div>"""
      val form =
        s"""<form method="post" action="/web/blob/admin/blobs/${escape_path_segment(id)}/delete" class="border rounded p-3">
           |  <input type="hidden" name="id" value="${escape(id)}">
           |  <div class="form-check mb-3">
           |    <input class="form-check-input" type="checkbox" id="blobAdminForceDelete" name="force" value="true">
           |    <label class="form-check-label" for="blobAdminForceDelete">Force delete and remove referencing Blob associations</label>
           |  </div>
           |  <div class="admin-action-row d-flex flex-wrap gap-2">
           |    <button class="btn btn-danger" type="submit">Delete Blob</button>
           |    <a class="btn btn-outline-secondary" href="/web/blob/admin/blobs/${escape_path_segment(id)}">Cancel</a>
           |  </div>
           |</form>""".stripMargin
      Page(simple_page(
        title = s"Delete Blob ${escape(id)}",
        subtitle = "Confirm controlled Blob deletion",
        body =
          s"""${admin_nav_card(Vector("Blob detail" -> s"/web/blob/admin/blobs/${escape_path_segment(id)}", "Blobs" -> "/web/blob/admin/blobs", "Associations" -> s"/web/blob/admin/associations?id=${escapeQuery(id)}"))}
             |${warning}
             |${admin_card("Blob metadata", field_table(blob_admin_blob_fields(blob)))}
             |${admin_card("Delete confirmation", form)}
             |${manual_raw_details("Raw Blob metadata", blob)}
             |${manual_raw_details("Raw Blob associations", associations)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDeleteResult(
    subsystem: Subsystem,
    id: String,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] = {
    val force = form.get("force").exists(blob_admin_is_truthy)
    blob_admin_record(subsystem, "admin_delete_blob", Vector("id" -> id, "force" -> force.toString), requestProperties).map { record =>
      Page(simple_page(
        title = "Blob Deleted",
        subtitle = s"Deleted Blob ${escape(id)}",
        body =
          s"""${admin_nav_card(Vector("Blobs" -> "/web/blob/admin/blobs", "Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${admin_card("Delete result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Blobs" -> "/web/blob/admin/blobs", "Associations" -> "/web/blob/admin/associations"))}
             |${manual_raw_details("Raw delete result", record)}""".stripMargin
      ))
    }
  }

  def renderBlobAdminAssociations(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_list_blob_associations", blob_admin_association_args(params), requestProperties).map { record =>
      val rows = record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${admin_empty_table_cell(8, "No Blob association rows are available for this filter.")}</tbody>"""
        else
          s"""<tbody>${rows.map(blob_admin_association_row).mkString("\n")}</tbody>"""
      val nav = admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Blobs" -> "/web/blob/admin/blobs",
        "Store" -> "/web/blob/admin/store"
      ))
      val filters = blob_admin_association_filter_form(params)
      val attach = blob_admin_association_attach_form(params)
      val paging = blob_admin_paging("/web/blob/admin/associations", params, record)
      Page(simple_page(
        title = "Blob Admin Associations",
        subtitle = "Read-only Blob-to-entity association inventory",
        body =
          s"""${nav}
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">${admin_card("Attach Blob", attach)}</div>
             |  <div class="col-12 col-xl-6">${admin_card("Filters", filters)}</div>
             |</div>
             |${admin_card("Associations",
               s"""<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>Association</th><th>Source Entity</th><th>Blob</th><th>Role</th><th>Sort</th><th>Domain</th><th>Collection</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${manual_raw_details("Raw association list", record)}""".stripMargin
      ))
    }

  def renderBlobAdminAssociationAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_attach_blob_to_entity", blob_admin_association_mutation_args(form, includeSortOrder = true), requestProperties).map { record =>
      Page(simple_page(
        title = "Blob Association Attached",
        subtitle = "Blob association was created or already existed",
        body =
          s"""${admin_nav_card(Vector("Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${admin_card("Attach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Associations" -> s"/web/blob/admin/associations?sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}", "Blob detail" -> s"/web/blob/admin/blobs/${escape_path_segment(form.getOrElse("id", ""))}"))}
             |${manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderBlobAdminAssociationDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_detach_blob_from_entity", blob_admin_association_mutation_args(form, includeSortOrder = false), requestProperties).map { record =>
      Page(simple_page(
        title = "Blob Association Detached",
        subtitle = "Blob association was removed",
        body =
          s"""${admin_nav_card(Vector("Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${admin_card("Detach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Associations" -> s"/web/blob/admin/associations?sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}", "Blob detail" -> s"/web/blob/admin/blobs/${escape_path_segment(form.getOrElse("id", ""))}"))}
             |${manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociations(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_association_record(subsystem, "admin_list_associations", admin_association_args(params), requestProperties).map { record =>
      val rows = record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${admin_empty_table_cell(8, "No Association rows are available for this filter.")}</tbody>"""
        else
          s"""<tbody>${rows.map(row => admin_association_row(row, includeDetach = true)).mkString("\n")}</tbody>"""
      val filters = admin_association_filter_form(params)
      val attach = admin_association_attach_form(params)
      val paging = admin_association_paging("/web/admin/associations", params, record)
      Page(simple_page(
        title = "Association Administration",
        subtitle = "Generic Entity-to-Entity Association inventory",
        body =
          s"""${admin_nav_card(Vector("System admin" -> "/web/system/admin", "Blob associations" -> "/web/blob/admin/associations"))}
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">${admin_card("Attach Association", attach)}</div>
             |  <div class="col-12 col-xl-6">${admin_card("Filters", filters)}</div>
             |</div>
             |${admin_card("Associations",
               s"""<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>Association</th><th>Source Entity</th><th>Target Entity</th><th>Target kind</th><th>Role</th><th>Sort</th><th>Domain</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${manual_raw_details("Raw association list", record)}""".stripMargin
      ))
    }

  def renderAdminTags(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    for {
      tree <- admin_tag_record(subsystem, "tag_tree", admin_tag_tree_args(params), requestProperties)
      search <- admin_tag_search_record(subsystem, params, requestProperties)
    } yield {
      val tags = record_seq(tree.asMap.get("data"))
      val tagSpace = params.get("tagSpace").filter(_.nonEmpty).getOrElse("default")
      val table =
        if (tags.isEmpty)
          s"""<tbody>${admin_empty_table_cell(9, "No Tags are available for this TagSpace.")}</tbody>"""
        else
          s"""<tbody>${tags.map(admin_tag_row).mkString("\n")}</tbody>"""
      val searchHtml = search.map(admin_tag_search_result(params, _)).getOrElse("")
      val emptyAlert =
        if (tags.isEmpty) admin_tag_empty_alert("No Tags are available for this TagSpace.")
        else ""
      Page(simple_page(
        title = "Tag Administration",
        subtitle = "Hierarchical Tag tree and Entity-to-Tag management",
        body =
          s"""${admin_nav_card(Vector("System admin" -> "/web/system/admin", "Associations" -> "/web/admin/associations"))}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-column flex-md-row justify-content-between gap-3 mb-3">
             |      <div>
             |        <h2 class="card-title h5 mb-1">TagSpace selector</h2>
             |        <p class="text-body-secondary mb-0">Current TagSpace <span class="badge text-bg-secondary">${escape(tagSpace)}</span></p>
             |      </div>
             |      <div class="text-body-secondary small">Tags are shared master data inside the selected TagSpace.</div>
             |    </div>
             |    ${admin_tag_filter_form(params)}
             |  </div>
             |</article>
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">
             |    <article class="card admin-card h-100">
             |      <div class="card-body">
             |        <h2 class="card-title h5">Create Tag</h2>
             |        ${admin_tag_create_form(params)}
             |      </div>
             |    </article>
             |  </div>
             |  <div class="col-12 col-xl-6">
             |    <article class="card admin-card h-100">
             |      <div class="card-body">
             |        <h2 class="card-title h5">Search Entities by Tag</h2>
             |        ${admin_tag_search_form(params)}
             |      </div>
             |    </article>
             |  </div>
             |</div>
             |${searchHtml}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
             |      <div>
             |        <h2 class="card-title h5 mb-1">Tags</h2>
             |        <p class="text-body-secondary mb-0">Browse, update, and move Tags in <span class="badge text-bg-secondary">${escape(tagSpace)}</span>.</p>
             |      </div>
             |      <span class="badge text-bg-light border align-self-start">${tags.size} tags</span>
             |    </div>
             |    ${emptyAlert}
             |    <div class="table-responsive">
             |      <table class="table table-sm table-hover align-middle mb-0">
             |      <thead><tr><th>Path</th><th>Key</th><th>TagSpace</th><th>Usage</th><th>Sort</th><th>Title</th><th>Description</th><th>Update</th><th>Move</th></tr></thead>
             |      ${table}
             |    </table>
             |  </div>
             |  </div>
             |</article>
             |${manual_raw_details("Raw tag tree", tree)}""".stripMargin
      ))
    }

  def renderAdminTagCreateResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_tag_record(subsystem, "tag_create", admin_tag_mutation_args(form, includeParent = true), requestProperties).map { record =>
      admin_tag_result_page("Tag Created", "Tag was created", form, record)
    }

  def renderAdminTagUpdateResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_tag_record(subsystem, "tag_update", admin_tag_update_args(form), requestProperties).map { record =>
      admin_tag_result_page("Tag Updated", "Tag metadata was updated", form, record)
    }

  def renderAdminTagMoveResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_tag_record(subsystem, "tag_move", admin_tag_move_args(form), requestProperties).map { record =>
      admin_tag_result_page("Tag Moved", "Tag path was updated", form, record)
    }

  def renderAdminTagAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_tag_record(subsystem, "tag_attach", admin_tag_attach_args(form), requestProperties).map { record =>
      Page(simple_page(
        title = "Tag Attached",
        subtitle = "TagAttachment was created or already existed",
        body =
          s"""${admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
             |${admin_card("Attach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${escapeQuery(form.getOrElse("tagSpace", ""))}&sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}"))}
             |${manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderAdminTagDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_tag_record(subsystem, "tag_detach", admin_tag_detach_args(form), requestProperties).map { record =>
      Page(simple_page(
        title = "Tag Detached",
        subtitle = "TagAttachment was removed",
        body =
          s"""${admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
             |${admin_card("Detach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${escapeQuery(form.getOrElse("tagSpace", ""))}&sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}"))}
             |${manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociationAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_association_record(subsystem, "admin_attach_association", admin_association_mutation_args(form, includeSortOrder = true), requestProperties).map { record =>
      Page(simple_page(
        title = "Association Attached",
        subtitle = "Association was created or already existed",
        body =
          s"""${admin_nav_card(Vector("Associations" -> "/web/admin/associations", "Blob associations" -> "/web/blob/admin/associations"))}
             |${admin_card("Attach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Associations" -> s"/web/admin/associations?domain=${escapeQuery(form.getOrElse("domain", ""))}&sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}"))}
             |${manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociationDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    admin_association_record(subsystem, "admin_detach_association", admin_association_mutation_args(form, includeSortOrder = false), requestProperties).map { record =>
      Page(simple_page(
        title = "Association Detached",
        subtitle = "Association was removed",
        body =
          s"""${admin_nav_card(Vector("Associations" -> "/web/admin/associations", "Blob associations" -> "/web/blob/admin/associations"))}
             |${admin_card("Detach result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${admin_action_row(Vector("Back to Associations" -> s"/web/admin/associations?domain=${escapeQuery(form.getOrElse("domain", ""))}&sourceEntityId=${escapeQuery(form.getOrElse("sourceEntityId", ""))}"))}
             |${manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderBlobAdminStore(
    subsystem: Subsystem,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    blob_admin_record(subsystem, "admin_blob_store_status", Vector.empty, requestProperties).map { record =>
      val nav = admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Blobs" -> "/web/blob/admin/blobs",
        "Associations" -> "/web/blob/admin/associations"
      ))
      Page(simple_page(
        title = "Blob Admin Store",
        subtitle = "BlobStore backend status",
        body =
          s"""${nav}
             |${admin_card("Store Status", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
             |${manual_raw_details("Raw BlobStore status", record)}""".stripMargin
      ))
    }

  protected def blob_admin_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(blob_admin_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Blob admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  protected def admin_association_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(admin_association_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Association admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  protected def admin_tag_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(admin_tag_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Tag admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  protected def admin_tag_search_record(
    subsystem: Subsystem,
    params: Map[String, String],
    requestProperties: Vector[(String, String)]
  ): Consequence[Option[Record]] =
    if (params.get("tagRef").orElse(params.get("tag")).exists(_.trim.nonEmpty) &&
        params.get("component").exists(_.trim.nonEmpty) &&
        params.get("entity").orElse(params.get("entityName")).exists(_.trim.nonEmpty))
      admin_tag_record(subsystem, "tag_search_entities", admin_tag_search_args(params), requestProperties).map(Some(_))
    else
      Consequence.success(None)

  protected def admin_tag_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "tag",
      service = "tag",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList,
      properties = requestProperties.map { case (key, value) => Property(key, value, None) }.toList
    )

  protected def admin_association_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "admin",
      service = "association",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList,
      properties = requestProperties.map { case (key, value) => Property(key, value, None) }.toList
    )

  protected def blob_admin_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "blob",
      service = "blob",
      operation = operation,
      properties = (requestProperties ++ args).map { case (key, value) => Property(key, value, None) }.toList
    )

  protected def blob_admin_page_args(
    params: Map[String, String]
  ): Vector[(String, String)] = {
    val limit = params.get("limit").orElse(params.get("pageSize")).flatMap(_.toIntOption).filter(_ > 0).getOrElse(renderer_config.adminPageSize)
    val offset = params.get("offset").flatMap(_.toIntOption).filter(_ >= 0).getOrElse {
      params.get("page").flatMap(_.toIntOption).filter(_ > 0).map(page => (page - 1) * limit).getOrElse(0)
    }
    Vector("offset" -> offset.toString, "limit" -> limit.toString)
  }

  protected def blob_admin_association_args(
    params: Map[String, String]
  ): Vector[(String, String)] =
    blob_admin_page_args(params) ++
      Vector("sourceEntityId", "id", "role").flatMap(key => params.get(key).filter(_.nonEmpty).map(key -> _))

  protected def blob_admin_association_mutation_args(
    values: Map[String, String],
    includeSortOrder: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("sourceEntityId", "id", "role").flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    if (includeSortOrder)
      base ++ values.get("sortOrder").filter(_.nonEmpty).map("sortOrder" -> _).toVector
    else
      base
  }

  protected def admin_association_args(
    params: Map[String, String]
  ): Vector[(String, String)] = {
    val pagesize = params.get("pageSize").orElse(params.get("limit")).flatMap(_.toIntOption).filter(_ > 0).getOrElse(renderer_config.adminPageSize)
    val page = params.get("page").flatMap(_.toIntOption).filter(_ > 0).getOrElse {
      params.get("offset").flatMap(_.toIntOption).filter(_ >= 0).map(offset => offset / pagesize + 1).getOrElse(1)
    }
    Vector("page" -> page.toString, "pageSize" -> pagesize.toString) ++
      Vector("domain" -> params.getOrElse("domain", "association")) ++
      Vector("sourceEntityId", "targetEntityId", "targetKind", "role")
        .flatMap(key => params.get(key).filter(_.nonEmpty).map(key -> _))
  }

  protected def admin_association_mutation_args(
    values: Map[String, String],
    includeSortOrder: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("domain", "sourceEntityId", "targetEntityId", "targetKind", "role")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    if (includeSortOrder)
      base ++ values.get("sortOrder").filter(_.nonEmpty).map("sortOrder" -> _).toVector
    else
      base
  }

  protected def admin_tag_tree_args(
    params: Map[String, String]
  ): Vector[(String, String)] =
    params.get("tagSpace").filter(_.nonEmpty).map("tagSpace" -> _).toVector

  protected def admin_tag_mutation_args(
    values: Map[String, String],
    includeParent: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("key", "tagSpace", "usageKind", "sortOrder", "title", "description")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    val parent =
      if (includeParent)
        values.get("parentTagId").filter(_.nonEmpty).map("parentTagId" -> _).toVector ++
          values.get("parentTagRef").filter(_.nonEmpty).map("parentTagRef" -> _).toVector
      else
        Vector.empty
    base ++ parent
  }

  protected def admin_tag_update_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("tagRef", "tagSpace", "usageKind", "sortOrder", "title", "description", "attributes")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  protected def admin_tag_move_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("tagRef", "tagSpace", "newParentTagRef", "newKey")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  protected def admin_tag_attach_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("sourceEntityId", "tagRef", "tagSpace", "role", "sortOrder")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  protected def admin_tag_detach_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("sourceEntityId", "tagRef", "tagSpace", "role")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  protected def admin_tag_search_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("component", "entity", "entityName", "tagRef", "tagSpace", "role", "includeDescendants")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  protected def blob_admin_is_truthy(
    value: String
  ): Boolean =
    Set("true", "1", "yes", "on").contains(value.trim.toLowerCase(java.util.Locale.ROOT))

  protected def admin_tag_row(
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val path = record.getString("path").getOrElse("")
    val tagSpace = record.getString("tagSpace").getOrElse("")
    val usage = record.getString("usageKind").getOrElse("")
    val sort = record.getString("sortOrder").getOrElse("")
    s"""<tr>
       |  <td><div><code class="fw-semibold">${escape(path)}</code></div><div class="small text-body-secondary">${escape(id)}</div></td>
       |  <td><code>${escape(record.getString("key").getOrElse(""))}</code></td>
       |  <td><span class="badge text-bg-secondary">${escape(tagSpace)}</span></td>
       |  <td><span class="badge text-bg-light border">${escape(usage)}</span></td>
       |  <td>${if (sort.isEmpty) "" else s"""<span class="badge text-bg-light border">${escape(sort)}</span>"""}</td>
       |  <td>${escape(record.getString("title").getOrElse(""))}</td>
       |  <td>${escape(record.getString("description").getOrElse(""))}</td>
       |  <td>${admin_tag_update_form(record)}</td>
       |  <td>${admin_tag_move_form(record)}</td>
       |</tr>""".stripMargin
  }

  protected def admin_tag_filter_form(
    params: Map[String, String]
  ): String =
    s"""<form method="get" action="/web/admin/tags" class="row g-2 align-items-end">
       |  <div class="col-12 col-md-7"><label class="form-label" for="tagAdminTagSpace">TagSpace</label><input class="form-control" id="tagAdminTagSpace" name="tagSpace" value="${escape(params.getOrElse("tagSpace", ""))}" placeholder="default"><div class="form-text">Use blank to open the default TagSpace.</div></div>
       |  <div class="col-6 col-md-2"><button class="btn btn-primary w-100" type="submit">Open</button></div>
       |  <div class="col-6 col-md-2"><a class="btn btn-outline-secondary w-100" href="/web/admin/tags">Default</a></div>
       |</form>""".stripMargin

  protected def admin_tag_create_form(
    params: Map[String, String]
  ): String =
    s"""<form method="post" action="/web/admin/tags/create" class="row g-2 align-items-end">
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateSpace">TagSpace</label><input class="form-control" id="tagCreateSpace" name="tagSpace" value="${escape(params.getOrElse("tagSpace", ""))}" placeholder="default"></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateKey">Key</label><input class="form-control" id="tagCreateKey" name="key" required></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateParent">Parent tag id/ref</label><input class="form-control" id="tagCreateParent" name="parentTagRef"></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateUsage">Usage</label><input class="form-control" id="tagCreateUsage" name="usageKind" list="tagUsageOptions" value="general"></div>
       |  <div class="col-12 col-md-3"><label class="form-label" for="tagCreateSort">Sort</label><input class="form-control" id="tagCreateSort" name="sortOrder"></div>
       |  <div class="col-12 col-md-5"><label class="form-label" for="tagCreateTitle">Title</label><input class="form-control" id="tagCreateTitle" name="title"></div>
       |  <div class="col-12"><label class="form-label" for="tagCreateDescription">Description</label><input class="form-control" id="tagCreateDescription" name="description"></div>
       |  <div class="col-12 d-flex justify-content-end"><button class="btn btn-primary" type="submit">Create Tag</button></div>
       |  <datalist id="tagUsageOptions"><option value="general"><option value="cms"><option value="navigation"><option value="powertype"></datalist>
       |</form>""".stripMargin

  protected def admin_tag_update_form(
    record: Record
  ): String =
    s"""<form method="post" action="/web/admin/tags/update" class="vstack gap-1">
       |  <input type="hidden" name="tagRef" value="${escape(record.getString("id").getOrElse(""))}">
       |  <input type="hidden" name="tagSpace" value="${escape(record.getString("tagSpace").getOrElse(""))}">
       |  <div class="input-group input-group-sm"><span class="input-group-text">Title</span><input class="form-control form-control-sm" name="title" value="${escape(record.getString("title").getOrElse(""))}" placeholder="Title"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Description</span><input class="form-control form-control-sm" name="description" value="${escape(record.getString("description").getOrElse(""))}" placeholder="Description"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Usage</span><input class="form-control form-control-sm" name="usageKind" value="${escape(record.getString("usageKind").getOrElse("general"))}" list="tagUsageOptions"><span class="input-group-text">Sort</span><input class="form-control form-control-sm" name="sortOrder" value="${escape(record.getString("sortOrder").getOrElse(""))}" placeholder="Sort"><button class="btn btn-outline-primary btn-sm" type="submit">Save</button></div>
       |</form>""".stripMargin

  protected def admin_tag_move_form(
    record: Record
  ): String =
    s"""<form method="post" action="/web/admin/tags/move" class="vstack gap-1">
       |  <input type="hidden" name="tagRef" value="${escape(record.getString("id").getOrElse(""))}">
       |  <input type="hidden" name="tagSpace" value="${escape(record.getString("tagSpace").getOrElse(""))}">
       |  <div class="input-group input-group-sm"><span class="input-group-text">Parent</span><input class="form-control form-control-sm" name="newParentTagRef" value="${escape(record.getString("parentTagId").getOrElse(""))}" placeholder="blank for root"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Key</span><input class="form-control form-control-sm" name="newKey" value="${escape(record.getString("key").getOrElse(""))}" placeholder="New key"><button class="btn btn-outline-primary btn-sm" type="submit">Move</button></div>
       |</form>""".stripMargin

  protected def admin_tag_search_form(
    params: Map[String, String]
  ): String =
    s"""<form method="get" action="/web/admin/tags" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="tagSearchSpace">TagSpace</label><input class="form-control" id="tagSearchSpace" name="tagSpace" value="${escape(params.getOrElse("tagSpace", ""))}" placeholder="default"></div>
       |  <div class="col-md-2"><label class="form-label" for="tagSearchComponent">Component</label><input class="form-control" id="tagSearchComponent" name="component" value="${escape(params.getOrElse("component", ""))}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="tagSearchEntity">Entity</label><input class="form-control" id="tagSearchEntity" name="entity" value="${escape(params.getOrElse("entity", ""))}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="tagSearchRef">Tag ref/path</label><input class="form-control" id="tagSearchRef" name="tagRef" value="${escape(params.getOrElse("tagRef", ""))}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="tagSearchRole">Role</label><input class="form-control" id="tagSearchRole" name="role" value="${escape(params.getOrElse("role", "tag"))}"></div>
       |  <div class="col-md-1"><label class="form-label" for="tagSearchDesc">Desc</label><select class="form-select" id="tagSearchDesc" name="includeDescendants"><option value="true"${if (params.get("includeDescendants").forall(_ != "false")) " selected" else ""}>yes</option><option value="false"${if (params.get("includeDescendants").contains("false")) " selected" else ""}>no</option></select></div>
       |  <div class="col-md-1"><button class="btn btn-primary w-100" type="submit">Search</button></div>
       |</form>""".stripMargin

  protected def admin_tag_search_result(
    params: Map[String, String],
    record: Record
  ): String = {
    val component = params.getOrElse("component", "")
    val entity = params.getOrElse("entity", params.getOrElse("entityName", ""))
    val tagRef = params.getOrElse("tagRef", params.getOrElse("tag", ""))
    val tagSpace = params.get("tagSpace").filter(_.nonEmpty).getOrElse("default")
    val role = params.getOrElse("role", "tag")
    val rows = record_seq(record.asMap.get("data"))
    val emptyAlert =
      if (rows.isEmpty) admin_tag_empty_alert("No visible Entities matched this Tag filter.")
      else ""
    val body =
      if (rows.isEmpty)
        s"""<tbody>${admin_empty_table_cell(3, "No visible Entities matched this Tag filter.")}</tbody>"""
      else
        s"""<tbody>${rows.map(row => admin_tag_search_result_row(component, entity, row)).mkString("\n")}</tbody>"""
    s"""<article class="card admin-card">
       |  <div class="card-body">
       |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
       |      <div>
       |        <h2 class="card-title h5 mb-1">Tag search result</h2>
       |        <p class="text-body-secondary mb-0">Tag <code>${escape(tagRef)}</code> in <span class="badge text-bg-secondary">${escape(tagSpace)}</span>, role <span class="badge text-bg-light border">${escape(role)}</span>.</p>
       |      </div>
       |      <span class="badge text-bg-light border align-self-start">${rows.size} entities</span>
       |    </div>
       |    ${emptyAlert}
       |    <div class="table-responsive">
       |      <table class="table table-sm table-hover align-middle mb-0">
       |        <thead><tr><th>Entity</th><th>Title/Name</th><th>Raw</th></tr></thead>
       |        ${body}
       |      </table>
       |    </div>
       |  </div>
       |</article>""".stripMargin
  }

  protected def admin_tag_empty_alert(
    message: String
  ): String =
    s"""<div class="alert alert-secondary mb-3" role="status">${escape(message)}</div>"""

  protected def admin_tag_search_result_row(
    component: String,
    entity: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val label = record.getString("title").orElse(record.getString("name")).orElse(record.getString("label")).getOrElse("")
    val componentPath = NamingConventions.toNormalizedSegment(component)
    val entityPath = NamingConventions.toNormalizedSegment(entity)
    s"""<tr>
       |  <td><a href="/web/${escape_path_segment(componentPath)}/admin/entities/${escape_path_segment(entityPath)}/${escape_path_segment(id)}"><code>${escape(id)}</code></a></td>
       |  <td>${escape(label)}</td>
       |  <td><code>${escape(record.toString)}</code></td>
       |</tr>""".stripMargin
  }

  protected def admin_tag_result_page(
    title: String,
    subtitle: String,
    form: Map[String, String],
    record: Record
  ): Page =
    Page(simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""${admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
           |${admin_card("Tag result", field_table(record.asMap.toVector.map { case (k, v) => k -> display_value(v) }.sortBy(_._1)))}
           |${admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${escapeQuery(form.getOrElse("tagSpace", record.getString("tagSpace").getOrElse("")))}"))}
           |${manual_raw_details("Raw tag result", record)}""".stripMargin
    ))

  protected def blob_admin_blob_row(
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val displayurl = record.getString("displayUrl").getOrElse("")
    val displaylink =
      if (displayurl.isEmpty) ""
      else
        blob_admin_safe_display_url(displayurl) match {
          case Some(url) => s"""<a href="${escape(url)}">display</a>"""
          case None => s"""<code>${escape(displayurl)}</code>"""
        }
    s"""<tr>
       |  <td><code>${escape(id)}</code></td>
       |  <td>${escape(record.getString("kind").getOrElse(""))}</td>
       |  <td>${escape(record.getString("sourceMode").getOrElse(""))}</td>
       |  <td>${escape(record.getString("filename").getOrElse(""))}</td>
       |  <td><code>${escape(record.getString("contentType").getOrElse(""))}</code></td>
       |  <td>${escape(record.getString("byteSize").getOrElse(""))}</td>
       |  <td><code>${escape(record.getString("digest").getOrElse(""))}</code></td>
       |  <td>${displaylink}</td>
       |  <td><a href="/web/blob/admin/blobs/${escape_path_segment(id)}">Open</a></td>
       |</tr>""".stripMargin
  }

  protected def blob_admin_safe_display_url(
    url: String
  ): Option[String] = {
    val trimmed = url.trim
    if (trimmed.startsWith("/web/blob/") || trimmed.startsWith("/rest/v1/blob/"))
      Some(trimmed)
    else
      org.goldenport.cncf.blob.BlobExternalUrlPolicy.normalize(trimmed).toOption
  }

  protected def blob_admin_association_row(
    record: Record
  ): String = {
    val blobid = record.getString("targetEntityId").getOrElse("")
    s"""<tr>
       |  <td><code>${escape(record.getString("associationId").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("sourceEntityId").getOrElse(""))}</code></td>
       |  <td><code>${escape(blobid)}</code></td>
       |  <td>${escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td>${escape(record.getString("associationDomain").getOrElse(""))}</td>
       |  <td><code>${escape(record.getString("id").getOrElse(""))}</code></td>
       |  <td><a href="/web/blob/admin/blobs/${escape_path_segment(blobid)}">Blob</a>${blob_admin_detach_form(record)}</td>
       |</tr>""".stripMargin
  }

  protected def blob_admin_detach_form(
    record: Record
  ): String = {
    val source = record.getString("sourceEntityId").getOrElse("")
    val blobid = record.getString("targetEntityId").getOrElse("")
    val role = record.getString("role").getOrElse("")
    admin_confirm_post_form(
      action = "/web/blob/admin/associations/detach",
      label = "Detach",
      title = "Detach Blob association",
      message = s"Detach Blob ${blobid} from Entity ${source}?",
      hiddenFields = Vector("sourceEntityId" -> source, "id" -> blobid, "role" -> role),
      formClass = "d-inline ms-2"
    )
  }

  protected def admin_association_row(
    record: Record,
    includeDetach: Boolean
  ): String = {
    val detach = if (includeDetach) admin_association_detach_form(record) else ""
    s"""<tr>
       |  <td><code>${escape(record.getString("associationId").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("sourceEntityId").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("targetEntityId").getOrElse(""))}</code></td>
       |  <td>${escape(record.getString("targetKind").getOrElse(""))}</td>
       |  <td>${escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td>${escape(record.getString("associationDomain").getOrElse(""))}</td>
       |  <td>${detach}</td>
       |</tr>""".stripMargin
  }

  protected def admin_association_detach_form(
    record: Record
  ): String = {
    val domain = record.getString("associationDomain").getOrElse("")
    val source = record.getString("sourceEntityId").getOrElse("")
    val target = record.getString("targetEntityId").getOrElse("")
    val targetKind = record.getString("targetKind").getOrElse("")
    val role = record.getString("role").getOrElse("")
    admin_confirm_post_form(
      action = "/web/admin/associations/detach",
      label = "Detach",
      title = "Detach Association",
      message = s"Detach ${targetKind} ${target} from Entity ${source}?",
      hiddenFields = Vector(
        "domain" -> domain,
        "sourceEntityId" -> source,
        "targetEntityId" -> target,
        "targetKind" -> targetKind,
        "role" -> role
      )
    )
  }

  protected def blob_admin_blob_fields(
    record: Record
  ): Vector[(String, String)] =
    Vector(
      "id",
      "kind",
      "sourceMode",
      "filename",
      "contentType",
      "byteSize",
      "digest",
      "storageRef",
      "externalUrl",
      "urlSource",
      "createdAt",
      "updatedAt"
    ).flatMap(key => record.getString(key).map(key -> _))

  protected def blob_admin_url_fields(
    record: Record
  ): Vector[(String, String)] =
    Vector("displayUrl", "downloadUrl").flatMap(key => record.getString(key).map(key -> _))

  protected def blob_admin_association_filter_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      escape(params.getOrElse(key, ""))
    s"""<form method="get" action="/web/blob/admin/associations" class="row g-2 align-items-end">
       |  <div class="col-md-4"><label class="form-label" for="blobAdminSourceEntityId">Source entity</label><input class="form-control" id="blobAdminSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}"></div>
       |  <div class="col-md-4"><label class="form-label" for="blobAdminId">Blob id</label><input class="form-control" id="blobAdminId" name="id" value="${value("id")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminRole">Role</label><input class="form-control" id="blobAdminRole" name="role" value="${value("role")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminLimit">Limit</label><input class="form-control" id="blobAdminLimit" name="limit" value="${escape(params.getOrElse("limit", renderer_config.adminPageSize.toString))}"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Filter</button> <a class="btn btn-outline-secondary" href="/web/blob/admin/associations">Clear</a></div>
       |</form>""".stripMargin
  }

  protected def blob_admin_association_attach_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      escape(params.getOrElse(key, ""))
    s"""<form method="post" action="/web/blob/admin/associations/attach" class="row g-2 align-items-end">
       |  <div class="col-md-4"><label class="form-label" for="blobAdminAttachSourceEntityId">Source entity</label><input class="form-control" id="blobAdminAttachSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}" required></div>
       |  <div class="col-md-4"><label class="form-label" for="blobAdminAttachId">Blob id</label><input class="form-control" id="blobAdminAttachId" name="id" value="${value("id")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminAttachRole">Role</label><input class="form-control" id="blobAdminAttachRole" name="role" value="${value("role")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminAttachSortOrder">Sort</label><input class="form-control" id="blobAdminAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Attach Blob</button></div>
       |</form>""".stripMargin
  }

  protected def admin_association_filter_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      escape(params.getOrElse(key, ""))
    val domainValue = escape(params.getOrElse("domain", "association"))
    s"""<form method="get" action="/web/admin/associations" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="associationAdminDomain">Domain</label><input class="form-control" id="associationAdminDomain" name="domain" value="${domainValue}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAdminSourceEntityId">Source entity</label><input class="form-control" id="associationAdminSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}"></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAdminTargetEntityId">Target entity</label><input class="form-control" id="associationAdminTargetEntityId" name="targetEntityId" value="${value("targetEntityId")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="associationAdminTargetKind">Target kind</label><input class="form-control" id="associationAdminTargetKind" name="targetKind" value="${value("targetKind")}"></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAdminRole">Role</label><input class="form-control" id="associationAdminRole" name="role" value="${value("role")}"></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAdminPageSize">Page size</label><input class="form-control" id="associationAdminPageSize" name="pageSize" value="${escape(params.get("pageSize").orElse(params.get("limit")).getOrElse(renderer_config.adminPageSize.toString))}"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Filter</button> <a class="btn btn-outline-secondary" href="/web/admin/associations">Clear</a></div>
       |</form>""".stripMargin
  }

  protected def admin_association_attach_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      escape(params.getOrElse(key, ""))
    val domainValue = escape(params.getOrElse("domain", "association"))
    s"""<form method="post" action="/web/admin/associations/attach" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="associationAttachDomain">Domain</label><input class="form-control" id="associationAttachDomain" name="domain" value="${domainValue}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAttachSourceEntityId">Source entity</label><input class="form-control" id="associationAttachSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAttachTargetEntityId">Target entity</label><input class="form-control" id="associationAttachTargetEntityId" name="targetEntityId" value="${value("targetEntityId")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="associationAttachTargetKind">Target kind</label><input class="form-control" id="associationAttachTargetKind" name="targetKind" value="${value("targetKind")}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAttachRole">Role</label><input class="form-control" id="associationAttachRole" name="role" value="${value("role")}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAttachSortOrder">Sort</label><input class="form-control" id="associationAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Attach Association</button></div>
       |</form>""".stripMargin
  }

  protected def admin_entity_images_section(
    record: Option[Record],
    fallbackId: String
  ): String = {
    val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
    val images = record.map(r => record_seq(r.getAny("images"))).getOrElse(Vector.empty)
    val representative = record.flatMap(_.getAny("representativeImage")).collect { case r: Record => r }
    val representativeHtml = representative.map(admin_entity_representative_image).getOrElse(admin_empty_state("No representative image is currently derived."))
    val table =
      if (images.isEmpty)
        s"""<tbody>${admin_empty_table_cell(7, "No BlobAttachment images are associated with this Entity.")}</tbody>"""
      else
        s"""<tbody>${images.map(row => admin_entity_image_row(sourceId, row)).mkString("\n")}</tbody>"""
    s"""<article class="card admin-card mt-3">
       |  <div class="card-body">
       |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
       |      <h2 class="card-title mb-0">Images</h2>
       |      <a class="btn btn-outline-secondary btn-sm" href="/web/blob/admin/associations?sourceEntityId=${escapeQuery(sourceId)}">Open Blob associations</a>
       |    </div>
       |    <section class="mb-3">
       |      <h3 class="h6">Representative Image</h3>
       |      ${representativeHtml}
       |    </section>
       |    <section class="mb-3">
       |      <h3 class="h6">Attach Existing Blob</h3>
       |      ${admin_entity_image_attach_form(sourceId)}
       |    </section>
       |    <section>
       |      <h3 class="h6">Associated Images</h3>
       |      <div class="table-responsive">
       |        <table class="table table-sm align-middle">
       |          <thead><tr><th>Role</th><th>Sort</th><th>Blob</th><th>Kind</th><th>Filename</th><th>Display</th><th>Actions</th></tr></thead>
       |          ${table}
       |        </table>
       |      </div>
       |    </section>
       |  </div>
       |</article>""".stripMargin
  }

  protected def admin_entity_representative_image(
    record: Record
  ): String = {
    val url = admin_entity_image_display_url(record)
    val id = record.getString("id").getOrElse("")
    val role = record.getString("role").getOrElse("")
    val filename = record.getString("filename").getOrElse(id)
    val media =
      url.flatMap(blob_admin_safe_display_url) match {
        case Some(safe) =>
          s"""<img src="${escape(safe)}" alt="${escape(filename)}" class="img-thumbnail" style="max-width: 12rem; max-height: 8rem;">"""
        case None =>
          s"""<code>${escape(url.getOrElse(""))}</code>"""
      }
    s"""<div class="d-flex flex-wrap gap-3 align-items-center">
       |  ${media}
       |  <div><div><strong>${escape(role)}</strong></div><code>${escape(id)}</code></div>
       |</div>""".stripMargin
  }

  protected def admin_entity_image_row(
    sourceId: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val display = admin_entity_image_display_url(record).flatMap(blob_admin_safe_display_url) match {
      case Some(url) => s"""<a href="${escape(url)}">display</a>"""
      case None => admin_entity_image_display_url(record).map(x => s"""<code>${escape(x)}</code>""").getOrElse("")
    }
    s"""<tr>
       |  <td>${escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td><code>${escape(id)}</code></td>
       |  <td>${escape(record.getString("kind").getOrElse(""))}</td>
       |  <td>${escape(record.getString("filename").getOrElse(""))}</td>
       |  <td>${display}</td>
       |  <td><a href="/web/blob/admin/blobs/${escape_path_segment(id)}">Blob</a>${admin_entity_image_detach_form(sourceId, record)}</td>
       |</tr>""".stripMargin
  }

  protected def admin_entity_image_display_url(
    record: Record
  ): Option[String] =
    record.getString("displayPath").orElse(record.getString("displayUrl"))

  protected def admin_entity_image_attach_form(
    sourceId: String
  ): String =
    s"""<form method="post" action="/web/blob/admin/associations/attach" class="row g-2 align-items-end">
       |  <input type="hidden" name="sourceEntityId" value="${escape(sourceId)}">
       |  <div class="col-md-5"><label class="form-label" for="entityImageAttachId">Blob id</label><input class="form-control" id="entityImageAttachId" name="id" required></div>
       |  <div class="col-md-3"><label class="form-label" for="entityImageAttachRole">Role</label><input class="form-control" id="entityImageAttachRole" name="role" list="entityImageRoleOptions" required></div>
       |  <div class="col-md-2"><label class="form-label" for="entityImageAttachSortOrder">Sort</label><input class="form-control" id="entityImageAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
       |  <datalist id="entityImageRoleOptions"><option value="primary"><option value="cover"><option value="thumbnail"><option value="gallery"><option value="inline"></datalist>
       |</form>""".stripMargin

  protected def admin_entity_image_detach_form(
    sourceId: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val role = record.getString("role").getOrElse("")
    admin_confirm_post_form(
      action = "/web/blob/admin/associations/detach",
      label = "Detach",
      title = "Detach Entity image",
      message = s"Detach Blob ${id} from Entity ${sourceId}?",
      hiddenFields = Vector("sourceEntityId" -> sourceId, "id" -> id, "role" -> role),
      formClass = "d-inline ms-2"
    )
  }

  protected def admin_entity_tags_section(
    subsystem: Subsystem,
    record: Option[Record],
    fallbackId: String,
    values: Map[String, String]
  ): String = {
    val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
    val tagSpace = values.getOrElse("tagSpace", "")
    val summary = admin_operation_record(
      subsystem,
      "/tag/tag/tag_list_entity_tags",
      Record.dataAuto(
        "sourceEntityId" -> sourceId,
        "tagSpace" -> tagSpace
      )
    )
    val rows = summary.map { r =>
      val tags = record_seq(r.asMap.get("tags"))
      val associations = record_seq(r.asMap.get("associations"))
      tags.zipWithIndex.map { case (tag, index) =>
        admin_entity_tag_row(sourceId, tagSpace, tag, associations.lift(index))
      }
    }.getOrElse(Vector.empty)
    val table =
      if (rows.isEmpty)
        s"""<tbody>${admin_empty_table_cell(8, "No Tags are attached to this Entity for the selected TagSpace.")}</tbody>"""
      else
        s"""<tbody>${rows.mkString("\n")}</tbody>"""
    s"""<article class="card admin-card mt-3">
       |  <div class="card-body">
       |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
       |      <h2 class="card-title mb-0">Tags</h2>
       |      <a class="btn btn-outline-secondary btn-sm" href="/web/admin/tags?tagSpace=${escapeQuery(tagSpace)}&amp;sourceEntityId=${escapeQuery(sourceId)}">Open Tags</a>
       |    </div>
       |    <section class="mb-3">
       |      <h3 class="h6">Attach Tag</h3>
       |      ${admin_entity_tag_attach_form(sourceId, tagSpace)}
       |    </section>
       |    <section>
       |      <h3 class="h6">Attached Tags</h3>
       |      <div class="table-responsive">
       |        <table class="table table-sm align-middle">
       |          <thead><tr><th>Path</th><th>TagSpace</th><th>Role</th><th>Sort</th><th>Usage</th><th>Title</th><th>Description</th><th>Actions</th></tr></thead>
       |          ${table}
       |        </table>
       |      </div>
       |    </section>
       |  </div>
       |</article>""".stripMargin
  }

  protected def admin_entity_tag_row(
    sourceId: String,
    tagSpace: String,
    record: Record,
    association: Option[Record]
  ): String = {
    val path = record.getString("path").getOrElse("")
    val role = association.flatMap(_.getString("role")).getOrElse("tag")
    val sortOrder = association.flatMap(_.getString("sortOrder")).getOrElse("")
    s"""<tr>
       |  <td><code>${escape(path)}</code></td>
       |  <td>${escape(record.getString("tagSpace").getOrElse(tagSpace))}</td>
       |  <td>${escape(role)}</td>
       |  <td>${escape(sortOrder)}</td>
       |  <td>${escape(record.getString("usageKind").getOrElse(""))}</td>
       |  <td>${escape(record.getString("title").getOrElse(""))}</td>
       |  <td>${escape(record.getString("description").getOrElse(""))}</td>
       |  <td>${admin_entity_tag_detach_form(sourceId, tagSpace, path, role)}</td>
       |</tr>""".stripMargin
  }

  protected def admin_entity_tag_attach_form(
    sourceId: String,
    tagSpace: String
  ): String =
    s"""<form method="post" action="/web/admin/tags/attach" class="row g-2 align-items-end">
       |  <input type="hidden" name="sourceEntityId" value="${escape(sourceId)}">
       |  <div class="col-md-3"><label class="form-label" for="entityTagAttachSpace">TagSpace</label><input class="form-control" id="entityTagAttachSpace" name="tagSpace" value="${escape(tagSpace)}" placeholder="default"></div>
       |  <div class="col-md-4"><label class="form-label" for="entityTagAttachRef">Tag ref/path</label><input class="form-control" id="entityTagAttachRef" name="tagRef" required></div>
       |  <div class="col-md-2"><label class="form-label" for="entityTagAttachRole">Role</label><input class="form-control" id="entityTagAttachRole" name="role" value="tag" required></div>
       |  <div class="col-md-1"><label class="form-label" for="entityTagAttachSort">Sort</label><input class="form-control" id="entityTagAttachSort" name="sortOrder"></div>
       |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
       |</form>""".stripMargin

  protected def admin_entity_tag_detach_form(
    sourceId: String,
    tagSpace: String,
    tagRef: String,
    role: String
  ): String =
    admin_confirm_post_form(
      action = "/web/admin/tags/detach",
      label = "Detach",
      title = "Detach Tag",
      message = s"Detach Tag ${tagRef} from Entity ${sourceId}?",
      hiddenFields = Vector(
        "sourceEntityId" -> sourceId,
        "tagSpace" -> tagSpace,
        "tagRef" -> tagRef,
        "role" -> role
      )
    )

  protected def admin_entity_associations_section(
    subsystem: Subsystem,
    component: Component,
    entityName: String,
    record: Option[Record],
    fallbackId: String
  ): String = {
    val relationships = admin_entity_association_relationships(component, entityName)
    if (relationships.isEmpty)
      ""
    else {
      val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
      val sourceIds = Vector(
        record.flatMap(_.getString("sourceEntityId")),
        record.flatMap(_.getString("id")),
        Some(fallbackId)
      ).flatten.filter(_.nonEmpty).distinct
      val sections = relationships.map { relationship =>
        val rows = deduplicate_association_rows(
          sourceIds.flatMap(id => admin_entity_association_rows(subsystem, relationship, id))
        )
        val table =
          if (rows.isEmpty)
            s"""<tbody>${admin_empty_table_cell(8, "No Associations are currently linked for this relationship.")}</tbody>"""
          else
            s"""<tbody>${rows.map(row => admin_association_row(row, includeDetach = true)).mkString("\n")}</tbody>"""
        val attach = admin_entity_association_attach_form(sourceId, relationship)
        val openlink = relationship.associationDomain.filter(_.nonEmpty).map { domain =>
          s"""<a class="btn btn-outline-secondary btn-sm" href="/web/admin/associations?domain=${escapeQuery(domain)}&amp;sourceEntityId=${escapeQuery(sourceId)}">Open Associations</a>"""
        }.getOrElse("")
        s"""<section class="mb-3">
           |  <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between">
           |    <h3 class="h6 mb-0">${escape(relationship.name)}</h3>
           |    ${openlink}
           |  </div>
           |  ${attach}
           |  <div class="table-responsive mt-2">
           |    <table class="table table-sm align-middle">
           |      <thead><tr><th>Association</th><th>Source Entity</th><th>Target Entity</th><th>Target kind</th><th>Role</th><th>Sort</th><th>Domain</th><th>Actions</th></tr></thead>
           |      ${table}
           |    </table>
           |  </div>
           |</section>""".stripMargin
      }.mkString("\n")
      s"""<article class="card admin-card mt-3">
         |  <div class="card-body">
         |    <h2 class="card-title">Associations</h2>
         |    ${sections}
         |  </div>
         |</article>""".stripMargin
    }
  }

  protected def deduplicate_association_rows(
    rows: Vector[Record]
  ): Vector[Record] =
    rows.foldLeft(Vector.empty[Record]) { (acc, row) =>
      val key = row.getString("id").getOrElse(row.toString)
      if (acc.exists(existing => existing.getString("id").getOrElse(existing.toString) == key))
        acc
      else
        acc :+ row
    }

  protected def admin_entity_association_relationships(
    component: Component,
    entityName: String
  ): Vector[CmlEntityRelationshipDefinition] =
    component.relationshipDefinitions.filter { relationship =>
      NamingConventions.equivalentByNormalized(relationship.sourceEntityName, entityName) &&
        relationship.storageMode == CmlEntityRelationshipDefinition.StorageAssociationRecord &&
        !is_blob_attachment_relationship(relationship)
    }

  protected def is_blob_attachment_relationship(
    relationship: CmlEntityRelationshipDefinition
  ): Boolean =
    relationship.associationDomain.contains("blob_attachment") &&
      relationship.targetKind.exists(NamingConventions.equivalentByNormalized(_, "blob"))

  protected def admin_entity_association_rows(
    subsystem: Subsystem,
    relationship: CmlEntityRelationshipDefinition,
    sourceId: String
  ): Vector[Record] =
    relationship.associationDomain.toVector.flatMap { domain =>
      admin_operation_record(
        subsystem,
        "/admin/association/admin_list_associations",
        Record.data(
          "domain" -> domain,
          "sourceEntityId" -> sourceId,
          "targetKind" -> relationship.targetKind.getOrElse(""),
          "pageSize" -> renderer_config.adminPageSize
        )
      ).toVector.flatMap(record => record_seq(record.asMap.get("data")))
    }

  protected def admin_entity_association_attach_form(
    sourceId: String,
    relationship: CmlEntityRelationshipDefinition
  ): String =
    (relationship.associationDomain, relationship.targetKind) match {
      case (Some(domain), Some(targetKind)) if domain.nonEmpty && targetKind.nonEmpty =>
        val role = relationship.targetRole.orElse(relationship.sourceRole).getOrElse(relationship.name.split("\\.").lastOption.getOrElse("related"))
        s"""<form method="post" action="/web/admin/associations/attach" class="row g-2 align-items-end mt-2">
           |  <input type="hidden" name="domain" value="${escape(domain)}">
           |  <input type="hidden" name="sourceEntityId" value="${escape(sourceId)}">
           |  <input type="hidden" name="targetKind" value="${escape(targetKind)}">
           |  <div class="col-md-5"><label class="form-label" for="associationTarget-${escape(NamingConventions.toNormalizedSegment(relationship.name))}">Target entity</label><input class="form-control" id="associationTarget-${escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="targetEntityId" required></div>
           |  <div class="col-md-3"><label class="form-label" for="associationRole-${escape(NamingConventions.toNormalizedSegment(relationship.name))}">Role</label><input class="form-control" id="associationRole-${escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="role" value="${escape(role)}" required></div>
           |  <div class="col-md-2"><label class="form-label" for="associationSort-${escape(NamingConventions.toNormalizedSegment(relationship.name))}">Sort</label><input class="form-control" id="associationSort-${escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="sortOrder"></div>
           |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
           |</form>""".stripMargin
      case _ =>
        admin_empty_state("This relationship is metadata-only because associationDomain or targetKind is not defined.")
    }

  protected def blob_admin_paging(
    basePath: String,
    params: Map[String, String],
    record: Record
  ): String = {
    val offset = record.getInt("offset").getOrElse(0)
    val limit = record.getInt("limit").getOrElse(renderer_config.adminPageSize)
    val hasmore = record.getBoolean("hasMore").getOrElse(false)
    val cleanparams = params -- Set("offset", "page", "pageSize")
    val querybase = cleanparams.toVector.sortBy(_._1).map { case (k, v) => s"${escapeQuery(k)}=${escapeQuery(v)}" }
    val prev =
      if (offset <= 0) ""
      else {
        val prevquery = (querybase :+ s"offset=${math.max(0, offset - limit)}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${prevquery}">Previous</a>"""
      }
    val next =
      if (!hasmore) ""
      else {
        val nextquery = (querybase :+ s"offset=${offset + limit}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${nextquery}">Next</a>"""
      }
    s"""<div class="d-flex flex-wrap gap-2 align-items-center"><span class="text-secondary">offset ${offset}, limit ${limit}</span>${prev}${next}</div>"""
  }

  protected def admin_association_paging(
    basePath: String,
    params: Map[String, String],
    record: Record
  ): String = {
    val page = record.getInt("page").getOrElse(1)
    val pagesize = record.getInt("pageSize").getOrElse(renderer_config.adminPageSize)
    val hasnext = record.getBoolean("hasNext").getOrElse(false)
    val cleanparams = params -- Set("offset", "limit", "page", "pageSize")
    val querybase = cleanparams.toVector.sortBy(_._1).map { case (k, v) => s"${escapeQuery(k)}=${escapeQuery(v)}" } :+
      s"pageSize=${pagesize}"
    val prev =
      if (page <= 1) ""
      else {
        val prevquery = (querybase :+ s"page=${page - 1}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${prevquery}">Previous</a>"""
      }
    val next =
      if (!hasnext) ""
      else {
        val nextquery = (querybase :+ s"page=${page + 1}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${nextquery}">Next</a>"""
      }
    s"""<div class="d-flex flex-wrap gap-2 align-items-center"><span class="text-secondary">page ${page}, page size ${pagesize}</span>${prev}${next}</div>"""
  }

  protected def record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value.toVector.flatMap {
      case r: Record => Vector(r)
      case xs: Vector[?] => xs.collect { case r: Record => r }
      case xs: Seq[?] => xs.toVector.collect { case r: Record => r }
      case xs: Array[?] => xs.toVector.collect { case r: Record => r }
      case _ => Vector.empty
    }

  protected def display_value(value: Any): String =
    value match {
      case null => ""
      case Some(x) => display_value(x)
      case None => ""
      case r: Record => r.show
      case xs: Seq[?] => xs.map(display_value).mkString(", ")
      case x => x.toString
    }
}
