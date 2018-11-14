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
    val localIp = Try(config.getString("local-ip")).getOrElse(Platform.get().linkLocalIp())
    val localPort = Try(config.getInt("local-port")).getOrElse(0)
    Tracing.newBuilder()
      .clock(Configurator(config, "clock").getOrElse(Platform.get().clock()))
      .currentTraceContext(CurrentTraceContext.Default.create())
      .supportsJoin(Try(config.getBoolean("supports-join")).getOrElse(true))
      .localServiceName(localServiceName)
      .localIp(localIp)
      .localPort(localPort)
      .traceId128Bit(Try(config.getBoolean("trace-id-128-bit")).getOrElse(false))
      .sampler(Configurator(config, "sampler").getOrElse(Sampler.ALWAYS_SAMPLE))
      .spanReporter(Configurator(config, "span-reporter").getOrElse(Reporter.CONSOLE))
      .errorParser(Configurator(config, "error-parser").getOrElse(ErrorParser.NOOP))
  }

}

class TracingConfigurator extends Configurator[Tracing] {

  override def configure(config: Config): Tracing = TracingConfigurator(config)

}
