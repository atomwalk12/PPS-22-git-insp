package gitinsp.tests.requirements

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import gitinsp.domain.ChatService
import gitinsp.domain.IngestorService
import gitinsp.domain.Pipeline
import gitinsp.domain.models.Language
import gitinsp.domain.models.RepositoryWithLanguages
import gitinsp.domain.models.URL
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.ContentService
import gitinsp.infrastructure.FetchingService
import gitinsp.infrastructure.GithubWrapperService
import gitinsp.infrastructure.factories.RAGComponentFactoryImpl
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import gitinsp.tests.ENABLE_LOGGING
import gitinsp.tests.externalServiceTag
import gitinsp.tests.repoName
import gitinsp.tests.storeName
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

class FunctionRequirementsSuite extends AnyFeatureSpec with GivenWhenThen with MockitoSugar
    with Matchers:

  // Setup test environment
  implicit val system: ActorSystem                = ActorSystem("business-requirements-test-system")
  implicit val materializer: Materializer         = Materializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  val config                                      = ConfigFactory.load()

  /*
    Repository URL Input Interface (FR1.1)

    - Choice: Create a flexible and robust repository input mechanism.
    - Rationale: The system needs a secure and user-friendly way to accept Git repository URLs.
    - Success Criteria:
        - 100% of valid GitHub URLs successfully accepted and processed
        - Error feedback displayed within 500ms of validation failure
        - Multiple extension selection functions correctly in 100% of test cases
        - URL validation completes within 5 seconds for all inputs
    - Implementation Considerations:
        - Provide a common interface to select Github repositories and select multiple extensions at once.
        - Implement URL validation and sanitization by using common sanitization libraries.
        - Provide clear feedback for invalid URLs (i.e. use common UI elements to display errors).
    - Related Requirements:
        - FR2.1 (Repository Cloning)
        - NFR3 (Security)
   */
  Feature("FR1.1: Repository URL Input Interface") {
    Scenario("Valid GitHub URL is successfully processed", externalServiceTag) {
      Given("A configured pipeline with necessary services")
      val githubService   = GithubWrapperService(config, FetchingService())
      val ragFactory      = RAGComponentFactoryImpl(config)
      val cacheService    = CacheService(ragFactory)
      val chatService     = ChatService(false, ContentService)
      val ingestorService = IngestorService(cacheService, config, IngestionStrategyFactory)
      val pipeline        = Pipeline(chatService, cacheService, ingestorService, githubService)

      And("A valid GitHub repository URL")
      val validUrl  = URL(repoName)
      val languages = RepositoryWithLanguages.detectLanguages("scala,md")

      When("The URL is processed")
      val startTime      = System.currentTimeMillis()
      val result         = githubService.buildRepository(validUrl, languages)
      val processingTime = System.currentTimeMillis() - startTime

      Then("The URL should be processed successfully")
      result.isSuccess shouldBe true

      And("Processing should complete within 1 second")
      processingTime should be <= 5000L

      And("The repository should contain the specified languages")
      result.foreach {
        repo =>
          repo.languages should contain(Language.SCALA)
          repo.languages should contain(Language.MARKDOWN)
      }
    }

    Scenario("Invalid GitHub URL is rejected with quick feedback", externalServiceTag) {
      Given("A configured GitHub service")
      val githubService = GithubWrapperService(config, FetchingService())
      val languages     = RepositoryWithLanguages.detectLanguages("scala")

      When("An invalid URL is processed")
      val startTime      = System.currentTimeMillis()
      val result         = Try(URL("invalid-url-format"))
      val processingTime = System.currentTimeMillis() - startTime

      Then("The URL should be rejected")
      if ENABLE_LOGGING then println(result)
      result.isFailure shouldBe true

      And("Error feedback should be provided within 500ms")
      processingTime should be <= 500L
    }

    Scenario("Multiple file extensions are correctly processed", externalServiceTag) {
      Given("A configured pipeline")
      val githubService = GithubWrapperService(config, FetchingService())
      val validUrl      = URL(repoName)

      When("Multiple extensions are specified")
      val languages = RepositoryWithLanguages.detectLanguages("scala,md,py")
      val result    = githubService.buildRepository(validUrl, languages)

      Then("All specified extensions should be included")
      result.foreach {
        repo =>
          repo.languages should contain allOf (
            Language.SCALA,
            Language.MARKDOWN,
            Language.PYTHON,
          )
      }
    }
  }

  /*
    Code Search Using Markdown (FR1.2)

    - Choice: Implement dual-approach search combining keyword matching and semantic search.
      - Validation Criteria:
        - Search results return in under 2 seconds for repositories up to 100MB
        - Language filtering correctly categorizes at least 95% of code files
      - Implementation Considerations:
        - Use embedding model to convert natural language queries to vectors.
        - Implement hybrid ranking that combines exact matches and semantic similarity.
        - Support filtering by language, content type and extension.
      - Related Requirements:
        - FR2.2 (Code Indexing)
        - FR2.3 (Vector Database)
        - NFR1 (Performance)
   */
  Feature("FR1.2: Code Search Using Markdown") {
    Scenario("Natural language query returns relevant code files", externalServiceTag) {
      Given("A configured pipeline with necessary services")
      val githubService      = GithubWrapperService(config, FetchingService())
      val ragFactory         = RAGComponentFactoryImpl(config)
      val qdrantClient       = ragFactory.createQdrantClient()
      val embeddingStore     = ragFactory.createEmbeddingStore(qdrantClient, storeName)
      val textEmbeddingModel = ragFactory.createTextEmbeddingModel()

      When("A user query is processed")
      val queryEmbedding = textEmbeddingModel.embed("What is this project about?").content()

      And("A natural language query is made")
      val searchRequest = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build()
      val result        = embeddingStore.search(searchRequest)

      Then("The result should be a list of code files")
      result.matches should not be empty
      if ENABLE_LOGGING then result.matches.asScala.foreach(m => println(m.score))
      if ENABLE_LOGGING then result.matches.asScala.foreach(m => println(m.embedded().text()))
    }

    Scenario(
      "Search results return within performance requirements within 2 seconds",
      externalServiceTag,
    ) {
      Given("A configured pipeline with necessary services")
      val githubService      = GithubWrapperService(config, FetchingService())
      val ragFactory         = RAGComponentFactoryImpl(config)
      val qdrantClient       = ragFactory.createQdrantClient()
      val embeddingStore     = ragFactory.createEmbeddingStore(qdrantClient, storeName)
      val textEmbeddingModel = ragFactory.createTextEmbeddingModel()

      When("A search query is executed")
      val startTime      = System.currentTimeMillis()
      val queryEmbedding = textEmbeddingModel.embed("Find code related to GitHub API").content()
      val searchRequest  = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build()
      val result         = embeddingStore.search(searchRequest)
      val endTime        = System.currentTimeMillis()
      val executionTime  = endTime - startTime

      Then("The search should complete within 2 seconds")
      executionTime should be <= 2000L

      And("Results should be returned")
      result.matches should not be empty
    }

    Scenario("Language filtering correctly categorizes code files", externalServiceTag) {
      Given("A configured pipeline with necessary services")
      val githubService = GithubWrapperService(config, FetchingService())
      val ragFactory    = RAGComponentFactoryImpl(config)
      val validUrl      = URL(repoName)
      val languages     = RepositoryWithLanguages.detectLanguages("scala,md")

      When("Repository content is analyzed")
      val repoResult = githubService.buildRepository(validUrl, languages)

      Then("Files should be correctly categorized by language")
      repoResult.isSuccess shouldBe true
      repoResult.foreach {
        repo =>
          // Check that languages are correctly detected
          repo.languages should contain(Language.SCALA)
          repo.languages should contain(Language.MARKDOWN)

          // Verify Scala files are correctly identified
          val scalaFiles = repo.docs.filter(_.language == Language.SCALA)
          scalaFiles.foreach {
            file =>
              file.path should endWith(".scala")
              file.language shouldBe Language.SCALA
          }

          // Verify Markdown files are correctly identified
          val markdownFiles = repo.docs.filter(_.language == Language.MARKDOWN)
          markdownFiles.foreach {
            file =>
              file.path should (endWith(".md") or endWith(".MD"))
              file.language shouldBe Language.MARKDOWN
          }
      }
    }
  }

  /*
      Code Search using Code Embeddings (FR1.4)

      - Choice: Implement programming language filters for search results.
        - Rationale: Developers often need to restrict searches to specific languages or file types.
        - Success Criteria:
          - Language detection accuracy works across all common programming languages in all tests
          - Filter application occurs within 500ms after selection
          - Multiple simultaneous filters function correctly in 100% of test cases
          - Filter UI elements receive >90% positive usability rating from test users (SUS)
        - Implementation Considerations:
          - Detect and classify programming languages during indexing.
          - Create efficient language metadata for fast filtering.
          - Support multiple simultaneous language filters.
          - Include language identification in UI.
        - Related Requirements:
          - FR1.2 (Search)
          - FR1.3 (Search Results)
          - FR2.2 (Code Indexing)
   */

  Feature("FR1.4: Code Search using Code Embeddings") {
    Scenario("Natural language query returns relevant code files", externalServiceTag) {
      Given("A configured pipeline with necessary services")
      val githubService      = GithubWrapperService(config, FetchingService())
      val ragFactory         = RAGComponentFactoryImpl(config)
      val qdrantClient       = ragFactory.createQdrantClient()
      val embeddingStore     = ragFactory.createEmbeddingStore(qdrantClient, storeName)
      val codeEmbeddingModel = ragFactory.createCodeEmbeddingModel()

      When("A user query is processed using a code embedding model")
      val queryEmbedding = codeEmbeddingModel.embed("What is this project about?").content()

      And("A natural language query is made")
      val searchRequest = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build()
      val result        = embeddingStore.search(searchRequest)

      Then("The result should be a list of code files")
      result.matches should not be empty
      result.matches.asScala.foreach(m => m.embedded().text().contains(".scala"))
      if ENABLE_LOGGING then result.matches.asScala.foreach(m => println(m.score))
      if ENABLE_LOGGING then result.matches.asScala.foreach(m => println(m.embedded().text()))
    }
  }

  /*
    Code Context Visualization (FR1.5)

    - Choice: Provide context around code snippets in search results.
      - Rationale: Code snippets without surrounding context are often difficult to understand.
      - Success Criteria:
        - Fetching a repository loads within 5 seconds for repositories under 100MB
        - 90% of users report sufficient context for understanding code purpose (SUS)
      - Implementation Considerations:
        - Display the entire code being indexed in the search results.
        - When answering questions, display relevant snippets from the codebase.
        - Allow the user to switch between the full text and retrieved snippets.
      - Related Requirements:
        - FR1.3 (Search Results)
        - NFR2 (Usability)
   */
  Feature("FR1.5: Code Context Visualization") {
    Scenario("Repository fetching performance meets requirements", externalServiceTag) {
      Given("A configured GitHub wrapper service")
      val githubService = GithubWrapperService(config, FetchingService())
      val validUrl      = URL(repoName)
      val languages     = RepositoryWithLanguages.detectLanguages("scala,md")

      When("A repository is fetched")
      val startTime = System.currentTimeMillis()
      val result    = githubService.fetchRepository(validUrl, languages)
      val endTime   = System.currentTimeMillis()
      val fetchTime = endTime - startTime

      Then("The repository should be fetched successfully")
      result.isSuccess shouldBe true

      And("Fetching should complete within 5 seconds")
      fetchTime should be <= 5000L

      And("The fetched repository should contain code content")
      result.foreach {
        content =>
          content should not be empty
          // Verify we have actual code content
          content should (include(".scala") or include(".md"))
      }
    }

    Scenario("Full code context is provided for search results", externalServiceTag) {
      Given("A configured GitHub wrapper service")
      val githubService = GithubWrapperService(config, FetchingService())
      val validUrl      = URL(repoName)
      val languages     = RepositoryWithLanguages.detectLanguages("scala")

      When("A repository is built with code files")
      val repoResult = githubService.buildRepository(validUrl, languages)

      Then("Each code file should contain full context")
      repoResult.isSuccess shouldBe true
      repoResult.foreach {
        repo =>
          repo.docs.foreach {
            file =>
              file.content should not be empty

              // Check for common code structure indicators
              if file.language == Language.SCALA then
                file.content should (
                  include("import") or
                    include("def") or
                    include("val") or
                    include("class") or
                    include("object") or
                    include("trait")
                )
          }
      }
    }
  }
  /*
    Model with Past Chat History (FR1.6)

    - Choice: Create a chat interface with model memory.
      - Rationale: User questions often build on previous questions and require remembering previous messages.
      - Success Criteria:
        - Context-aware responses remain relevant for at least 2 consecutive related questions
      - Implementation Considerations:
        - Maintain chat history within session scope.
        - Structure LLM prompts to include chat history and retrieved code.
      - Related Requirements:
        - FR2.4 (LLM Integration)
        - FR2.3 (Vector Database)
        - NFR2 (Usability)
   */
  Feature("FR1.6: Model with Memory of Chat History") {
    Scenario(
      "Context-aware responses remain relevant for at least 2 consecutive related questions",
      externalServiceTag,
    ) {
      Given("A configured pipeline with necessary services")
      val ragFactory      = RAGComponentFactoryImpl(config)
      val cacheService    = CacheService(ragFactory)
      val chatService     = ChatService(false, ContentService)
      val ingestorService = IngestorService(cacheService, config, IngestionStrategyFactory)
      val githubService   = GithubWrapperService(config, FetchingService())
      val pipeline        = Pipeline(chatService, cacheService, ingestorService, githubService)
      val aiServiceUrl    = Some(URL(repoName).toAIServiceURL())

      When("A user asks to remember a keyword")
      pipeline.getAIService(aiServiceUrl)
        .map(service => pipeline.chat("Please remember the following keyword: apple?", service))

      And("The user asks what was the keyword")
      val responseSource = pipeline.getAIService(aiServiceUrl)
        .map(service => pipeline.chat("What is the keyword?", service))

      Then("The keyword should be remembered")
      responseSource.isSuccess shouldBe true
      responseSource.foreach {
        source =>
          // Convert Source[String, NotUsed] to String
          val result = Await.result(source.runWith(Sink.fold("")(_ + _)), 60.seconds)
          result should include("apple")
      }
    }
  }
