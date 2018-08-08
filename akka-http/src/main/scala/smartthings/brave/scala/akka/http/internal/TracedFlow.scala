package smartthings.brave.scala.akka.http.internal

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import akka.util.ByteString
import brave.Span
import brave.Tracer.SpanInScope
import brave.http.{HttpServerHandler, HttpTracing}
import brave.propagation.TraceContext.Extractor

import scala.util.{Failure, Success}

object TracedFlow {

  def apply(flow: Flow[HttpRequest, HttpResponse, Any],
            httpTracing: HttpTracing, handler: HttpServerHandler[HttpRequest, HttpResponse],
            extractor: Extractor[HttpRequest]): Flow[HttpRequest, HttpResponse, Any] = {
    BidiFlow.fromGraph(wrap(httpTracing, handler, extractor)).join(flow)
  }

  private def wrap(httpTracing: HttpTracing,
                   handler: HttpServerHandler[HttpRequest, HttpResponse],
                   extractor: Extractor[HttpRequest]) =
    new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {

    private val requestIn = Inlet.create[HttpRequest]("request.in")
    private val requestOut = Outlet.create[HttpRequest]("request.out")
    private val responseIn = Inlet.create[HttpResponse]("response.in")
    private val responseOut = Outlet.create[HttpResponse]("response.out")

    private class ServerGraphStageLogic extends GraphStageLogic(shape) {

      private var currentSpan: Option[Span] = None
      private var spanInScope: Option[SpanInScope] = None

      setHandler(requestIn, new InHandler {
        override def onPush(): Unit = {
          val request = grab(requestIn)
          setContext(handler.handleReceive(extractor, request))
          push(requestOut, request)
        }

        override def onUpstreamFinish(): Unit = complete(requestOut)
      })

      setHandler(requestOut, new OutHandler {
        override def onPull(): Unit = {
          clearSpanContext()
          pull(requestIn)
        }
        override def onDownstreamFinish(): Unit = {
          clearSpanContext()
          cancel(requestIn)
        }
      })

      setHandler(responseIn, new InHandler {
        override def onPush(): Unit = {
          val response = currentSpan.map { span =>
            span.annotate("response-ready")
            val response = grab(responseIn)

            if (!response.entity.isKnownEmpty()) {
              response.transformEntityDataBytes(Flow[ByteString]
                .watchTermination()(Keep.right)
                  .mapMaterializedValue { f =>
                    f.andThen {
                      case Success(_) =>
                        if (response.status.isFailure()) {
                          // TODO brave requires an exception for error tagging.  Is there a better way?
                          handler.handleSend(response, new Exception(response.status.reason()), span)
                        } else {
                          handler.handleSend(response, null, span)
                        }

                      case Failure(exception) =>
                        handler.handleSend(response, exception, span)
                    }(materializer.executionContext)
                  }
              )
            } else {
              handler.handleSend(response, null, span)
              response
            }

          }.getOrElse(grab(responseIn))

          push(responseOut, response)
        }

        override def onUpstreamFinish(): Unit = {
          clearContext()
          completeStage()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          val response = grab(responseIn)
          currentSpan.foreach { span =>
            handler.handleSend(response, ex, span)
          }
          clearContext()
          completeStage()
        }
      })

      setHandler(responseOut, new OutHandler {
        override def onPull(): Unit = {
          clearContext()
          pull(responseIn)
        }

        override def onDownstreamFinish(): Unit = {
          clearContext()
          cancel(responseIn)
        }
      })

      private def setContext(span: Span): Unit = {
        spanInScope.foreach { ws => ws.close() }
        spanInScope = Some(httpTracing.tracing().tracer().withSpanInScope(span))
        currentSpan = Some(span)
      }

      private def clearContext(): Unit = {
        clearSpanContext()
        currentSpan = None
      }

      private def clearSpanContext(): Unit = {
        spanInScope = spanInScope.flatMap { ws => ws.close(); None }
        httpTracing.tracing().tracer().withSpanInScope(null)
      }
    }

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new ServerGraphStageLogic()
    }

    override def shape: BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse] =
      BidiShape(requestIn, requestOut, responseIn, responseOut)

  }

}
