package smartthings.brave.scala.config

import com.typesafe.config.Config

object Configurator {

  def apply[T](config: Config, key: String, default: Config => T): T = {
    getInstance(config, key, default)
  }

  private def getInstance[T](config: Config, key: String, default: Config => T): T = {
    if (config.hasPath(key)) {
      val configSection = config.getConfig(key)
      val kind = configSection.getString("type")
      if (kind == null || kind == "" || kind == "default") {
        default(configSection)
      } else if (config.hasPath(kind)) {
        getInstance(config, kind, default)
      } else {
        Class.forName(kind)
          .getConstructor()
          .newInstance()
          .asInstanceOf[Configurator[T]]
          .configure(configSection)
      }
    } else {
      default(config)
    }
  }

}

trait Configurator[+T] {

  def configure(config: Config): T

}
