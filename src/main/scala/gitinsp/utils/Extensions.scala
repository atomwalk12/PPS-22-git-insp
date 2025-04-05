package gitinsp.utils

import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import io.grpc.StatusRuntimeException
import io.qdrant.client.QdrantClient

import java.util.concurrent.ExecutionException

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Failure
import scala.util.Try

object IngestorServiceExtensions:
  extension (ingestor: EmbeddingStoreIngestor)
    def ingest(repository: GitRepository, lang: Language): Unit =
      repository.docs.filter(_.language == lang).foreach(
        doc => doc.createLangchainDocument().fold(())(ingestor.ingest),
      )

object QdrantClientExtensions extends LazyLogging:
  extension (qdrantClient: QdrantClient)
    def delete(index: IndexName): Try[Unit] =
      Try {
        qdrantClient.deleteCollectionAsync(index.name).get
        ()
      }.recoverWith {
        case e: ExecutionException =>
          logger.warn(s"Error deleting collection ${index.name}: ${e.getMessage}")
          Failure(e)
        case e: StatusRuntimeException =>
          logger.warn(s"gRPC error deleting collection ${index.name}: ${e.getMessage}")
          Failure(e)
        case e: InterruptedException =>
          logger.warn(s"Operation interrupted while deleting ${index.name}: ${e.getMessage}")
          Failure(e)
      }

    def listCollections(): Try[List[String]] =
      Try {
        qdrantClient.listCollectionsAsync().get().asScala.toList
      }.recoverWith {
        case e: ExecutionException =>
          logger.warn(s"Error listing collections: ${e.getMessage}")
          Failure(e)
        case e: StatusRuntimeException =>
          logger.warn(s"gRPC error listing collections: ${e.getMessage}")
          Failure(e)
        case e: InterruptedException =>
          logger.warn(s"Operation interrupted while listing collections: ${e.getMessage}")
          Failure(e)
      }
