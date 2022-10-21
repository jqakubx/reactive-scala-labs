package EShop.lab2

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ShopTest extends App {

  val system = ActorSystem("ShopSystem")

//  val actorCheckout = system.actorOf(Props[Checkout], "mainCheckout")
//  actorCheckout ! Checkout.StartCheckout
//  actorCheckout ! Checkout.CancelCheckout
//  Await.result(system.whenTerminated, Duration.Inf)


  val actor = system.actorOf(Props[CartActor], "main")
  actor ! CartActor.AddItem("first")
  actor ! CartActor.RemoveItem("first")
  actor ! CartActor.StartCheckout
  actor ! CartActor.ConfirmCheckoutClosed
  Await.result(system.whenTerminated, Duration.Inf)


}
