package smartthings.brave.scala.config

import java.util.concurrent.ConcurrentHashMap

import brave.internal.Platform
import brave.propagation.CurrentTraceContext
import brave.sampler.Sampler
import brave.{ErrorParser, Tracing}
import com.typesafe.config.{Config, ConfigFactory}
import zipkin2.reporter.Reporter

import scala.util.Try

object TracingConfigurator extends TracingConfigurator {

  private lazy val instances = new ConcurrentHashMap[String, Tracing]()

  def apply(path: String): Tracing = apply(ConfigFactory.load().getConfig(path))

  def apply(config: Config): Tracing = {
    val name = Try(config.getString("name")).getOrElse("default")
    val singleton = Option(config.getBoolean("singleton")).getOrElse(false)

    if (singleton) {
      instances.computeIfAbsent(name, _ => builder(config)
        .build())
    } else {
      builder(config)
        .build()
    }
  }

  private def builder(config: Config): Tracing.Builder = {
    val localServiceName = Try(config.getString("local-service-name")).getOrElse("unknown")
    Tracing.newBuilder()
      .clock(Configurator(config, "clock", _ => Platform.get().clock()))
      .currentTraceContext(CurrentTraceContext.Default.create())
      .endpoint(Configurator(config, "endpoint", _ => Platform.get().endpoint().toBuilder.serviceName(localServiceName).build()))
      .supportsJoin(Try(config.getBoolean("supports-join")).getOrElse(true))
      .localServiceName(localServiceName)
      .traceId128Bit(Try(config.getBoolean("trace-id-128-bit")).getOrElse(false))
      .sampler(Configurator(config, "sampler", _ => Sampler.ALWAYS_SAMPLE))
      .spanReporter(Configurator(config, "span-reporter", _ => Reporter.CONSOLE))
      .errorParser(Configurator(config, "error-parser", _ => new ErrorParser))
  }

}

class TracingConfigurator extends Configurator[Tracing] {

  override def configure(config: Config): Tracing = TracingConfigurator(config)

}
