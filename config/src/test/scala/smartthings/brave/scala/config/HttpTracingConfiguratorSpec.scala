package smartthings.brave.scala.config

import brave.ErrorParser
import brave.http.HttpSampler
import brave.sampler.Sampler
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}
import smartthings.brave.scala.config.http.HttpTracingConfigurator

class HttpTracingConfiguratorSpec extends FunSpec with Matchers {

  describe("HttpTracingConfigurator") {

    it("should instantiate with Brave default settings") {
      val config = ConfigFactory.load("brave-defaults.conf").getConfig("brave.tracing")

      val tracing = TracingConfigurator(config)

      val httpTracing = HttpTracingConfigurator(config, tracing)

      httpTracing.clientSampler() shouldBe HttpSampler.TRACE_ID
      httpTracing.serverSampler() shouldBe HttpSampler.TRACE_ID
      httpTracing.serverName() shouldBe ""

      tracing.sampler() shouldBe Sampler.ALWAYS_SAMPLE
      tracing.isNoop shouldBe false
      tracing.errorParser() shouldBe ErrorParser.NOOP
    }

  }

}
