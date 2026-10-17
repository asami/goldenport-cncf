package org.goldenport.cncf.display

/**
 * Standalone Display mutation wire schema identities.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object DisplayMutationProtocol {
  val requestSchema: String = "cncf.display-mutation.v1"
  val descriptorSchema: String = "cncf.display-mutation-descriptor.v1"
  val resultSchema: String = "cncf.display-mutation-result.v1"
  val version: Int = 1
}
