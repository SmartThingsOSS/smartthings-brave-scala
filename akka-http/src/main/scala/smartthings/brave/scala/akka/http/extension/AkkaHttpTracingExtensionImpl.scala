package smartthings.brave.scala.akka.http.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import brave.http.{HttpSampler, HttpTracing}
import com.typesafe.config.Config
import smartthings.brave.scala.config.TracingConfigurator

object AkkaHttpTracingExtension extends ExtensionId[AkkaHttpTracingExtensionImpl] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): AkkaHttpTracingExtensionImpl =
    new AkkaHttpTracingExtensionImpl(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaHttpTracingExtension

}

class AkkaHttpTracingExtensionImpl(config: Config) extends Extension {

  lazy val httpTracing: HttpTracing = {

    val tracing = TracingConfigurator(config.getConfig("brave.tracing"))

    // TODO read configuration
    val clientSampler = HttpSampler.TRACE_ID
    val serverSampler = HttpSampler.TRACE_ID

    HttpTracing.newBuilder(tracing)
      .clientSampler(clientSampler)
      .serverSampler(serverSampler)
      .build()
  }

}
