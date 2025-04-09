package gitinsp.infrastructure

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor as Ingestor
import gitinsp.chatpipeline.RAGComponentFactory
import gitinsp.infrastructure.strategies.IngestionStrategy
import gitinsp.utils.AIServiceURL
import gitinsp.utils.Assistant
import gitinsp.utils.Category
import gitinsp.utils.Given.given_Conversion_AIServiceURL_String
import gitinsp.utils.Language
import gitinsp.utils.QdrantURL
import gitinsp.utils.RepositoryWithCategories
import io.grpc.StatusRuntimeException
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Collections.Distance

import java.util.concurrent.ExecutionException

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object CacheService:
  def apply(): CacheService =
    new CacheServiceImpl()

  def apply(factory: RAGComponentFactory): CacheService =
    // A constructor used to simplify testing
    new CacheServiceImpl(factory)

  private class CacheServiceImpl(providedFactory: Option[RAGComponentFactory]) extends CacheService:

    // Default constructor
    def this() = this(None)
    def this(factory: RAGComponentFactory) = this(Some(factory))

    // Data fields
    val config: Config               = ConfigFactory.load()
    val factory: RAGComponentFactory = providedFactory.getOrElse(RAGComponentFactory(config))

    // The Triemap is a caching structure that is thread safe
    private val aiServiceCache: TrieMap[AIServiceURL, Assistant] = TrieMap.empty
    private val _qdrantClient: QdrantClient                      = factory.createQdrantClient()
    // Getter for the Qdrant client
    override def qdrantClient: QdrantClient = _qdrantClient

    val scoringModel: ScoringModel = factory.createScoringModel()
    val fmt                        = ContentFormatter

    def initializeAIServices(repository: Option[RepositoryWithCategories]): Assistant =
      // Create the streaming model
      val model = factory.createStreamingChatModel()

      repository.fold {
        // Default AI service when no repository is provided
        val aiService = factory.createAssistant(model, None)
        aiServiceCache.putIfAbsent(AIServiceURL.default, aiService)
        logger.debug(s"Created default AI service: ${AIServiceURL.default}")
        aiServiceCache(AIServiceURL.default)
      } {
        repo =>
          val name = repo.url.toAIServiceURL()
          // Return from cache if it exists
          aiServiceCache.getOrElse(
            name, {
              // Create components for the RAG pipeline
              val modelRouter        = factory.createModelRouter()
              val textEmbeddingModel = factory.createTextEmbeddingModel()
              val codeEmbeddingModel = factory.createCodeEmbeddingModel()

              // Build retrievers based on categories
              val retrievers = repo.categories.flatMap {
                category =>
                  category match
                    case Category.TEXT =>
                      val markdownStore = factory.createEmbeddingStore(
                        _qdrantClient,
                        s"$name-${Category.TEXT}",
                      )
                      List(factory.createMarkdownRetriever(markdownStore, textEmbeddingModel, name))
                    case Category.CODE =>
                      val codeStore =
                        factory.createEmbeddingStore(_qdrantClient, s"$name-${Category.CODE}")
                      List(factory.createCodeRetriever(
                        codeStore,
                        codeEmbeddingModel,
                        name,
                        modelRouter,
                      ))
              }

              // Compose the assistant from constructed components
              val router            = factory.createQueryRouter(retrievers, modelRouter)
              val contentAggregator = factory.createContentAggregator(scoringModel)
              val augmentor         = factory.createRetrievalAugmentor(router, contentAggregator)
              val aiservice         = factory.createAssistant(model, Some(augmentor))

              // Store in cache and return
              aiServiceCache.putIfAbsent(name, aiservice)
              logger.debug(s"Created AI service: $name")
              aiServiceCache(name)
            },
          )
      }

    def getAIService(index: AIServiceURL): Try[Assistant] =
      // In a real scenario, the index should ALWAYS be in the cache (due to the APP initialization)
      aiServiceCache.get(index).fold[Try[Assistant]](
        Failure(new Exception(s"AI service not found: $index")),
      )(Success(_))

    def createCollection(name: String, distance: Distance): Try[Unit] =
      Try {
        // Create a collection when generating a new index
        factory.createCollection(name, _qdrantClient, distance)
      }.map(_ => logger.debug(s"Successfully created collection: $name"))

    override def getIngestor(
      index: QdrantURL,
      language: Language,
      strategy: IngestionStrategy,
    ): Ingestor =
      // Determine embedding model based on category
      val embeddingModel = index.category match
        case Category.TEXT => factory.createTextEmbeddingModel()
        case Category.CODE => factory.createCodeEmbeddingModel()

      val embeddingStore = factory.createEmbeddingStore(_qdrantClient, index.value)

      // Return the ingestor by composing components
      factory.createIngestor(language, embeddingModel, embeddingStore, strategy)

    override def deleteCollection(indexName: QdrantURL): Try[Unit] =
      delete(indexName)
        .map(_ => logger.debug(s"Successfully deleted collection: $indexName"))

    // Core implementation of collection deletion with detailed error handling
    def delete(index: QdrantURL): Try[Unit] =
      // Attempt to delete the collection
      Try(qdrantClient.deleteCollectionAsync(index.value).get())
        .map(_ => ())
        .recoverWith {
          case e: ExecutionException =>
            logger.warn(s"Error deleting collection ${index.value}: ${e.getMessage}")
            Failure(e)
          case e: StatusRuntimeException =>
            logger.warn(s"gRPC error deleting collection ${index.value}: ${e.getMessage}")
            Failure(e)
          case e: InterruptedException =>
            logger.warn(s"Operation interrupted ${index.value}: ${e.getMessage}")
            Failure(e)
        }

    override def deleteAIService(indexName: AIServiceURL): Try[Unit] =
      // Delete the AI service as well
      Option(indexName)
        .filter(aiServiceCache.contains)
        .fold {
          logger.warn(s"AI Service may have been already deleted: $indexName")
          Success(())
        } {
          name =>
            Try(aiServiceCache.remove(name))
              .map(_ => logger.debug(s"Successfully removed AI service: $name"))
        }

    override def listCollections(): Try[List[String]] =
      // List all collections (used when initializing the app)
      Try(_qdrantClient.listCollectionsAsync().get().asScala.toList)
        .map {
          collections =>
            logger.debug(s"Collections: $collections")
            collections
        }
        .recoverWith {
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

trait CacheService extends LazyLogging:
  def qdrantClient: QdrantClient // Todo: remove this
  def listCollections(): Try[List[String]]
  def initializeAIServices(repository: Option[RepositoryWithCategories]): Assistant
  def getIngestor(index: QdrantURL, language: Language, strategy: IngestionStrategy): Ingestor
  def factory: RAGComponentFactory
  def createCollection(name: String, distance: Distance): Try[Unit]
  def getAIService(index: AIServiceURL): Try[Assistant]
  def deleteCollection(indexName: QdrantURL): Try[Unit]
  def deleteAIService(indexName: AIServiceURL): Try[Unit]
  def delete(index: QdrantURL): Try[Unit]
