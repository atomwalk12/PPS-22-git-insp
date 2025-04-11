package gitinsp.domain.interfaces.infrastructure

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel

/** Factory trait for creating chat models from different providers.
  * This abstraction allows for switching between different model implementations (Ollama, Gemini).
  */
trait ChatModelFactory:
  /** Creates a chat model for standard (non-streaming) interactions.
    *
    * @return A ChatLanguageModel implementation specific to the provider
    */
  def createChatModel(): ChatLanguageModel

  /** Creates a streaming chat model.
    *
    * @return A streaming chat model implementation specific to the provider
    */
  def createStreamingChatModel(): StreamingChatLanguageModel
