package smartthings.brave.scala.config

import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}

class AWSCredentialsProviderConfiguratorSpec extends FunSpec with Matchers {

  describe("AWSCredentialsProviderConfigurator") {

    it("should create a DefaultAWSCredentialsProviderChain when missing static credentials") {
      val config = ConfigFactory.load("aws-credentials.conf").getConfig("credentials")

      val provider = Configurator[AWSCredentialsProvider](config, "default-credentials", _ => DefaultAWSCredentialsProviderChain.getInstance())

      provider.isInstanceOf[DefaultAWSCredentialsProviderChain] shouldBe true
    }

    it("should create static credentials provider") {

      val config = ConfigFactory.load("aws-credentials.conf").getConfig("credentials")

      val provider = Configurator[AWSCredentialsProvider](config, "static-credentials", _ => DefaultAWSCredentialsProviderChain.getInstance())

      provider.getCredentials.getAWSAccessKeyId shouldBe "test"
      provider.getCredentials.getAWSSecretKey shouldBe "test"

    }

    it("should create an STS provider when a role arn is set") {
      val config = ConfigFactory.load("aws-credentials.conf").getConfig("credentials")

      val provider = Configurator[AWSCredentialsProvider](config, "sts-credentials", _ => DefaultAWSCredentialsProviderChain.getInstance())

      provider.isInstanceOf[STSProfileCredentialsServiceProvider] shouldBe true
    }

  }

}
