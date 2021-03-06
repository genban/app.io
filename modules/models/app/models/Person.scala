package models

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.misc._
import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Person(
  id: UUID,
  name: PersonalName = PersonalName.default,
  updated_at: DateTime = DateTime.now
) extends HasUUID with TimeBased

trait PersonCanonicalNamed extends CanonicalNamed {

  override val basicName = "persons"
}

sealed class PersonTable extends NamedCassandraTable[PersonTable, Person]
  with PersonCanonicalNamed {

  object id
    extends TimeUUIDColumn(this)
      with PartitionKey[UUID]

  object name
    extends JsonColumn[PersonTable, Person, PersonalName](this)
      with PersonalNameJsonStringifier

  object last_name
    extends StringColumn(this)

  object updated_at
    extends DateTimeColumn(this)

  override def fromRow(r: Row): Person = {
    Person(
      id(r),
      name(r),
      updated_at(r)
    )
  }
}

object Person extends PersonCanonicalNamed
  with ExceptionDefining {

  case class NotFound(id: UUID)
    extends BaseException(error_code("not.found"))

  implicit val jsonFormat = Json.format[Person]
}

class Persons(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends PersonTable
  with EntityTable[Person]
  with ExtCQL[PersonTable, Person]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw Person.NotFound(id)
    case _    => true
  }

  def find(id: UUID): Future[Person] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw Person.NotFound(id)
    case Some(u) => u
  }

  def save(p: Person): Future[ResultSet] = CQL {
    update
      .where(_.id eqs p.id)
      .modify(_.name setTo p.name)
      .and(_.updated_at setTo DateTime.now)
  }.future()

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)
}