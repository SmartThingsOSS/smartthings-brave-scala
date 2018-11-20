package smartthings.brave.scala.config.http

import brave.http.{HttpRuleSampler, HttpSampler}
import com.typesafe.config.{Config, ConfigFactory}
import smartthings.brave.scala.config.Configurator
import scala.collection.JavaConverters._

import scala.util.Try


class HttpSamplerConfigurator extends Configurator[HttpSampler] {
  override def configure(config: Config): HttpSampler = {
    val mode = Try(config.getString("mode")).getOrElse("always")
    mode match {
      case "" | "always" => HttpSampler.TRACE_ID
      case "rule" => ruleSampler(config)
    }
  }

  private def ruleSampler(config: Config): HttpSampler = {
    val builder = HttpRuleSampler.newBuilder()

    config.getConfigList("rules").asScala.foreach( r => {
      val method = Try(r.getString("method")).toOption.orNull
      val path = Try(r.getString("path")).getOrElse("")
      val rate = Try(r.getNumber("rate")).map(_.floatValue()).getOrElse(0f)

      builder.addRule(method, path, rate)
    })

    builder.build()
  }
}
