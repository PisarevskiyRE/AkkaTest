package model

import java.sql.Date
import scala.util.Random

// сущность резервации
case class Reservation(
                      guestId: String,
                      hotelId: String,
                      startDate: Date,
                      endDate: Date,
                      roomNumber: Int,
                      confirmationNumber: String
                      )
{

  // пересечение резервирвания
  def intersect(another: Reservation) = {
   this.hotelId == another.hotelId && this.roomNumber == another.roomNumber &&
     (
       startDate.compareTo((another.startDate)) >= 0 && startDate.compareTo(another.endDate) <= 0 ||
         another.startDate.compareTo(startDate) >= 0 && another.startDate.compareTo(endDate) <= 0
     )
  }

  // по коду проверяем резервацию
  override def equals(obj: Any) = obj match {
    case Reservation(_, _, _, _, _, `confirmationNumber`) => true
    case _ => false
  }

  override def hashCode(): Int = confirmationNumber.hashCode
}

object Reservation{

  // создание резервации и генерация confirmationNumber
  def make(guestId: String, hotelId: String, startDate: Date, endDate: Date, roomNumber: Int): Reservation = {

    val chars = ('A' to 'Z') ++ ('0' to '9')
    val nChars = chars.length
    val confirmationNumber = (1 to 10).map(_ => chars(Random.nextInt(nChars))).mkString

    Reservation(guestId, hotelId,startDate, endDate, roomNumber,confirmationNumber)
  }

}





