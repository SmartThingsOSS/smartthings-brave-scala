package smartthings.brave.scala

import java.util.concurrent.ForkJoinPool

import brave.sampler.Sampler
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class TracedFutureSpec extends FunSpec with Matchers with BeforeAndAfter {

  val fixture = LeakDetectingFixture()

  val tracing = fixture
    .newTracingBuilder(Sampler.ALWAYS_SAMPLE)
    .build()

  val executorService = tracing.currentTraceContext().executorService(new ForkJoinPool())

  implicit val executionContext = ExecutionContext.fromExecutorService(executorService)
  implicit val tracer = tracing.tracer()

  after {
    fixture.clear()
  }

  describe("TracedFuture") {

    it("should add proper name to span") {
      TracedFuture("future thing") { span =>
        span.tag("type", "some_type")
        Thread.sleep(100)
      }

      val span = fixture.takeSpan()
      span.name() shouldBe "future thing"
      span.tags().asScala.keys.exists(_ == "type") shouldBe true

    }

    it("should start and complete span with nested futures") {
      TracedFuture("future thing 1") { span =>
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

      val spans = List(fixture.takeSpan(), fixture.takeSpan(), fixture.takeSpan(), fixture.takeSpan(), fixture.takeSpan())

      val parentSpan = spans.find(_.tags().asScala.values.exists(_ == "parent"))
      val child1Span = spans.find(_.tags().asScala.values.exists(_ == "child1"))
      val child2Span = spans.find(_.tags().asScala.values.exists(_ == "child2"))
      val child3Span = spans.find(_.tags().asScala.values.exists(_ == "child3"))
      val nestedChild1Span = spans.find(_.tags().asScala.values.exists(_ == "nestedChild1"))

      withClue("Parent span") {
        parentSpan.isDefined shouldBe true
        parentSpan.get.name() shouldBe "future thing 1"
      }

      withClue("child1 span") {
        child1Span.isDefined shouldBe true
        child1Span.get.name() shouldBe "future thing 2"
        child1Span.get.parentId() shouldBe parentSpan.get.id()
      }

      withClue("child2 span") {
        child2Span.isDefined shouldBe true
        child2Span.get.name() shouldBe "future thing 3"
        child2Span.get.parentId() shouldBe parentSpan.get.id()
      }

      withClue("child3 span") {
        child3Span.isDefined shouldBe true
        child3Span.get.name() shouldBe "future thing 4"
        child3Span.get.parentId() shouldBe parentSpan.get.id()
      }

      withClue("nestedChild1 span") {
        nestedChild1Span.isDefined shouldBe true
        nestedChild1Span.get.name() shouldBe "future thing 5"
        nestedChild1Span.get.parentId() shouldBe child3Span.get.id()
        nestedChild1Span.get.traceId() shouldBe parentSpan.get.traceId()
      }
    }
  }

}
