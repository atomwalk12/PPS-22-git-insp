package gitinsp.infrastructure.factories

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel
import gitinsp.domain.interfaces.infrastructure.ChatModelFactory

object GeminiChatModelFactory:
  def apply(config: Config): ChatModelFactory =
    GeminiChatModelFactoryImpl(config)

/** Gemini implementation of ChatModelFactory.
  * This factory creates chat models that connect to an Claude server.
  *
  * @param config The application configuration
  */
class GeminiChatModelFactoryImpl(config: Config) extends ChatModelFactory with LazyLogging:

  val modelName = config.getString("gitinsp.gemini.model")
  val project   = config.getString("gitinsp.gemini.project")
  val location  = config.getString("gitinsp.gemini.location")

  /** Creates a standard (non-streaming) chat model.
    *
    * @return A GeminiChatModel
    */
  override def createChatModel(): ChatLanguageModel =
    logger.debug(s"Creating chat model with model=$modelName, project=$project")
    VertexAiGeminiChatModel.builder()
      .project(project)
      .location(location)
      .modelName(modelName)
      .build();

  /** Creates a streaming chat model.
    *
    * @return A GeminiStreamingChatModel
    */
  override def createStreamingChatModel(): StreamingChatLanguageModel =
    logger.debug(s"Creating streaming chat model with model=$modelName, project=$project")
    VertexAiGeminiStreamingChatModel.builder()
      .project(project)
      .location(location)
      .modelName(modelName)
      .build()
