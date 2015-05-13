package simulation


import java.util.{Scanner, Random}

import akka.actor._
import akka.event.EventStream

import scala.collection.mutable
import scala.collection.parallel.immutable

//Business message
case class OpenForBusiness(message:String="Open for business",from:ActorRef)
case class OrderPreference(pref:Seq[String]=Seq("Sub","Salad")){
  def getRandom() = scala.util.Random.shuffle(pref).head
}
case class WaitForYourTurnPlease(from:ActorRef)

case class BillingRequest(amount:Float,paymentType:Seq[String]=Seq("Cash","Card"))

case class DrinkRequest(options:Seq[String]=Seq("Coke","Sprite","Pepsi"),size:Seq[String]= Seq("Medium","Large","Small"))

case class BillingResponse(amount:Float,paymentMode:String)

//Customer
case class CustomerEntersForPlacingOrder(msg:String,from:ActorRef)
case class TopMenuOrderPref(msg:String){ // pref = {Sub|Salad}
  def isSalad = msg.equalsIgnoreCase("salad")
  def isSub = msg.equalsIgnoreCase("sub")
}
case class DrinkResponse(drink:String,size:String) {
  def isRequested = if(drink.isEmpty||drink.isEmpty)false else true;
}

case class ChooseBread(options:Seq[String]=Seq("9 Grain HoneyOats","FlatBread","Italian Herb and Cheese","Italian","9 Grain wheat"),size:Seq[String]= Seq("Footlong","Regular")){
  def makeBreadChoice():String = util.Random.shuffle(options).head
  def makeSizeChoice():String = util.Random.shuffle(size).head
}
case class BreadChoice(bread:String,breadSize:String)



class Staff extends Actor{
  val stream: EventStream = context.system.eventStream
  var currentCustomer:ActorRef = null
  val queue = new mutable.Queue[ActorRef]()

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    stream.subscribe(self,classOf[CustomerEntersForPlacingOrder])
  }

  override def receive: Receive = {
    case message:String => {
     context.system.eventStream.publish(OpenForBusiness("We are open now",self))
    }

    case CustomerEntersForPlacingOrder(msg,from)=> {
     currentCustomer match{
       case customer:ActorRef => {
         queue.enqueue(customer)
         from ! WaitForYourTurnPlease(self)
       }
       case _ => { // Dont have any current Customer I will take your order
              println("Hello Sir what would you like to have Sub or Salad? from %s".format(sender().path.name))
              currentCustomer = sender()
              sender()!OrderPreference()
           }
       }
     }

    case TopMenuOrderPref(topMenu)=>{
      println("Staff=> ok lets build %s for you".format(topMenu))
      TopMenuOrderPref(topMenu).isSub match {
        case true =>{ //sub
          Thread.sleep(1000)
          // ask for bread now
          sender() ! ChooseBread()
        }
        case false => { //salad
           println("Staff=>%s Here's your Salad sir, would you like to have a drink?".format(sender().path.name))
           println("Staff=> Your Drink options are %s and sizes are %s",DrinkRequest().options,DrinkRequest().size)
           sender() ! DrinkRequest()
        }
      }
    }

    case DrinkResponse(drinkType,drinkSize) =>{
      DrinkResponse(drinkType,drinkSize).isRequested match {
        case true => {
            sender!BillingRequest(amount =(2.00+6.50).toFloat)
        }
        case false =>{
            sender!BillingRequest(amount = 6.50.toFloat)
        }
      }
    }

    case BillingResponse(amount,paymentMode)=>{
      println("Staff=> We have charged you %s by payment mode %s".format(amount,paymentMode))
      println("Staff=> Thank you for your business have a great day!")
      currentCustomer = null
      queue.isEmpty match {
        case false => {
          currentCustomer=queue.dequeue()
          currentCustomer!OrderPreference()
        }
        case true => //wait for next customer indefinitely
      }

    }

    case BreadChoice(bread,size)=>{

    }

  }// receive ends
}

class Customer extends Actor{
  val stream: EventStream = context.system.eventStream
  val orderPref = Array("Salad","Sub")
  val random = scala.util.Random
  val drinkPref = List(true,false)

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    println("%s created".format(self.path.name))
    stream.subscribe(self,classOf[OpenForBusiness])
  }

  override def receive: Actor.Receive = {
    case OpenForBusiness(msg,from)=>{
      from!CustomerEntersForPlacingOrder("%s -> Hello how are you today!".format(self.path.name),self)
    }

    case OrderPreference(pref)=>{
      val topChoice: String = OrderPreference(pref).getRandom
      println("%s I would like to have %s today".format(self.path.name, topChoice))
      Thread.sleep(2000)
      sender ! TopMenuOrderPref(topChoice)
    }

    case WaitForYourTurnPlease(sender) =>{
      for(x<-Range(0,3)){
        Thread.sleep(1000)
        println("%s Doing some random stuff".format(self.path.name))
      }
    }

    case DrinkRequest(options,sizes) =>{
      util.Random.shuffle(drinkPref).head match{
        case true => {
          Thread.sleep(1000)
          val drink: String = util.Random.shuffle(options).head
          val size:String = util.Random.shuffle(sizes).head
          println("I would like to have a %s %s".format(size,drink))
          sender ! DrinkResponse(drink,size)
        }
        case false => {
          println("%s => No Thanks I done for today! bill please".format(self.path.name))
          sender!DrinkResponse("","") // no drink required
        }
      }
    }
    
    case BillingRequest(amount,paymentType)=>{
      val paymentMode: String = util.Random.shuffle(paymentType).head
      println("%s=> I will pay the amount by %s by %s".format(self.path.name,amount,paymentMode))
      sender! BillingResponse(amount,paymentMode)
    }

    case ChooseBread(options,size)=>{
      val bread: String = ChooseBread(options,size).makeBreadChoice()
      val breadSize: String = ChooseBread(options,size).makeSizeChoice()
      println("%s I would like to have %s bread with size %s".format(self.path.name,bread,breadSize))
      sender ! BreadChoice(bread,breadSize)
    }  
  } //receive ends
}

object SubwaySimulator {
  def main(args:Array[String]): Unit = {
    // actor system for the store initialized
    val system: ActorSystem = ActorSystem("SimulationActors")

    println("***** Starting Subway Simulation ********* ")
    println("Please enter the no of customers you would like to be in the store: ")
    val staff: ActorRef = system.actorOf(Props[Staff],"FrontDeskStaff")

    //read from stdin
    val noOfCustomers: Int = (for(ln<-io.Source.stdin.getLines()) yield ln).toSeq.head.toInt
    for(x<-Range(0,noOfCustomers)){
      system.actorOf(Props[Customer],"Customer%s".format(x))
    }

    import concurrent.duration._
    system.scheduler.scheduleOnce(2.seconds,staff,"Subway is open for business")(system.dispatcher,Actor.noSender)
  }
}



