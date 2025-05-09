package gitinsp.infrastructure

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import gitinsp.domain.interfaces.infrastructure.FetchingService
import gitinsp.domain.interfaces.infrastructure.GithubWrapperService
import gitinsp.domain.models.CodeFile
import gitinsp.domain.models.GitRepository
import gitinsp.domain.models.Language
import gitinsp.domain.models.URL
import io.circe.Json
import io.circe.parser.*

import scala.util.Failure
import scala.util.Try

object GithubWrapperService:
  def apply(config: Config, urlClient: FetchingService): GithubWrapperService =
    new WrapperService(config, urlClient)

  def apply(): GithubWrapperService =
    apply(ConfigFactory.load(), FetchingService())

  private class WrapperService(config: Config, urlClient: FetchingService)
      extends GithubWrapperService with LazyLogging:

    private def fetch(url: URL, languages: List[Language], json: Boolean): Try[String] =
      // The upper limit of the timeout for the request
      val readTimeout    = config.getInt("gitinsp.uithub.read-timeout")
      val connectTimeout = config.getInt("gitinsp.uithub.connection-timeout")

      // Get the processed URL and fetch the content
      val processedUrl = getUrl(url.value, languages, json)
      urlClient.fetchUrl(processedUrl, connectTimeout, readTimeout, "GET", Map.empty)

    def fetchRepository(url: URL, languages: List[Language]): Try[String] =
      // Fetch the repository in plain text (use for showing the content in the UI)
      fetch(url, languages, false)

    def buildRepository(url: URL, languages: List[Language]): Try[GitRepository] =
      // Fetch the repository in JSON (used for indexing)
      fetch(url, languages, true).flatMap {
        responseText =>
          Try {
            // Create documents for each retrieved file
            val codeFiles = fromGithub(responseText)

            // Return the index with languages (will be used for indexing)
            GitRepository(url, languages, codeFiles)
          }
      }.recoverWith {
        // If the request fails...
        case ex =>
          Failure(new Exception(s"Failed to fetch repository"))
      }

    private def getUrl(url: String, languages: List[Language], json: Boolean): String =
      // Add protocol if missing
      val baseUrl =
        if !url.startsWith("http://") && !url.startsWith("https://") then s"http://$url" else url

      baseUrl match
        case url if !url.contains("github.com") => url
        case _                                  =>
          // Config parameters
          val maxTokens      = config.getInt("gitinsp.uithub.index-tokens")
          val maxPlainTokens = config.getInt("gitinsp.uithub.view-tokens")

          // Request parameters
          val tokenParam    = if json then maxTokens else maxPlainTokens
          val acceptParam   = if json then "application%2Fjson" else "text%2Fplain"
          val baseProcessed = baseUrl.replaceFirst("github", "uithub")

          // Build URL with language parameter if provided
          languages.map(_.toString).mkString(",") match
            case "" => s"$baseProcessed?accept=$acceptParam&maxTokens=$tokenParam"
            case languageParam =>
              s"$baseProcessed?accept=$acceptParam&maxTokens=$tokenParam&ext=$languageParam"

    def fromGithub(json: String): List[CodeFile] =
      parse(json) match {
        case Right(json) =>
          // Create the JSON cursor
          json.hcursor.downField("files").as[Json] match {
            case Right(filesJson) =>

              // Parse the files object from the JSON response
              filesJson.asObject match {
                case Some(filesObject) =>

                  // Now since the object exists, convert it to a map
                  filesObject.toMap.flatMap {
                    case (filePath, fileJson) =>

                      // Parse the content field from the file object
                      fileJson.hcursor.downField("content").as[String] match {
                        case Right(content) => {
                          val languageOpt = GitRepository.detectLanguageFromFile(filePath)
                          languageOpt.map(
                            language => {
                              val category = language.category
                              val cs = config.getInt(s"gitinsp.${category}-embedding.chunk-size")
                              val co = config.getInt(s"gitinsp.${category}-embedding.chunk-overlap")
                              CodeFile(content, language, filePath, cs, co)
                            },
                          )
                        }
                        case Left(_) => None
                      }
                  }.toList
                case None =>
                  logger.warn("Files object is not a valid JSON object")
                  List.empty[CodeFile]
              }
            case Left(error) =>
              logger.warn(s"Error accessing files: ${error.getMessage}")
              List.empty[CodeFile]
          }
        case Left(error) =>
          logger.warn(s"Error parsing JSON: ${error.getMessage}")
          List.empty[CodeFile]
      }
