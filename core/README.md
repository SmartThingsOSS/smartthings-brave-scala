# smartthings-brave-scala-core

# Example usage

```scala
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
```

Console output will be similar to:
```json
{"traceId":"a1391b28c9eb3c66","parentId":"a1391b28c9eb3c66","id":"6dade2c62aaa9e4a","name":"inner","timestamp":1535562435581999,"duration":53717,"localEndpoint":{"serviceName":"unknown","ipv4":"192.168.254.116"},"tags":{"some_key":"inner"}}
{"traceId":"a1391b28c9eb3c66","id":"a1391b28c9eb3c66","name":"outer","timestamp":1535562435493883,"duration":188811,"localEndpoint":{"serviceName":"unknown","ipv4":"192.168.254.116"},"tags":{"some_key":"outer"}}
```
