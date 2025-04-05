package gitinsp.domain

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import gitinsp.infrastructure.URLClient
import gitinsp.utils.GitDocument
import gitinsp.utils.GitRepository
import gitinsp.utils.Language

import scala.util.Failure
import scala.util.Try
trait GithubWrapperService:
  def buildRepository(url: String, languages: List[Language]): Try[GitRepository]
  def fetchRepository(url: String, languages: List[Language]): Try[String]

object GithubWrapperService:
  def apply(config: Config, urlClient: URLClient): GithubWrapperService =
    new WrapperService(config, urlClient)

  def apply(): GithubWrapperService =
    apply(ConfigFactory.load(), URLClient())

  private class WrapperService(config: Config, urlClient: URLClient) extends GithubWrapperService:

    private def fetch(url: String, languages: List[Language], json: Boolean): Try[String] =
      val readTimeout    = config.getInt("gitinsp.timeout")
      val connectTimeout = config.getInt("gitinsp.timeout")

      val processedUrl = getUrl(url, languages, json)
      urlClient.fetchUrl(processedUrl, connectTimeout, readTimeout, "GET", Map.empty)

    def fetchRepository(url: String, languages: List[Language]): Try[String] =
      fetch(url, languages, false)

    def buildRepository(url: String, languages: List[Language]): Try[GitRepository] =
      fetch(url, languages, true).flatMap {
        responseText =>
          Try {
            val docs = GitDocument.fromGithub(responseText)
            GitRepository(url, languages, docs)
          }
      }.recoverWith {
        case ex =>
          Failure(ex)
      }

    private def getUrl(url: String, languages: List[Language], json: Boolean): String =
      val baseUrl =
        if !url.startsWith("http://") && !url.startsWith("https://") then s"http://$url" else url

      if !baseUrl.contains("github.com") then
        baseUrl
      else
        val maxTokens      = config.getInt("gitinsp.max-tokens")
        val maxPlainTokens = config.getInt("gitinsp.plain-tokens")

        val languageParam = languages.map(_.toString).mkString(",")

        val acceptParam = if json then "application%2Fjson" else "text%2Fplain"
        val tokenParam  = if json then maxTokens else maxPlainTokens

        val baseProcessed = baseUrl.replaceFirst("github", "uithub")

        languageParam match
          case "" => s"$baseProcessed?accept=$acceptParam&maxTokens=$tokenParam"
          case _  => s"$baseProcessed?accept=$acceptParam&maxTokens=$tokenParam&ext=$languageParam"
