package org.goldenport.cncf.http

import java.io.{IOException, InterruptedIOException}
import java.net.{SocketTimeoutException, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse as JdkHttpResponse, HttpTimeoutException}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.{CompletableFuture, CompletionStage, ExecutionException, Flow, TimeUnit, TimeoutException}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/*
 * Runtime-owned whole-exchange deadline and finite response materialization.
 *
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
private[http] final class BoundedHttpExchange {
  private val _client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

  def execute(
    uri: URI,
    method: String,
    body: Option[String],
    headers: Map[String, String],
    policy: BoundedHttpExchange.Policy
  ): BoundedHttpExchange.Response = {
    val started = System.nanoTime()
    val budget = TimeUnit.MILLISECONDS.toNanos(policy.totalmillis.toLong)
    val subscriber = new BoundedHttpExchange.FiniteBodySubscriber(policy.maxbytes)
    var future: CompletableFuture[JdkHttpResponse[Array[Byte]]] = null
    var succeeded = false
    try {
      if (Thread.currentThread().isInterrupted)
        throw new InterruptedException
      val publisher = body match {
        case Some(value) => HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8)
        case None => HttpRequest.BodyPublishers.noBody()
      }
      val builder = HttpRequest.newBuilder(uri)
      headers.foreach { case (key, value) => builder.header(key, value) }
      val request = builder.method(method, publisher)
        .timeout(Duration.ofNanos(_remaining(started, budget))).build()
      future = _client.sendAsync(request, new JdkHttpResponse.BodyHandler[Array[Byte]] {
        def apply(responseInfo: JdkHttpResponse.ResponseInfo): JdkHttpResponse.BodySubscriber[Array[Byte]] =
          subscriber
      })
      // This future includes subscriber completion, not merely response headers.
      val response = future.get(_remaining(started, budget), TimeUnit.NANOSECONDS)
      _remaining(started, budget)
      val responseheaders = response.headers().map().entrySet().asScala.toVector.flatMap { entry =>
        entry.getValue.asScala.headOption.map(entry.getKey -> _)
      }.sortBy { case (key, _) => key.toLowerCase(java.util.Locale.ROOT) }
      val result = BoundedHttpExchange.Response(
        response.statusCode(), responseheaders,
        response.headers().firstValue("Content-Type").orElse(null), response.body()
      )
      succeeded = true
      result
    } catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new InterruptedIOException("Bounded HTTP exchange interrupted")
      case _: TimeoutException | _: HttpTimeoutException =>
        throw new SocketTimeoutException("Bounded HTTP exchange timed out")
      case failed: ExecutionException =>
        failed.getCause match {
          case _: HttpTimeoutException | _: TimeoutException =>
            throw new SocketTimeoutException("Bounded HTTP exchange timed out")
          case NonFatal(_) => throw new IOException("Bounded HTTP exchange failed")
          case fatal => throw fatal
        }
      case NonFatal(_) => throw new IOException("Bounded HTTP exchange failed")
    } finally {
      if (!succeeded) {
        subscriber.cancel()
        if (future != null) future.cancel(true)
      }
    }
  }

  private def _remaining(started: Long, budget: Long): Long = {
    val remaining = budget - (System.nanoTime() - started)
    if (remaining <= 0) throw new TimeoutException
    remaining
  }
}

private[http] object BoundedHttpExchange {
  final case class Policy(totalmillis: Int, maxbytes: Int)
  final case class Response(status: Int, headers: Vector[(String, String)], contenttype: String, bytes: Array[Byte])

  private final class FiniteBodySubscriber(maxbytes: Int) extends JdkHttpResponse.BodySubscriber[Array[Byte]] {
    private val _body = new CompletableFuture[Array[Byte]]()
    private var _subscription = Option.empty[Flow.Subscription]
    private var _terminal = false
    private var _size = 0
    private var _chunks = Vector.empty[Array[Byte]]

    def getBody(): CompletionStage[Array[Byte]] = _body

    def onSubscribe(subscription: Flow.Subscription): Unit = {
      val admitted = synchronized {
        if (_terminal || _subscription.nonEmpty) false
        else {
          _subscription = Some(subscription)
          true
        }
      }
      if (admitted) subscription.request(1L)
      else subscription.cancel()
    }

    def onNext(items: java.util.List[ByteBuffer]): Unit =
      try _accept(items).foreach(_.request(1L))
      catch { case NonFatal(_) => _fail(new IOException("Bounded HTTP body rejected")) }

    def onError(throwable: Throwable): Unit =
      throwable match {
        case NonFatal(_) => _fail(new IOException("Bounded HTTP body failed"))
        case fatal => _fail(fatal)
      }

    def onComplete(): Unit =
      try {
        synchronized {
          if (!_terminal) {
            val bytes = new Array[Byte](_size)
            var offset = 0
            _chunks.foreach { chunk =>
              System.arraycopy(chunk, 0, bytes, offset, chunk.length)
              offset += chunk.length
            }
            _terminal = true
            _chunks = Vector.empty
            _body.complete(bytes)
          }
        }
      } catch { case NonFatal(_) => _fail(new IOException("Bounded HTTP body failed")) }

    def cancel(): Unit = _fail(new IOException("Bounded HTTP exchange cancelled"))

    private def _accept(items: java.util.List[ByteBuffer]): Option[Flow.Subscription] = synchronized {
      if (_terminal) None
      else {
        val incoming = items.asScala.foldLeft(0L)((total, buffer) => total + buffer.remaining().toLong)
        if (incoming > maxbytes.toLong - _size.toLong)
          throw new IOException("Bounded HTTP body exceeds byte limit")
        // Admit the whole batch before allocating even its first chunk.
        items.asScala.foreach { buffer =>
          if (buffer.hasRemaining) {
            val chunk = new Array[Byte](buffer.remaining())
            buffer.get(chunk)
            _chunks :+= chunk
            _size += chunk.length
          }
        }
        _subscription
      }
    }

    private def _fail(error: Throwable): Unit = {
      val subscription = synchronized {
        if (!_terminal) {
          _terminal = true
          _chunks = Vector.empty
          _body.completeExceptionally(error)
        }
        _subscription
      }
      subscription.foreach(_.cancel())
    }
  }
}
