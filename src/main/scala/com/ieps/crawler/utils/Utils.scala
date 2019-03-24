package com.ieps.crawler.utils

import com.typesafe.scalalogging.StrictLogging

import scala.reflect.{ClassTag, classTag}
import scala.util.Random

object Utils extends StrictLogging {
  @annotation.tailrec
  def retry[T](n: Int)(fn: => T): util.Try[T] = {
    util.Try { fn } match {
      case util.Failure(_) if n > 1 => retry(n - 1)(fn)
      case fn => fn
    }
  }

  /**
    * Retries function default (10) number of times with exponential backoff, in case of an exception.
    * @param f function to be retried
    * @tparam A return type of f
    * @return returns result of f
    */
  def retryWithBackoff[A](f: => A): A =
    retryWithBackoff()(f)

  /**
    * Retries function retryCount times with exponential backoff, in case of an exception.
    * @param count number of times to be retried
    * @param logRetry logging failures
    * @param f function to be retried
    * @tparam A return type of f
    * @return returns result of f
    */
  def retryWithBackoff[A](count: Int = 10, logRetry: Boolean = false)(f: => A): A =
    retryWithBackoffWhen[Exception, A](count, logRetry)(f)

  /**
    * Retries the function in case of an expected Exception E. Useful for retrying operations where
    * a specific Exception is expected (e.g. throughput exceptions).
    * @param count number of times to be retried
    * @param logRetry logging failures
    * @param f function to be retried
    * @tparam E Exception type that should be retried.
    * @tparam A return type of f
    * @return returns result of f
    */
  def retryWithBackoffWhen[E <: Exception: ClassTag, A](count: Int = 10, logRetry: Boolean = false, onException: () => Unit = () => {})(f: => A): A = {
    def go(n: Int): A = {
      try {
        f
      } catch {
        case e if classTag[E].runtimeClass.isInstance(e) && n < count => {
          onException()
          if (logRetry) logger.warn(s"Attempt $n/$count failed with exception.", e)
          Thread.sleep((1 << n) * 10 + Random.nextInt(100))
          go(n + 1)
        }
      }
    }
    go(0)
  }
}
