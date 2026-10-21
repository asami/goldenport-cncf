package org.goldenport.cncf.component.cache

import java.nio.channels.{FileChannel, FileLock, OverlappingFileLockException}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

/** One native lock per stable filename, shared among this JVM's readers.
  * ConcurrentHashMap.compute serializes only a short operation on one key;
  * downloads, extraction, waiting and consumers never run in its callback.
  * @since Oct. 21, 2026
 * @version Oct. 21, 2026
  */
private[cache] object ManagedCacheLocks {
  private final class Held(val channel: FileChannel, val lock: FileLock, val shared: Boolean) {
    var references = 1
  }
  private val _held = new ConcurrentHashMap[Path, Held]()

  final class Pin private[ManagedCacheLocks] (path: Path, owner: Held) extends AutoCloseable {
    private val _closed = new AtomicBoolean(false)
    def close(): Unit = if (_closed.compareAndSet(false, true)) {
      var failure: Option[Throwable] = None
      _held.compute(path, (_, current) => {
        if (current ne owner) throw new IllegalStateException("managed lock ownership changed")
        current.references -= 1
        if (current.references > 0) current
        else {
          try current.lock.release()
          catch { case NonFatal(error) => failure = Some(error) }
          try current.channel.close()
          catch {
            case NonFatal(error) => failure match {
              case Some(primary) => primary.addSuppressed(error)
              case None => failure = Some(error)
            }
          }
          null
        }
      })
      failure.foreach(error => throw error)
    }
  }

  def tryAcquire(path: Path, shared: Boolean): Option[Pin] = {
    val key = path.toAbsolutePath.normalize
    var result: Option[Pin] = None
    _held.compute(key, (_, existing) => {
      if (existing != null) {
        if (shared && existing.shared) {
          existing.references += 1
          result = Some(new Pin(key, existing))
        }
        existing
      } else {
        Files.createDirectories(key.getParent)
        val channel = FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
        var retained = false
        try {
          val lock = try channel.tryLock(0L, Long.MaxValue, shared)
          catch { case _: OverlappingFileLockException => null }
          if (lock == null) null
          else if (shared && !lock.isShared) {
            lock.release()
            throw CacheFailure(CacheFailure.Reason.UnsupportedFilesystem, "shared-lock", key.toString,
              detail = "filesystem granted exclusive protection for a shared usage request")
          } else {
            val owner = new Held(channel, lock, shared)
            result = Some(new Pin(key, owner))
            retained = true
            owner
          }
        } finally if (!retained) channel.close()
      }
    })
    result
  }

  def acquire(path: Path, shared: Boolean, policy: ManagedCachePolicy): Pin = {
    val started = System.nanoTime()
    var attempts = 0
    while (attempts < policy.acquisitionMaxAttempts) {
      attempts += 1
      tryAcquire(path, shared) match {
        case Some(pin) => return pin
        case None =>
          val elapsed = (System.nanoTime() - started) / 1000000L
          if (elapsed >= policy.acquisitionMaxWaitMillis)
            throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "acquire-lock", path.toString)
          // Timed entry-level retry also observes lock release by other JVMs.
          // It is never called while a coordinate or another exclusive lock is held.
          try Thread.sleep(math.min(250L, policy.acquisitionMaxWaitMillis - elapsed))
          catch { case error: InterruptedException => Thread.currentThread().interrupt(); throw error }
      }
    }
    throw CacheFailure(CacheFailure.Reason.ContentionExceeded, "acquire-lock", path.toString)
  }
}
