package EShop.lab3

import EShop.lab2
import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import TypedCartActor._

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val inbox          = TestInbox[TypedCheckout.Event]()
    val inboxOM        = TestInbox[TypedCartActor.Command]()
    val paymentInboxOM = TestInbox[Payment.Event]()
    val checkoutActor  = BehaviorTestKit(new TypedCheckout(inbox.ref).start)

    checkoutActor.run(TypedCheckout.StartCheckout)
    checkoutActor.run(TypedCheckout.SelectDeliveryMethod("delievery"))
    checkoutActor.run(TypedCheckout.SelectPayment("payment", inbox.ref, paymentInboxOM.ref))
    checkoutActor.run(TypedCheckout.ConfirmPaymentReceived)
    inbox.receiveMessage()
    inbox.expectMessage(TypedCheckout.CheckOutClosed)
  }

  it should "Send close confirmation to cart (async)" in {
    val inbox          = testKit.createTestProbe[TypedCheckout.Event]()
    val inboxOM        = TestInbox[TypedCartActor.Command]()
    val paymentInboxOM = TestInbox[Payment.Event]()
    val checkoutActor  = testKit.spawn(new TypedCheckout(inbox.ref).start)

    checkoutActor ! TypedCheckout.StartCheckout
    checkoutActor ! TypedCheckout.SelectDeliveryMethod("delievery")
    checkoutActor ! TypedCheckout.SelectPayment("payment", inbox.ref, paymentInboxOM.ref)
    checkoutActor ! TypedCheckout.ConfirmPaymentReceived
    inbox.receiveMessage()
    inbox.expectMessage(TypedCheckout.CheckOutClosed)
  }
}
