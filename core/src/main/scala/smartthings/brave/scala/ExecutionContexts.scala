package smartthings.brave.scala

import java.util.concurrent.{Executor, ExecutorService}

import brave.propagation.CurrentTraceContext

import scala.concurrent.{DefaultExecutorServiceFactory, ExecutionContext, ExecutionContextExecutor, ExecutionContextExecutorService}

object ExecutionContexts {

  def fromExecutor(currentTraceContext: CurrentTraceContext, executor: Executor, reporter: Throwable => Unit): ExecutionContextExecutor = {
    ExecutionContext.fromExecutor(currentTraceContext.executor(executor), reporter)
  }

  def fromExecutorService(currentTraceContext: CurrentTraceContext, executorService: ExecutorService, reporter: Throwable => Unit): ExecutionContextExecutorService = {
    ExecutionContext.fromExecutorService(currentTraceContext.executorService(executorService), reporter)
  }

  def fromExecutorService(currentTraceContext: CurrentTraceContext, reporter: Throwable => Unit): ExecutionContextExecutorService = {
    val default = DefaultExecutorServiceFactory.createDefaultExecutorService(reporter)
    fromExecutorService(currentTraceContext, default, ExecutionContext.defaultReporter)
  }

  def fromDefaultExecutorService(currentTraceContext: CurrentTraceContext): ExecutionContextExecutorService = {
    val default = DefaultExecutorServiceFactory.createDefaultExecutorService(ExecutionContext.defaultReporter)
    fromExecutorService(currentTraceContext, default, ExecutionContext.defaultReporter)
  }

}
