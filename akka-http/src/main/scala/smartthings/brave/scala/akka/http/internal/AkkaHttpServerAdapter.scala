package smartthings.brave.scala.akka.http.internal

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import brave.Span
import brave.http.HttpServerAdapter

class AkkaHttpServerAdapter extends HttpServerAdapter[HttpRequest, (HttpRequest, HttpResponse)] {
  override def method(request: HttpRequest): String = request.method.value

  override def methodFromResponse(response: (HttpRequest, HttpResponse)): String = response match {
    case (req, _) if req != null => req.method.value
    case _ => null
  }

  // TODO is there a good way to return a template route for span names?
  override def route(response: (HttpRequest, HttpResponse)): String = super.route(response)

  override def path(request: HttpRequest): String = {
    request.uri.path.toString()
  }

  override def url(request: HttpRequest): String = {
    println(request.uri.toString())
    request.uri.toString()
  }

  override def requestHeader(request: HttpRequest, name: String): String = {
    val lower = name.toLowerCase
    request.headers
      .find(_.lowercaseName() == lower)
      .map(_.value()).orNull
  }

  override def statusCode(response: (HttpRequest, HttpResponse)): Integer = response match {
    case (_, res) => res.status.intValue()
  }

  override def parseClientIpAndPort(req: HttpRequest, span: Span): Boolean = {
    if (parseClientIpFromXForwardedFor(req, span)) true
    else {
      extractIp(requestHeader(req, "Remote-Address"))
        .orElse(extractIp(requestHeader(req, "X-Real-Ip")))
        .exists(span.remoteIpAndPort(_, 0))
    }

  }

  private def extractIp(address: String): Option[String] = {
    Option(address) match {
      case None => None
      case Some(remoteAddress) => {
        val indexOfComma = remoteAddress.indexOf(',')
        Some(if (indexOfComma != -1) remoteAddress.substring(0, indexOfComma) else remoteAddress)
      }
    }
  }
}
