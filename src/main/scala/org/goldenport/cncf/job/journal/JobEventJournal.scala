package org.goldenport.cncf.job.journal

import org.goldenport.cncf.event.EventId
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.log.journal.JobEventLogEntry

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait JobEventJournal {
  def append(entry: JobEventLogEntry): Unit
  def appendAll(entries: Seq[JobEventLogEntry]): Unit
  def list(jobId: JobId): Seq[JobEventLogEntry]
  def all(): Seq[JobEventLogEntry]
}

final class InMemoryJobEventJournal extends JobEventJournal {
  private var _entries: Map[EventId, JobEventLogEntry] = Map.empty

  def append(entry: JobEventLogEntry): Unit = {
    if (!_entries.contains(entry.eventId)) {
      _entries = _entries.updated(entry.eventId, entry)
    }
  }

  def appendAll(entries: Seq[JobEventLogEntry]): Unit =
    entries.foreach(append)

  def list(jobId: JobId): Seq[JobEventLogEntry] =
    _entries.values.filter(_.jobId.contains(jobId)).toVector

  def all(): Seq[JobEventLogEntry] =
    _entries.values.toVector
}
