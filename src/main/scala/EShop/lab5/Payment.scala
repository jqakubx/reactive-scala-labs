package EShop.lab5

import EShop.lab2.TypedCheckout
import EShop.lab3.OrderManager
import EShop.lab5.PaymentService.PaymentSucceeded
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy, Terminated}

import scala.concurrent.duration._

object Payment {
  sealed trait Message
  case object DoPayment                                                       extends Message
  case class WrappedPaymentServiceResponse(response: PaymentService.Response) extends Message
  sealed trait Response
  case object PaymentRejected extends Response

  val restartStrategy = SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  def apply(
    method: String,
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Message] =
    Behaviors
      .receive[Message](
        (context, msg) =>
          msg match {
            case DoPayment =>
              val paymentServiceRef: ActorRef[PaymentService.Response] = context.messageAdapter {
                case PaymentSucceeded =>
                  WrappedPaymentServiceResponse(PaymentSucceeded)
              }

              val supervisedPaymentServiceRef =
                Behaviors
                  .supervise(PaymentService(method, paymentServiceRef))
                  .onFailure(restartStrategy)

              val paymentService = context.spawnAnonymous(supervisedPaymentServiceRef)
              context.watch(paymentService)

              Behaviors.same

            case WrappedPaymentServiceResponse(PaymentSucceeded) =>
              orderManager ! OrderManager.ConfirmPaymentReceived
              checkout ! TypedCheckout.ConfirmPaymentReceived
              Behaviors.stopped
        }
      )
      .receiveSignal {
        case (_, Terminated(_)) =>
          notifyAboutRejection(orderManager, checkout)
          Behaviors.same
      }

  // please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Unit = {
    orderManager ! OrderManager.PaymentRejected
    checkout ! TypedCheckout.PaymentRejected
  }

}
