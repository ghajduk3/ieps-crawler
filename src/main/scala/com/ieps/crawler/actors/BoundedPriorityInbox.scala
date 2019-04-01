package com.ieps.crawler.actors

import java.util.concurrent.{BlockingQueue, TimeUnit}
import java.util.{Comparator, PriorityQueue}

import akka.AkkaException
import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch._
import akka.util.BoundedBlockingQueue

import scala.concurrent.duration.Duration

class MessageQueueAppendFailedException(message: String) extends AkkaException(message)

/*
 * Fail fast bounded mailbox that will throw an exception when queue limit is reached.
 *
 * Default Akka BoundedMailbox doesn't throw an exception when queue limit is reached. Instead it notifies the receiver with DeadLetter.
 * https://github.com/akka/akka/blob/release-2.3/akka-actor/src/main/scala/akka/dispatch/Mailbox.scala#L471-L472
 */
private[ieps] trait ProducesPushTimeoutSemanticsMailbox {
  def pushTimeOut: Duration
}

trait CustomBoundedQueueBasedMessageQueue extends BoundedQueueBasedMessageQueue {
  override def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    if (pushTimeOut.length >= 0) {
      if (!queue.offer(handle, pushTimeOut.length, pushTimeOut.unit))
        throw new MessageQueueAppendFailedException("Limit for mailbox reached.")
    } else queue put handle
}

/**
  * BoundedPriorityMailbox is a bounded mailbox that allows for prioritization of its contents.
  * Extend this class and provide the Comparator in the constructor.
  */
case class BoundedPriorityMailbox(cmp: Comparator[Envelope], capacity: Int, override val pushTimeOut: Duration)
  extends MailboxType with ProducesMessageQueue[BoundedPriorityMailbox.MessageQueue]
    with ProducesPushTimeoutSemanticsMailbox {

  def this(cmp: Comparator[Envelope], settings: ActorSystem.Settings, config: com.typesafe.config.Config) =
    this(cmp, config.getInt("mailbox-capacity"), Duration(config.getDuration("mailbox-push-timeout-time", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS))

  if (capacity < 0) throw new IllegalArgumentException("The capacity for BoundedMailbox can not be negative")
  if (pushTimeOut eq null) throw new IllegalArgumentException("The push time-out for BoundedMailbox can not be null")

  override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new BoundedPriorityMailbox.MessageQueue(capacity, cmp, pushTimeOut)
}

object BoundedPriorityMailbox {
  class MessageQueue(capacity: Int, cmp: Comparator[Envelope], val pushTimeOut: Duration)
    extends BoundedBlockingQueue[Envelope](capacity, new PriorityQueue[Envelope](11, cmp))
      with CustomBoundedQueueBasedMessageQueue {
    final def queue: BlockingQueue[Envelope] = this
  }
}