package smartthings.brave.scala.example.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ValidationRejection
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import smartthings.brave.scala.TracedFuture
import smartthings.brave.scala.akka.http.TracedHttpExt.Instances._
import smartthings.brave.scala.akka.http.directive.TracingDirective._
import smartthings.brave.scala.akka.http.extension.AkkaHttpTracingExtension

import scala.concurrent.Future
import scala.util.{Failure, Success}

object AkkaHttpExample extends App {

  implicit val system = ActorSystem("tracing-example")
  val settings = ActorMaterializerSettings(system)
    .withDispatcher("tracing-dispatcher")
  implicit val mat = ActorMaterializer(settings)(system)
  implicit val ec = system.dispatcher

  implicit val httpTracing = AkkaHttpTracingExtension(system).httpTracing
  implicit val tracer = httpTracing.tracing().tracer()

  val client: HttpRequest => Future[HttpResponse] = (request: HttpRequest) => Http().singleRequestWithTracing(request)

  lazy val route = path("future") {
    get {
      extractSpanCustomizer { sc =>
        sc.tag("message", "future")
        complete(TracedFuture("test") { _ => "ok" })
      }
    }
  } ~
    path("static") {
      get {
        complete("ok")
      }
    } ~
    path("client") {
      tracingNextSpanInScope { (localSpan, spanInScope) =>
        localSpan.name("around-client").start()
        complete {
          client(HttpRequest(uri = "http://www.example.com"))
            .andThen {
              case Success(r) =>
                localSpan.tag("message", s"query completed with status ${r.status}").finish()
                spanInScope.close()
              case Failure(exception) =>
                localSpan.tag("error", exception.getLocalizedMessage).finish()
                spanInScope.close()
            }
            .flatMap(r => Unmarshal(r.entity).to[String])

        }
      }
    } ~
    path("exception") {
      get {
        complete {
          throw new RuntimeException("oh nooooo!")
        }
      }
    } ~
    path("reject") {
      get {
        reject(ValidationRejection("validation rejection"))
      }
    }

  Http().bindAndHandleWithTracing(route, "localhost", 8080)

}
