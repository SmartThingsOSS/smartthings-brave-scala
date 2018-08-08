package smartthings.brave.scala.config

import brave.ErrorParser
import brave.sampler.Sampler
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}

class TracingConfiguratorSpec extends FunSpec with Matchers {

  describe("TracingConfigurator") {

    it("should instantiate with Brave default settings") {
      val config = ConfigFactory.load("brave-defaults.conf").getConfig("brave.tracing")

      val tracing = TracingConfigurator(config)

      tracing.sampler() shouldBe Sampler.ALWAYS_SAMPLE
      tracing.isNoop shouldBe false
      tracing.errorParser() shouldBe ErrorParser.NOOP
    }

    it("should create single instance") {
      val config = ConfigFactory.load("brave-defaults.conf").getConfig("brave.tracing")

      val tracing = TracingConfigurator(config)

      TracingConfigurator(config) shouldEqual tracing
    }

  }

}
