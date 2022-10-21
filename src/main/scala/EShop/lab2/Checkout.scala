package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private def checkoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)


  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(checkoutTimer)
    case _ =>
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
    case SelectDeliveryMethod(_) =>
      context become selectingPaymentMethod(paymentTimer)
    case _ =>
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(_) =>
      context become processingPayment(timer)
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
    case _ =>
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived =>
      timer.cancel()
      context become closed
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpirePayment =>
      context become cancelled
    case _ =>
  }

  def cancelled: Receive = LoggingReceive {
    case _ => context stop self
  }

  def closed: Receive = LoggingReceive {
    case _ => context stop self
  }
}
