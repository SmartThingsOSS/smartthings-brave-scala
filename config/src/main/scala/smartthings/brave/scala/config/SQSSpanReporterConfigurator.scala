package smartthings.brave.scala.config

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.typesafe.config.Config
import zipkin2.Span
import zipkin2.codec.Encoding
import zipkin2.reporter.sqs.SQSSender
import zipkin2.reporter.{AsyncReporter, Reporter}

class SQSSpanReporterConfigurator extends Configurator[Reporter[Span]] {
  override def configure(config: Config): Reporter[Span] = {

    val credentialsProvider = Configurator(config, "aws-credentials-provider", _ => DefaultAWSCredentialsProviderChain.getInstance())

    val builder = SQSSender.newBuilder()
      .messageMaxBytes(messageMaxBytes(config.getNumber("message-max-bytes")))
      .queueUrl(queueUrl(config.getString("queue-url")))
      .encoding(encoding(config.getString("encoding")))
      .credentialsProvider(credentialsProvider)

    val endpointConfiguration = Configurator[EndpointConfiguration](config, "aws-endpoint-configuration", _ => null)
    if (endpointConfiguration != null) {
      builder.endpointConfiguration(endpointConfiguration)
    }

    AsyncReporter.create(builder.build())
  }

  private def queueUrl(value: String): String = {
    value match {
      case null | "" => throw new NullPointerException("queue-url == null")
      case _ => value
    }
  }

  private def messageMaxBytes(value: Number): Int = {
    value match {
      case null => 256 * 1024
      case _ => value.intValue()
    }
  }

  private def encoding(kind: String): Encoding = {
    kind.toUpperCase match {
      case "JSON" => Encoding.JSON
      case "THRIFT" => Encoding.THRIFT
      case "PROTO" => Encoding.PROTO3
      case _ => Encoding.JSON
    }
  }
}
