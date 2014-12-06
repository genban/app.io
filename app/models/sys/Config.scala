package models.sys

import java.util.UUID

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.Assignment
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import common.Logging
import models.CassandraConnector
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class Config(
  module_name: String,
  timestamp: DateTime,
  cfs_root: UUID
)

sealed class Configs extends CassandraTable[Configs, Config] {

  override val tableName = "system_config"

  object module
    extends StringColumn(this)
    with PartitionKey[String]

  object timestamp
    extends DateTimeColumn(this)
    with ClusteringOrder[DateTime] with Ascending

  object cfs_root extends UUIDColumn(this)

  override def fromRow(r: Row): Config = {
    Config(module(r), timestamp(r), cfs_root(r))
  }
}


object Config extends Configs with Logging with CassandraConnector {

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.language.postfixOps

  Await.result(create.future(), 500 millis)

  def get[T](
    module: String,
    cfs_root: (Configs) => SelectColumnRequired[Configs, Config, T]
  ): Future[Option[T]] = {
    select(cfs_root)
      .where(_.module eqs module)
      .one()
  }

  def put(
    module: String,
    timestamp: DateTime,
    updates: ((Configs) => Assignment)*
  ): Future[Option[ResultSet]] = {
    val stmt = update
      .where(_.module eqs module)
      .and(_.timestamp eqs timestamp)

    updates.toList match {
      case Nil          => Future.successful(None)
      case head :: tail => {
        (stmt.modify(head) /: tail)(_ and _)
      }.future().map(Some(_))
    }
  }
}
