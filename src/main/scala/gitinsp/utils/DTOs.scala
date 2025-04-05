package gitinsp.utils

import com.typesafe.scalalogging.LazyLogging
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.Metadata

import java.util.Map as JMap

/** Enum for supported programming languages.
  * Each case includes a short string representation (`value`) often used as a file extension.
  */
enum Language(val value: String):
  case CPP        extends Language("cpp")
  case GO         extends Language("go")
  case JAVA       extends Language("java")
  case KOTLIN     extends Language("kt")
  case JS         extends Language("js")
  case TS         extends Language("ts")
  case PHP        extends Language("php")
  case PROTO      extends Language("proto")
  case PYTHON     extends Language("py")
  case CODE       extends Language("code") // Placeholder for all code languages
  case RST        extends Language("rst")
  case RUBY       extends Language("rb")
  case RUST       extends Language("rs")
  case SCALA      extends Language("scala")
  case SWIFT      extends Language("swift")
  case MARKDOWN   extends Language("md")
  case TEXT       extends Language("text")
  case LATEX      extends Language("latex")
  case HTML       extends Language("html")
  case SOL        extends Language("sol")
  case CSHARP     extends Language("cs")
  case COBOL      extends Language("cob")
  case C          extends Language("c")
  case LUA        extends Language("lua")
  case HASKELL    extends Language("hs")
  case ELIXIR     extends Language("ex")
  case POWERSHELL extends Language("ps")

  // Override toString to return the custom value
  override def toString: String = value

  def category: String =
    this match
      case Language.MARKDOWN => Language.TEXT.value
      case _                 => Language.CODE.value

object GitRepository extends LazyLogging:
  def detectLanguage(language: String): Option[List[Language]] =
    Option(language).map(_.trim).flatMap {
      case lang if lang.contains(",") =>
        Some(
          lang.split(",")
            .toList
            .map(l => findLanguage(l.trim)),
        )
      case lang if lang.nonEmpty =>
        Some(List(findLanguage(lang)))
      case _ =>
        Some(List())
    }.orElse(Some(List()))

  private def findLanguage(lang: String): Language =
    Language.values.find(_.toString == lang) match
      case Some(language) => language
      case None =>
        val message = s"Unsupported language: $lang"
        logger.error(message)
        scala.sys.error(message)

  def detectLanguages(languages: String): List[Language] =
    languages.split(",")
      .toList
      .map(l => findLanguage(l.trim))

  def detectLanguageFromFile(filePath: String): Language =
    val language = filePath.split("\\.").last
    findLanguage(language)

final case class GitRepository(url: String, languages: List[Language], docs: List[GitDocument]):
  val indexNames: List[IndexName] = languages.map(indexName)
  assert(indexNames.length == languages.length, s"Length mismatch: $indexNames, $languages")

  def indexName(lang: Language): IndexName =
    val sanitizedName = URLSanitizerService.sanitize(url)
    // Here we are creating different separate index for each code language
    // Since textual input is generally markdown, we are creating a single, separate index for it
    // The reason is that the document splitter must use differet separators according to the
    // language being used. By creating separate indexes, we ensure that this is done correctly.
    // The markdown index contains all the markdown text, and only one list of separators is needed.
    lang match
      case Language.MARKDOWN => IndexName(s"$sanitizedName-${Language.TEXT.toString}", lang)
      case lang              => IndexName(s"$sanitizedName-${lang.toString}", lang)

  override def toString: String = s"GitRepository(url=$url, languages=$languages, docs=$docs)"

final case class IndexName(name: String, language: Language)

object IndexName:
  def apply(indexName: String): IndexName =
    val unsanitizedName = URLSanitizerService.unsanitize(indexName)
    val (repositoryPart, languagePart) = unsanitizedName.lastIndexOf("-") match
      case idx if idx > 0 => (unsanitizedName.substring(0, idx), unsanitizedName.substring(idx + 1))
      case _              => (unsanitizedName, "")
    val language = languagePart match
      case Language.TEXT.value => Language.MARKDOWN
      case lang                => Language.values.find(_.toString == lang).getOrElse(Language.CODE)
    IndexName(repositoryPart, language)

final case class GitDocument(content: String, language: Language, path: String):
  def createLangchainDocument(): Option[Document] =
    Option(content.trim)
      .filter(_.nonEmpty)
      .map(
        trimmedContent => {
          val metadata = Metadata.from(JMap.of("file_name", path, "code", language.toString))
          Document.from(trimmedContent, metadata)
        },
      )

object GitDocument extends LazyLogging:
  def fromGithub(json: String): List[GitDocument] =
    import io.circe.parser.*
    import io.circe.Json
    parse(json) match {
      case Right(json) =>
        json.hcursor.downField("files").as[Json] match {
          case Right(filesJson) =>
            filesJson.asObject match {
              case Some(filesObject) =>
                filesObject.toMap.flatMap {
                  case (filePath, fileJson) =>
                    fileJson.hcursor.downField("content").as[String] match {
                      case Right(content) => {
                        val language = GitRepository.detectLanguageFromFile(filePath)
                        Some(GitDocument(content, language, filePath))
                      }
                      case Left(_) => None
                    }
                }.toList
              case None =>
                logger.warn("Files object is not a valid JSON object")
                List.empty[GitDocument]
            }
          case Left(error) =>
            logger.warn(s"Error accessing files: ${error.getMessage}")
            List.empty[GitDocument]
        }
      case Left(error) =>
        logger.warn(s"Error parsing JSON: ${error.getMessage}")
        List.empty[GitDocument]
    }
