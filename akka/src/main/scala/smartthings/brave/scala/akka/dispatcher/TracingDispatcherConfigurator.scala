package smartthings.brave.scala.akka.dispatcher

import java.util.concurrent.{ExecutorService, ThreadFactory, TimeUnit}

import akka.dispatch._
import brave.propagation.CurrentTraceContext
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

object Helpers {
  final implicit class ConfigOps(val config: Config) extends AnyVal {
    def getMillisDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.MILLISECONDS)

    def getNanosDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.NANOSECONDS)

    private def getDuration(path: String, unit: TimeUnit): FiniteDuration =
      Duration(config.getDuration(path, unit), unit)
  }
}

/**
  * Same as akka.dispatch.DispatcherConfigurator except it wraps the ExecutorService
  * with trace context propagation that uses a shared ThreadLocal instance for storage.
  *
  * @param config
  * @param prerequisites
  */
class TracingDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites) extends MessageDispatcherConfigurator(config, prerequisites) {

  import Helpers.ConfigOps

  private val instance = new Dispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    config.getNanosDuration("throughput-deadline-time"),
    configureExecutor(),
    config.getMillisDuration("shutdown-timeout"))

  /**
    * Returns the same dispatcher instance for each invocation
    */
  override def dispatcher(): MessageDispatcher = instance

  override def configureExecutor(): ExecutorServiceConfigurator = {
    new DelegatingExecutorServiceConfigurator(super.configureExecutor(), config, prerequisites)
  }

  private class DelegatingExecutorServiceConfigurator(delegate: ExecutorServiceConfigurator, config: Config, prerequisites: DispatcherPrerequisites) extends ExecutorServiceConfigurator(config, prerequisites) {
    override def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
      new DelegatingExecutorServiceFactory(delegate.createExecutorServiceFactory(id, threadFactory))
    }
  }

  private class DelegatingExecutorServiceFactory(delegate: ExecutorServiceFactory) extends ExecutorServiceFactory {
    override def createExecutorService: ExecutorService = {
      CurrentTraceContext.Default.create().executorService(delegate.createExecutorService)
    }
  }
}
