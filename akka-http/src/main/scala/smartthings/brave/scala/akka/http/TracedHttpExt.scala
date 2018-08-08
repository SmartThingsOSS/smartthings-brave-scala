package smartthings.brave.scala.akka.http

import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import brave.http.{HttpClientHandler, HttpServerHandler, HttpTracing}
import brave.propagation.TraceContext.{Extractor, Injector}
import smartthings.brave.scala.akka.http.internal.{AkkaHttpClientAdapter, AkkaHttpServerAdapter, TracedFlow}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object TracedHttpExt {

  object Instances {
    implicit def httpExtToTracedHttpExt(httpExt: HttpExt)(implicit httpTracing: HttpTracing): TracedHttpExt = new TracedHttpExt(httpExt, httpTracing)
  }

  private[brave] object singleThreadExecutor extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()

    override def reportFailure(t: Throwable): Unit = {
      throw new IllegalStateException("exception in sameThreadExecutionContext", t)
    }
  }

}

class TracedHttpExt(httpExt: HttpExt, httpTracing: HttpTracing) {

  import TracedHttpExt.singleThreadExecutor

  private val clientHandler = HttpClientHandler.create(httpTracing, new AkkaHttpClientAdapter)

  private val injector: Injector[scala.collection.mutable.Buffer[HttpHeader]] = httpTracing.tracing().propagation().injector {
    (carrier, key, value) => carrier.append(RawHeader(key, value))
  }

  private val serverHandler = HttpServerHandler.create(httpTracing, new AkkaHttpServerAdapter)

  private val extractor: Extractor[HttpRequest] = httpTracing.tracing().propagation().extractor {
    (r: HttpRequest, name: String) => r.headers.find(_.lowercaseName() == name).map(_.value()).orNull
  }

  def singleRequestWithTracing(request: HttpRequest): Future[HttpResponse] =
    transformClientRequest(request) { r => httpExt.singleRequest(r) }

  def bindAndHandleWithTracing(handler:   Flow[HttpRequest, HttpResponse, Any],
                               interface: String)(implicit fm: Materializer): Future[ServerBinding] = {
    httpExt.bindAndHandle(TracedFlow(handler, httpTracing, serverHandler, extractor), interface)(fm)
  }

  def bindAndHandleWithTracing(handler:   Flow[HttpRequest, HttpResponse, Any],
                               interface: String, port: Int)(implicit fm: Materializer): Future[ServerBinding] = {
    httpExt.bindAndHandle(TracedFlow(handler, httpTracing, serverHandler, extractor), interface, port)(fm)
  }

  private def transformClientRequest(request: HttpRequest)(f: HttpRequest => Future[HttpResponse]): Future[HttpResponse] = {
    val span = clientHandler.handleSend(injector, request.headers.toBuffer, request)

    val ws = httpTracing.tracing().tracer().withSpanInScope(span)

    try {
      f(request).andThen {
        case Success(value) => clientHandler.handleReceive(value, null, span)
        case Failure(exception) => clientHandler.handleReceive(null, exception, span)
      }(singleThreadExecutor)
    } catch {
      case t: Throwable =>
        span.error(t)
        throw t
    } finally {
      ws.close()
    }
  }

}
