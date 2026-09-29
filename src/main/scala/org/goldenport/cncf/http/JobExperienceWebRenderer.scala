package org.goldenport.cncf.http

import org.goldenport.cncf.job.{JobExperiencePage, JobExperienceResult, JobExperienceScope, JobExperienceView}
import org.goldenport.cncf.usernotification.UserNotificationInboxViewPage

/*
 * Small accessible HTML projection. All data enters text or attribute contexts
 * through the single escaping helper and the initial document needs no script.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object JobExperienceWebRenderer {
  def list(
    page: JobExperiencePage,
    scope: JobExperienceScope,
    filters: Map[String, String]
  ): String = {
    val rows = page.entries.map { entry =>
      val href = JobExperienceWebSupport.detailPath(scope, entry.jobId)
      s"<tr><td><a href=\"${JobExperienceWebSupport.escape(href)}\">${JobExperienceWebSupport.escape(entry.jobId.value)}</a></td><td>${JobExperienceWebSupport.escape(entry.vocabulary.status)}</td><td>${JobExperienceWebSupport.escape(entry.vocabulary.nextStep)}</td><td>${JobExperienceWebSupport.escape(entry.updatedAt.toString)}</td></tr>"
    }.mkString
    val next = page.nextCursor.map { cursor =>
      val href = JobExperienceWebSupport.nextHref(scope, cursor.value, filters)
      s"<a rel=\"next\" href=\"${JobExperienceWebSupport.escape(href)}\">Next</a>"
    }.getOrElse("")
    val empty = if (page.entries.isEmpty) "<p role=\"status\">No admitted jobs are available.</p>" else ""
    val status = JobExperienceWebSupport.escape(filters.getOrElse("status", ""))
    val origin = JobExperienceWebSupport.escape(filters.getOrElse("origin", ""))
    val persistent = JobExperienceWebSupport.escape(filters.getOrElse("persistentOnly", "false"))
    s"""<main class="job-experience"><h1>Jobs</h1><form method="get" action="${JobExperienceWebSupport.escape(JobExperienceWebSupport.listPath(scope))}"><label for="job-status-filter">Status <input id="job-status-filter" name="status" value="$status"></label><label for="job-origin-filter">Origin <input id="job-origin-filter" name="origin" value="$origin"></label><label for="job-persistent-filter">Persistent only <input id="job-persistent-filter" name="persistentOnly" value="$persistent"></label><button type="submit">Filter</button><a href="${JobExperienceWebSupport.escape(JobExperienceWebSupport.listPath(scope))}">Refresh</a></form>$empty<table><caption>Authorized Jobs</caption><thead><tr><th scope="col">Job</th><th scope="col">Status</th><th scope="col">Next step</th><th scope="col">Updated</th></tr></thead><tbody>$rows</tbody></table><nav aria-label="Job pages">$next</nav></main>"""
  }

  def detail(view: JobExperienceView, scope: JobExperienceScope, csrf: String): String = {
    val id = view.detail.summary.jobId.value
    val result = _result(view.result)
    val controls = view.controls.map { command =>
      s"""<form method="post" action="/form/job_control/job_experience/control_job_experience"><input type="hidden" name="csrf" value="${JobExperienceWebSupport.escape(csrf)}"><input type="hidden" name="id" value="${JobExperienceWebSupport.escape(id)}"><input type="hidden" name="scope" value="${JobExperienceWebSupport.escape(scope.visibilityKey.takeWhile(_ != ':'))}"><input type="hidden" name="application" value="${JobExperienceWebSupport.escape(scope.applicationOption.getOrElse(""))}"><input type="hidden" name="command" value="${JobExperienceWebSupport.escape(command.toString)}"><button type="submit">${JobExperienceWebSupport.escape(command.toString)}</button></form>"""
    }.mkString
    val refresh = s"<a href=\"${JobExperienceWebSupport.escape(JobExperienceWebSupport.detailPath(scope, id))}\">Refresh</a>"
    val pollurl = JobExperienceWebSupport.pollingUrl(scope, id)
    val polling = if (view.refreshable) _polling_script else ""
    s"""<main class="job-experience" data-poll-enabled="${view.refreshable}" data-poll-url="${JobExperienceWebSupport.escape(pollurl)}"><h1>Job ${JobExperienceWebSupport.escape(id)}</h1><p>Canonical status: <span id="job-status">${JobExperienceWebSupport.escape(view.vocabulary.status)}</span></p><p>Progress: <span id="job-progress">${JobExperienceWebSupport.escape(view.progress.state)}; ${view.progress.taskCount} tasks recorded</span></p>$result<p aria-live="polite" id="job-poll-status"></p>$refresh<button type="button" id="job-poll-toggle">Pause polling</button><section aria-label="Job controls">$controls</section>$polling</main>"""
  }

  def notifications(
    page: UserNotificationInboxViewPage,
    application: Option[String],
    filters: Map[String, String],
    csrf: String
  ): String = {
    val rows = page.entries.map { entry =>
      val action = entry.actionUrl.map { url =>
        s"<a href=\"${JobExperienceWebSupport.escape(url)}\">Open</a>"
      }.getOrElse("")
      val form = if (entry.readAt.isEmpty) {
        s"""<form method="post" action="/form/job_control/job_experience/mark_notification_read"><input type="hidden" name="csrf" value="${JobExperienceWebSupport.escape(csrf)}"><input type="hidden" name="id" value="${JobExperienceWebSupport.escape(entry.id)}"><input type="hidden" name="application" value="${JobExperienceWebSupport.escape(application.getOrElse(""))}"><button type="submit">Mark read</button></form>"""
      } else "Read"
      s"<li><h2>${JobExperienceWebSupport.escape(entry.title)}</h2><p>${JobExperienceWebSupport.escape(entry.body)}</p>$action$form</li>"
    }.mkString
    val next = page.nextCursor.map { cursor =>
      val href = JobExperienceWebSupport.notificationHref(application, cursor, filters)
      s"<a rel=\"next\" href=\"${JobExperienceWebSupport.escape(href)}\">Next</a>"
    }.getOrElse("")
    val unread = JobExperienceWebSupport.escape(filters.getOrElse("unreadOnly", "false"))
    val empty = if (page.entries.isEmpty) "No notifications." else ""
    s"""<main class="job-notifications"><h1>Notifications</h1><form method="get" action="${JobExperienceWebSupport.escape(JobExperienceWebSupport.notificationPath(application))}"><label for="notification-unread-filter">Unread only <input id="notification-unread-filter" name="unreadOnly" value="$unread"></label><button type="submit">Filter</button><a href="${JobExperienceWebSupport.escape(JobExperienceWebSupport.notificationPath(application))}">Refresh</a></form><p aria-live="polite">$empty</p><ol>$rows</ol><nav aria-label="Notification pages">$next</nav></main>"""
  }

  def error(message: String, back: String): String =
    s"<main class=\"job-experience\"><h1>Jobs unavailable</h1><p role=\"alert\">${JobExperienceWebSupport.escape(message)}</p><a href=\"${JobExperienceWebSupport.escape(back)}\">Return to Jobs</a></main>"

  private def _result(result: JobExperienceResult): String = result match {
    case JobExperienceResult.Available(text) => s"<p id=\"job-result\">${JobExperienceWebSupport.escape(text)}</p>"
    case JobExperienceResult.Failed => "<p id=\"job-result\">The job failed. Contact an operator for assistance.</p>"
    case JobExperienceResult.Pending => "<p id=\"job-result\">Result pending.</p>"
    case JobExperienceResult.Unavailable => "<p id=\"job-result\">Result is no longer available.</p>"
    case JobExperienceResult.UnavailableAfterRestart => "<p id=\"job-result\">Result is unavailable after restart.</p>"
  }

  private def _polling_script: String =
    """<script>(function(){const root=document.currentScript.parentElement;const button=root.querySelector('#job-poll-toggle');const status=root.querySelector('#job-poll-status');const allowedStatus=new Set(['Queued','Running','Paused','Cancelled','Completed','Failed']);const allowedProgress=new Set(['indeterminate','paused','terminal']);const allowedAvailability=new Set(['available','failed','pending','unavailable','unavailable-after-restart']);let attempts=0;let inflight=false;let paused=false;let stopped=false;let controller=null;let timer=null;let generation=0;const clearTimer=()=>{if(timer!==null){clearTimeout(timer);timer=null;}};const stop=message=>{stopped=true;generation+=1;clearTimer();if(controller){controller.abort();controller=null;}status.textContent=message;};const schedule=()=>{if(!stopped&&!paused&&!inflight&&timer===null){timer=setTimeout(()=>{timer=null;tick();},5000);}};const tick=()=>{if(stopped||paused||document.hidden){return;}if(attempts>=60){stop('Polling stopped. Refresh manually.');return;}if(inflight){return;}inflight=true;attempts+=1;const requestGeneration=generation;const requestController=new AbortController();controller=requestController;const timeout=setTimeout(()=>requestController.abort(),5000);fetch(root.dataset.pollUrl,{credentials:'same-origin',redirect:'error',signal:requestController.signal}).then(response=>{const type=(response.headers.get('content-type')||'').split(';',1)[0].trim().toLowerCase();if(!response.ok||response.redirected||(type!=='application/json'&&!type.endsWith('+json')))throw new Error('response');return response.json();}).then(value=>{if(stopped||paused||requestGeneration!==generation)return;const data=value&&typeof value==='object'&&value.data&&typeof value.data==='object'?value.data:value;const availability=data&&data.result&&typeof data.result==='object'?data.result.availability:null;if(!data||typeof data!=='object'||!allowedStatus.has(data.status)||!allowedProgress.has(data.progress)||!allowedAvailability.has(availability))throw new Error('payload');root.querySelector('#job-status').textContent=data.status;root.querySelector('#job-progress').textContent=data.progress;root.querySelector('#job-result').textContent=availability;if(['Paused','Cancelled','Completed','Failed'].includes(data.status)||['unavailable','unavailable-after-restart'].includes(availability))stop('Polling stopped.');}).catch(()=>{if(!stopped&&!paused&&requestGeneration===generation)stop('Polling unavailable. Refresh manually.');}).finally(()=>{clearTimeout(timeout);if(controller===requestController)controller=null;inflight=false;if(!stopped&&!paused)schedule();});};button.addEventListener('click',()=>{if(stopped)return;paused=!paused;button.textContent=paused?'Resume polling':'Pause polling';if(paused){generation+=1;clearTimer();if(controller)controller.abort();status.textContent='Polling paused.';}else{status.textContent='Polling resumed.';if(!inflight)schedule();}});document.addEventListener('visibilitychange',()=>{if(document.hidden)stop('Polling stopped. Refresh manually.');});window.addEventListener('beforeunload',()=>stop(''));schedule();})();</script>"""
}
