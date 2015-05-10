package stockapp


import java.util.Random

import akka.actor._
import akka.event.EventStream

//Business message
case class OpenForBusiness(message:String="Open for business",from:ActorRef)
case class OrderPreference(msg:String,from:ActorRef)

//Customer messages
case class CustomerEntersForPlacingOrder(msg:String,from:ActorRef)
case class TopMenuOrderPref(pref:String,from:ActorRef)

//preferences
class Staff extends Actor{
  val stream: EventStream = context.system.eventStream
  var currentCustomer="None"

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    stream.subscribe(self,classOf[CustomerEntersForPlacingOrder])
  }

  override def receive: Receive = {
    case message:String => {
     context.system.eventStream.publish(OpenForBusiness("We are open now",self))
    }

    case CustomerEntersForPlacingOrder(msg,from)=> {
     println("Customer => %s".format(msg))
     from!OrderPreference("Hello Sir what would you like to have Sub or Salad?",from)
    }

    case TopMenuOrderPref(topMenu,from)=>{
      println("Customer => %s".format(TopMenuOrderPref(topMenu,from)))
    }
  }
}

class Customer extends Actor{
  val stream: EventStream = context.system.eventStream
  val orderPref = Array("Salad","Sub")
  val random = new Random()

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    stream.subscribe(self,classOf[OpenForBusiness])
  }

  override def receive: Actor.Receive = {
    case OpenForBusiness(msg,from)=>{
      println("Opening for business now")
      from!CustomerEntersForPlacingOrder("Hello how are you today!",self)
    }

    case OrderPreference(msg,from)=>{
      println("staff=> %s".format(msg))
      from ! TopMenuOrderPref("Sub",self)
    }
  }
}

object SubwaySimulator {
  def main(args:Array[String]): Unit = {
   val system: ActorSystem = ActorSystem("SimulationActors")
   val staff: ActorRef = system.actorOf(Props[Staff],"FrontDeskStaff")
   val customer:ActorRef = system.actorOf(Props[Customer],"Customer1")
   staff ! "Subway is open for business"
  }
}



