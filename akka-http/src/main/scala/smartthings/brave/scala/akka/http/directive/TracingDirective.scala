package smartthings.brave.scala.akka.http.directive

import akka.http.scaladsl.server.directives.BasicDirectives.extractActorSystem
import akka.http.scaladsl.server.{Directive, Directive1}
import brave.Tracer.SpanInScope
import brave.{Span, SpanCustomizer, Tracer}
import smartthings.brave.scala.akka.http.extension.AkkaHttpTracingExtension


object TracingDirective extends TracingDirective

trait TracingDirective {

  def nextSpanInScope: Directive[(Span, SpanInScope)] = extractTracer.tmap { tracer =>
    val span = tracer._1.nextSpan()
    val ws = tracer._1.withSpanInScope(span)
    (span, ws)
  }

  def extractSpanCustomizer: Directive1[SpanCustomizer] = extractTracer.tmap { tracer =>
    tracer._1.currentSpanCustomizer()
  }

  def extractTracer: Directive1[Tracer] = extractActorSystem.tmap { system =>
    AkkaHttpTracingExtension(system._1).httpTracing.tracing().tracer()
  }

}
