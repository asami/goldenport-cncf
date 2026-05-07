package org.goldenport.cncf.component.builtin.jobcontrol.entity

import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.job.{JobDefinitionEntity, JobEntityCollections}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Generated-shape entity module for the built-in job_control JobDefinition
 * entity.
 *
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object JobDefinition {
  def collectionId: EntityCollectionId =
    JobEntityCollections.JobDefinition

  def given_EntityPersistent_JobDefinition: EntityPersistent[JobDefinitionEntity] =
    JobDefinitionEntity.entityPersistent
}
