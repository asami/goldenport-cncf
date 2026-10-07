package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{KnowledgeContributionDeclaration, KnowledgeContributionSnapshot}

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
/** Optional knowledge-supplier capability of an already installed Component. */
trait ComponentKnowledgeProvider { this: Component =>
  def knowledgeContributionDeclarations: Vector[KnowledgeContributionDeclaration]
  def readKnowledgeContributionC(declaration: KnowledgeContributionDeclaration): Consequence[KnowledgeContributionSnapshot]
}
