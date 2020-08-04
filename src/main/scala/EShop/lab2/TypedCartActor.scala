package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (ctx, msg) => msg match {
      case AddItem(item) =>
        nonEmpty(Cart.empty.addItem(item), scheduleTimer(ctx))
      case _ => Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (ctx, msg) => msg match {
      case AddItem(item) =>
        nonEmpty(cart.addItem(item), timer)
      case RemoveItem(item) =>
        if (cart.contains(item)) {
          val newCart = cart.removeItem(item)
          if (newCart.size == 0) {
            timer.cancel()
            empty
          } else {
          nonEmpty(newCart, timer)
          }
        } else
          Behaviors.same
      case ExpireCart =>
        empty
      case StartCheckout =>
        timer.cancel()
        inCheckout(cart)
      case _ => Behaviors.same
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (ctx, msg) => msg match {
      case ConfirmCheckoutClosed =>
        empty
      case ConfirmCheckoutCancelled =>
        nonEmpty(cart, scheduleTimer(ctx))
    }
  )

}
