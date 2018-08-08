package smartthings.brave.scala

import java.util.concurrent.ForkJoinPool

import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.sampler.Sampler
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}
import zipkin2.Span
import zipkin2.reporter.Reporter

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class TracedFutureSpec extends FunSpec with Matchers with BeforeAndAfter {

  val reporter = new Reporter[Span] {

    val spans = ArrayBuffer[Span]()

    override def report(span: Span): Unit = {
      spans += span
    }

    def clear(): Unit = spans.clear()
  }

  val tracing = Tracing.newBuilder()
    .sampler(Sampler.ALWAYS_SAMPLE)
    .currentTraceContext(CurrentTraceContext.Default.create())
    .spanReporter(reporter)
    .build()

  val executorService = tracing.currentTraceContext().executorService(new ForkJoinPool())

  implicit val executionContext = ExecutionContext.fromExecutorService(executorService)
  implicit val tracer = tracing.tracer()

  after {
    reporter.clear()
  }

  describe("TracedFuture") {

    it("should add proper name to span") {
      val result = TracedFuture("future thing") { span =>
        span.tag("type", "parent")
        Thread.sleep(100)
      }

      Await.result(result, 1.second)

      reporter.spans.length shouldBe 1

      val parentSpan = reporter.spans.find(_.tags().containsValue("parent"))

      parentSpan.isDefined shouldBe true
      parentSpan.get.name() shouldBe "future thing"
    }

    it("should start and complete span with nested futures") {
      val result = TracedFuture("future thing 1") { span =>
        span.tag("type", "parent")
        TracedFuture("future thing 2") { span =>
          span.tag("type", "child1")
          Thread.sleep(100)
        }

        TracedFuture("future thing 3") { span =>
          span.tag("type", "child2")
          Thread.sleep(50)
        }

        TracedFuture("future thing 4") { span =>
          span.tag("type", "child3")
          Thread.sleep(75)

          TracedFuture("future thing 5") { span =>
            span.tag("type", "nestedChild1")
            Thread.sleep(20)
          }
        }

        Thread.sleep(200)
      }

      Await.result(result, 1.second)

      reporter.spans.length shouldBe 5

      println(reporter.spans)

      val parentSpan = reporter.spans.find(_.tags().containsValue("parent"))
      val child1Span = reporter.spans.find(_.tags().containsValue("child1"))
      val child2Span = reporter.spans.find(_.tags().containsValue("child2"))
      val child3Span = reporter.spans.find(_.tags().containsValue("child3"))
      val nestedChild1Span = reporter.spans.find(_.tags().containsValue("nestedChild1"))

      parentSpan.isDefined shouldBe true
      child1Span.isDefined shouldBe true
      child2Span.isDefined shouldBe true
      child3Span.isDefined shouldBe true
      nestedChild1Span.isDefined shouldBe true

      child1Span.get.parentId() shouldEqual parentSpan.get.id()
      child2Span.get.parentId() shouldEqual parentSpan.get.id()
      child3Span.get.parentId() shouldEqual parentSpan.get.id()
      nestedChild1Span.get.parentId() shouldEqual child3Span.get.id()
    }
  }

}
