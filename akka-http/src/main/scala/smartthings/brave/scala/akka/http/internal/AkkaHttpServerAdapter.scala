package smartthings.brave.scala.akka.http.internal

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import brave.http.HttpServerAdapter

class AkkaHttpServerAdapter extends HttpServerAdapter[HttpRequest, HttpResponse] {
  override def method(request: HttpRequest): String = request.method.value

  override def url(request: HttpRequest): String = request.uri.path.toString()

  override def requestHeader(request: HttpRequest, name: String): String = {
    val lower = name.toLowerCase
    request.headers.find(_.lowercaseName() == lower).map(_.value()).orNull
  }

  override def statusCode(response: HttpResponse): Integer = response.status.intValue()
}
