package simulation


import java.util.{Scanner, Random}

import akka.actor._
import akka.event.EventStream

import scala.StringBuilder
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

case class FlavourRequest(options:Seq[String]=Seq("Meat","Egg","Veggie"))

case class SauceRequest(options:Seq[String]=Seq("Mustard","Mayonnaise","Chipotle", "Sweet Onion", "Ranch"))



case class BillingResponse(amount:Float,paymentMode:String)
case class FlavourResponse(flavor:String)
case class SauceResponse(sauce1:String,sauce2:String)


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

//Random Messages

case class ScanMenu(person:ActorRef)
case class TakeACall(person:ActorRef)
case class ExitRestaurant(person:ActorRef)
case class MakeACall(person:ActorRef)


class Staff extends Actor{
  val stream: EventStream = context.system.eventStream
  var currentCustomer:ActorRef = null
  val queue = new scala.collection.mutable.Queue[ActorRef]()

// print the list
  def printList(args: Seq[_]): Unit = {
  val newBuilder=new StringBuilder()
  args.foreach(el=>{newBuilder.append(el+",")})
  print(newBuilder.toString()+"\n")
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    stream.subscribe(self,classOf[ExitRestaurant])
    stream.subscribe(self,classOf[CustomerEntersForPlacingOrder])
  }

  override def receive: Receive = {
    case message:String => {
     context.system.eventStream.publish(OpenForBusiness("We are open now",self))
    }

    case CustomerEntersForPlacingOrder(msg,from)=> {
     currentCustomer match{
       case customer:ActorRef => {
         queue.enqueue(from)
         from ! WaitForYourTurnPlease(self)
       }
       case _ => { // Dont have any current Customer I will take your order

              println("Staff => Hello %s what would you like to have Sub or Salad?".format(sender().path.name))
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
          println("Staff=> Here is your  %s cup for a drink".format(drinkSize,sender().path.name))
          val billAmount: Float = (2.00 + 6.50).toFloat
          sender!BillingRequest(amount =billAmount)
          println("Staff=> %s the bill would be %s".format(sender().path.name,billAmount))
        }
        case false =>{
          val billAmount: Float = 6.50.toFloat
          sender!BillingRequest(amount = billAmount)
          println("Staff=> %s the bill would be %s".format(billAmount,sender().path.name))
        }
      }
    }

    case BillingResponse(amount,paymentMode)=>{
      println("Staff=> We have charged you %s by payment mode %s".format(amount,paymentMode))
      println("Staff=> Thank you for your business have a great day!")
      currentCustomer = null
      queue.isEmpty match {
        case false => {
          currentCustomer= queue.dequeue()
          currentCustomer!OrderPreference()
        }
        case true => //wait for next customer indefinitely
      }
    }

    case BreadChoice(bread,size)=>{
      println("Staff=> Ok.. i will get you a %s with size %s".format(bread,size))
      //println("Staff=> What flavour would you like..You can have %s ".format(FlavourRequest().options))
      println("Staff=> What flavour would you like..You can have any of these..")
      printList(FlavourRequest().options)
      sender() ! FlavourRequest()
    }

   // received flavour now ask for sauce
    case FlavourResponse(flavour)=>{
      println("Staff=> sure! I will get you  %s".format(flavour))
      println("Staff=> What sauce would you like..You can have any of these..")
      printList(SauceRequest().options)
      sender() ! SauceRequest()
    }


    case ExitRestaurant(from)=>{
      val name: String = from.path.name
      queue.dequeueFirst(_.path.name.equals(name))
    }


// received sauce preference. Now ask for a drink
  case SauceResponse(sauce1,sauce2)=>{
    println("Staff=> sure! I will get you %s and %s".format(sauce1,sauce2))
    println("Staff=> Would you like to have any drink? you can go for")
    printList(DrinkRequest().options)
    println("Staff=> for sizes ")
    printList(DrinkRequest().size)
    sender() ! DrinkRequest()
  }
}


}

