package smartthings.brave.scala.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import brave.http.HttpTracing
import brave.test.http.ITHttpAsyncClient
import org.scalatest.junit.JUnitSuiteLike
import smartthings.brave.scala.akka.http.TracedHttpExt.Instances._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


case class Client(httpTracing: HttpTracing, scheme: String, host: String, port: Int) {

  private val maxRedirects = 20

  implicit private val system: ActorSystem = ActorSystem("client")
  implicit private val ht: HttpTracing = httpTracing

  def close(): Unit = Await.result(system.terminate(), 3.seconds)

  def call(request: HttpRequest, count: Int = 0): Future[HttpResponse] = {

    def maybeRedirect(req: HttpRequest, count: Int): Future[HttpResponse] = {
      // akka http client doesn't support redirects so we are on our own to make them work.
      Http().singleRequestWithTracing(req).flatMap { resp =>
        resp.status match {
          case StatusCodes.Found | StatusCodes.PermanentRedirect => resp.header[headers.Location].map { loc =>
            val locUri = loc.uri .copy(scheme = req.uri.scheme, authority = req.uri.authority)
            val newReq = req.copy(uri = locUri)
            if (count < maxRedirects) maybeRedirect(newReq, count + 1) else Http().singleRequestWithTracing(newReq)
          }.getOrElse(throw new RuntimeException(s"location not found on 302 for ${req.uri}"))
          case _ => Future(resp)
        }
      }
    }

    val decorated = request.withUri(request.uri.withScheme(scheme).withHost(host).withPort(port))
    maybeRedirect(decorated, 0)

  }

}

class ITAkkaHttpClient extends ITHttpAsyncClient[Client] with JUnitSuiteLike {

  override def getAsync(client: Client, pathIncludingQuery: String): Unit =
    client.call(HttpRequest(HttpMethods.GET, Uri(pathIncludingQuery)))

  override def newClient(port: Int): Client = {
    Client(httpTracing, "http", "127.0.0.1", port)
  }

  override def closeClient(client: Client): Unit = client.close()

  override def get(client: Client, pathIncludingQuery: String): Unit =
    Await.result(client.call(HttpRequest(HttpMethods.GET, Uri(pathIncludingQuery))), 4.seconds)

  override def post(client: Client, pathIncludingQuery: String, body: String): Unit =
    Await.result(client.call(HttpRequest(HttpMethods.POST, Uri(pathIncludingQuery), entity = body)), 4.seconds)

}
