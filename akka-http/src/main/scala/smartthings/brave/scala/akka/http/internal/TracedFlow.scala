package smartthings.brave.scala.akka.http.internal

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._
import akka.util.ByteString
import brave.Span
import brave.Tracer.SpanInScope
import brave.http.{HttpServerHandler, HttpTracing}
import brave.propagation.TraceContext.Extractor

import scala.util.{Failure, Success}

object TracedFlow {

  def apply(flow: Flow[HttpRequest, HttpResponse, Any],
            httpTracing: HttpTracing, handler: HttpServerHandler[HttpRequest, (HttpRequest, HttpResponse)],
            extractor: Extractor[HttpRequest]): Flow[HttpRequest, HttpResponse, Any] = {
    BidiFlow.fromGraph(wrap(httpTracing, handler, extractor)).join(flow)
  }

  private def wrap(httpTracing: HttpTracing,
                   handler: HttpServerHandler[HttpRequest, (HttpRequest, HttpResponse)],
                   extractor: Extractor[HttpRequest]) =
    new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {

    private val requestIn = Inlet.create[HttpRequest]("request.in")
    private val requestOut = Outlet.create[HttpRequest]("request.out")
    private val responseIn = Inlet.create[HttpResponse]("response.in")
    private val responseOut = Outlet.create[HttpResponse]("response.out")

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new ServerGraphStageLogic(httpTracing, handler, extractor,
        requestIn, requestOut, responseIn, responseOut, shape)
    }

    override def shape: BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse] =
      BidiShape(requestIn, requestOut, responseIn, responseOut)

  }

  private class ServerGraphStageLogic(httpTracing: HttpTracing,
                                      handler: HttpServerHandler[HttpRequest, (HttpRequest, HttpResponse)],
                                      extractor: Extractor[HttpRequest],
                                      requestIn: Inlet[HttpRequest], requestOut: Outlet[HttpRequest],
                                      responseIn: Inlet[HttpResponse], responseOut: Outlet[HttpResponse],
                                      shape: Shape) extends GraphStageLogic(shape) {

    private var currentSpan: Option[Span] = None
    private var spanInScope: Option[SpanInScope] = None
    private var capturedRequest: Option[HttpRequest] = None

    private val requestInHandler = new InHandler {
      override def onPush(): Unit = {
        val request = grab(requestIn)
        setContext(request, handler.handleReceive(extractor, request))
        push(requestOut, request)
      }

      override def onUpstreamFinish(): Unit = complete(requestOut)
    }

    private val requestOuthandler = new OutHandler {
      override def onPull(): Unit = {
        clearSpanContext()
        pull(requestIn)
      }
      override def onDownstreamFinish(): Unit = {
        clearSpanContext()
        cancel(requestIn)
      }
    }

    private val responseInHandler = new InHandler {
      override def onPush(): Unit = {
        val response = currentSpan.map { span =>
          span.annotate("response-ready")

          val cRequest = capturedRequest.orNull
          val response = grab(responseIn)

          if (!response.entity.isKnownEmpty()) {
            response.transformEntityDataBytes(Flow[ByteString]
              .watchTermination()(Keep.right)
              .mapMaterializedValue {
                _.andThen {
                  case Success(_) =>
                    if (response.status == StatusCodes.BadRequest) {
                      handler.handleSend((cRequest, response), new Exception(response.status.reason()), span)
                    }
                    else if (response.status.isFailure()) {
                      handler.handleSend((cRequest, response), new Exception(response.status.reason()), span)
                    } else {
                      // scalastyle:off null
                      handler.handleSend((cRequest, response), null, span)
                      // scalastyle:on null
                    }

                  case Failure(exception) => handler.handleSend((cRequest, response), exception, span)
                }(materializer.executionContext)
              }
            )
          } else {
            // scalastyle:off null
            handler.handleSend((cRequest, response), null, span)
            // scalastyle:on null
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
          handler.handleSend((capturedRequest.orNull, response), ex, span)
        }
        clearContext()
        completeStage()
      }
    }

    private val responseOutHandler = new OutHandler {
      override def onPull(): Unit = {
        clearContext()
        pull(responseIn)
      }

      override def onDownstreamFinish(): Unit = {
        clearContext()
        cancel(responseIn)
      }
    }

    setHandler(requestIn, requestInHandler)

    setHandler(requestOut, requestOuthandler)

    setHandler(responseIn, responseInHandler)

    setHandler(responseOut, responseOutHandler)

    private def setContext(request: HttpRequest, span: Span): Unit = {
      spanInScope.foreach { _.close() }
      spanInScope = Some(httpTracing.tracing().tracer().withSpanInScope(span))
      currentSpan = Some(span)
      capturedRequest = Some(request)
    }

    private def clearContext(): Unit = {
      clearSpanContext()
      currentSpan = None
      capturedRequest = None
    }

    private def clearSpanContext(): Unit = {
      spanInScope = spanInScope.flatMap { ws => ws.close(); None }
      // scalastyle:off null
      httpTracing.tracing().tracer().withSpanInScope(null)
      // scalastye:on null
    }

  }

}
