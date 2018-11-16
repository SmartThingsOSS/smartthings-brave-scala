[![CircleCI](https://circleci.com/gh/SmartThingsOSS/smartthings-brave-scala.svg?style=svg)](https://circleci.com/gh/SmartThingsOSS/smartthings-brave-scala)

# smartthings-brave-scala
Experimental Scala extensions for the Open Zipkin Brave library.  This is not
considered fit for production use and APIs are still influx and _will_ change
without notice.  If you are looking for mature and stable instrumentation for Scala Futures,
Akka, Akka Http or Monix we suggest looking at [Kamon](https://kamon.io/documentation/get-started/).

First we would like to give recognition to several existing libraries
that helped with the initial implementations here.

https://github.com/levkhomich/akka-tracing

https://github.com/kamon-io/kamon-akka

https://github.com/FindHotel/akka-stream-trace

Why a new set of libraries when there are already a few choices?

At SmartThings we use the Brave trace instrumentation library extensively
across all of our Java based micro services.  While some of the mentioned
libraries would have given sufficient and or better trace instrumentation for 
Akka Http, we wanted to provide our developers access to the already familiar Brave API.
Additionally we wanted to build on a consistent span reporter implementation
which meets our needs now and allows for greater flexibility in the future.

Long term we hope that this grows into a mature project worthy of becoming part
of the OpenZipkin Brave instrumentation libraries.

## modules
#### smartthings-brave-scala-core

The Core module contains Scala specific Tracing helpers.
Right now this just includes a `TracedFuture` which manages
span scope and naming.

#### smartthings-brave-scala-config

The Config module provides patterns for building a tracer
using Typesafe Config.  Since Typesafe Config is widely used
in the Scala ecosystem, this should give a quick way for any
Scala project to add basic tracing support.

#### smartthings-brave-scala-akka

The Akka module provides Akka instrumentation.  This is currently limited
to a custom Akka Dispatcher that is used to wrap the ExecutorService with the Brave
CurrentSpanContext to handle context propagation across threads. 

#### smartthings-brave-scala-akka-http

The Akka Http module provides Brave Http instrumentation adapters, a custom BidiFlow
and some additional syntax for `akka.http.scaladsl.HttpExt` which adds two methods 
`bindAndHandleWithTracing` and `singleReqeustWithTracing`.  In addition there are several
Akka Http directives that provide convenient access to the `SpanCustomizer` and `Tracing`
instances.

For an example of both server and client tracing please refer to 
`smartthings.brave.scala.examples.http.AkkaHttpExample` in the examples module.
