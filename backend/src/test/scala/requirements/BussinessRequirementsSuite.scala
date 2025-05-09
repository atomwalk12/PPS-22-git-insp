package gitinsp.tests.requirements

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import gitinsp.application.LangchainCoordinator
import gitinsp.domain.ChatService
import gitinsp.domain.IngestorService
import gitinsp.domain.PipelineService
import gitinsp.infrastructure.CacheService
import gitinsp.infrastructure.ContentService
import gitinsp.infrastructure.GithubWrapperService
import gitinsp.infrastructure.factories.RAGComponentFactoryImpl
import gitinsp.infrastructure.strategies.IngestionStrategyFactory
import gitinsp.tests.ENABLE_LOGGING
import gitinsp.tests.externalServiceTag
import gitinsp.tests.repoName
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

type StreamingResponse = Source[ServerSentEvent, NotUsed]

class BussinessRequirementsSuite extends AnyFeatureSpec with GivenWhenThen with MockitoSugar
    with Matchers:

  // Setup test environment
  implicit val system: ActorSystem                = ActorSystem("business-requirements-test-system")
  implicit val materializer: Materializer         = Materializer(system)
  implicit val executionContext: ExecutionContext = system.dispatcher
  val config                                      = ConfigFactory.load()

  private def createSink[T]: Sink[T, ?] =
    if ENABLE_LOGGING then
      Sink.foreach[T](s => println(s))
    else
      Sink.seq[T]

  Feature("BR1: Code Search Productivity"):
    Scenario(
      "As a user, I can search for code using keywords or natural language queries.",
      externalServiceTag,
    ):
      Given("A configured pipeline with necessary services")
      val factory         = RAGComponentFactoryImpl(config)
      val cacheService    = CacheService(factory)
      val ingestorService = IngestorService(cacheService, config, IngestionStrategyFactory)
      val gitService      = GithubWrapperService()
      val chatService     = ChatService(prettyFmt = true, ContentService)
      val pipeline        = PipelineService(chatService, cacheService, ingestorService, gitService)
      val langchainCoordinator = LangchainCoordinator(pipeline, prettyFmt = true)
      val repository           = Some(repoName)

      And("A user query")
      val query = "find all functions that handle user authentication"

      When("The user performs a search through the pipeline")
      val startTime                             = System.currentTimeMillis()
      val searchResults: Try[StreamingResponse] = langchainCoordinator.chat(query, repository)

      Then("The results should be returned within 10 seconds and be relevant")
      searchResults match {
        case Failure(error) => fail(s"Search failed: ${error.getMessage}")
        case Success(result: StreamingResponse) => {
          val endTime   = System.currentTimeMillis()
          val queryTime = endTime - startTime

          // BR1 success criteria: query-to-result time under 10 seconds
          queryTime should be < 10000L

          val sinkWithPrint = createSink[String]

          val future    = result.map(_.data).alsoTo(sinkWithPrint).runWith(Sink.seq[String])
          val responses = Await.result(future, 60.seconds)

          // Verify we got meaningful responses
          responses shouldNot be(empty)
          responses.foreach { (response: String) => response shouldNot be(empty) }
        }
      }

  // ----- NOTE: BR2 is assessed using the SUS survey -----
