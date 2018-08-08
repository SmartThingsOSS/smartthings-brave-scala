package smartthings.brave.scala.example.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import smartthings.brave.scala.TracedFuture
import smartthings.brave.scala.config.TracingConfigurator

import scala.concurrent.Future
import scala.util.{Failure, Success}

object AkkaStreamsExample extends App {

  val system = ActorSystem("tracing-example")
  val settings = ActorMaterializerSettings(system)
    .withDispatcher("tracing-dispatcher")
  implicit val mat = ActorMaterializer(settings)(system)
  implicit val ec = system.dispatcher

  val tracing = TracingConfigurator("brave.tracing")

  implicit val tracer = tracing.tracer()

  val trace = tracer
    .newTrace()
    .name("stream")

  val ws = tracing.tracer().withSpanInScope(trace.start())

  Source.fromIterator(() => (1 until 30).toIterator)
    .async
    .mapAsyncUnordered(4)(i => TracedFuture(s"child-$i", trace.context()){ sc =>
      val result = i * i
      sc.tag("result", result.toString)
      result
    })
    .runFoldAsync(Seq[Int]()) { (acc, curr) => Future(acc :+ curr) }
    .onComplete {
      case Failure(exception) =>
        trace.tag("error", exception.getLocalizedMessage)
        trace.finish()
        ws.close()
        system.terminate()
      case Success(value) =>
        trace.tag("result", value.mkString(","))
        trace.finish()
        ws.close()
        system.terminate()
    }

}
