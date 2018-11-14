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

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object TracedHttpExt {

  object Instances {
    implicit def httpExtToTracedHttpExt(httpExt: HttpExt)(implicit httpTracing: HttpTracing): TracedHttpExt = new TracedHttpExt(httpExt, httpTracing)
  }

}

class TracedHttpExt(httpExt: HttpExt, httpTracing: HttpTracing) {

  import smartthings.brave.scala.TracedFuture.singleThreadExecutor

  private val clientHandler = HttpClientHandler.create(httpTracing, new AkkaHttpClientAdapter)

  private val injector: Injector[scala.collection.mutable.Buffer[HttpHeader]] = httpTracing.tracing().propagation().injector {
    (carrier, key, value) => carrier.append(RawHeader(key, value))
  }

  private val serverHandler = HttpServerHandler.create(httpTracing, new AkkaHttpServerAdapter)

  private val extractor: Extractor[HttpRequest] = httpTracing.tracing().propagation().extractor {
    (r: HttpRequest, name: String) => {
      r.headers.find(_.lowercaseName() == name.toLowerCase).map(_.value()).orNull
    }
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

  private def transformClientRequest(request: HttpRequest)(future: HttpRequest => Future[HttpResponse]): Future[HttpResponse] = {

    val carrier = request.headers.toBuffer
    val span = clientHandler
      .handleSend(injector, carrier, request)

    val requestWithHeaders = request.withHeaders(carrier.toList)

    val remoteAddress =
      request.uri.authority.host.inetAddresses.headOption
        .map(_.getHostAddress)
        .getOrElse(request.uri.authority.host.address())

    span.remoteIpAndPort(remoteAddress, request.uri.authority.port)

    try {
      future(requestWithHeaders).andThen {
        case Success(response) =>
          clientHandler.handleReceive(response, null, span)
        case Failure(exception) =>
          println(s"failure ${exception.getMessage}")
          clientHandler.handleReceive(null, exception, span)
      }(singleThreadExecutor)
    } catch {
      case NonFatal(t) =>
        span.error(t).finish()
        throw t
    }
  }

}
