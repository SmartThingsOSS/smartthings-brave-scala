package smartthings.brave.scala.config

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.typesafe.config.Config
import zipkin2.Span
import zipkin2.codec.Encoding
import zipkin2.reporter.sqs.SQSSender
import zipkin2.reporter.{AsyncReporter, Reporter}

class SQSSpanReporterConfigurator extends Configurator[Reporter[Span]] {
  override def configure(config: Config): Reporter[Span] = {

    val credentialsProvider: AWSCredentialsProvider = Configurator(config, "aws-credentials-provider")
      .getOrElse(DefaultAWSCredentialsProviderChain.getInstance())

    val sender = for {
      step0 <- Some(SQSSender.newBuilder()
        .messageMaxBytes(messageMaxBytes(config.getNumber("message-max-bytes")))
        .queueUrl(queueUrl(config.getString("queue-url")))
        .encoding(encoding(config.getString("encoding")))
        .credentialsProvider(credentialsProvider))
      step1 <- Configurator[EndpointConfiguration](config, "aws-endpoint-configuration")
        .map(step0.endpointConfiguration).orElse(Some(step0))
    } yield step1.build()

    AsyncReporter.create(sender.get)
  }

  private def queueUrl(value: String): String = {
    Option(value) match {
      case None | Some("") => throw new NullPointerException("queue-url == null")
      case _ => value
    }
  }

  private def messageMaxBytes(value: Number): Int = {
    Option(value) match {
      case None => 256 * 1024
      case Some(v) => v.intValue()
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
