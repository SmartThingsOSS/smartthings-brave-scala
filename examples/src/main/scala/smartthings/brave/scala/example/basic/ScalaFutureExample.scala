package smartthings.brave.scala.example.basic

import java.util.concurrent.ForkJoinPool

import brave.Tracing
import smartthings.brave.scala.TracedFuture
import zipkin2.reporter.Reporter

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._


object ScalaFutureExample extends App {

  // setup tracing
  val tracing = Tracing.newBuilder().spanReporter(Reporter.CONSOLE).build()

  // wrap a Java ExecutorService with the CurrentTraceContext
  val executorService = tracing.currentTraceContext().executorService(new ForkJoinPool())

  // create a new Scala ExecutionContext from the wrapped ExecutorService
  implicit val executionContext = ExecutionContext.fromExecutorService(executorService)

  implicit val tracer = tracing.tracer()


  val future = TracedFuture("outer") { spanCustomizer =>
    spanCustomizer.tag("some_key", "outer")

    TracedFuture("inner") { spanCustomizer =>
      spanCustomizer.tag("some_key", "inner")
      Thread.sleep(50)
    }

    Thread.sleep(100)
  }

  Await.result(future, 300.millis)

}
