package smartthings.brave.scala.config.http

import java.util.concurrent.ConcurrentHashMap

import brave.http.{HttpClientParser, HttpSampler, HttpServerParser, HttpTracing}
import brave.{ErrorParser, Tracing}
import com.typesafe.config.{Config, ConfigFactory}
import smartthings.brave.scala.config.Configurator

import scala.util.Try

object HttpTracingConfigurator {

  private lazy val instances = new ConcurrentHashMap[String, HttpTracing]()

  def apply(path: String, tracing: Tracing): HttpTracing =
    apply(ConfigFactory.load().getConfig(path), tracing)

  def apply(config: Config, tracing: Tracing): HttpTracing = {
    val name = Try(config.getString("name")).getOrElse("default")
    val singleton = Option(config.getBoolean("singleton")).getOrElse(false)

    if (singleton) {
      instances.computeIfAbsent(name, _ => builder(config, tracing).build())
    } else {
      builder(config, tracing).build()
    }
  }

  private def builder(config: Config, tracing: Tracing): HttpTracing.Builder = {
    HttpTracing.newBuilder(tracing)
      .clientParser(Configurator(config, "client-parser").getOrElse(new HttpClientParser() {
        override def errorParser(): ErrorParser = tracing.errorParser()
      }))
      .serverParser(Configurator(config, "server-parser").getOrElse(new HttpServerParser() {
        override def errorParser(): ErrorParser = tracing.errorParser()
      }))
      .clientSampler(Configurator(config, "client-sampler").getOrElse(HttpSampler.TRACE_ID))
      .serverSampler(Configurator(config, "server-sampler").getOrElse(HttpSampler.TRACE_ID))
  }

}
