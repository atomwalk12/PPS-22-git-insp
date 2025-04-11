package gitinsp.infrastructure.factories

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import gitinsp.domain.interfaces.infrastructure.ChatModelFactory

object ClaudeFactoryProvider:
  def apply(config: Config): ChatModelFactory =
    ClaudeFactoryProviderImpl(config)

/** Claude implementation of ChatModelFactory.
  * This factory creates chat models that connect to an Claude server.
  *
  * @param config The application configuration
  */
class ClaudeFactoryProviderImpl(config: Config) extends ChatModelFactory with LazyLogging:

  val modelName       = config.getString("gitinsp.claude.model")
  val envVariable     = config.getString("gitinsp.claude.antrophic-api-key")
  val antrophicApiKey = System.getenv(envVariable)

  /** Creates a standard (non-streaming) chat model.
    *
    * @return A ClaudeChatModel
    */
  override def createChatModel(): ChatLanguageModel =
    logger.debug(s"Creating chat model with model=$modelName")
    AnthropicChatModel.builder()
      .apiKey(antrophicApiKey)
      .modelName(modelName)
      .logRequests(true)
      .logResponses(true)
      .build();

  /** Creates a streaming chat model.
    *
    * @return A ClaudeStreamingChatModel
    */
  override def createStreamingChatModel(): StreamingChatLanguageModel =
    logger.debug(s"Creating chat model with model=$modelName")
    AnthropicStreamingChatModel.builder()
      .apiKey(antrophicApiKey)
      .modelName(modelName)
      .logRequests(true)
      .build();
