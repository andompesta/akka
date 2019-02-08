/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.actor.typed.javadsl;

import akka.actor.typed.*;
import akka.actor.typed.TypedActorContext;

import java.time.Duration;

import static akka.actor.typed.javadsl.Behaviors.*;

@SuppressWarnings("unused")
public class ActorCompile {

  interface MyMsg {}

  static class MyMsgA implements MyMsg {
    final ActorRef<String> replyTo;

    public MyMsgA(ActorRef<String> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static class MyMsgB implements MyMsg {
    final String greeting;

    public MyMsgB(String greeting) {
      this.greeting = greeting;
    }
  }

  Behavior<MyMsg> actor1 =
      Behaviors.receive((context, message) -> stopped(), (context, signal) -> same());
  Behavior<MyMsg> actor2 = Behaviors.receive((context, message) -> unhandled());
  Behavior<MyMsg> actor4 = empty();
  Behavior<MyMsg> actor5 = ignore();
  Behavior<MyMsg> actor6 =
      intercept(
          new BehaviorInterceptor<MyMsg, MyMsg>() {
            @Override
            public Behavior<MyMsg> aroundReceive(
                TypedActorContext<MyMsg> context, MyMsg message, ReceiveTarget<MyMsg> target) {
              return target.apply(context, message);
            }

            @Override
            public Behavior<MyMsg> aroundSignal(
                TypedActorContext<MyMsg> context, Signal signal, SignalTarget<MyMsg> target) {
              return target.apply(context, signal);
            }
          },
          actor5);
  Behavior<MyMsgA> actor7 = actor6.narrow();
  Behavior<MyMsg> actor8 =
      setup(
          context -> {
            final ActorRef<MyMsg> self = context.getSelf();
            return monitor(self, ignore());
          });
  Behavior<MyMsg> actor9 = widened(actor7, pf -> pf.match(MyMsgA.class, x -> x));
  Behavior<MyMsg> actor10 =
      Behaviors.receive((context, message) -> stopped(actor4), (context, signal) -> same());

  ActorSystem<MyMsg> system = ActorSystem.create(actor1, "Sys");

  {
    Behaviors.<MyMsg>receive(
        (context, message) -> {
          if (message instanceof MyMsgA) {
            return Behaviors.receive(
                (ctx2, msg2) -> {
                  if (msg2 instanceof MyMsgB) {
                    ((MyMsgA) message).replyTo.tell(((MyMsgB) msg2).greeting);

                    ActorRef<String> adapter =
                        ctx2.messageAdapter(String.class, s -> new MyMsgB(s.toUpperCase()));
                  }
                  return same();
                });
          } else return unhandled();
        });
  }

  {
    Behavior<MyMsg> b =
        Behaviors.withTimers(
            timers -> {
              timers.startPeriodicTimer("key", new MyMsgB("tick"), Duration.ofSeconds(1));
              return Behaviors.ignore();
            });
  }

  static class MyBehavior implements ExtensibleBehavior<MyMsg> {
    @Override
    public Behavior<MyMsg> receive(TypedActorContext<MyMsg> ctx, MyMsg msg) throws Exception {
      return Behaviors.same();
    }

    @Override
    public Behavior<MyMsg> receiveSignal(TypedActorContext<MyMsg> ctx, Signal msg)
        throws Exception {
      return Behaviors.same();
    }
  }

  // SupervisorStrategy
  {
    SupervisorStrategy strategy1 = SupervisorStrategy.restart();
    SupervisorStrategy strategy2 = SupervisorStrategy.restart().withLoggingEnabled(false);
    SupervisorStrategy strategy3 = SupervisorStrategy.resume();
    SupervisorStrategy strategy4 = SupervisorStrategy.restart().withLimit(3, Duration.ofSeconds(1));

    SupervisorStrategy strategy5 =
        SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(10), 0.1);

    BackoffSupervisorStrategy strategy6 =
        SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(10), 0.1);
    SupervisorStrategy strategy7 = strategy6.withResetBackoffAfter(Duration.ofSeconds(2));

    Behavior<MyMsg> behv =
        Behaviors.supervise(
                Behaviors.supervise(Behaviors.<MyMsg>ignore())
                    .onFailure(IllegalStateException.class, strategy6))
            .onFailure(RuntimeException.class, strategy1);
  }
}
