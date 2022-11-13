package EShop.lab5

import EShop.lab5.ProductCatalog.GetItems
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives.{_symbol2NR, complete, failWith, onSuccess, parameters, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

object ProductCatalogHttpServer {
  case class GetItems(brand: String, keywords: List[String])
  case class Response(products: List[ProductCatalog.Item])
}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit lazy val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
  implicit lazy val getItemsFormat = jsonFormat2(ProductCatalogHttpServer.GetItems)
  implicit lazy val itemFormat = jsonFormat5(ProductCatalog.Item)
  implicit lazy val responseFormat = jsonFormat1(ProductCatalogHttpServer.Response)
}

object ProductCatalogHttpServerApp extends App {
  val server = new ProductCatalogHttpServer()
  server.start(9000)
}

class ProductCatalogHttpServer extends ProductCatalogJsonSupport {
  implicit val config = ConfigFactory.load()
  implicit val actorSystem = ActorSystem[Nothing](
    Behaviors.empty,
    "ProductCatalog",
    config.getConfig("httpserver").withFallback(config)
  )
  implicit val timeout: Timeout = 3.second

  private val productCatalogActorSystem = ActorSystem[Nothing](Behaviors.empty, "ProductCatalog")
  implicit val scheduler = productCatalogActorSystem.scheduler

  def routes: Route = {
    path("catalog") {
      Directives.get {
        parameters(Symbol("brand"), Symbol("keywords").as[String].repeated) { (brand, keywords) =>
          val listingFuture = actorSystem.receptionist.ask(
            (ref: ActorRef[Receptionist.Listing]) => Receptionist.find(ProductCatalog.ProductCatalogServiceKey, ref)
          )
          onSuccess(listingFuture) {
            case ProductCatalog.ProductCatalogServiceKey.Listing(listing) =>
              try {
                val productCatalog = listing.head
                val items = productCatalog.ask(ref => GetItems(brand, keywords.toList, ref)).mapTo[ProductCatalog.Items]
                onSuccess(items) {
                  case ProductCatalog.Items(items) => complete(items)
                }
              } catch {
                case e: NoSuchElementException => failWith(e)
              }
          }
        }
      }
    }
  }

  def start(port: Int) = {
    Http().newServerAt("localhost", port).bind(routes)
    Await.ready(actorSystem.whenTerminated, Duration.Inf)
  }


}
