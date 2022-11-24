package EShop.lab6

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object ProductCatalogClusterNode extends App {
  private val productCatalogInstances = 3
  private val config                  = ConfigFactory.load()

  val system = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { ctx =>
      for (i <- 1 to productCatalogInstances)
        yield ctx.spawn(ProductCatalog(new SearchService()), s"pc$i")
      Behaviors.same
    },
    "ProductCatalog",
    config.getConfig(Try(args(0)).getOrElse("seed-node1")).withFallback(config)
  )

  Await.ready(system.whenTerminated, Duration.Inf)
}
