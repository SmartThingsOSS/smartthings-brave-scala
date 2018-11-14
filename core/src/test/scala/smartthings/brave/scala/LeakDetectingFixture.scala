package smartthings.brave.scala

import java.io.Closeable
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit}

import brave.Tracing
import brave.internal.HexCodec
import brave.propagation._
import brave.sampler.Sampler
import zipkin2.Span

import _root_.scala.collection.JavaConverters._


object LeakDetectingFixture {
  val CONTEXT_LEAK: String = "context.leak"
  val EXTRA_KEY: String = "user-id"


  def apply(): LeakDetectingFixture = new LeakDetectingFixture()
}

class LeakDetectingFixture extends Closeable {
  import LeakDetectingFixture._

  private val strictTraceContext: CurrentTraceContext =
    ThreadLocalCurrentTraceContext.newBuilder.addScopeDecorator(StrictScopeDecorator.create).build

  private val spans = new LinkedBlockingDeque[Span]()

  def newTracingBuilder(sampler: Sampler): Tracing.Builder =
    Tracing.newBuilder()
      .spanReporter(s => {
        // make sure the context was cleared prior to finish.. no leaks!
        val current = strictTraceContext.get()
        var contextLeak = false
        var span = s
        if (current != null) {
          // add annotation in addition to throwing, in case we are off the main thread
          if (HexCodec.toLowerHex(current.spanId()).equals(s.id())) {
            span = s.toBuilder.addAnnotation(s.timestampAsLong(), CONTEXT_LEAK).build()
            contextLeak = true
          }
        }
        spans.add(span)
        // throw so that we can see the path to the code that leaked the context
        if (contextLeak) {
          throw new AssertionError(s"$CONTEXT_LEAK  on  ${Thread.currentThread().getName}")
        }
      })
      .propagationFactory(ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, EXTRA_KEY))
      .currentTraceContext(strictTraceContext)
      .sampler(sampler)


  def takeSpan(): Span = {
    val result = spans.poll(3, TimeUnit.SECONDS)
    assert(result != null, "Span was not reported")
    assert(!result.annotations().asScala.exists(_.value() == "context.leak"), "Context leak detected")
    result
  }

  def close(): Unit = {
    Option(Tracing.current()).foreach(_.close())
  }

  def clear(): Unit = spans.clear()

}
