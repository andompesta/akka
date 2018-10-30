/*
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.impl

import java.util.concurrent.atomic.AtomicBoolean

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.testkit.StreamSpec
import akka.stream.testkit.Utils.TE
import akka.testkit.TestProbe
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped

class FanoutProcessorSpec extends StreamSpec {

  implicit val mat = ActorMaterializer()

  "The FanoutProcessor" must {

    // #25634
    "not leak running actors on failed upstream without subscription" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      promise.failure(TE("boom"))
    }

    // #25634
    "not leak running actors on failed upstream with one subscription" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      val completed = Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)
      val boom = TE("boom")
      promise.failure(boom)
    }

    // #25634
    "not leak running actors on failed upstream with multiple subscriptions" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val completed1 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val completed2 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val boom = TE("boom")
      promise.failure(boom)
    }

    "not leak running actors on completed upstream with one subscription" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      val completed = Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)

      promise.success(Some(1))

      probe.expectTerminated(publisherRef)
      // would throw if not completed
      completed.futureValue
    }

    // #25634
    "not leak running actors on completed upstream with multiple subscriptions" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      val completed1 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val completed2 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)
      promise.success(Some(1))

      probe.expectTerminated(publisherRef)
      // would throw if not completed
      completed1.futureValue
      completed2.futureValue
    }

    "not leak running actors on failed downstream" in assertAllStagesStopped {
      val probe = TestProbe()
      val (promise, publisher) = Source.repeat(1).toMat(Sink.asPublisher(true))(Keep.both).run()
      Source.fromPublisher(publisher).map(elem ⇒ throw TE("boom")).runWith(Sink.ignore)
    }

  }

}
