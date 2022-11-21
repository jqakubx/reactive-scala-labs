package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command
  case object PaymentRejected                                                                         extends Command
  case object PaymentRestarted                                                                        extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  var cartEventMapper: ActorRef[TypedCartActor.Event] = null

  var checkoutEventMapper: ActorRef[TypedCheckout.Event] = null

  var paymentEventMapper: ActorRef[Payment.Event] = null

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.setup { ctx =>
    cartEventMapper = ctx.messageAdapter { case TypedCartActor.CheckoutStarted(checkoutRef) =>
      ConfirmCheckoutStarted(checkoutRef)
    }

    checkoutEventMapper = ctx.messageAdapter { case TypedCheckout.PaymentStarted(paymentRef) =>
      ConfirmPaymentStarted(paymentRef)
    }

    paymentEventMapper = ctx.messageAdapter { case Payment.PaymentReceived =>
      ConfirmPaymentReceived
    }

    val cartActor = ctx.spawn(new TypedCartActor().start, "cartActor")
    open(cartActor)
  }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((ctx, msg) => {
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same
        case Buy(sender) =>
          inCheckout(cartActor, sender)
        case _ => Behaviors.same
      }
    })

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.setup { ctx =>
      cartActorRef ! TypedCartActor.StartCheckout(cartEventMapper)
      Behaviors.receive((ctx, msg) =>
        msg match {
          case ConfirmCheckoutStarted(checkoutRef) =>
            senderRef ! Done
            inCheckout(checkoutRef)
          case _ => Behaviors.same
        }
      )
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive((ctx, msg) =>
      msg match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, checkoutEventMapper, paymentEventMapper)
          inPayment(sender)
      }
    )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case ConfirmPaymentStarted(paymentRef) =>
        senderRef ! Done
        inPayment(paymentRef, senderRef)
    }
  )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        sender ! Done
        Behaviors.same
      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished
    }
  )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
