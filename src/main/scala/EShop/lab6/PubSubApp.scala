package EShop.lab6

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object PubSubActor {
  sealed trait Event
  case class GetStats(actorRef: ActorRef[Ack]) extends Event
  case class AddStats(actorRef: String)        extends Event

  sealed trait Ack
  case class Stats(stats: Map[String, Int]) extends Ack

  val StatsActorServiceKey: ServiceKey[Event] = ServiceKey[Event]("StatsActorKey")

  def apply(): Behavior[Event] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist.register(StatsActorServiceKey, context.self)
    val topic = context.spawn(Topic[AddStats]("product-catalog-topic"), "ProductCatalogTopicStatsActor")
    topic ! Topic.Subscribe(context.self)

    val stats: Map[String, Int] = Map.empty
    count(stats)
  }

  def count(stats: Map[String, Int]): Behavior[Event] =
    Behaviors.receiveMessage {
      case AddStats(actorRef) =>
        val actorName   = actorRef.split("#")(0)
        val queryAmount = stats.getOrElse(actorName, 0) + 1
        val newStats    = stats + (actorName -> queryAmount)
        count(newStats)
      case GetStats(actorRef) =>
        actorRef ! Stats(stats)
        Behaviors.same
    }
}

object PubSubApp extends App {
  private val config = ConfigFactory.load()
  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(PubSubActor(), "StatsActor")
      Behaviors.same
    },
    "ProductCatalog",
    config
      .getConfig("stats-node")
      .withFallback(config)
  )

  Await.ready(system.whenTerminated, Duration.Inf)
}
