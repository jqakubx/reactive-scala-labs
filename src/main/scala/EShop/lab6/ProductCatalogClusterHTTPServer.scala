package EShop.lab6

import EShop.lab5.ProductCatalog.{GetItems, ProductCatalogServiceKey}
import EShop.lab5.{ProductCatalog, SearchService}
import EShop.lab6.PubSubActor.GetStats
import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives.{_symbol2NR, complete, onSuccess, parameter, parameters, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait ProductCatalogJsonSupportCluster extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat                                         = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat                                        = jsonFormat1(ProductCatalog.Items)
  implicit val statsJsonFormat: RootJsonFormat[PubSubActor.Stats] = jsonFormat1(PubSubActor.Stats)
}

class PCHttpServerCluster extends ProductCatalogJsonSupportCluster {
  implicit val system: ActorSystem[Nothing]               = ActorSystem(Behaviors.empty, "ProductCatalog")
  implicit val scheduler                                  = system.scheduler
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  val productCatalogs: ActorRef[ProductCatalog.Query] = system.systemActorOf(
    Routers.group(ProductCatalogServiceKey),
    "productCatalogsRouter"
  )

  implicit val timeout: Timeout = 5.seconds
  Thread.sleep(10000)

  def routes: Route = {
    Directives.concat(
      path("products") {
        parameters(Symbol("brand").optional, Symbol("keywords").optional) { (brand, keywords) =>
          Directives.get {
            val keywordsAsList = keywords.getOrElse("").split(",").toList
            val future =
              productCatalogs.ask(ref => GetItems(brand.getOrElse(""), keywordsAsList, ref)).mapTo[ProductCatalog.Items]
            onSuccess(future) { items: ProductCatalog.Items =>
              println(items)
              complete(items)
            }
          }
        }
      },
      path("stats") {
        Directives.get {
          val listingFuture: Future[Receptionist.Listing] =
            system.receptionist.ask((ref: ActorRef[Receptionist.Listing]) =>
              Receptionist.find(PubSubActor.StatsActorServiceKey, ref)
            )
          onSuccess(listingFuture) { case PubSubActor.StatsActorServiceKey.Listing(listing) =>
            val statsActor = listing.head
            val future     = statsActor.ask(ref => GetStats(ref)).mapTo[PubSubActor.Stats]
            onSuccess(future) { stats: PubSubActor.Stats =>
              complete(stats)
            }
          }
        }
      }
    )
  }

  def start(port: Int): Future[Done] = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)

    bindingFuture.onComplete {
      case Success(bound) =>
        system.log.info(
          s"Server now online. Please navigate to http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/products"
        )
        scala.io.StdIn.readLine
        bindingFuture.flatMap(_.unbind).onComplete(_ => system.terminate)
      case Failure(e) =>
        e.printStackTrace()
        system.terminate()
    }

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogClusterHTTPServer extends App {
  new PCHttpServerCluster().start(args(0).toInt)
}
