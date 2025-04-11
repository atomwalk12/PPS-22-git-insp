package gitinsp.infrastructure.factories

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import gitinsp.domain.interfaces.infrastructure.ChatModelFactory

import java.util.Locale

/** Provider that creates appropriate ChatModelFactory instances based on configuration.
  * This allows for runtime selection of different model providers.
  */
object ChatModelFactoryProvider extends LazyLogging:
  /** Creates a ChatModelFactory based on the configured provider.
    *
    * @param config The application configuration
    * @return A ChatModelFactory implementation for the specified provider
    */
  def create(config: Config): ChatModelFactory =
    val provider = config.getString("gitinsp.models.provider")

    logger.info(s"Creating ChatModelFactory for provider: $provider")

    provider.toLowerCase(Locale.ROOT) match
      case "ollama" => OllamaChatModelFactory(config)
      case "gemini" => GeminiChatModelFactory(config)
      case "claude" => ClaudeFactoryProvider(config)
      case _ =>
        logger.warn(s"Unknown provider '$provider', falling back to Ollama")
        OllamaChatModelFactory(config)
