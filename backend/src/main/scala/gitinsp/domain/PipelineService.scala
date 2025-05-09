package gitinsp.domain

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import gitinsp.domain.interfaces.application.ChatService
import gitinsp.domain.interfaces.application.IngestorService
import gitinsp.domain.interfaces.application.Pipeline
import gitinsp.domain.interfaces.infrastructure.CacheService
import gitinsp.domain.interfaces.infrastructure.GithubWrapperService
import gitinsp.domain.models.AIServiceURL
import gitinsp.domain.models.Assistant
import gitinsp.domain.models.Category
import gitinsp.domain.models.GitRepository
import gitinsp.domain.models.Language
import gitinsp.domain.models.QdrantURL
import gitinsp.domain.models.StreamedResponse
import gitinsp.domain.models.URL

import scala.concurrent.ExecutionContext
import scala.util.Try

object PipelineService extends LazyLogging:
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

    def generateIndex(repository: GitRepository, regenerate: Boolean): Try[Unit] =
      Try {
        // Delete current repository if it exists
        if regenerate then ingestorService.deleteRepository(repository)

        // Ingest the new repository
        ingestorService.ingest(repository)
      }

    def regenerateIndex(repository: GitRepository): Try[Unit] =
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

    def deleteIndex(index: URL, category: Category): Try[Unit] =
      // When an index is removed delete the collection and the AI service
      if index == URL.default then
        for {
          _ <- cacheService.deleteAIService(index.toAIServiceURL())
          _ = cacheService.initializeAIServices(None)
        } yield ()
      else
        for {
          _ <- cacheService.deleteCollection(index.toQdrantURL(category))
          _ <- cacheService.deleteAIService(index.toAIServiceURL())
        } yield ()

    def initializeApp(): Try[Unit] =
      // Initialize the AI services
      for {
        qdrantCollections <- cacheService.listCollections()
        indexes      = qdrantCollections.map(QdrantURL(_)).distinctBy(_.value)
        repositories = GitRepository.from(indexes)
        _            = repositories.foreach(repo => cacheService.initializeAIServices(Some(repo)))
        _            = cacheService.initializeAIServices(None)
      } yield ()

    def buildRepository(url: URL, languages: List[Language]): Try[GitRepository] =
      // Used to build the repository for indexing
      githubWrapperService.buildRepository(url, languages)

    def fetchRepository(url: URL, languages: List[Language]): Try[String] =
      // Used to fetch repository content as a string for display in the UI
      githubWrapperService.fetchRepository(url, languages)

    def getAIService(index: Option[AIServiceURL]): Try[Assistant] =
      // Get the AI service from the cache (used when chatting)
      index match
        case Some(index) => cacheService.getAIService(index)
        case None        => cacheService.getAIService(AIServiceURL.default)
