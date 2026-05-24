package org.goldenport.cncf.http

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.information.{InformationSpaceProjection, *}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   May. 20, 2026
 * @version May. 25, 2026
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
        admin_empty_table_cell(7, "No components are loaded.")
      else
        projections.map { projection =>
          val path = escape_path_segment(projection.componentName)
          val counts = projection.counts
          s"""<tr>
             |  <td><a href="/web/system/admin/information/${path}">${escape(projection.componentName)}</a></td>
             |  <td>${counts.informationCount}</td>
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
                |  <thead><tr><th>Component</th><th>Information</th><th>Issues</th><th>Candidates</th><th>Bindings</th><th>Publications</th><th>Conflicts</th></tr></thead>
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
    val informationrows = snapshot.information.sortBy(_.id.print).take(previewlimit).map { information =>
      s"""<tr>
         |  <td><code>${escape(information.id.print)}</code></td>
         |  <td>${escape(information.domain)}</td>
         |  <td><span class="badge text-bg-secondary">${escape(information.state.label)}</span></td>
         |  <td>${escape(information.workingData.getString("title").getOrElse(""))}</td>
         |  <td>${information.validationIssues.size}</td>
         |  <td>${information.resolutionCandidates.size}</td>
         |  <td>${information.publicationStatuses.headOption.map(x => s"<code>${escape(x.publicationKey)}</code>").getOrElse("")}</td>
         |</tr>""".stripMargin
    }.mkString("\n")
    val issues = snapshot.information.flatMap(information =>
      information.validationIssues.map(issue => information -> issue)
    ).sortBy(_._1.id.print).take(previewlimit).map { case (information, issue) =>
      s"""<tr><td><code>${escape(information.id.print)}</code></td><td>${escape(issue.fieldPath)}</td><td>${escape(issue.severity)}</td><td>${escape(issue.message)}</td></tr>"""
    }.mkString("\n")
    val publications = snapshot.information.flatMap(information =>
      information.publicationStatuses.map(publication => information -> publication)
    ).sortBy(x => (x._1.id.print, x._2.publicationKey)).take(previewlimit).map { case (information, publication) =>
      s"""<tr><td><code>${escape(information.id.print)}</code></td><td><code>${escape(publication.publicationKey)}</code></td><td>${escape(publication.target)}</td><td>${escape(publication.state.label)}</td><td>${escape(publication.message.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val conflicts = snapshot.information.flatMap(information =>
      information.conflicts.map(conflict => information -> conflict)
    ).sortBy(x => (x._1.id.print, x._2.conflictKey)).take(previewlimit).map { case (information, conflict) =>
      s"""<tr><td><code>${escape(information.id.print)}</code></td><td><code>${escape(conflict.conflictKey)}</code></td><td>${escape(conflict.fieldPath)}</td><td>${escape(conflict.state.label)}</td><td>${escape(conflict.resolution.getOrElse(""))}</td></tr>"""
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
             "Information" -> projection.counts.informationCount.toString,
             "Validation issues" -> projection.counts.validationIssueCount.toString,
             "Resolution candidates" -> projection.counts.resolutionCandidateCount.toString,
             "Identity bindings" -> projection.counts.identityBindingCount.toString,
             "Publications" -> projection.counts.publicationStatusCount.toString,
             "Conflicts" -> projection.counts.conflictCount.toString
           )))}
           |${admin_card("Information", information_table(informationrows, 7, "No information is loaded.", "Information", "Domain", "State", "Title", "Issues", "Candidates", "Publication"))}
           |${admin_card("Validation issues", information_table(issues, 4, "No validation issues are loaded.", "Information", "Field", "Severity", "Message"))}
           |${admin_card("Publication status", information_table(publications, 5, "No publication status records are loaded.", "Information", "Publication", "Target", "State", "Message"))}
           |${admin_card("Conflicts", information_table(conflicts, 5, "No conflicts are loaded.", "Information", "Conflict", "Field", "State", "Resolution"))}""".stripMargin
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
