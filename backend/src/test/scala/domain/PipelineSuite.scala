package gitinsp.tests.pipe
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.google.common.util.concurrent.Futures as F
import com.typesafe.config.Config
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.TokenStream
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import gitinsp.domain.ChatService
import gitinsp.domain.IngestorService
import gitinsp.domain.Pipeline
import gitinsp.domain.interfaces.application.ChatService
import gitinsp.domain.interfaces.application.IngestorService
import gitinsp.domain.interfaces.infrastructure.CacheService
import gitinsp.domain.interfaces.infrastructure.GithubWrapperService
import gitinsp.domain.interfaces.infrastructure.RAGComponentFactory
import gitinsp.domain.models.Assistant
import gitinsp.domain.models.Category
import gitinsp.domain.models.CodeFile
import gitinsp.domain.models.Language
import gitinsp.domain.models.RepositoryWithLanguages
import gitinsp.domain.models.URL
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.ContentService
import gitinsp.infrastructure.factories.RAGComponentFactory
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import io.qdrant.client.QdrantClient
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import java.util.Collections as C

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Success

class PipelineTest extends AnyFlatSpec with Matchers with MockitoSugar with BeforeAndAfterAll:

  val config                                      = mock[Config]
  implicit val system: ActorSystem                = ActorSystem("pipeline-test-system")
  implicit val materializer: Materializer         = Materializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher

  val mockRAGFactory      = mock[RAGComponentFactory]
  val mockIngestorService = mock[IngestorService]
  val mockChatService     = spy(ChatService(false, ContentService))
  val mockCacheService    = spy(CacheService(mockRAGFactory))

  override def beforeAll(): Unit =
    when(config.getString("gitinsp.ollama.url")).thenReturn("http://localhost:11434")
    when(config.getString("gitinsp.code-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.text-embedding.model")).thenReturn("nomic-embed-text")
    when(config.getString("gitinsp.models.default-model")).thenReturn("llama3.3")
    when(config.getString("gitinsp.rag.model")).thenReturn("llama3.3")
    when(config.getInt("gitinsp.code-embedding.chunk-size")).thenReturn(1000)
    when(config.getInt("gitinsp.text-embedding.chunk-size")).thenReturn(1000)
    when(config.getInt("gitinsp.code-embedding.chunk-overlap")).thenReturn(200)
    when(config.getInt("gitinsp.text-embedding.chunk-overlap")).thenReturn(200)
    when(config.getString("gitinsp.models.provider")).thenReturn("ollama")

  "Pipeline" should "allow dependency injection for easier testing" in:
    // Create mocks
    val mockChatService   = mock[ChatService]
    val mockCacheService  = mock[CacheService]
    val mockAssistant     = mock[Assistant]
    val mockResponse      = Source.single("Mocked response")
    val mockGithubWrapper = mock[GithubWrapperService]

    // Set up mocks behavior
    when(mockCacheService.initializeAIServices(None)).thenReturn(mockAssistant)
    when(mockCacheService.listCollections()).thenReturn(Success(List()))
    when(mockChatService.chat("Test query", mockAssistant)).thenReturn(mockResponse)
    when(mockCacheService.getAIService(any())).thenReturn(Success(mockAssistant))

    // Execute the pipeline
    val pipe  = Pipeline(mockChatService, mockCacheService, mockIngestorService, mockGithubWrapper)
    val index = None
    val aiService = pipe.getAIService(index)

    // Verify
    aiService.isSuccess shouldBe true
    aiService match
      case Success(aiService) =>
        val chatResult = pipe.chat("Test query", aiService)
        chatResult.fold(
          (ex: Throwable) => fail(s"Expected success but got failure: $ex"),
          (response: Source[String, NotUsed]) => response shouldBe mockResponse,
        )
      case _ => fail("Expected success but got failure")

    // Verify
    verify(mockCacheService).initializeAIServices(None)
    verify(mockChatService).chat("Test query", mockAssistant)

  it should "assert that the chat pipeline works" in:
    // Setup mocks and real classes
    val mockAssistant      = mock[Assistant]
    val mockStreamingModel = mock[OllamaStreamingChatModel]
    val mockAugmentor      = mock[RetrievalAugmentor]
    val mockTokenStream    = mock[TokenStream]
    val mockGithubWrapper  = mock[GithubWrapperService]

    val pipe = Pipeline(mockChatService, mockCacheService, mockIngestorService, mockGithubWrapper)
    val url  = URL("https://github.com/atomwalk12/PPS-22-git-insp")
    val repository = RepositoryWithLanguages(
      url,
      List(Language.SCALA, Language.MARKDOWN),
      List(),
    )

    // Setup data
    val mockSource = Source(List("Test response 1", "Test response 2"))

    // Setup behaviour
    when(mockTokenStream.onRetrieved(any())).thenReturn(mockTokenStream)
    when(mockTokenStream.onPartialResponse(any())).thenReturn(mockTokenStream)
    when(mockTokenStream.onCompleteResponse(any())).thenReturn(mockTokenStream)
    when(mockTokenStream.onError(any())).thenReturn(mockTokenStream)
    when(mockAssistant.chat(anyString())).thenReturn(mockTokenStream)
    doReturn(mockAssistant).when(mockRAGFactory).createAssistant(any(), any())
    doReturn(mockSource).when(mockChatService).chat(any(), any())

    // Initialize with a repository (for an AIService)
    pipe.regenerateIndex(repository)

    // Execute
    val aiService = pipe.getAIService(Some(url.toAIServiceURL()))
    aiService.isSuccess shouldBe true
    aiService.map {
      assistant =>
        val tryResult = pipe.chat("Test query", assistant)
        tryResult.fold(
          (ex: Throwable) => fail(s"Expected success but got failure: $ex"),
          (result: Source[String, NotUsed]) => {
            val future  = result.runWith(Sink.seq)
            val results = Await.result(future, 5.seconds)

            // Verify
            verify(mockCacheService).getAIService(url.toAIServiceURL())
            results should contain theSameElementsAs List("Test response 1", "Test response 2")
          },
        )
    }

  it should "ingest the repository" in:
    // Mocks
    val mockQdrantClient   = mock[QdrantClient]
    val scoringModel       = mock[ScoringModel]
    val mockEmbeddingModel = mock[OllamaEmbeddingModel]
    val mockIngestor       = mock[EmbeddingStoreIngestor]
    val mockFactory        = spy(RAGComponentFactory(config))
    val mockGithubWrapper  = mock[GithubWrapperService]

    // Setup mocks
    doReturn(mockIngestor).when(mockCacheService).getIngestor(any, any, any)
    doReturn(F.immediateFuture(C.emptyList[String]())).when(mockQdrantClient).listCollectionsAsync()
    doReturn(mockQdrantClient).when(mockFactory).createQdrantClient()
    doReturn(scoringModel).when(mockFactory).createScoringModel()
    doReturn(mockEmbeddingModel).when(mockFactory).createTextEmbeddingModel()
    doReturn(mockEmbeddingModel).when(mockFactory).createCodeEmbeddingModel()
    doReturn(mockIngestor).when(mockFactory).createIngestor(any, any, any, any)

    when(mockCacheService.qdrantClient).thenReturn(mockQdrantClient)
    when(mockFactory.createScoringModel()).thenReturn(scoringModel)

    // Setup data
    val ingestorService = IngestorService(mockCacheService, config, IngestionStrategyFactory)

    // Setup data
    val pipe      = Pipeline(mockChatService, mockCacheService, ingestorService, mockGithubWrapper)
    val languages = RepositoryWithLanguages.detectLanguages("scala,md")
    val doc1      = CodeFile("def test()", Language.SCALA, "test.scala")
    val doc2      = CodeFile("# Hello, world!", Language.MARKDOWN, "test.md")
    val docs      = List(doc1, doc2, doc1, doc2)
    val url       = URL("https://github.com/atomwalk12/PPS-22-git-insp")
    val repo      = RepositoryWithLanguages(url, languages, docs)

    // Execute
    pipe.regenerateIndex(repo)

    // Verify
    repo.indexNames.zip(repo.languages).foreach {
      case (index, language) =>
        val strategy = IngestionStrategyFactory.createStrategy("default", language, config)

        import gitinsp.domain.models.IngestorServiceExtensions.ingest
        verify(mockIngestor, times(2)).ingest(repo, language)
    }

  it should "list indexes" in:
    // Setup data
    val url1      = URL("https://github.com/atomwalk12/PPS-22-git-insp")
    val url2      = URL("https://github.com/atom/PPS-22-git-insp")
    val index1    = url1.toQdrantURL(Category.TEXT)
    val index2    = url2.toQdrantURL(Category.CODE)
    val indexes   = List(index1, index2)
    val mockValue = F.immediateFuture(java.util.Arrays.asList(index1.value, index2.value))

    // Setup mocks
    val mockQdrantClient    = mock[QdrantClient]
    val mockChatService     = mock[ChatService]
    val mockIngestorService = mock[IngestorService]
    val mockGithubWrapper   = mock[GithubWrapperService]

    // Setup behaviour
    when(mockQdrantClient.listCollectionsAsync()).thenReturn(mockValue)
    when(mockRAGFactory.createQdrantClient()).thenReturn(mockQdrantClient)
    when(mockQdrantClient.listCollectionsAsync()).thenReturn(mockValue)

    // Execute
    val mockCacheService = spy(CacheService(mockRAGFactory))

    val pipe = Pipeline(mockChatService, mockCacheService, mockIngestorService, mockGithubWrapper)
    val tryResults = pipe.listIndexes()

    tryResults.isSuccess shouldBe true
    val results = tryResults.fold(
      ex => fail(s"Expected success but got failure: $ex"),
      results => results,
    )
    // Asert
    results should have length 2
    results should contain(index1.buildAIServiceURL())
    results should contain(index2.buildAIServiceURL())

  it should "throw an exception when the qdrant client fails" in:
    // Setup mocks
    val mockGithubWrapper = mock[GithubWrapperService]
    val mockQdrantClient  = mock[QdrantClient]

    // Behaviour
    when(mockQdrantClient.listCollectionsAsync()).thenThrow(new RuntimeException("Failure"))
    when(mockRAGFactory.createQdrantClient()).thenReturn(mockQdrantClient)

    // Dependencies
    val mockCacheService = spy(CacheService(mockRAGFactory))
    when(mockCacheService.qdrantClient).thenReturn(mockQdrantClient)

    // Execute
    val pipe = Pipeline(mockChatService, mockCacheService, mockIngestorService, mockGithubWrapper)

    // Use intercept instead of throwAn
    val tryResults = pipe.listIndexes()

    // Verify
    tryResults.isFailure shouldBe true
    tryResults.failed.fold(
      ex => ex shouldBe a[RuntimeException],
      _ => fail("Expected failure but got success"),
    )
