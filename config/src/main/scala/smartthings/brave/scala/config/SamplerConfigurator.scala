package smartthings.brave.scala.config

import brave.sampler.{BoundarySampler, CountingSampler, Sampler}
import com.typesafe.config.Config

import scala.util.Try

class SamplerConfigurator extends Configurator[Sampler] {
  override def configure(config: Config): Sampler = {
    val mode = Try(config.getString("mode")).getOrElse("always")
    mode match {
      case null | "" | "always" | "on" => Sampler.ALWAYS_SAMPLE
      case "never" | "off" => Sampler.NEVER_SAMPLE
      case "counting" => {
        val rate = config.getNumber("rate")
        CountingSampler.create(rate.floatValue())
      }
      case "boundary" => {
        val rate = config.getNumber("rate")
        BoundarySampler.create(rate.floatValue())
      }
    }
  }
}
