package smartthings.brave.scala.akka.http.extension

import java.util.function.Supplier

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import brave.http.HttpTracing
import com.typesafe.config.Config
import smartthings.brave.scala.config.TracingConfigurator
import smartthings.brave.scala.config.http.HttpTracingConfigurator

object AkkaHttpTracingExtension extends ExtensionId[AkkaHttpTracingExtensionImpl] with ExtensionIdProvider {

  private[http] var httpTracingSupplier: Supplier[Option[HttpTracing]] = () => None

  override def createExtension(system: ExtendedActorSystem): AkkaHttpTracingExtensionImpl = {
    new AkkaHttpTracingExtensionImpl(system.settings.config, httpTracingSupplier)
  }

  override def lookup(): ExtensionId[_ <: Extension] = AkkaHttpTracingExtension

}

class AkkaHttpTracingExtensionImpl(config: Config, httpTracingSupplier: Supplier[Option[HttpTracing]]) extends Extension {

  lazy val httpTracing: HttpTracing = httpTracingSupplier.get().getOrElse {

    val tracing = TracingConfigurator(config.getConfig("brave.tracing"))

    HttpTracingConfigurator(config.getConfig("brave.tracing.http"), tracing)
  }

}
