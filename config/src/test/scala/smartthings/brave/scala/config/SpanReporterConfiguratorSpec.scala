package smartthings.brave.scala.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}
import zipkin2.Span
import zipkin2.reporter.Reporter

class SpanReporterConfiguratorSpec extends FunSpec with Matchers {

  describe("SpanReporterConfigurator") {

    it("should create console reporter") {
      val config = ConfigFactory.load("base-span-reporters.conf")

      val reporter = Configurator[Reporter[Span]](config.getConfig("brave.tracing"), "console-test", _ => null)

      reporter shouldBe Reporter.CONSOLE
    }

    it("should create noop reporter") {
      val config = ConfigFactory.load("base-span-reporters.conf")

      val reporter = Configurator[Reporter[Span]](config.getConfig("brave.tracing"), "noop-test", _ => null)

      reporter shouldBe Reporter.NOOP
    }

  }

}
