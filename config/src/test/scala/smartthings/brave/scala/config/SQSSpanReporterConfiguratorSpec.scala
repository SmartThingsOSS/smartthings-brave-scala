package smartthings.brave.scala.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}
import zipkin2.Span
import zipkin2.reporter.AsyncReporter

import scala.util.Try

class SQSSpanReporterConfiguratorSpec extends FunSpec with Matchers {

  describe("SQSSpanReporterConfigurator") {

    it("should fail when a queue url is not specified") {
      val config = ConfigFactory.load()

      val reporter = Try(Configurator[AsyncReporter[Span]](config.getConfig("brave.tracing"), "sqs-span-reporter").orNull)

      reporter.isFailure shouldBe true
    }

    it("should create SQS span reporter from default config") {
      val config = ConfigFactory.load("sqs-reporter.conf")

      val reporter = Configurator[AsyncReporter[Span]](config.getConfig("brave.tracing"), "sqs-span-reporter")

      reporter.isDefined shouldBe true
    }

  }

}
