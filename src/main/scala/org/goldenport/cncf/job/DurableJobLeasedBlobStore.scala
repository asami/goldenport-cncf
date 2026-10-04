package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.BinaryBag
import org.goldenport.cncf.blob.*

/*
 * The binding lease surrounds each native Blob effect without owning bytes.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobLeasedBlobStore(
  delegate: BlobStore,
  lease: DurableJobRuntimeLease,
  onfault: () => Unit
) extends BlobStore {
  def name: String = delegate.name
  def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = _use_c(delegate.put(request, payload))
  def get(ref: BlobStorageRef): Consequence[BlobReadResult] = _use_c(delegate.get(ref))
  def delete(ref: BlobStorageRef): Consequence[Unit] = _use_c(delegate.delete(ref))
  def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = _use_c(delegate.accessUrl(ref))
  def status(): Consequence[BlobStoreStatus] = _use_c(delegate.status())

  private def _use_c[A](body: => Consequence[A]): Consequence[A] =
    try {
      val result = lease.use(body)
      result match { case Consequence.Failure(_) => onfault(); case _ => () }
      result
    } catch {
      case exception: InterruptedException => onfault(); Thread.currentThread().interrupt(); throw exception
      case NonFatal(exception) => onfault(); throw exception
    }
}
