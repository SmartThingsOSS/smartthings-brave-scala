package smartthings.brave.scala.config

import com.typesafe.config.Config
import zipkin2.Span
import zipkin2.reporter.Reporter

class SpanReporterConfigurator[C >: Reporter[Span]] extends Configurator[C] {
  override def configure(config: Config): C = {
    Option(config.getString("mode")) match {
      case Some("") | Some("console") => Reporter.CONSOLE
      case _ => Reporter.NOOP
    }
  }
}
