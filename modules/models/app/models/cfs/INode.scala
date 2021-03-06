package models.cfs

import java.util.UUID

import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import models.cfs.CassandraFileSystem._
import models.{HasUUID, TimeBased}
import org.joda.time.DateTime
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * @author zepeng.li@gmail.com
 */
trait INode extends HasUUID with TimeBased {

  def name: String

  def path: Path

  def parent: UUID

  def is_directory: Boolean

  def owner_id: UUID

  def permission: Permission

  def ext_permission: ExtPermission

  def attributes: Map[String, String]

  lazy val updated_at: DateTime = created_at

  /**
   * Rename this inode to another name
   *
   * @param newName   new name for target inode
   * @param overwrite whether to overwrite existing inode
   * @param checker   check if user have permission to overwrite existing inode
   * @param ttl       time to live of a temporary inode
   * @param cfs       cassandra file system
   * @return
   */
  def rename(
    newName: String,
    overwrite: Boolean = false,
    checker: PermissionChecker = alwaysBlock,
    ttl: Duration = Duration.Inf
  )(
    implicit cfs: CassandraFileSystem
  ): Future[Boolean] = {
    for {
      pdir <- cfs._directories.find(parent)(onFound = p => p)
      done <- cfs._directories.renameChild(pdir, this, newName, ttl).recoverWith {
        case e: Directory.ChildExists if overwrite =>
          if (is_directory) {
            for {
              old <- pdir.dir(newName)
              ___ <- old.delete(recursive = true, checker)
              ret <- rename(newName, overwrite, checker, ttl)
            } yield ret
          }
          else {
            for {
              old <- pdir.file(newName)
              ___ <- old.delete() if checker(old)
              ret <- rename(newName, overwrite, checker, ttl)
            } yield ret
          }
      }
    } yield done
  }

  def delete()(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    for {
      pdir <- cfs._directories.find(parent)(onFound = p => p)
      done <- cfs._directories.delChild(pdir, this.name)
    } yield Unit
  }

  def savePerm(perm: Permission)(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    cfs._inodes.savePerm(id, perm)
  }

  def savePerm(gid: UUID, perm: Permission)(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    cfs._inodes.savePerm(id, gid, perm)
  }

  def deletePerm(gid: UUID)(
    implicit cfs: CassandraFileSystem
  ): Future[Unit] = {
    cfs._inodes.deletePerm(id, gid)
  }
}

trait INodeCanonicalNamed extends CanonicalNamed {

  override val basicName = "inodes"
}

trait INodeKey[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object inode_id
    extends TimeUUIDColumn(self)
      with PartitionKey[UUID]

}

trait INodeColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object parent
    extends UUIDColumn(self)
      with StaticColumn[UUID]

  object is_directory
    extends BooleanColumn(self)
      with StaticColumn[Boolean]

  object owner_id
    extends UUIDColumn(self)
      with StaticColumn[UUID]

  object permission
    extends LongColumn(self)
      with StaticColumn[Long]

  object ext_permission
    extends StaticMapColumn[T, R, UUID, Int](self)
      with StaticColumn[Map[UUID, Int]]

  object attributes
    extends StaticMapColumn[T, R, String, String](self)
      with StaticColumn[Map[String, String]]

}

trait FileColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object size
    extends LongColumn(self)
      with StaticColumn[Long]

  object indirect_block_size
    extends IntColumn(self)
      with StaticColumn[Int]

  object block_size
    extends IntColumn(self)
      with StaticColumn[Int]

}

trait DirectoryColumns[T <: CassandraTable[T, R], R] {
  self: CassandraTable[T, R] =>

  object name
    extends StringColumn(self)
      with ClusteringOrder[String] with Ascending

  object child_id
    extends UUIDColumn(self)

}

/**
 *
 */
sealed class INodeTable
  extends NamedCassandraTable[INodeTable, Row]
    with INodeCanonicalNamed
    with INodeKey[INodeTable, Row]
    with INodeColumns[INodeTable, Row]
    with FileColumns[INodeTable, Row]
    with DirectoryColumns[INodeTable, Row] {

  override def fromRow(r: Row): Row = r
}

object INode extends INodeCanonicalNamed {

  implicit val jsonWrites = new Writes[INode] {
    override def writes(o: INode): JsValue = o match {
      case d: Directory => Json.toJson(d)
      case f: File      => Json.toJson(f)
    }
  }
}

class INodes(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends INodeTable
  with ExtCQL[INodeTable, Row]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def find(id: UUID): Future[Option[Row]] = {
    CQL {select.where(_.inode_id eqs id)}.one()
  }

  def savePerm(id: UUID, perm: Permission): Future[Unit] = CQL {
    update
      .where(_.inode_id eqs id)
      .modify(_.permission setTo perm.self)
  }.future().map(_ => Unit)

  def savePerm(id: UUID, gid: UUID, perm: Permission): Future[Unit] = CQL {
    update
      .where(_.inode_id eqs id)
      .modify(_.ext_permission put gid -> perm.self.toInt)
  }.future().map(_ => Unit)

  def deletePerm(id: UUID, gid: UUID): Future[Unit] = CQL {
    delete(_.ext_permission(gid))
      .where(_.inode_id eqs id)
  }.future().map(_ => Unit)
}