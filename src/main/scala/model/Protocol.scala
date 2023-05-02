package model

import akka.actor.typed.ActorRef

import java.sql.Date


// типы команд
sealed trait Command

case class MakeReservation(guestId: String, startDate: Date, endDate: Date, roomNumber: Int, replyTo: ActorRef[Any]) extends Command
case class ChangeReservation(confirmationNumber: String, startDate: Date, endDate: Date, roomNumber: Int, replyTo: ActorRef[Any]) extends Command
case class CancelReservation(confirmationNumber: String, replyTo: ActorRef[Any]) extends Command



// типы событий
sealed trait Event

case class ReservationAccepted(reservation: Reservation) extends Event
case class ReservationUpdated(oldReservation: Reservation, newReservation: Reservation) extends Event
case class ReservationCanceled(reservation: Reservation) extends Event


case class CommandFailure(reason: String)
