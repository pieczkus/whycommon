package pl.why.common

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.Materializer
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.index.RichIndexResponse
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition
import com.sksamuel.elastic4s.update.RichUpdateResponse
import com.sksamuel.elastic4s.xpack.security.XPackElasticClient
import com.sksamuel.elastic4s.{ElasticsearchClientUri, Indexable}
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse
import com.typesafe.config.Config
import org.elasticsearch.common.settings.Settings
import spray.json.JsonFormat
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

trait ElasticSearchSupport {
  me: CommonActor =>

  val esSettings = ElasticSearchSettings(context.system)

  val settings: Settings = Settings.builder()
    .put("cluster.name", "arrsearchcluster")
    .put("xpack.security.user", esSettings.username + ":" + esSettings.password)
    .build()
  val client = XPackElasticClient(settings, ElasticsearchClientUri(esSettings.host, esSettings.port))

  def indexRoot: String

  def entityType: String

  def queryElasticSearch[RT](query: QueryDefinition, start: Option[Int] = None, size: Option[Int] = None, sort: Option[SortDefinition] = None)(implicit ec: ExecutionContext, jf: RootJsonFormat[RT]): Future[List[RT]] = {
    var searchDefinition = search(indexRoot / entityType).query(query)

    if (sort.isDefined) {
      searchDefinition = searchDefinition.sortBy(sort.get)
    }
    if (start.isDefined && size.isDefined) {
      searchDefinition = searchDefinition.start(start.get).size(size.get)
    }

    //    log.info(searchDefinition.show)

//    val response = client.execute {
//      searchDefinition
//    }.await
//    log.warning(response.toString)

    client.execute {
      searchDefinition
    }.map(_.hits.map(_.sourceAsString.parseJson.convertTo[RT]).toList)
    //    Future.successful(List.empty[RT])
  }

  def index[RT](id: String, rm: RT)(implicit ec: ExecutionContext, jf: JsonFormat[RT]): Future[RichIndexResponse] = {
    implicit object RTIndexable extends Indexable[RT] {
      override def json(t: RT): String = t.toJson.toString()
    }
    client.execute {
      indexInto(indexRoot / entityType).id(id).doc(rm)
    }
  }

  def updateIndex[RT](id: String, ru: Map[String, Any]): Future[RichUpdateResponse] = {
    client.execute {
      update(id).in(indexRoot / entityType).doc(ru.toSeq: _*)
    }
  }

  def clearIndex(implicit ec: ExecutionContext, mater: Materializer): Future[DeleteIndexResponse] = {
    client.execute {
      deleteIndex(indexRoot)
    }
  }
}

class ElasticSearchSettingsImpl(conf: Config) extends Extension {
  val esConfig: Config = conf.getConfig("elasticsearch")
  val host: String = esConfig.getString("host")
  val port: Int = esConfig.getInt("port")
  val username: String = esConfig.getString("username")
  val password: String = esConfig.getString("password")
}

object ElasticSearchSettings extends ExtensionId[ElasticSearchSettingsImpl] with ExtensionIdProvider {
  override def lookup = ElasticSearchSettings

  override def createExtension(system: ExtendedActorSystem) =
    new ElasticSearchSettingsImpl(system.settings.config)
}

