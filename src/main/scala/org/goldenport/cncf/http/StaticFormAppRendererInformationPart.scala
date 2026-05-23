package org.goldenport.cncf.http

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.information.{InformationSpaceProjection, *}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   May. 20, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererInformationPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererSystemAdminPart =>
  import StaticFormAppRendererSupport.*

  def renderSystemAdminInformation(subsystem: Subsystem): Page =
    Page(information_admin_page(subsystem))

  def renderSystemAdminInformationComponent(
    subsystem: Subsystem,
    componentname: String
  ): Option[Page] =
    InformationSpaceProjection
      .componentOption(subsystem.components, componentname)
      .map(component => Page(information_component_page(component)))

  protected def information_admin_page(subsystem: Subsystem): String = {
    val projections = InformationSpaceProjection.components(subsystem.components)
    val rows =
      if (projections.isEmpty)
        admin_empty_table_cell(8, "No components are loaded.")
      else
        projections.map { projection =>
          val path = escape_path_segment(projection.componentName)
          val counts = projection.counts
          s"""<tr>
             |  <td><a href="/web/system/admin/information/${path}">${escape(projection.componentName)}</a></td>
             |  <td>${counts.recordCount}</td>
             |  <td>${counts.itemCount}</td>
             |  <td>${counts.validationIssueCount}</td>
             |  <td>${counts.resolutionCandidateCount}</td>
             |  <td>${counts.identityBindingCount}</td>
             |  <td>${counts.publicationStatusCount}</td>
             |  <td>${counts.conflictCount}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    simple_page(
      title = "System Information",
      subtitle = "Component-owned InformationSpace import and curation state",
      body =
        s"""${admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "KnowledgeSpace" -> "/web/system/admin/knowledge"
           ))}
           |${admin_card(
             "InformationSpace Components",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Component</th><th>Information</th><th>Confirmed</th><th>Issues</th><th>Candidates</th><th>Bindings</th><th>Publications</th><th>Conflicts</th></tr></thead>
                |  <tbody>${rows}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Boundary policy",
             """<p class="mb-2">InformationSpace is the editing and curation boundary for knowledge information.</p>
               |<p class="mb-0">KnowledgeSpace remains the runtime semantic traversal boundary. This page does not expose raw RDF triples, Vector DB payloads, or source bodies.</p>""".stripMargin
           )}""".stripMargin
    )
  }

  protected def information_component_page(component: Component): String = {
    val projection = InformationSpaceProjection.component(component)
    val snapshot = projection.snapshot
    val previewlimit = renderer_config.previewLimit
    val records = snapshot.records.sortBy(_.id.print).take(previewlimit).map { record =>
      s"""<tr>
         |  <td><code>${escape(record.id.print)}</code></td>
         |  <td>${escape(record.domain)}</td>
         |  <td><span class="badge text-bg-secondary">${escape(record.state.label)}</span></td>
         |  <td>${record.validationIssueIds.size}</td>
         |  <td>${record.resolutionCandidateIds.size}</td>
         |  <td>${record.itemId.map(x => s"<code>${escape(x.print)}</code>").getOrElse("")}</td>
         |</tr>""".stripMargin
    }.mkString("\n")
    val items = snapshot.items.sortBy(_.id.print).take(previewlimit).map { item =>
      s"""<tr>
         |  <td><code>${escape(item.id.print)}</code></td>
         |  <td>${escape(item.domain)}</td>
         |  <td><span class="badge text-bg-secondary">${escape(item.state.label)}</span></td>
         |  <td>${escape(item.data.getString("title").getOrElse(""))}</td>
         |  <td>${item.identityBindingIds.size}</td>
         |  <td>${item.publicationId.map(x => s"<code>${escape(x.print)}</code>").getOrElse("")}</td>
         |</tr>""".stripMargin
    }.mkString("\n")
    val issues = snapshot.validationIssues.sortBy(_.id.print).take(previewlimit).map { issue =>
      s"""<tr><td><code>${escape(issue.id.print)}</code></td><td><code>${escape(issue.recordId.print)}</code></td><td>${escape(issue.fieldPath)}</td><td>${escape(issue.severity)}</td><td>${escape(issue.message)}</td></tr>"""
    }.mkString("\n")
    val publications = snapshot.publicationStatuses.sortBy(_.id.print).take(previewlimit).map { publication =>
      s"""<tr><td><code>${escape(publication.id.print)}</code></td><td><code>${escape(publication.itemId.print)}</code></td><td>${escape(publication.target)}</td><td>${escape(publication.state.label)}</td><td>${escape(publication.message.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val conflicts = snapshot.conflicts.sortBy(_.id.print).take(previewlimit).map { conflict =>
      s"""<tr><td><code>${escape(conflict.id.print)}</code></td><td><code>${escape(conflict.itemId.print)}</code></td><td>${escape(conflict.fieldPath)}</td><td>${escape(conflict.state.label)}</td><td>${escape(conflict.resolution.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    simple_page(
      title = s"System Information ${component.name}",
      subtitle = "Component InformationSpace compact projection",
      body =
        s"""${admin_nav_card(Vector(
             "System information" -> "/web/system/admin/information",
             "System admin" -> "/web/system/admin"
           ))}
           |${admin_card("Counts", field_table(Vector(
             "Component" -> component.name,
             "Information" -> projection.counts.recordCount.toString,
             "Confirmed information" -> projection.counts.itemCount.toString,
             "Validation issues" -> projection.counts.validationIssueCount.toString,
             "Resolution candidates" -> projection.counts.resolutionCandidateCount.toString,
             "Identity bindings" -> projection.counts.identityBindingCount.toString,
             "Publications" -> projection.counts.publicationStatusCount.toString,
             "Conflicts" -> projection.counts.conflictCount.toString
           )))}
           |${admin_card("Editable information", information_table(records, 6, "No editable information is loaded.", "Information", "Domain", "State", "Issues", "Candidates", "Confirmed"))}
           |${admin_card("Information items", information_table(items, 6, "No information items are loaded.", "Item", "Domain", "State", "Title", "Bindings", "Publication"))}
           |${admin_card("Validation issues", information_table(issues, 5, "No validation issues are loaded.", "Issue", "Record", "Field", "Severity", "Message"))}
           |${admin_card("Publication status", information_table(publications, 5, "No publication status records are loaded.", "Publication", "Item", "Target", "State", "Message"))}
           |${admin_card("Conflicts", information_table(conflicts, 5, "No conflicts are loaded.", "Conflict", "Item", "Field", "State", "Resolution"))}""".stripMargin
    )
  }

  protected def information_table(
    rows: String,
    colspan: Int,
    empty: String,
    headers: String*
  ): String = {
    val header = headers.map(x => s"<th>${escape(x)}</th>").mkString
    val body = if (rows.isEmpty) admin_empty_table_cell(colspan, empty) else rows
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
       |  <thead><tr>${header}</tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }
}
