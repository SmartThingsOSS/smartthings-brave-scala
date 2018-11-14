package scala.concurrent

import java.util.concurrent.ExecutorService

import scala.concurrent.impl.ExecutionContextImpl

/**
  * Exposes package private ExecutorService factory so it can be reused
  * when setting up wrapped tracing context ExecutorServices.
  */
object DefaultExecutorServiceFactory {

  def createDefaultExecutorService(reporter: Throwable => Unit): ExecutorService =
    ExecutionContextImpl.createDefaultExecutorService(reporter)

}
