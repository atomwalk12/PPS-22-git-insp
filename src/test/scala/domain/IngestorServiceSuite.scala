package gitinsp.tests.domain

import com.google.common.util.concurrent.Futures as F
import com.typesafe.config.Config
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import gitinsp.chatpipeline.RAGComponentFactory
import gitinsp.domain.IngestorService
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import gitinsp.utils.GitDocument
import gitinsp.utils.GitRepository
import gitinsp.utils.IngestorServiceExtensions.ingest
import gitinsp.utils.Language
import io.qdrant.client.QdrantClient
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.util.Collections as C

import scala.concurrent.ExecutionContext
import scala.util.Success

class CacheServiceSuite
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar
    with ScalaFutures:

  // Create a dedicated execution context for tests
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
    java.util.concurrent.Executors.newFixedThreadPool(4),
  )

  // Create the mocks
  val config             = mock[Config]
  val mockFactory        = spy(RAGComponentFactory(config))
  val mockQdrantClient   = mock[QdrantClient]
  val scoringModel       = mock[ScoringModel]
  val mockEmbeddingModel = mock[OllamaEmbeddingModel]
  val mockIngestor       = mock[EmbeddingStoreIngestor]

  override def beforeAll(): Unit =
    // Setup spies and mocks
    doReturn(mockQdrantClient).when(mockFactory).createQdrantClient()
    doReturn(F.immediateFuture(C.emptyList[String]())).when(mockQdrantClient).listCollectionsAsync()
    doReturn(scoringModel).when(mockFactory).createScoringModel()
    doReturn(mockEmbeddingModel).when(mockFactory).createTextEmbeddingModel()
    doReturn(mockEmbeddingModel).when(mockFactory).createCodeEmbeddingModel()
    doReturn(mockIngestor).when(mockFactory).createIngestor(any, any, any, any)
    doReturn(Success(())).when(mockFactory).createCollection(any(), any(), any())

    when(config.getString("gitinsp.ollama.url")).thenReturn("http://localhost:11434")
    when(config.getString("gitinsp.code-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.text-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.models.default-model")).thenReturn("llama3.3")
    when(config.getString("gitinsp.rag.model")).thenReturn("llama3.3")
    when(config.getInt("gitinsp.code-embedding.chunk-size")).thenReturn(1000)
    when(config.getInt("gitinsp.text-embedding.chunk-size")).thenReturn(1000)
    when(config.getInt("gitinsp.code-embedding.chunk-overlap")).thenReturn(200)
    when(config.getInt("gitinsp.text-embedding.chunk-overlap")).thenReturn(200)
    when(config.getInt("gitinsp.qdrant.dimension")).thenReturn(768)

  "IngestorService" should "create an ingestor for markdown" in:
    // Data
    val url        = "https://github.com/gitinsp/gitinsp"
    val languages  = "md,scala"
    val doc1       = GitDocument("Hello, world!", Language.MARKDOWN, "README.md")
    val doc2       = GitDocument("Hello, world!", Language.SCALA, "README.scala")
    val docs       = List(doc1, doc2)
    val repository = GitRepository(url, GitRepository.detectLanguages(languages), docs)

    // Mocks
    val mockCacheService = spy(CacheService(mockFactory))

    // Execute
    val ingestorService = IngestorService(mockCacheService, config)
    ingestorService.ingest(repository)

    // Verify
    repository.indexNames.foreach {
      index =>
        val strategy = IngestionStrategyFactory.createStrategy("default", index.language, config)

        verify(mockIngestor, times(1)).ingest(repository, index.language) // once for each language
    }
