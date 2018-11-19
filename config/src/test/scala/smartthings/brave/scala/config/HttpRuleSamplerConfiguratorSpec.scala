package smartthings.brave.scala.config

import brave.http.{HttpAdapter, HttpRuleSampler, HttpSampler, HttpServerAdapter}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}

class HttpRuleSamplerConfiguratorSpec extends FunSpec with Matchers {

  describe("HttpRuleSamplerConfigurator") {

    val adapter = new MockAdapter()

    it("should create client rule sampler") {
      val config = ConfigFactory.load("http-rule-sampler.conf")

      val sampler = Configurator[HttpSampler](config.getConfig("brave.tracing.http"), "client-sampler").orNull

      sampler.isInstanceOf[HttpRuleSampler] shouldBe true

      val ruleSampler = sampler.asInstanceOf[HttpRuleSampler]

      ruleSampler.trySample(adapter, MockRequest("GET", "/foo", Map.empty)) shouldBe true
    }

    it("should create server rule sampler") {
      val config = ConfigFactory.load("http-rule-sampler.conf")

      val sampler = Configurator[HttpSampler](config.getConfig("brave.tracing.http"), "server-sampler").orNull

      sampler.isInstanceOf[HttpRuleSampler] shouldBe true

      val ruleSampler = sampler.asInstanceOf[HttpRuleSampler]

      ruleSampler.trySample(adapter, MockRequest("POST", "/bar", Map.empty)) shouldBe true
    }

  }

  class MockAdapter extends HttpServerAdapter[MockRequest, MockResponse] {

    override def method(request: MockRequest): String = request.method

    override def url(request: MockRequest): String = request.url

    override def requestHeader(request: MockRequest, name: String): String = request.headers.get(name).orNull

    override def statusCode(response: MockResponse): Integer = response.status
  }

  case class MockRequest(method: String, url: String, headers: Map[String, String])
  case class MockResponse(status: Int)

}
