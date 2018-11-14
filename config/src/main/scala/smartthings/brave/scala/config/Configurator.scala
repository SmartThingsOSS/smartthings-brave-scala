package smartthings.brave.scala.config

import com.typesafe.config.Config

object Configurator {

  def apply[T](config: Config, key: String): Option[T] = {
    getInstance(config, key)
  }

  private def getInstance[T](config: Config, key: String): Option[T] = {
    if (config.hasPath(key)) {
      val configSection = config.getConfig(key)
      val kind = configSection.getString("type")
      if (kind == null || kind == "" || kind == "default") {
        None
//        default(configSection)
      } else if (config.hasPath(kind)) {
        getInstance(config, kind)
      } else {
        Option(Class.forName(kind)
          .getConstructor()
          .newInstance()
          .asInstanceOf[Configurator[T]]
          .configure(configSection))
      }
    } else {
      None
    }
  }

}

trait Configurator[+T] {

  def configure(config: Config): T

}
