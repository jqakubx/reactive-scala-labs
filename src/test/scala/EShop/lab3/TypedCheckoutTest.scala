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
    val inbox = TestInbox[TypedCartActor.Command]()
    val inboxOM = TestInbox[OrderManager.Command]()
    val checkoutActor = BehaviorTestKit(new TypedCheckout(inbox.ref).start)

    checkoutActor.run(TypedCheckout.StartCheckout)
    checkoutActor.run(TypedCheckout.SelectDeliveryMethod("delievery"))
    checkoutActor.run(TypedCheckout.SelectPayment("payment", inboxOM.ref))
    checkoutActor.run(TypedCheckout.ConfirmPaymentReceived)
    inbox.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }

  it should "Send close confirmation to cart (async)" in {
    val inbox = testKit.createTestProbe[lab2.TypedCartActor.Command]()
    val inboxOM = testKit.createTestProbe[OrderManager.Command]()
    val checkoutActor = testKit.spawn(new TypedCheckout(inbox.ref).start)

    checkoutActor ! TypedCheckout.StartCheckout
    checkoutActor ! TypedCheckout.SelectDeliveryMethod("delievery")
    checkoutActor ! TypedCheckout.SelectPayment("payment", inboxOM.ref)
    checkoutActor ! TypedCheckout.ConfirmPaymentReceived
    inbox.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }
}
