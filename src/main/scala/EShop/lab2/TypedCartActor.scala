package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, scaladsl}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.OrderManager.ConfirmCheckoutStarted

import java.beans.BeanInfo

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart]) extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(ctx))
      case GetItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
      case _ => Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case AddItem(item) =>
        timer.cancel()
        nonEmpty(cart.addItem(item), scheduleTimer(ctx))
      case RemoveItem(item) =>
        if (cart.contains(item)) {
          val newCart = cart.removeItem(item)
          if (newCart.size == 0) {
            timer.cancel()
            empty
          } else {
            timer.cancel()
            nonEmpty(newCart, scheduleTimer(ctx))
          }
        } else
          Behaviors.same
      case GetItems(sender) =>
        sender ! cart
        Behaviors.same

      case ExpireCart =>
        empty

      case StartCheckout(orderManagerRef) =>
        timer.cancel()
        val checkoutActor = ctx.spawn(new TypedCheckout(ctx.self).start, "CheckoutActor")
        checkoutActor ! TypedCheckout.StartCheckout
        orderManagerRef ! ConfirmCheckoutStarted(checkoutActor)
        inCheckout(cart)

      case _ => Behaviors.same
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case ConfirmCheckoutClosed =>
        empty
      case ConfirmCheckoutCancelled =>
        nonEmpty(cart, scheduleTimer(ctx))
      case _ => Behaviors.same
    }
  )

}
