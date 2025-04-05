package gitinsp.utils

import dev.langchain4j.service.TokenStream

trait Assistant:
  def chat(msg: String): TokenStream

object Assistant:
  def apply(): Assistant = new StreamingAssistantImpl()

  private class StreamingAssistantImpl extends Assistant:
    @SuppressWarnings(Array("org.wartremover.warts.Null")) // TODO: Implement
    override def chat(msg: String): TokenStream =
      null

object URLSanitizerService:
  def sanitize(url: String): String =
    url.replaceAll("^https?://", "").replace("/", "[slash]").replace(":", "[colon]")

  def unsanitize(url: String): String =
    url.replace("[colon]", ":").replace("[slash]", "/")
