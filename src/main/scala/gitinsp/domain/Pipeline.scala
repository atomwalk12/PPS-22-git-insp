package gitinsp.domain

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import gitinsp.infrastructure.CacheService
import gitinsp.utils.GitRepository
import gitinsp.utils.IndexName
import gitinsp.utils.Language
import gitinsp.utils.QdrantClientExtensions.listCollections

import scala.concurrent.ExecutionContext
import scala.util.Try

object Pipeline:
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

    def chat(message: String, index: Option[IndexName]): Try[Source[String, NotUsed]] =
      Try {
        index match
          case Some(idx) =>
            val aiservice = cacheService.getAIService(Some(idx))
            chatService.chat(message, aiservice)
          case None =>
            val aiservice = cacheService.getAIService(None)
            chatService.chat(message, aiservice)
      }

    def generateIndex(repository: GitRepository, regenerate: Boolean): Try[Unit] =
      // Delete current repository if it exists
      // Ingest the new repository
      Try {
        if regenerate then ingestorService.deleteRepository(repository)
        ingestorService.ingest(repository)
      }

    def regenerateIndex(repository: GitRepository): Try[Unit] =
      generateIndex(repository, true)

    def listIndexes(): Try[List[IndexName]] =
      val qdrantClient = cacheService.qdrantClient
      qdrantClient.listCollections().map(collections => collections.map(IndexName(_)))

    def fetchRepository(url: String, languages: List[Language]): Try[GitRepository] =
      githubWrapperService.buildRepository(url, languages)

trait Pipeline:
  def chat(message: String, index: Option[IndexName]): Try[Source[String, NotUsed]]
  def generateIndex(repository: GitRepository, regenerate: Boolean): Try[Unit]
  def regenerateIndex(repository: GitRepository): Try[Unit]
  def listIndexes(): Try[List[IndexName]]
  def fetchRepository(url: String, languages: List[Language]): Try[GitRepository]
