package apps

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.scaladsl.{Sink, Source}
import model._

import java.time.temporal.ChronoUnit
import scala.concurrent.Future

object HotelEventReader {

  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "HotelEventReaderSystem")

  //чтение журнала
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val persistenceIds: Source[String, NotUsed] = readJournal.persistenceIds()
  val consumptionSink = Sink.foreach(println)
  val connectedGraph = persistenceIds.to(consumptionSink)




  import system.executionContext

  val session = CassandraSessionRegistry(system).sessionFor("akka.projection.cassandra.session-config")


  def makeReservation(reservation: Reservation): Future[Unit] = {
    val Reservation(guestId, hotelId, startDate, endDate, roomNumber, confirmationNumber) = reservation
    val startLocalDate = startDate.toLocalDate
    val endLocalDate = endDate.toLocalDate
    val daysBlocked = startLocalDate.until(endLocalDate, ChronoUnit.DAYS).toInt

    val blockedDaysFutures = for {
      days <- 0 until daysBlocked
    } yield session.executeWrite(
      "UPDATE hotel.available_rooms_by_hotel_date SET is_available = false WHERE " +
        s"hotel_id='$hotelId' and date='${startLocalDate.plusDays(days)}' and room_number=$roomNumber"
    ).recover(e => println(s"Room day blocking failed: ${e}"))

    val reservationGuestDateFuture = session.executeWrite(
      "INSERT INTO reservation.reservations_by_hotel_date (hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES " +
        s"('$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', $guestId)"
    ).recover(e => println(s"reservation for date failed: ${e}"))

    val reservationGuestFuture = session.executeWrite(
      "INSERT INTO reservation.reservations_by_guest (guest_last_name, hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES " +
        s"('ROCKTHEJVM', '$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', $guestId)"
    ).recover(e => println(s"reservation for guest failed: ${e}"))

    Future.sequence(reservationGuestFuture :: reservationGuestDateFuture :: blockedDaysFutures.toList).map(_ => ())
  }



  val eventsForTestHotel = readJournal
    .eventsByPersistenceId("testHotel", 0 , Long.MaxValue)
    .map(_.event)
    .mapAsync(8) {
      case ReservationAccepted(res) =>
        println(s"Создана резервация $res")
        makeReservation(res)
      case ReservationUpdated(oldRes, newRes) =>
        println(s"Резервация изменена с $oldRes на $newRes")
        Future.successful(())
      case ReservationCanceled(res) =>
        println(s"Резервация отменена $res")
        Future.successful(())
    }
  def main(args: Array[String]): Unit = {

    eventsForTestHotel.to(Sink.ignore).run()

  }
}
