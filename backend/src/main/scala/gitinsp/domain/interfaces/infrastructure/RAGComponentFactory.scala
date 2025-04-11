package gitinsp.domain.interfaces.infrastructure
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.model.scoring.ScoringModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.QueryRouter
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import gitinsp.domain.models.Assistant
import gitinsp.domain.models.Language
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.Collections.Distance

import scala.util.Try

/** Factory trait for creating components of the Retrieval Augmented Generation (RAG) pipeline.
  * This interface allows for different implementations and configurations of RAG components.
  */
trait RAGComponentFactory:

  /** Creates a specified retriever type. The retriever is used to embed documents using the
    * embedding model and store them in the embedding store.
    *
    * @param embeddingStore The embedding store
    * @param embeddingModel The embedding model
    * @param indexName The specific collection to be used
    * @return A retriever for the specified index
    */
  def createMarkdownRetriever(
    embeddingStore: QdrantEmbeddingStore,
    embeddingModel: OllamaEmbeddingModel,
    indexName: String,
  ): EmbeddingStoreContentRetriever

  /** Creates a retriever for code.
    *
    * @param embeddingStore The embedding store
    * @param embeddingModel The embedding model
    * @param indexName The specific collection to be used
    * @param dynamicFilter Allows to filter the documents based on a specific language
    * @return A retriever for the specified index
    */
  def createCodeRetriever(
    embeddingStore: QdrantEmbeddingStore,
    embeddingModel: OllamaEmbeddingModel,
    indexName: String,
    modelRouter: OllamaChatModel,
  ): EmbeddingStoreContentRetriever

  /** Creates a QueryRouter based on the provided retrievers
    *
    * @param retrievers List of content retrievers to use
    * @return A configured QueryRouter
    */
  def createQueryRouter(
    retrievers: List[EmbeddingStoreContentRetriever],
    modelRouter: OllamaChatModel,
  ): QueryRouter

  /** Creates a content aggregator for ranking and filtering retrieved content.
    * This allows to rerank results, potentially yielding more relevant content.
    *
    * @return A configured ReRankingContentAggregator
    */
  def createContentAggregator(scoringModel: ScoringModel): ReRankingContentAggregator

  /** Creates a RetrievalAugmentor that combines router and aggregator
    *
    * @param router The QueryRouter to use
    * @param aggregator The ContentAggregator to use
    * @return A configured DefaultRetrievalAugmentor
    */
  def createRetrievalAugmentor(
    router: QueryRouter,
    aggregator: ReRankingContentAggregator,
  ): DefaultRetrievalAugmentor

  /** Creates an embedding model for text.
    *
    * @return An OllamaEmbeddingModel
    */
  def createTextEmbeddingModel(): OllamaEmbeddingModel

  /** Creates an embedding model for code.
    *
    * @return An OllamaEmbeddingModel
    */
  def createCodeEmbeddingModel(): OllamaEmbeddingModel

  /** Creates a streaming chat model.
    *
    * @return A streaming chat model implementation
    */
  def createStreamingChatModel(): StreamingChatLanguageModel

  /** Create model router
    *
    * @return An OllamaChatModel
    */
  def createModelRouter(): OllamaChatModel

  /** Creates a StreamingAssistant. This service is the main entry point for the RAG pipeline.
    *
    * @param model The chat model
    * @param augmentor The retrieval augmentor
    * @return A StreamingAssistant
    */
  def createAssistant(
    model: StreamingChatLanguageModel,
    augmentor: Option[RetrievalAugmentor],
  ): Assistant

  /** Creates an embedding store.
    *
    * @return A QdrantEmbeddingStore
    */
  def createEmbeddingStore(client: QdrantClient, name: String): QdrantEmbeddingStore

  /** Creates a Qdrant client.
    *
    * @return A QdrantClient
    */
  def createQdrantClient(): QdrantClient

  /** Creates a scoring model. It is used to rerank documents (potentially yield better results).
    *
    * @return A ScoringModel
    */
  def createScoringModel(): ScoringModel

  /** Creates an ingestor.
    *
    * @return An EmbeddingStoreIngestor
    */
  def createIngestor(
    language: Language,
    embeddingModel: OllamaEmbeddingModel,
    embeddingStore: QdrantEmbeddingStore,
    strategy: IngestionStrategy,
  ): EmbeddingStoreIngestor

  /** Creates a collection in Qdrant.
    *
    * @param name The name of the collection
    */
  def createCollection(name: String, client: QdrantClient, distance: Distance): Try[Unit]
