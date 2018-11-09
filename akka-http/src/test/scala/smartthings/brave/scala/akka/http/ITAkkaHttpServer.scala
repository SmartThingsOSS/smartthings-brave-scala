package smartthings.brave.scala.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}
import brave.http.HttpTracing
import brave.sampler.Sampler
import brave.test.http.ITHttpServer
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.{After, Before, Test}
import org.scalatest.junit.JUnitSuiteLike
import smartthings.brave.scala.akka.http.TracedHttpExt.Instances._
import smartthings.brave.scala.akka.http.directive.TracingDirective._
import smartthings.brave.scala.akka.http.extension.AkkaHttpTracingExtension

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class ITAkkaHttpServer extends ITHttpServer with JUnitSuiteLike {

  private val route: Route = {
    (pathEndOrSingleSlash & method(HttpMethods.OPTIONS)) {
      complete("")
    } ~
      path("extra") {
        complete("joey")
      } ~
      (path("foo") & parameterSeq) { _ =>
        complete(StatusCodes.OK)
      } ~
      path("exception") {
        complete(new RuntimeException("failed"))
      } ~
      (path("items" / IntNumber ) & parameterSeq) { (segment, _) =>
        tracingRouteName("/items/:item") {
          complete(segment.toString)
        }
      } ~
      path("badrequest") {
        complete(StatusCodes.BadRequest)
      } ~
      path("async") {
        complete(Future(""))
      } ~
      (path("async_items" / IntNumber) & parameterSeq) { (segment, _) =>
        tracingRouteName("/async_items/:item") {
          complete(Future(segment.toString))
        }
      } ~
      (path("nested" / "items" / IntNumber) & parameterSeq) { (segment, _) =>
        tracingRouteName("/nested/items/:item") {
          complete(segment.toString)
        }
      } ~
      path("exceptionAsync") {
        complete(Future(new Exception("async error")))
      } ~
      path("child") {
        tracingNextSpanInScope("child span") { (span, ws) =>
          Thread.sleep(1.milli.toMillis)
          ws.close()
          span.finish()

          complete(StatusCodes.OK)
        }

      }
  }

  private var context: Option[ServerContext] = None

  private def maybeClose(sc: Option[ServerContext]): Option[ServerContext] = { sc.flatMap { c => c.close(); None } }

  private def bind(port: Int): Unit = {

    this.context = maybeClose(context)

    // hack to override tracing instance that would normally be instantiated from config.
    AkkaHttpTracingExtension.httpTracingSupplier = () => Some(httpTracing)

    implicit val system: ActorSystem = ActorSystem("tracing-example")

    val settings = ActorMaterializerSettings(system).withDispatcher("tracing-dispatcher")

    implicit val mat: Materializer = ActorMaterializer(settings)(system)

    // bring implicit httpTracing into scope so implicit conversion on HttpExt is performed.
    implicit val ht: HttpTracing = AkkaHttpTracingExtension(system).httpTracing

    context = Some(ServerContext(
      system,
      Await.result(Http().bindAndHandleWithTracing(route, "localhost", port), 5.seconds)
    ))
  }

  @Before
  @throws[Exception]
  override def setup(): Unit = {
    httpTracing = HttpTracing
      .newBuilder(tracingBuilder(Sampler.ALWAYS_SAMPLE).build)
      .build()
    init()
  }

  @After
  override def close(): Unit = {
    super.close()
    context = maybeClose(context)
  }

  override def init(): Unit = bind(8282)

  private def port: Int = context.map(_.binding.localAddress.getPort).getOrElse(0)

  override def url(path: String): String = s"http://localhost:$port$path"

  @Test
  @throws[Exception]
  override def addsStatusCode_badRequest(): Unit = {
    try
      get("/badrequest")
    catch {
      case NonFatal(_) =>
    }

    val span = takeSpan
    assertThat(span.tags)
      .containsEntry("http.status_code", "400")
      .containsEntry("error", "Bad Request")
  }

  @Test
  @throws[Exception]
  override def reportsClientAddress(): Unit = {
    get(new Request.Builder()
      .url(url("/foo"))
      .header("Remote-Address", "1.2.3.4")
      .build)

    val span = takeSpan
    assertThat(span.remoteEndpoint).isNotNull
  }

  case class ServerContext(system: ActorSystem, binding: Http.ServerBinding) {
    def close(): Unit = {
      Await.result(binding.terminate(1.second), 2.seconds)
      Await.result(system.terminate(), 2.seconds)
    }
  }
}
