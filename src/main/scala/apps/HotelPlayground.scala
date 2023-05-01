package apps

import actor.Hotel
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import model._

import java.sql.Date
import scala.concurrent.duration._

object DemoHotel {

  def main(args: Array[String]): Unit = {

    // актор логов
    val simpleLogger = Behaviors.receive[Any]{ (ctx, message) =>
      ctx.log.info(s"[==Лог==] $message")
      Behaviors.same
    }

    val root = Behaviors.setup[String]{ ctx =>

      // дочерние акторы
      val logger = ctx.spawn(simpleLogger,"logger")
      val hotel = ctx.spawn(Hotel("testHotel"),"testHotel")


    //  hotel ! MakeReservation("Тест резерва", Date.valueOf("2023-05-01"),  Date.valueOf("2023-05-05"), 101, logger)
    //  hotel ! ChangeReservation("UW0D4Z5NTM", Date.valueOf("2023-05-01"),  Date.valueOf("2023-05-11"), 101, logger)

      Behaviors.empty
    }

    val system = ActorSystem(root, "DemoHotel")


    import system.executionContext
    system.scheduler.scheduleOnce(5.seconds, () => system.terminate())
/*
  docker ps -> имя касандры

  docker exec -it akkatest-cassandra-1 cqlsh


  UW0D4Z5NTM
 */
  }
}
