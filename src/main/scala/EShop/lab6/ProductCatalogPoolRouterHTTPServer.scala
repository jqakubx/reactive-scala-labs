package EShop.lab6

import EShop.lab5.ProductCatalog.GetItems
import EShop.lab5.{ProductCatalog, SearchService}
import akka.Done
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives.{_symbol2NR, complete, onSuccess, parameters, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat  = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat = jsonFormat1(ProductCatalog.Items)
}

class PCHttpServer extends ProductCatalogJsonSupport {
  implicit val system: ActorSystem[Nothing]               = ActorSystem(Behaviors.empty, "ProductCatalog")
  implicit val scheduler                                  = system.scheduler
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  val productCatalogs: ActorRef[ProductCatalog.Query] = {
    val searchService = new SearchService()
    system.systemActorOf(Routers.pool(3)(ProductCatalog(searchService)), "productCatalogs")
  }

  implicit val timeout: Timeout = 5.seconds
  Thread.sleep(10000)

  def routes: Route = {
    path("products") {
      parameters(Symbol("brand").optional, Symbol("keywords").optional) { (brand, keywords) =>
        Directives.get {
          val keywordsList = keywords.getOrElse("").split(",").toList
          val future =
            productCatalogs.ask(ref => GetItems(brand.getOrElse(""), keywordsList, ref)).mapTo[ProductCatalog.Items]
          onSuccess(future) { items: ProductCatalog.Items =>
            println(items)
            complete(items)
          }
        }
      }
    }
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

object ProductCatalogPoolRouterHTTPServer extends App {
  new PCHttpServer().start(9000)
}
