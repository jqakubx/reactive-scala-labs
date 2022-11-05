package EShop.lab2

import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}
import EShop.lab3.OrderManager.ConfirmPaymentStarted
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[Event], omPaymentRef: ActorRef[Payment.Event]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command
  case object PaymentRejected                                                                extends Command
  case object PaymentRestarted                                                               extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartActor: ActorRef[TypedCheckout.Event]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def checkoutTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def paymentTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case StartCheckout =>
        selectingDelivery(checkoutTimer(ctx))
      case _ => Behaviors.same
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ExpireCheckout =>
        cancelled
      case SelectDeliveryMethod(method) =>
        selectingPaymentMethod(paymentTimer(ctx))
      case _ => Behaviors.same
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case SelectPayment(payment, orderManagerRef, omPaymentRef) =>
        timer.cancel()
        val paymentActor = ctx.spawn(new Payment(payment, omPaymentRef, ctx.self).start, "PaymentActor")
        orderManagerRef ! PaymentStarted(paymentActor)
        processingPayment(paymentTimer(ctx))
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ExpireCheckout =>
        cancelled
      case _ => Behaviors.same
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case ConfirmPaymentReceived =>
        cartActor ! CheckOutClosed
        timer.cancel()
        closed
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ExpirePayment =>
        cancelled
      case _ => Behaviors.same
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case _ => Behaviors.stopped
    }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive((ctx, msg) =>
    msg match {
      case _ => Behaviors.stopped
    }
  )
}
