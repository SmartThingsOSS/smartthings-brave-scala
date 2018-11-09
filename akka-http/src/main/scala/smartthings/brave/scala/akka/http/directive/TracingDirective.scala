package smartthings.brave.scala.akka.http.directive

import akka.http.scaladsl.server.directives.BasicDirectives.extractActorSystem
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequest
import akka.http.scaladsl.server.{Directive, Directive0, Directive1}
import brave.Tracer.SpanInScope
import brave.{Span, SpanCustomizer, Tracer}
import smartthings.brave.scala.akka.http.extension.AkkaHttpTracingExtension


object TracingDirective extends TracingDirective

trait TracingDirective {

  def tracingRouteName(route: String): Directive0 = (extractRequest & extractSpanCustomizer).tmap {
    case (request, customizer) =>
      customizer.name(s"${request.method.value.toLowerCase()} $route")
      ()
  }

  def tracingNextSpanInScope(name: String): Directive[(Span, SpanInScope)] = extractTracer.tmap { tracer =>
    val span = tracer._1.nextSpan().name(name).start()
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
