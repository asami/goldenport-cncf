package org.goldenport.cncf.component.builtin.jobcontrol.entity

import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.job.{JobEntity, JobEntityCollections}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Generated-shape entity module for the built-in job_control Job entity.
 *
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object Job {
  def collectionId: EntityCollectionId =
    JobEntityCollections.Job

  def given_EntityPersistent_Job: EntityPersistent[JobEntity] =
    JobEntity.entityPersistent
}
