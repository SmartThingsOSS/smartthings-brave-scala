package smartthings.brave.scala

import java.io.Closeable

import brave.Tracer.SpanInScope
import brave.propagation.TraceContext
import brave.{Span, SpanCustomizer, Tracer}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object TracedFuture {

  /**
    * Object to encapsulate some mutable state.  The objective here is to have a closable
    * object that we can call in both the success and failure cases without putting the
    * span in scope before the future is scheduled.
    *
    * @param value
    * @param tracer
    */
  private [this] class FutureSpanInScope(value: Span, tracer: Tracer) extends Closeable {

    private var ws: SpanInScope = _

    def markInScope(): Span = {
      ws = tracer.withSpanInScope(value)
      value
    }

    def close(): Unit = {
      value.finish()
      if (ws != null) ws.close()
    }
  }

  def apply[T](name: String, parent: TraceContext)(body: SpanCustomizer => T)(implicit executor: ExecutionContext, tracer: Tracer): Future[T] = {
    val span = tracer.newChild(parent).name(name).start()
    val futureInScope = new FutureSpanInScope(span, tracer)
    Future {
      body(futureInScope.markInScope())
    }(executor).andThen {
      case Success(_) => futureInScope.close()
      case Failure(exception) =>
        span.tag("error", exception.getLocalizedMessage)
        futureInScope.close()
    }
  }


  def apply[T](name: String)(body: SpanCustomizer => T)(implicit executor: ExecutionContext, tracer: Tracer): Future[T] = {
    val span = tracer.nextSpan().name(name).start()
    val futureInScope = new FutureSpanInScope(span, tracer)
    Future {
      body(futureInScope.markInScope())
    }(executor).andThen {
      case Success(_) => futureInScope.close()
      case Failure(exception) =>
        span.tag("error", exception.getLocalizedMessage)
        futureInScope.close()
    }
  }
}
