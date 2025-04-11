package gitinsp.tests.cacheservice

import com.typesafe.config.Config
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import gitinsp.domain.models.Language
import gitinsp.domain.models.QdrantURL
import gitinsp.domain.models.RepositoryWithLanguages
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.factories.RAGComponentFactory
import gitinsp.infrastructure.strategies.IngestionStrategyFactory as ISF
import io.qdrant.client.QdrantClient
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext

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
  val config           = setupConfig()
  val mockFactory      = spy(RAGComponentFactory(config))
  val mockQdrantClient = mock[QdrantClient]
  val scoringModel     = mock[ScoringModel]

  def setupConfig() =
    val config = mock[Config]
    // Setup the configuration
    when(config.getString("gitinsp.ollama.url")).thenReturn("http://localhost:11434")
    when(config.getString("gitinsp.code-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.text-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.models.default-model")).thenReturn("llama3.3")
    when(config.getString("gitinsp.rag.model")).thenReturn("llama3.3")
    when(config.getInt("gitinsp.chat.memory")).thenReturn(10)
    when(config.getString("gitinsp.models.provider")).thenReturn("ollama")
    config

  override def beforeAll(): Unit =
    // Setup the mock to return our mock client
    doReturn(mockQdrantClient).when(mockFactory).createQdrantClient()
    doReturn(scoringModel).when(mockFactory).createScoringModel()

  "CacheService" should "run without any exceptions" in:
    // Setup the data
    val indexURL1         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-code")
    val indexURL2         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-text")
    val qdrantCollections = List(indexURL1, indexURL2)
    val repositories      = RepositoryWithLanguages.from(qdrantCollections)

    // Get the CacheService implementation
    val cache = CacheService(mockFactory)

    // Execute and verify
    repositories.foreach(r => noException should be thrownBy cache.initializeAIServices(Some(r)))

  it should "return the same AI service instance for the same repository name" in:
    // Data setup
    val indexURL1         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-code")
    val indexURL2         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-text")
    val qdrantCollections = List(indexURL1, indexURL2, indexURL1) // Duplicate indexURL1
    val repository        = RepositoryWithLanguages.from(qdrantCollections)

    // Services
    val cacheService = CacheService(mockFactory)

    // Execute and verify
    repository.foreach(
      repo => {
        val aiService1 = cacheService.initializeAIServices(Some(repo))
        val aiService2 = cacheService.initializeAIServices(Some(repo))

        // Verify
        aiService1 should be theSameInstanceAs aiService2
      },
    )

  it should "create an ingestor for a specific language" in:
    // Data setup
    val cacheService      = CacheService(mockFactory)
    val ingestionStrategy = ISF.createStrategy("default", Language.SCALA, config)
    val indexName         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-code")

    // Setup behavior
    when(mockFactory.createQdrantClient()).thenReturn(mockQdrantClient)

    // Execute
    val ingestor = cacheService.getIngestor(indexName, Language.SCALA, ingestionStrategy)

    // Verify
    ingestor shouldBe a[EmbeddingStoreIngestor]

  it should "create an ingestor for markdown" in:
    when(mockFactory.createQdrantClient()).thenReturn(mockQdrantClient)
    // Data
    val cacheService      = CacheService(mockFactory)
    val ingestionStrategy = ISF.createStrategy("default", Language.MARKDOWN, config)
    val indexName         = QdrantURL("github.com[slash]langchain-ai[slash]langchain-text")

    // Execute
    val ingestor = cacheService.getIngestor(indexName, Language.MARKDOWN, ingestionStrategy)

    // Verify
    ingestor shouldBe a[EmbeddingStoreIngestor]
