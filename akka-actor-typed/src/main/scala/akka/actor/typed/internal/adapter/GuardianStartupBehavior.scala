/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.actor.typed.internal.adapter

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object GuardianStartupBehavior {
  case object Start
}

/**
 * INTERNAL API
 *
 * Messages to the user provided guardian must be deferred while the actor system is starting up, this
 * behavior delays starting up the user provided behavior until the Start command is delivered from the actor
 * system and we know that the bootstrap is completed and that we can access the actor context
 */
@InternalApi
private[akka] final class GuardianStartupBehavior[T](val guardianBehavior: Behavior[T]) extends AbstractBehavior[Any] {

  import GuardianStartupBehavior.Start

  private val stash = StashBuffer[T](256) // we should typically not see many messages here, worth configuring?

  override def onMessage(msg: Any): Behavior[Any] =
    msg match {
      case Start =>
        // ctx is not available initially so we cannot use it until here
        Behaviors.setup(ctx =>
          stash.unstashAll(ctx.asInstanceOf[ActorContext[T]], guardianBehavior).asInstanceOf[Behavior[Any]])
      case other =>
        stash.stash(other.asInstanceOf[T])
        this
    }

}
