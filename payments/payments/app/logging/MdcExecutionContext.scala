package logging

import scala.concurrent.{Future, ExecutionContextExecutor, ExecutionContext}
import org.slf4j.MDC
import play.api.mvc.{Result, SimpleResult, Request, ActionBuilder}


object MdcExecutionContext {
  object Implicit {
    implicit def defaultContext: ExecutionContext = fromThread
  }

  def fromThread: ExecutionContext = new MdcExecutionContext(MDC.getCopyOfContextMap, play.api.libs.concurrent.Execution.defaultContext)
}

class MdcExecutionContext(context: java.util.Map[_, _], parent: ExecutionContext) extends ExecutionContextExecutor {
  def execute(runnable: Runnable): Unit = parent.execute(new Runnable {
    def run() {
      val previous = MDC.getCopyOfContextMap
      if (context == null) {
        MDC.clear()
      } else {
        MDC.setContextMap(context)
      }
      try {
        runnable.run()
      } finally {
        if (previous != null)
          MDC.setContextMap(previous)
      }
    }
  })

  def reportFailure(t: Throwable): Unit = parent.reportFailure(t)
}

object MdcAction extends ActionBuilder[Request] {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = block(request)
  override def executionContext: ExecutionContext = MdcExecutionContext.fromThread
}
