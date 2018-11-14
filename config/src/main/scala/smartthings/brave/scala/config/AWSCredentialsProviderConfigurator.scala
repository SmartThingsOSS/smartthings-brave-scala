package smartthings.brave.scala.config

import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.profile.internal.securitytoken.{RoleInfo, STSProfileCredentialsServiceProvider}
import com.typesafe.config.Config

import scala.util.{Success, Try}


class AWSCredentialsProviderConfigurator[C >: AWSCredentialsProvider] extends Configurator[C] {
  override def configure(config: Config): C = {
    val provider = basicCredentialsProvider(Try(config.getString("access-key-id")), Try(config.getString("secret-access-key"))) match {
      case None => DefaultAWSCredentialsProviderChain.getInstance()
      case Some(basicProvider) => {
        new AWSCredentialsProviderChain(
          basicProvider,
          new EnvironmentVariableCredentialsProvider,
          new SystemPropertiesCredentialsProvider,
          new ProfileCredentialsProvider(Try(config.getString("profile")).toOption.orNull),
          new EC2ContainerCredentialsProviderWrapper)
      }
    }

    val stsRoleArn = Try(config.getString("sts-role-arn"))

    stsRoleArn match {
      case Success(roleArn) => {
        val roleInfo = new RoleInfo()
          .withExternalId("zipkin")
          .withRoleArn(roleArn)
          .withRoleSessionName("zipkin")
          .withLongLivedCredentialsProvider(provider)

        new STSProfileCredentialsServiceProvider(roleInfo)
      }
      case _ => provider
    }
  }

  private def basicCredentialsProvider(accessKey: Try[String], secretKey: Try[String]): Option[AWSCredentialsProvider] = {
    (accessKey, secretKey) match {
      case (Success(a), Success(s)) => Some(new AWSStaticCredentialsProvider(new BasicAWSCredentials(a, s)))
      case _ => None
    }
  }
}
