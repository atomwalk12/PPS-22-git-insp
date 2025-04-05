package gitinsp.domain

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import gitinsp.utils.GitRepository
import gitinsp.utils.IngestorServiceExtensions.ingest
import gitinsp.utils.QdrantClientExtensions.delete
import gitinsp.utils.QdrantClientExtensions.listCollections
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.Collections.Distance.Cosine

trait IngestorService:
  def ingest(repository: GitRepository): Unit
  def deleteRepository(repository: GitRepository): Unit

object IngestorService:
  def apply(cache: CacheService, config: Config): IngestorService =
    new IngestorServiceImpl(cache, config)

  def apply(): IngestorService =
    new IngestorServiceImpl(CacheService(), ConfigFactory.load())

  private class IngestorServiceImpl(cache: CacheService, config: Config) extends IngestorService:

    val client = cache.qdrantClient

    override def ingest(repository: GitRepository): Unit =
      // Get all collections
      val collections = client.listCollections()

      // Create the collection if it doesn't exist
      repository.indexNames
        .filterNot(index => collections.getOrElse(List.empty).contains(index.name))
        .foreach(index => cache.createCollection(index.name, Cosine))

      // Create ingestor
      repository.indexNames.foreach {
        case index =>
          val strategy = IngestionStrategyFactory.createStrategy("default", index.language, config)
          val ingestor = cache.getIngestor(index, strategy)
          ingestor.ingest(repository, index.language)
      }

    override def deleteRepository(repository: GitRepository): Unit =
      val collections = client.listCollections()
      repository.indexNames
        .filter(index => collections.getOrElse(List.empty).contains(index.name))
        .foreach(index => client.delete(index))
