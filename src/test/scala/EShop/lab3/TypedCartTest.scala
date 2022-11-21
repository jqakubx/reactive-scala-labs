package EShop.lab3

import EShop.lab2
import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val test        = BehaviorTestKit(new TypedCartActor().start)
    val exampleItem = "exampleItem"
    val inbox       = TestInbox[Cart]()

    test.run(TypedCartActor.AddItem(exampleItem))
    test.run(TypedCartActor.GetItems(inbox.ref))
    val notEmptyCart = Cart.empty.addItem(exampleItem)
    inbox.expectMessage(notEmptyCart)
  }

  it should "add item properly (async)" in {
    val testKit     = ActorTestKit()
    val test        = testKit.spawn(new TypedCartActor().start)
    val probe       = testKit.createTestProbe[Cart]()
    val exampleItem = "exampleItem"

    test ! AddItem(exampleItem)
    test ! GetItems(probe.ref)
    val notEmptyCart = Cart.empty.addItem(exampleItem)
    probe.expectMessage(notEmptyCart)
  }

  it should "be empty after adding and removing the same item" in {
    val test        = BehaviorTestKit(new TypedCartActor().start)
    val exampleItem = "exampleItem"
    val inbox       = TestInbox[Cart]()

    test.run(TypedCartActor.AddItem(exampleItem))
    test.run(TypedCartActor.GetItems(inbox.ref))
    val notEmptyCart = Cart.empty.addItem(exampleItem)
    inbox.expectMessage(notEmptyCart)

    test.run(TypedCartActor.RemoveItem(exampleItem))
    test.run(TypedCartActor.GetItems(inbox.ref))
    val emptyCart = Cart.empty
    inbox.expectMessage(emptyCart)
  }

  it should "be empty after adding and removing the same item (async)" in {
    val testKit     = ActorTestKit()
    val test        = testKit.spawn(new TypedCartActor().start)
    val probe       = testKit.createTestProbe[Cart]()
    val exampleItem = "exampleItem"

    test ! AddItem(exampleItem)
    test ! GetItems(probe.ref)
    val notEmptyCart = Cart.empty.addItem(exampleItem)
    probe.expectMessage(notEmptyCart)

    test ! RemoveItem(exampleItem)
    test ! GetItems(probe.ref)
    val emptyCart = Cart.empty
    probe.expectMessage(emptyCart)
  }

  it should "start checkout" in {
    val test        = BehaviorTestKit(new TypedCartActor().start)
    val exampleItem = "exampleItem"
    val inbox       = TestInbox[TypedCartActor.Event]()

    test.run(TypedCartActor.AddItem(exampleItem))
    test.run(TypedCartActor.StartCheckout(inbox.ref))
    val msg = inbox.receiveMessage()
    assert(msg.isInstanceOf[TypedCartActor.CheckoutStarted])
  }

  it should "start checkout (async)" in {
    val testKit     = ActorTestKit()
    val test        = testKit.spawn(new TypedCartActor().start)
    val exampleItem = "exampleItem"
    val probe       = testKit.createTestProbe[TypedCartActor.Event]()

    test ! AddItem(exampleItem)
    test ! StartCheckout(probe.ref)
    val msg = probe.receiveMessage()
    assert(msg.isInstanceOf[TypedCartActor.CheckoutStarted])
  }
}
