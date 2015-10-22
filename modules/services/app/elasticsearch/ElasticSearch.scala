package elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import helpers._
import models._
import models.cassandra.Indexable
import org.elasticsearch.action.admin.indices.mapping.delete.DeleteMappingResponse
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.search.sort.SortOrder
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zepeng.li@gmail.com
 */
class ElasticSearch(
  val basicPlayApi: BasicPlayApi
)
  extends AppConfigComponents
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Logging {

  applicationLifecycle.addStopHook { () =>
    Logger.info(s"Shutdown Elastic Search Client")
    Future.successful(Client.close())
  }

  import ESyntax._

  implicit lazy val Client = (
    for {
      settings <- config.getString("cluster.name").map { name =>
        ImmutableSettings
          .settingsBuilder()
          .put("cluster.name", name)
          .build()
      }
      uri <- config.getString("client.uri").map { uri =>
        ElasticsearchClientUri(uri)
      }
    } yield {
      ElasticClient.remote(settings, uri)
    })
    .getOrElse {
      Logger.warn("client.uri / cluster.name is not configured yet")
      ElasticClient.local
    }

  implicit val indexName = domain

  def Index[T <: HasID[_]](r: T) = new IndexAction(r)

  def Delete[ID](r: ID) = new DeleteAction(r)

  def Delete = new DeleteMappingAction

  def Update[T <: HasID[_]](r: T) = new UpdateAction(r)

  def BulkIndex[T <: HasID[_]](rs: Seq[T]) = new BulkIndexAction(rs)

  def Search(
    q: Option[String],
    p: Pager,
    sort: Seq[String]
  ) = new SearchAction(q, p, sort)

  def Multi(defs: (ElasticSearch => ReadySearchDefinition)*) =
    new ReadyMultiSearchDefinition(Client)(
      defs.map(_ (this).definition)
    )

}

object ESyntax {

  class ReadyMultiSearchDefinition(client: ElasticClient)(
    val defs: Seq[SearchDefinition]
  ) {

    def future(): Future[MultiSearchResponse] = {
      client.execute(multi(defs))
    }
  }

  class SearchAction(q: Option[String], p: Pager, sort: Seq[String])(
    implicit client: ElasticClient, indexName: String
  ) {

    val pattern = """([- ])([\w]+)""".r

    val sortDefs: Seq[FieldSortDefinition] = sort.collect {
      case pattern("-", word) => new FieldSortDefinition(word).order(SortOrder.DESC)
      case pattern(" ", word) => new FieldSortDefinition(word).order(SortOrder.ASC)
    }

    def in(t: Indexable[_]): ReadySearchDefinition = {
      new ReadySearchDefinition(client, p)(
        Def(search in indexName / t.basicName)
          .?(cond = true)(_ start p.start limit p.limit)
          .?(q.isDefined && q.get.nonEmpty)(_ query q.get)
          .?(sortDefs.nonEmpty)(_ sort (sortDefs: _*))
          .result
      )
    }
  }

  class ReadySearchDefinition(client: ElasticClient, pager: Pager)(
    val definition: SearchDefinition
  ) {

    def future()(implicit ec: ExecutionContext): Future[PageSResp] = {
      client.execute(definition).map(PageSResp(pager, _))
    }
  }

  class IndexAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Indexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[IndexResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          index into indexName / t.basicName id r.id doc src
        }
      )
    }
  }

  class DeleteAction[ID](id: ID)(
    implicit client: ElasticClient, indexName: String
  ) {

    def from[T <: HasID[ID]](t: Indexable[T]): Future[DeleteResponse] =
      client.execute {
        delete id id from s"$indexName/${t.basicName}"
      }
  }

  class DeleteMappingAction(
    implicit client: ElasticClient, indexName: String
  ) {

    def from[T](t: Indexable[T]): Future[DeleteMappingResponse] =
      client.execute {
        delete mapping indexName / t.basicName
      }
  }

  class UpdateAction[T <: HasID[_]](r: T)(
    implicit client: ElasticClient, indexName: String
  ) {

    def in(t: Indexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[(JsValue, Future[UpdateResponse])] = {
      val src = converter(r)
      Future.successful(
        src.jsval, client.execute {
          update id r.id in s"$indexName/${t.basicName}" doc r
        }
      )
    }
  }

  class BulkIndexAction[T <: HasID[_]](rs: Seq[T])(
    implicit client: ElasticClient, indexName: String
  ) {

    def into(t: Indexable[T])(
      implicit converter: T => JsonDocSource
    ): Future[BulkResponse] = client.execute {
      bulk(
        rs.map { r =>
          index into s"$indexName/${t.basicName}" id r.id doc r
        }: _*
      )
    }
  }

  private case class Def[T](result: T) {

    def ?(cond: => Boolean)(append: T => T): Def[T] = {
      if (cond) Def(append(result)) else this
    }
  }

}