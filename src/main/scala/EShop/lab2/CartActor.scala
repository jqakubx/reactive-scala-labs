package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command
  case object GetItems                 extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props: Props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = ???

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case _ =>
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case ExpireCart =>
      context become empty
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val removeCart = cart.removeItem(item)
        if (removeCart.size == 0) {
          timer.cancel()
          context become empty
        } else {
          context become nonEmpty(removeCart, timer)
        }
      }
    case AddItem(item) =>
      val addCart = cart.addItem(item)
      context become nonEmpty(addCart, timer)
    case StartCheckout =>
      timer.cancel()
      context become inCheckout(cart)
    case _ =>
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      context become empty
    case _ =>
  }

}
