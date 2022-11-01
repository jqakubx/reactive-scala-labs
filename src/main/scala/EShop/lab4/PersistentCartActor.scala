package EShop.lab4

import EShop.lab2.TypedCheckout
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  var checkoutEventMapper: ActorRef[TypedCheckout.Event] = null

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    checkoutEventMapper = context.messageAdapter {
      case TypedCheckout.CheckOutClosed => ConfirmCheckoutClosed
    }
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item))
          case _ => Effect.none
        }

      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item))
          case RemoveItem(item) =>
            if (cart.contains(item)) {
              if (cart.size == 1)
                Effect.persist(CartEmptied)
              else
                Effect.persist(ItemRemoved(item))
            } else {
              Effect.none
            }
          case ExpireCart => Effect.persist(CartExpired)
          case StartCheckout(orderManagerRef) =>
            val checkoutActor = context.spawn(new TypedCheckout(checkoutEventMapper).start, "CheckoutActor")
            Effect.persist(CheckoutStarted(checkoutActor))
              .thenRun {
                _ =>
                  checkoutActor ! TypedCheckout.StartCheckout
                  orderManagerRef ! CheckoutStarted(checkoutActor)
              }
          case _ => Effect.none

        }

      case InCheckout(_) =>
        command match {
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)
          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted(_)        => InCheckout(state.cart)
      case ItemAdded(item)           => NonEmpty(state.cart.addItem(item), scheduleTimer(context))
      case ItemRemoved(item)         => NonEmpty(state.cart.removeItem(item), scheduleTimer(context))
      case CartEmptied | CartExpired => Empty
      case CheckoutClosed            => Empty
      case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
    }
  }

}
