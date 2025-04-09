package gitinsp.domain

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import gitinsp.infrastructure.CacheService
import gitinsp.utils.AIServiceURL
import gitinsp.utils.Assistant
import gitinsp.utils.Category
import gitinsp.utils.Language
import gitinsp.utils.QdrantURL
import gitinsp.utils.RepositoryWithLanguages
import gitinsp.utils.StreamedResponse
import gitinsp.utils.URL

import scala.concurrent.ExecutionContext
import scala.util.Try

object Pipeline extends LazyLogging:
  def apply(using s: ActorSystem, m: Materializer, e: ExecutionContext): Pipeline =
    new PipelineImpl(ChatService(), CacheService(), IngestorService(), GithubWrapperService())

  def apply(cs: ChatService, cas: CacheService, is: IngestorService, ws: GithubWrapperService)(using
    ActorSystem,
    Materializer,
    ExecutionContext,
  ): Pipeline =
    new PipelineImpl(cs, cas, is, ws)

  private class PipelineImpl(
    val chatService: ChatService,
    val cacheService: CacheService,
    val ingestorService: IngestorService,
    val githubWrapperService: GithubWrapperService,
  )(using s: ActorSystem, m: Materializer, e: ExecutionContext) extends Pipeline:

    // This initializes the AI services
    initializeApp()

    def chat(message: String, aiService: Assistant): StreamedResponse =
      // Stream the response
      chatService.chat(message, aiService)

    def generateIndex(repository: RepositoryWithLanguages, regenerate: Boolean): Try[Unit] =
      Try {
        // Delete current repository if it exists
        if regenerate then ingestorService.deleteRepository(repository)

        // Ingest the new repository
        ingestorService.ingest(repository)
      }

    def regenerateIndex(repository: RepositoryWithLanguages): Try[Unit] =
      // This deletes the existing index if it exists and creates a new one
      generateIndex(repository, true).map {
        _ => cacheService.initializeAIServices(Some(repository.toRepositoryWithCategories()))
      }

    def listIndexes(): Try[List[AIServiceURL]] =
      // List and return all indexes
      for {
        collections <- cacheService.listCollections().map(_.map(QdrantURL(_)))
        aiServiceIndexes = collections.map(index => index.buildAIServiceURL())
        indexes          = aiServiceIndexes.distinctBy(_.value)
      } yield indexes

    def deleteIndex(indexName: URL, category: Category): Try[Unit] =
      // When an index is removed delete the collection and the AI service
      for {
        _ <- cacheService.deleteCollection(indexName.toQdrantURL(category))
        _ <- cacheService.deleteAIService(indexName.toAIServiceURL())
      } yield ()

    def initializeApp(): Try[Unit] =
      // Initialize the AI services
      for {
        qdrantCollections <- cacheService.listCollections()
        indexes      = qdrantCollections.map(QdrantURL(_)).distinctBy(_.value)
        repositories = RepositoryWithLanguages.from(indexes)
        _            = repositories.foreach(repo => cacheService.initializeAIServices(Some(repo)))
        _            = cacheService.initializeAIServices(None)
      } yield ()

    def fetchRepository(url: URL, languages: List[Language]): Try[RepositoryWithLanguages] =
      // Used to display the repository content to the UI
      githubWrapperService.buildRepository(url, languages)

    def getAIService(index: Option[AIServiceURL]): Try[Assistant] =
      // Get the AI service from the cache (used when chatting)
      index match
        case Some(index) => cacheService.getAIService(index)
        case None        => cacheService.getAIService(AIServiceURL.default)

trait Pipeline:
  def chat(message: String, aiService: Assistant): StreamedResponse
  def generateIndex(repository: RepositoryWithLanguages, regenerate: Boolean): Try[Unit]
  def regenerateIndex(repository: RepositoryWithLanguages): Try[Unit]
  def listIndexes(): Try[List[AIServiceURL]]
  def fetchRepository(url: URL, languages: List[Language]): Try[RepositoryWithLanguages]
  def deleteIndex(indexName: URL, category: Category): Try[Unit]
  def getAIService(index: Option[AIServiceURL]): Try[Assistant]