class Customer extends Actor{
  val stream: EventStream = context.system.eventStream
  val orderPref = Array("Salad","Sub")
  val random = scala.util.Random
  val drinkPref = List(true,false)
  val customerMessages = List(ScanMenu(self),TakeACall(self),MakeACall(self),ExitRestaurant(self))


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    println("%s Entered".format(self.path.name))
    stream.subscribe(self,classOf[OpenForBusiness])
  }

  override def receive: Actor.Receive = {
    case OpenForBusiness(msg,from)=>{
      from!CustomerEntersForPlacingOrder("%s -> Hello how are you today!".format(self.path.name),self)
    }

    case OrderPreference(pref)=>{
      val topChoice: String = OrderPreference(pref).getRandom
    println("%s I would like to have %s today".format(self.path.name, "Sub"))
      Thread.sleep(2000)
      sender ! TopMenuOrderPref("Sub")
    }

    case WaitForYourTurnPlease(sender) =>{
      import scala.util.control.Breaks._
      import scala.util.control._
      val loop = new Breaks;

      loop.breakable{
        for(x<-Range(0,7)){
          Thread.sleep(1500)
          val message = util.Random.shuffle(customerMessages).head
          if(!message.equals(ExitRestaurant(self))){
            stream.publish(message)
          }else{
            stream.publish(ExitRestaurant(self))
            loop.break()
          }
        }
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

    case FlavourRequest(options) =>{
          Thread.sleep(2000)
          val flavour: String = util.Random.shuffle(options).head
          println("%s=> I would like to go for %s".format(self.path.name,flavour))
          sender ! FlavourResponse(flavour)
    }
    case SauceRequest(options) =>{
      Thread.sleep(2000)
      val sauce1: String = util.Random.shuffle(options).head
      val sauce2: String = util.Random.shuffle(options).head

      println("%s=> I would like to go for %s and %s".format(self.path.name,sauce1,sauce2))
      sender ! SauceResponse(sauce1,sauce2)
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

class Observer extends Actor{
  val stream: EventStream = context.system.eventStream

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    stream.subscribe(self,classOf[ExitRestaurant])
    stream.subscribe(self,classOf[ScanMenu])
    stream.subscribe(self,classOf[TakeACall])
    stream.subscribe(self,classOf[MakeACall])
    stream.subscribe(self,classOf[CustomerEntersForPlacingOrder])
  }

  override def receive: Actor.Receive = {
    case ExitRestaurant(customer)=>{
      println("Observer => %s exited the restaurant ".format(customer.path.name))
    }

    case TakeACall(customer)=>{
      println("Observer=> %s is taking a call".format(customer.path.name))
    }

    case MakeACall(customer)=>{
      println("Observer=> %s is making a call".format(customer.path.name))
    }

    case ScanMenu(customer)=>{
      println("Observer=> %s is Scanning the menu".format(customer.path.name))
      for(x<-Range(1,4)){
        Thread.sleep(800)
        println(".........................................")
      }
    }

    case CustomerEntersForPlacingOrder(msg,from)=>{
      println("Observer=> %s entered the Subway restaurant premises".format(from.path.name))
    }
  }
}

object SubwaySimulator {
  def main(args:Array[String]): Unit = {
    // actor system for the store initialized
    val system: ActorSystem = ActorSystem("SimulationActors")
    val staff: ActorRef = system.actorOf(Props[Staff],"Staff")
    val observer:ActorRef = system.actorOf(Props[Observer],"Observer")

    println("***** Starting Subway Simulation ********* ")
    println("Please enter the no of customers you would like to be in the store: ")

    //read from stdin
    val noOfCustomers: Int = (for(ln<-io.Source.stdin.getLines()) yield ln).toSeq.head.toInt
    for(x<-Range(0,noOfCustomers)){
      system.actorOf(Props[Customer],"Customer%s".format(x))
    }

    import concurrent.duration._
    system.scheduler.scheduleOnce(2.seconds,staff,"Subway is open for business")(system.dispatcher,Actor.noSender)
  }
}



