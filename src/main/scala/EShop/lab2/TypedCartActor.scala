package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, scaladsl}

import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._


object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart]) extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)

}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  var checkoutEventMapper: ActorRef[TypedCheckout.Event] = null


  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = Behaviors.setup { ctx =>
    checkoutEventMapper =
      ctx.messageAdapter {
        case TypedCheckout.CheckOutClosed => ConfirmCheckoutClosed
      }
    empty
  }

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
        val checkoutActor = ctx.spawn(new TypedCheckout(checkoutEventMapper).start, "CheckoutActor")
        checkoutActor ! TypedCheckout.StartCheckout
        orderManagerRef ! CheckoutStarted(checkoutActor)
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
