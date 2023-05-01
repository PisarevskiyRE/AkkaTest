package actor


import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import model._


// главный актор
object Hotel {

  // хранилище резерваций
  case class State(reservations: Set[Reservation])


  // обработчик команд
  def commandHandler(hotelId: String): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      // пришла команда на создание резерва
      case MakeReservation(guestId, startDate, endDate, roomNumber, replyTo) => {

        // создаем и проверяем на конфликты
        val tentativeReservation = Reservation.make(guestId, hotelId, startDate, endDate, roomNumber)
        val conflictingResevationOption: Option[Reservation] = state.reservations.find(r => r.intersect(tentativeReservation))

        // если все ок резервируем и посылаем сообщение об этом
        if (conflictingResevationOption.isEmpty){
          Effect
            .persist(ReservationAccepted(tentativeReservation))
            .thenReply(replyTo)(s => ReservationAccepted(tentativeReservation))
        } else {
          // сообщаем об ошибке
          Effect.reply(replyTo)(CommandFailure("Не удалось создать резерв, есть конфликты!"))
        }
      }
      // пришла команда изменить резерв
      case ChangeReservation(confirmationNumber, startDate, endDate, roomNumber, replyTo) =>

        val oldReservationOption = state.reservations.find(_.confirmationNumber == confirmationNumber)
        val newReservationOption = oldReservationOption
          .map(res => res.copy(startDate = startDate, endDate = endDate, roomNumber = roomNumber))
        val reservationUpdatedEventOption = oldReservationOption.zip(newReservationOption)
          .map(ReservationUpdated.tupled)
        val conflictingReservationOption = newReservationOption.flatMap { tentativeReservation =>
          state.reservations.find(r => r.confirmationNumber != confirmationNumber && r.intersect(tentativeReservation))
        }

        (reservationUpdatedEventOption, conflictingReservationOption) match {
          case (None, _) =>
            Effect.reply(replyTo)(CommandFailure(s"Не удалось изменить $confirmationNumber: не найден"))
          case (_, Some(_)) =>
            Effect.reply(replyTo)(CommandFailure(s"Не удалось изменить n $confirmationNumber: конфликт"))
          case (Some(resUpdated), None) =>
            Effect.persist(resUpdated).thenReply(replyTo)(s => resUpdated)
        }

      case CancelReservation(confirmationNumber, replyTo) =>
        val reservationOption = state.reservations.find(_.confirmationNumber == confirmationNumber)
        reservationOption match {
          case Some(res) =>

            Effect.persist(ReservationCanceled(res)).thenReply(replyTo)(s => ReservationCanceled(res))
          case None =>
            Effect.reply(replyTo)(CommandFailure(s"Не удалось отменить $confirmationNumber: не найден"))
        }


    }


  // обработчик событий
  def eventHandler(hotelId: String): (State, Event) => State = (state, event) =>
    event match {
      case ReservationAccepted(res) =>
        val newState = state.copy(reservations = state.reservations + res)
        println(s"состояние изменено: $newState")
        newState
      case ReservationUpdated(oldReservation, newReservation) =>
        val newState = state.copy(reservations = state.reservations - oldReservation + newReservation)
        println(s"состояние изменено: $newState")
        newState
      case ReservationCanceled(res) =>
        val newState = state.copy(reservations = state.reservations - res)
        println(s"состояние изменено: $newState")
        newState
    }

  // создание самого себя
  def apply(hotelId: String): Behavior[Command] =
    EventSourcedBehavior[Command,Event,State](
      persistenceId = PersistenceId.ofUniqueId(hotelId),
      emptyState = State(Set()),
      commandHandler = commandHandler(hotelId),
      eventHandler = eventHandler(hotelId)
    )
}
