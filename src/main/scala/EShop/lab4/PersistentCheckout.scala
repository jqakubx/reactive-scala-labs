package EShop.lab4

import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command], expire: TypedCheckout.Command): Cancellable =
    context.scheduleOnce(timerDuration, context.self, expire)


  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)
          case _ => Effect.none
        }


      case SelectingDelivery(_) =>
        command match {
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case SelectDeliveryMethod(method) =>
            Effect.persist(DeliveryMethodSelected(method))
          case _ => Effect.none
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case SelectPayment(payment, orderManagerRef, omPaymentRef) =>
            val paymentActor = context.spawn(new Payment(payment, omPaymentRef, context.self).start, "PaymentActor")
            Effect.persist(PaymentStarted(paymentActor))
              .thenRun {
                _ =>
                  orderManagerRef ! PaymentStarted(paymentActor)
              }
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case _ => Effect.none
        }

      case ProcessingPayment(_) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect.persist(CheckOutClosed)
              .thenRun {
                _ =>
                  cartActor ! TypedCartActor.ConfirmCheckoutClosed
              }
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case ExpirePayment =>
            Effect.persist(CheckoutCancelled)
          case _ => Effect.none
        }

      case Cancelled =>
        command match {
          case _ => Effect.none
        }

      case Closed =>
        command match {
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted           => SelectingDelivery(schedule(context, ExpireCheckout))
      case DeliveryMethodSelected(_) => SelectingPaymentMethod(schedule(context, ExpirePayment))
      case PaymentStarted(_)         => ProcessingPayment(schedule(context, ExpirePayment))
      case CheckOutClosed            => Closed
      case CheckoutCancelled         => Cancelled
    }
  }
}
