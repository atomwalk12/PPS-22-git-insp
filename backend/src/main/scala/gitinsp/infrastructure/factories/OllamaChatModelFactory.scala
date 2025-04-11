package gitinsp.infrastructure.factories

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import gitinsp.domain.interfaces.infrastructure.ChatModelFactory

object OllamaChatModelFactory:
  def apply(config: Config): ChatModelFactory =
    OllamaChatModelFactoryImpl(config)

/** Ollama implementation of ChatModelFactory.
  * This factory creates chat models that connect to an Ollama server.
  *
  * @param config The application configuration
  */
class OllamaChatModelFactoryImpl(config: Config) extends ChatModelFactory with LazyLogging:

  /** Creates a standard (non-streaming) chat model.
    *
    * @return An OllamaChatModel
    */
  override def createChatModel(): ChatLanguageModel =
    val modelName = config.getString("gitinsp.models.default-model")
    val baseUrl   = config.getString("gitinsp.ollama.url")

    logger.debug(s"Creating chat model with model=$modelName, url=$baseUrl")
    OllamaChatModel
      .builder()
      .baseUrl(baseUrl)
      .modelName(modelName)
      .build()

  /** Creates a streaming chat model.
    *
    * @return An OllamaStreamingChatModel
    */
  override def createStreamingChatModel(): StreamingChatLanguageModel =
    val modelName = config.getString("gitinsp.models.default-model")
    val baseUrl   = config.getString("gitinsp.ollama.url")

    logger.debug(s"Creating streaming chat model with model=$modelName, url=$baseUrl")
    OllamaStreamingChatModel
      .builder()
      .baseUrl(baseUrl)
      .modelName(modelName)
      .build()
