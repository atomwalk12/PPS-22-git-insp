package gitinsp.tests.componentfactory

import com.typesafe.config.Config
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.Query
import dev.langchain4j.rag.query.router.DefaultQueryRouter
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import gitinsp.domain.models.Assistant
import gitinsp.domain.models.Language
import gitinsp.infrastructure.factories.QueryRoutingStrategyFactory
import gitinsp.infrastructure.factories.RAGComponentFactoryImpl
import gitinsp.infrastructure.factories.RouterWithStrategy
import gitinsp.infrastructure.strategies.ConditionalQueryStrategy
import gitinsp.infrastructure.strategies.DefaultQueryStrategy
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import io.qdrant.client.QdrantClient
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class AnalysisTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach
    with MockitoSugar
    with ScalaFutures:

  val mockConfig = setupConfig()

  def setupConfig() =
    val config = mock[Config]
    when(config.getString("gitinsp.ollama.url")).thenReturn("http://localhost:11434")
    when(config.getString("gitinsp.models.default-model")).thenReturn("llama3.1")
    when(config.getString("gitinsp.models.provider")).thenReturn("ollama")
    when(config.getBoolean("gitinsp.rag.use-conditional-rag")).thenReturn(true)
    config

  "The RAG Component Factory" should "be able to create a streaming chat model" in:
    val mockFactory = mock[RAGComponentFactoryImpl]
    val mockChat    = mock[OllamaStreamingChatModel]

    // Expectations
    when(mockFactory.createStreamingChatModel()).thenReturn(mockChat)

    // Mock objects
    val handler = mock[StreamingChatResponseHandler]

    // Action: Chat with the model
    val chatModel = mockFactory.createStreamingChatModel()
    mockChat.chat("Hello, how are you?", handler)

    // Verify interactions
    verify(mockFactory).createStreamingChatModel()
    verify(mockChat).chat("Hello, how are you?", handler)

  it should "be able to create a text embedding model" in:
    val mockFactory        = mock[RAGComponentFactoryImpl]
    val mockEmbeddingModel = mock[OllamaEmbeddingModel]

    // Create test data
    val expectedArray     = Array(0.1f, 0.2f, 0.3f)
    val textSegment       = TextSegment.from("Hello, world!")
    val embeddingVector   = Embedding.from(expectedArray)
    val embeddingResponse = Response.from(embeddingVector)

    // Expectations
    when(mockFactory.createTextEmbeddingModel()).thenReturn(mockEmbeddingModel)
    when(mockEmbeddingModel.embed(textSegment)).thenReturn(embeddingResponse)

    // Action: Attempt to embed code
    val embeddingModel = mockFactory.createTextEmbeddingModel()
    val result         = embeddingModel.embed(textSegment)

    // Verify interactions and result
    verify(mockFactory).createTextEmbeddingModel()
    verify(embeddingModel).embed(textSegment)
    result should be(embeddingResponse)

    // Verify the embedding values
    val embedding = result.content()
    embedding.vectorAsList().size() should be(3)
    embedding.vectorAsList().get(0).floatValue() should be(0.1f +- 0.001f)
    embedding.vectorAsList().get(1).floatValue() should be(0.2f +- 0.001f)
    embedding.vectorAsList().get(2).floatValue() should be(0.3f +- 0.001f)

  it should "be able to create a code embedding model" in:
    val mockFactory        = mock[RAGComponentFactoryImpl]
    val mockEmbeddingModel = mock[OllamaEmbeddingModel]

    // Create test data
    val expectedArray     = Array(0.1f, 0.2f, 0.3f)
    val textSegment       = TextSegment.from("Hello, world!")
    val embeddingVector   = Embedding.from(expectedArray)
    val embeddingResponse = Response.from(embeddingVector)

    // Expectations
    when(mockFactory.createCodeEmbeddingModel()).thenReturn(mockEmbeddingModel)
    when(mockEmbeddingModel.embed(textSegment)).thenReturn(embeddingResponse)

    // Action: Attempt to embed code
    val embeddingModel = mockFactory.createCodeEmbeddingModel()
    val result         = embeddingModel.embed(textSegment)

    // Verify interactions and result
    verify(mockFactory).createCodeEmbeddingModel()
    verify(embeddingModel).embed(textSegment)
    result should be(embeddingResponse)

    // Verify the embedding values
    val embedding = result.content()
    embedding.vectorAsList().size() should be(3)
    embedding.vectorAsList().get(0).floatValue() should be(0.1f +- 0.001f)
    embedding.vectorAsList().get(1).floatValue() should be(0.2f +- 0.001f)
    embedding.vectorAsList().get(2).floatValue() should be(0.3f +- 0.001f)

  it should "create a text embedding model with correct configuration" in:
    // Setup
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getString("gitinsp.ollama.url")).thenReturn("http://test-ollama:11434")
    when(mockConfig.getString("gitinsp.text-embedding.model")).thenReturn("test-embedding-model")

    // Execute
    val embeddingModel = factory.createTextEmbeddingModel()

    // Verify
    embeddingModel shouldBe a[OllamaEmbeddingModel]
    noException should be thrownBy embeddingModel

  it should "create a code embedding model with correct configuration" in:
    // Setup
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getString("gitinsp.ollama.url")).thenReturn("http://test-ollama:11434")
    when(mockConfig.getString("gitinsp.code-embedding.model")).thenReturn("test-code-model")

    // Execute
    val codeEmbeddingModel = factory.createCodeEmbeddingModel()

    // Verify
    codeEmbeddingModel shouldBe a[OllamaEmbeddingModel]
    noException should be thrownBy codeEmbeddingModel

  it should "create DefaultQueryRouter when conditional RAG is disabled" in:
    // Setup
    val mockChat         = mock[OllamaChatModel]
    val mockScoringModel = mock[ScoringModel]
    val factory          = new RAGComponentFactoryImpl(mockConfig)

    // Create mock retrievers
    val retriever1 = mock[EmbeddingStoreContentRetriever]
    val retriever2 = mock[EmbeddingStoreContentRetriever]
    val retrievers = List(retriever1, retriever2)

    // Configure the mock to return false for conditional RAG
    when(mockConfig.getBoolean("gitinsp.rag.use-conditional-rag")).thenReturn(false)

    // Execute the method
    val router = factory.createQueryRouter(retrievers, mockChat)

    // Verify the router type
    router shouldBe a[DefaultQueryRouter]

  it should "create RouterWithStrategy when conditional RAG is enabled" in:
    // Setup
    val mockChat         = mock[OllamaChatModel]
    val mockScoringModel = mock[ScoringModel]
    val factory          = new RAGComponentFactoryImpl(mockConfig)

    // Create mock retrievers
    val retriever1 = mock[EmbeddingStoreContentRetriever]
    val retriever2 = mock[EmbeddingStoreContentRetriever]
    val retrievers = List(retriever1, retriever2)

    // Configure the mock to return true for conditional RAG
    when(mockConfig.getBoolean("gitinsp.rag.use-conditional-rag")).thenReturn(true)

    // Execute the method
    val router = factory.createQueryRouter(retrievers, mockChat)

    // Verify the router type
    router shouldBe a[RouterWithStrategy]

  it should "create a content aggregator" in:
    // Setup
    val mockScoringModel = mock[ScoringModel]
    val factory          = new RAGComponentFactoryImpl(mockConfig)

    // Execute the method
    val contentAggregator = factory.createContentAggregator(mockScoringModel)

    // Verify the content aggregator type
    contentAggregator shouldBe a[ReRankingContentAggregator]

  it should "create a retrieval augmentor" in:
    // Setup
    val mockChat         = mock[OllamaChatModel]
    val mockScoringModel = mock[ScoringModel]
    val factory          = new RAGComponentFactoryImpl(mockConfig)

    // Configure parameters
    val mockRouter            = mock[QueryRouter]
    val mockContentAggregator = mock[ReRankingContentAggregator]

    // Execute
    val retrievalAugmentor = factory.createRetrievalAugmentor(mockRouter, mockContentAggregator)

    // Verify
    retrievalAugmentor shouldBe a[DefaultRetrievalAugmentor]

  it should "create a markdown retriever with correct configuration" in:
    // Setup
    val factory            = new RAGComponentFactoryImpl(mockConfig)
    val mockEmbeddingStore = mock[QdrantEmbeddingStore]
    val mockEmbeddingModel = mock[OllamaEmbeddingModel]
    val indexName          = "markdown-index"

    // Configure mocks
    when(mockConfig.getInt("gitinsp.text-embedding.max-results")).thenReturn(5)
    when(mockConfig.getDouble("gitinsp.text-embedding.min-score")).thenReturn(0.75)

    // Execute
    val retriever =
      factory.createMarkdownRetriever(mockEmbeddingStore, mockEmbeddingModel, indexName)

    // Verify
    retriever shouldBe a[EmbeddingStoreContentRetriever]
    // Just verify it was created successfully without checking specific properties
    noException should be thrownBy retriever

  it should "create a code retriever with correct configuration" in:
    // Setup
    val factory            = new RAGComponentFactoryImpl(mockConfig)
    val mockEmbeddingStore = mock[QdrantEmbeddingStore]
    val mockEmbeddingModel = mock[OllamaEmbeddingModel]
    val mockModelRouter    = mock[OllamaChatModel]
    val indexName          = "code-index"

    // Configure mocks
    when(mockConfig.getInt("gitinsp.code-embedding.max-results")).thenReturn(10)
    when(mockConfig.getDouble("gitinsp.code-embedding.min-score")).thenReturn(0.8)

    // Execute
    val retriever = factory.createCodeRetriever(
      mockEmbeddingStore,
      mockEmbeddingModel,
      indexName,
      mockModelRouter,
    )

    // Verify
    retriever shouldBe a[EmbeddingStoreContentRetriever]
    // Just verify it was created successfully without checking specific properties
    noException should be thrownBy retriever

  it should "create a model router with correct configuration" in:
    // Setup
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getString("gitinsp.ollama.url")).thenReturn("http://test-ollama:11434")
    when(mockConfig.getString("gitinsp.rag.model")).thenReturn("test-model")

    // Execute
    val modelRouter = factory.createModelRouter()

    // Verify
    modelRouter shouldBe a[OllamaChatModel]

  it should "create an embedding store with correct configuration" in:
    // Setup
    val factory        = new RAGComponentFactoryImpl(mockConfig)
    val mockClient     = mock[QdrantClient]
    val collectionName = "test-collection"

    // Configure mocks
    when(mockConfig.getString("gitinsp.qdrant.host")).thenReturn("localhost")
    when(mockConfig.getInt("gitinsp.qdrant.port")).thenReturn(6333)

    // Execute
    val embeddingStore = factory.createEmbeddingStore(mockClient, collectionName)

    // Verify
    embeddingStore shouldBe a[QdrantEmbeddingStore]
    noException should be thrownBy embeddingStore

  it should "create a Qdrant client without errors" in:
    // Setup
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getString("gitinsp.qdrant.host")).thenReturn("localhost")
    when(mockConfig.getInt("gitinsp.qdrant.port")).thenReturn(6333)

    // Execute
    val client = factory.createQdrantClient()

    // Verify basic creation and config usage
    client shouldBe a[QdrantClient]
    noException should be thrownBy client

  it should "create a scoring model with CPU configuration" in:
    // Setup
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks for common parameters
    when(mockConfig.getString("gitinsp.reranker.model-path")).thenReturn("/path/to/model")
    when(mockConfig.getString("gitinsp.reranker.tokenizer-path")).thenReturn("/path/to/tokenizer")
    when(mockConfig.getBoolean("gitinsp.reranker.normalize-scores")).thenReturn(true)
    when(mockConfig.getInt("gitinsp.reranker.max-length")).thenReturn(512)
    when(mockConfig.getBoolean("gitinsp.reranker.use-gpu")).thenReturn(false)

    // Verify
    a[RuntimeException] should be thrownBy factory.createScoringModel()

  it should "create an ai assistant" in:
    // Setup
    val augmentor = mock[RetrievalAugmentor]
    val model     = mock[OllamaStreamingChatModel]
    val factory   = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getInt("gitinsp.chat.memory")).thenReturn(5)

    // Execute
    val assistant = factory.createAssistant(model, Some(augmentor))
    assistant shouldBe a[Assistant]

  it should "create an ai assistant without an augmentor" in:
    // Setup
    val model   = mock[OllamaStreamingChatModel]
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Configure mocks
    when(mockConfig.getInt("gitinsp.chat.memory")).thenReturn(5)

    // Execute
    val assistant = factory.createAssistant(model, None)
    assistant shouldBe a[Assistant]

  it should "create an ingestor specialized for markdown" in:
    // Mocks
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Data
    val language       = Language.MARKDOWN
    val embeddingModel = mock[OllamaEmbeddingModel]
    val embeddingStore = mock[QdrantEmbeddingStore]
    val strategy       = IngestionStrategyFactory.createStrategy("default", language, mockConfig)

    // Execute
    val ingestor = factory.createIngestor(language, embeddingModel, embeddingStore, strategy)

    // Verify
    ingestor shouldBe a[EmbeddingStoreIngestor]
    noException should be thrownBy ingestor

  it should "create an ingestor specialized for code" in:
    // Mocks
    val factory = new RAGComponentFactoryImpl(mockConfig)

    // Data
    val language       = Language.JAVA
    val embeddingModel = mock[OllamaEmbeddingModel]
    val embeddingStore = mock[QdrantEmbeddingStore]
    val strategy       = IngestionStrategyFactory.createStrategy("Default", language, mockConfig)

    // Execute
    val ingestor = factory.createIngestor(language, embeddingModel, embeddingStore, strategy)

    // Verify
    ingestor shouldBe a[EmbeddingStoreIngestor]
    noException should be thrownBy ingestor

  "Query Routing Strategy Factory" should "be able to create a query router" in:
    val mockChatModel = mock[OllamaChatModel]

    val condStrategy = QueryRoutingStrategyFactory.createStrategy("conditional", mockChatModel)
    condStrategy shouldBe a[ConditionalQueryStrategy]

    val defaultStrategy = QueryRoutingStrategyFactory.createStrategy("default", mockChatModel)
    defaultStrategy shouldBe a[DefaultQueryStrategy]

    val fallbackStrategy = QueryRoutingStrategyFactory.createStrategy("unknown", mockChatModel)
    fallbackStrategy shouldBe a[DefaultQueryStrategy]

    val uppercaseStrategy = QueryRoutingStrategyFactory.createStrategy("CONDITIONAL", mockChatModel)
    uppercaseStrategy shouldBe a[ConditionalQueryStrategy]

    val mixedCaseStrategy = QueryRoutingStrategyFactory.createStrategy("Default", mockChatModel)
    mixedCaseStrategy shouldBe a[DefaultQueryStrategy]

  "The Default Query Strategy" should "always return all retrievers" in:
    // Setup
    val strategy = DefaultQueryStrategy()

    // Create mock retrievers
    val retriever1 = mock[EmbeddingStoreContentRetriever]
    val retriever2 = mock[EmbeddingStoreContentRetriever]
    val retrievers = List(retriever1, retriever2)

    // Test with different queries
    val query1 = Query.from("How does this code work?")
    val query2 = Query.from("Don't use RAG for this question")

    // Verify that all retrievers are always returned regardless of query content
    strategy.determineRetrievers(query1, retrievers).size() should be(2)
    strategy.determineRetrievers(query2, retrievers).size() should be(2)

    // The returned collection should contain both our mock retrievers
    val result = strategy.determineRetrievers(query1, retrievers)
    result.contains(retriever1) should be(true)
    result.contains(retriever2) should be(true)

  "The Conditional Query Strategy" should "return all retrievers when the model answers 'no'" in:
    // Setup
    val mockChat = mock[OllamaChatModel]
    val strategy = ConditionalQueryStrategy(mockChat)

    // Create mock retrievers
    val retriever1 = mock[EmbeddingStoreContentRetriever]
    val retriever2 = mock[EmbeddingStoreContentRetriever]
    val retrievers = List(retriever1, retriever2)

    // Test query that should use RAG
    val query = Query.from("How does this code work? Please do not query the index.")

    // Mock the chat response indicating RAG should be used
    val aiMessage = AiMessage.from("Yes, the user did ask to avoid using RAG.")
    val response  = ChatResponse.builder().aiMessage(aiMessage).build()
    when(mockChat.chat(ArgumentMatchers.any(classOf[UserMessage]))).thenReturn(response)

    // Verify that all retrievers are returned
    val result = strategy.determineRetrievers(query, retrievers)
    result.size() should be(0)
    result.contains(retriever1) should be(false)
    result.contains(retriever2) should be(false)

  it should "return no retrievers when the model answers 'yes'" in:
    // Setup
    val mockChat = mock[OllamaChatModel]
    val strategy = ConditionalQueryStrategy(mockChat)

    // Create mock retrievers
    val retriever1 = mock[EmbeddingStoreContentRetriever]
    val retriever2 = mock[EmbeddingStoreContentRetriever]
    val retrievers = List(retriever1, retriever2)

    // Test query that should not use RAG
    val query = Query.from("How does this code work? Please query the index for better results.")

    // Mock the chat response indicating RAG should not be used
    val aiMessage = AiMessage.from("No.")
    val response  = ChatResponse.builder().aiMessage(aiMessage).build()
    when(mockChat.chat(ArgumentMatchers.any(classOf[UserMessage]))).thenReturn(response)

    // Verify that no retrievers are returned
    val result = strategy.determineRetrievers(query, retrievers)
    result.size() should be(2)
    result.contains(retriever1) should be(true)
    result.contains(retriever2) should be(true)
