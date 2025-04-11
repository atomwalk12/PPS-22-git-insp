package gitinsp.infrastructure.parser

import gitinsp.domain.models.Language
import gitinsp.domain.models.Language.*

import scala.util.matching.Regex

@SuppressWarnings(Array(
  "org.wartremover.warts.Throw",
  "org.wartremover.warts.IterableOps",
  "org.wartremover.warts.SizeIs",
  "org.wartremover.warts.ListAppend",
  "org.wartremover.warts.DefaultArguments",
))
class CharacterTextSplitter(
  _separator: String = "\n\n",
  _isSeparatorRegex: Boolean = false,
) extends TextSplitter {
  sealed trait KeepSeparator
  case object DontKeep    extends KeepSeparator
  case object KeepAtStart extends KeepSeparator
  case object KeepAtEnd   extends KeepSeparator
  override def splitText(text: String): List[String] = {
    val _separator2 = if _isSeparatorRegex then _separator else Regex.quote(_separator)
    val splits      = splitTextWithRegex(text, _separator2, keepSeparator)
    val separator3  = if processEither(keepSeparator) then "" else _separator
    mergeSplits(splits, separator3)
  }

  def splitTextWithRegex(
    text: String,
    separator: String,
    keepSeparator: Either[Boolean, String],
  ): List[String] = {
    // Convert keepSeparator to our trait representation
    val keepMode = keepSeparator match {
      case Right("start") => KeepAtStart
      case Right("end")   => KeepAtEnd
      case Left(true)     => KeepAtStart // Default to start if just true
      case Left(false)    => DontKeep
      case _ =>
        throw new IllegalArgumentException("keepSeparator must be boolean, 'start', or 'end'")
    }

    // Split the text based on the separator
    if separator.nonEmpty then {
      keepMode match {
        case DontKeep =>
          // Simple split without keeping separator
          separator.r.split(text).filter(_.nonEmpty).toList

        case KeepAtStart | KeepAtEnd =>
          // The parentheses in the pattern keep the delimiters in the result
          val splitPattern = s"($separator)" // NOTE: used to keep separators in the result
          val splits       = splitPattern.r.split(text).toList

          val result = keepMode match {
            case KeepAtStart if splits.length > 1 =>
              // Keep separator at the start of each split
              val pairs = splits.tail
                .grouped(2)
                .map {
                  case sep :: content :: Nil => sep + content
                  case sep :: Nil            => sep
                  case _                     => ""
                }
                .toList
              splits.head :: pairs

            case KeepAtEnd if splits.length > 1 =>
              // Keep separator at the end of each split
              val pairs = splits
                .dropRight(1)
                .grouped(2)
                .map {
                  case content :: sep :: Nil => content + sep
                  case content :: Nil        => content
                  case _                     => ""
                }
                .toList

              if splits.length % 2 == 0 then
                pairs :+ splits.last
              else
                pairs

            case _ => splits
          }

          result.filter(_.nonEmpty)
      }
    }
    else {
      // If no separator, return list of individual characters
      text.map(_.toString).filter(_.nonEmpty).toList
    }
  }

  def processEither(either: Either[Boolean, String]): Boolean =
    either match {
      case Left(boolValue) =>
        if boolValue then {
          println("Boolean value is true")
          true
        }
        else {
          println("Boolean value is false")
          false
        }
      case Right(stringValue) =>
        if stringValue.nonEmpty then { // Check if the string is non-empty
          println(s"String value is non-empty: $stringValue")
          true
        }
        else {
          println("String value is empty")
          false
        }
    }
}
import scala.collection.mutable.ListBuffer

@SuppressWarnings(Array(
  "org.wartremover.warts.SeqApply",
  "org.wartremover.warts.MutableDataStructures",
  "org.wartremover.warts.IterableOps",
  "org.wartremover.warts.Var",
  "org.wartremover.warts.Throw",
))
/** Helper function to split text with regex */
object TextSplitterUtils {
  def splitTextWithRegex(
    text: String,
    separator: String,
    keepSeparator: Either[Boolean, String],
  ): List[String] =
    // Now that we have the separator, split the text
    if separator.nonEmpty then {
      keepSeparator match {
        case Left(false) =>
          // Simple split with no separator retention
          separator.r.split(text).toList.filter(_.nonEmpty)

        case Left(true) | Right("start") =>
          // Keep separator at start of chunks
          val pattern = separator.r

          // Find all separators and their positions
          val separatorMatches   = pattern.findAllMatchIn(text).toList
          val separatorPositions = separatorMatches.map(_.start)

          // Create splits including the separators
          val result = ListBuffer[String]()

          // Add first chunk if needed
          if separatorPositions.nonEmpty && separatorPositions.head > 0 then {
            result += text.substring(0, separatorPositions.head)
          }

          // Add middle chunks with separators at start
          for i <- separatorPositions.indices do {
            val sepMatch = separatorMatches(i)
            val sepStart = sepMatch.start
            val sepEnd   = sepMatch.end

            val nextStart = if i + 1 < separatorPositions.length then {
              separatorPositions(i + 1)
            }
            else {
              text.length
            }

            // Add the separator plus text
            if sepEnd < nextStart then {
              result += text.substring(sepStart, nextStart)
            }
            else {
              result += text.substring(sepStart, sepEnd)
            }
          }

          result.filter(_.nonEmpty).toList

        case Right("end") =>
          // Keep separator at end of chunks
          val pattern = separator.r

          // Find all separators and their positions
          val separatorMatches   = pattern.findAllMatchIn(text).toList
          val separatorPositions = separatorMatches.map(_.start)

          // Create splits including the separators
          val result = ListBuffer[String]()

          // Handle all chunks with separators at end
          var prevEnd = 0

          for sepPos <- separatorPositions do {
            val chunk = text.substring(prevEnd, sepPos + separator.length)
            result += chunk
            prevEnd = sepPos + separator.length
          }

          // Add final chunk if there's text after the last separator
          if prevEnd < text.length then {
            result += text.substring(prevEnd)
          }

          result.filter(_.nonEmpty).toList

        case _ =>
          throw new IllegalArgumentException("keepSeparator must be boolean, 'start', or 'end'")
      }
    }
    else {
      // If no separator, split into individual characters
      text.map(_.toString).toList.filter(_.nonEmpty)
    }
}

@SuppressWarnings(Array(
  "org.wartremover.warts.While",
  "org.wartremover.warts.Var",
  "org.wartremover.warts.IterableOps",
  "org.wartremover.warts.DefaultArguments",
  "org.wartremover.warts.MutableDataStructures",
  "org.wartremover.warts.Equals",
  "org.wartremover.warts.Recursion",
))
/** Splitting text by recursively looking at characters. Recursively tries to split by different
  * characters to find one that works.
  */
class RecursiveCharacterTextSplitter(
  separators: Option[List[String]] = None,
  override val keepSeparator: Either[Boolean, String] = Left(true),
  isSeparatorRegex: Boolean = false,
  override val chunkSize: Int = 4000,
  override val chunkOverlap: Int = 200,
  override val lengthFunction: String => Int = _.length,
  override val addStartIndex: Boolean = false,
  override val stripWhitespace: Boolean = true,
) extends TextSplitter(
      chunkSize,
      chunkOverlap,
      lengthFunction,
      keepSeparator,
      addStartIndex,
      stripWhitespace,
    ) {

  private val _separators: List[String] = separators.getOrElse(List("\n\n", "\n", " ", ""))

  /** Split incoming text and return chunks. */
  private def _split_text(text: String, separators: List[String]): List[String] = {
    val finalChunks = ListBuffer[String]()

    // Get appropriate separator to use
    var separator     = separators.last
    var newSeparators = List.empty[String]

    val iterator = separators.iterator.zipWithIndex
    var found    = false

    while iterator.hasNext && !found do {
      val (s, i)     = iterator.next()
      val _separator = if isSeparatorRegex then s else Regex.quote(s)

      if s.isEmpty then {
        separator = s
        found = true
      }
      else if _separator.r.findFirstIn(text).isDefined then {
        separator = s
        newSeparators = separators.drop(i + 1)
        found = true
      }
    }

    val _separator = if isSeparatorRegex then separator else Regex.quote(separator)
    val splits     = TextSplitterUtils.splitTextWithRegex(text, _separator, keepSeparator)

    // Now go merging things, recursively splitting longer texts
    val goodSplits      = ListBuffer[String]()
    val actualSeparator = if keepSeparator == Left(false) then separator else ""

    for s <- splits do
      if lengthFunction(s) < chunkSize then {
        goodSplits += s
      }
      else {
        if goodSplits.nonEmpty then {
          val mergedText = mergeSplits(goodSplits.toList, actualSeparator)
          finalChunks ++= mergedText
          goodSplits.clear()
        }

        if newSeparators.isEmpty then {
          finalChunks += s
        }
        else {
          val otherChunks = _split_text(s, newSeparators)
          finalChunks ++= otherChunks
        }
      }

    if goodSplits.nonEmpty then {
      val mergedText = mergeSplits(goodSplits.toList, actualSeparator)
      finalChunks ++= mergedText
    }

    finalChunks.toList
  }

  /** Split the input text into smaller chunks based on predefined separators. */
  override def splitText(text: String): List[String] = _split_text(text, _separators)
}

@SuppressWarnings(Array(
  "org.wartremover.warts.DefaultArguments",
  "org.wartremover.warts.Throw",
))
object RecursiveCharacterTextSplitter {
  def fromLanguage(
    language: Language,
    keepSeparator: Either[Boolean, String] = Left(true),
    chunkSize: Int = 4000,
    chunkOverlap: Int = 200,
    lengthFunction: String => Int = _.length,
    addStartIndex: Boolean = false,
    stripWhitespace: Boolean = true,
  ): RecursiveCharacterTextSplitter = {
    val separators = getSeparatorsForLanguage(language)
    new RecursiveCharacterTextSplitter(
      separators = Some(separators),
      isSeparatorRegex = true,
      chunkSize = chunkSize,
      chunkOverlap = chunkOverlap,
      keepSeparator = keepSeparator,
      lengthFunction = lengthFunction,
      addStartIndex = addStartIndex,
      stripWhitespace = stripWhitespace,
    )
  }

  def getSeparatorsForLanguage(language: Language): List[String] =
    language match {
      case Language.C | Language.CPP =>
        List(
          // Split along class definitions
          "\nclass ",
          // Split along function definitions
          "\nvoid ",
          "\nint ",
          "\nfloat ",
          "\ndouble ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nswitch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.GO =>
        List(
          // Split along function definitions
          "\nfunc ",
          "\nvar ",
          "\nconst ",
          "\ntype ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nswitch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.JAVA =>
        List(
          // Split along class definitions
          "\nclass ",
          // Split along method definitions
          "\npublic ",
          "\nprotected ",
          "\nprivate ",
          "\nstatic ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nswitch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.KOTLIN =>
        List(
          // Split along class definitions
          "\nclass ",
          // Split along method definitions
          "\npublic ",
          "\nprotected ",
          "\nprivate ",
          "\ninternal ",
          "\ncompanion ",
          "\nfun ",
          "\nval ",
          "\nvar ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nwhen ",
          "\ncase ",
          "\nelse ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.JS =>
        List(
          // Split along function definitions
          "\nfunction ",
          "\nconst ",
          "\nlet ",
          "\nvar ",
          "\nclass ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nswitch ",
          "\ncase ",
          "\ndefault ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.TS =>
        List(
          "\nenum ",
          "\ninterface ",
          "\nnamespace ",
          "\ntype ",
          // Split along class definitions
          "\nclass ",
          // Split along function definitions
          "\nfunction ",
          "\nconst ",
          "\nlet ",
          "\nvar ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nswitch ",
          "\ncase ",
          "\ndefault ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.PHP =>
        List(
          // Split along function definitions
          "\nfunction ",
          // Split along class definitions
          "\nclass ",
          // Split along control flow statements
          "\nif ",
          "\nforeach ",
          "\nwhile ",
          "\ndo ",
          "\nswitch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.PROTO =>
        List(
          // Split along message definitions
          "\nmessage ",
          // Split along service definitions
          "\nservice ",
          // Split along enum definitions
          "\nenum ",
          // Split along option definitions
          "\noption ",
          // Split along import statements
          "\nimport ",
          // Split along syntax declarations
          "\nsyntax ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.PYTHON =>
        List(
          // First, try to split along class definitions
          "\nclass ",
          "\ndef ",
          "\n\tdef ",
          // Now split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case Language.RST =>
        List(
          // Split along section titles
          "\n=+\n",
          "\n-+\n",
          "\n\\*+\n",
          // Split along directive markers
          "\n\n.. *\n\n",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case RUBY =>
        List(
          // Split along method definitions
          "\ndef ",
          "\nclass ",
          // Split along control flow statements
          "\nif ",
          "\nunless ",
          "\nwhile ",
          "\nfor ",
          "\ndo ",
          "\nbegin ",
          "\nrescue ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case ELIXIR =>
        List(
          // Split along method function and module definition
          "\ndef ",
          "\ndefp ",
          "\ndefmodule ",
          "\ndefprotocol ",
          "\ndefmacro ",
          "\ndefmacrop ",
          // Split along control flow statements
          "\nif ",
          "\nunless ",
          "\nwhile ",
          "\ncase ",
          "\ncond ",
          "\nwith ",
          "\nfor ",
          "\ndo ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case RUST =>
        List(
          // Split along function definitions
          "\nfn ",
          "\nconst ",
          "\nlet ",
          // Split along control flow statements
          "\nif ",
          "\nwhile ",
          "\nfor ",
          "\nloop ",
          "\nmatch ",
          "\nconst ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case SCALA =>
        List(
          // Split along class definitions
          "\nclass ",
          "\nobject ",
          // Split along method definitions
          "\ndef ",
          "\nval ",
          "\nvar ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nmatch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case SWIFT =>
        List(
          // Split along function definitions
          "\nfunc ",
          // Split along class definitions
          "\nclass ",
          "\nstruct ",
          "\nenum ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\ndo ",
          "\nswitch ",
          "\ncase ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case MARKDOWN =>
        List(
          // First, try to split along Markdown headings (starting with level 2)
          """\n#{1,6} """, // Use raw string for regex
          // Note the alternative syntax for headings (below) is not handled here
          // Heading level 2
          // ---------------
          // End of code block
          "```\n",
          // Horizontal lines
          """\n\*\*\*+\n""", // Use raw string and escape *
          """\n---+\n""",
          """\n___+\n""",
          // Note that this splitter doesn't handle horizontal lines defined
          // by *three or more* of ***, ---, or ___, but this is not handled
          "\n\n",
          "\n",
          " ",
          "",
        )
      case LATEX =>
        List(
          // First, try to split along Latex sections
          """\n\\chapter{""", // Use raw string and escape \
          """\n\\section{""",
          """\n\\subsection{""",
          """\n\\subsubsection{""",
          // Now split by environments
          """\n\\begin{enumerate}""",
          """\n\\begin{itemize}""",
          """\n\\begin{description}""",
          """\n\\begin{list}""",
          """\n\\begin{quote}""",
          """\n\\begin{quotation}""",
          """\n\\begin{verse}""",
          """\n\\begin{verbatim}""",
          // Now split by math environments
          """\n\\begin{align}""",
          "$$",
          "$",
          // Now split by the normal type of lines
          " ",
          "",
        )
      case HTML =>
        List(
          // First, try to split along HTML tags
          "<body",
          "<div",
          "<p",
          "<br",
          "<li",
          "<h1",
          "<h2",
          "<h3",
          "<h4",
          "<h5",
          "<h6",
          "<span",
          "<table",
          "<tr",
          "<td",
          "<th",
          "<ul",
          "<ol",
          "<header",
          "<footer",
          "<nav",
          // Head
          "<head",
          "<style",
          "<script",
          "<meta",
          "<title",
          "",
        )
      case CSHARP =>
        List(
          "\ninterface ",
          "\nenum ",
          "\nimplements ",
          "\ndelegate ",
          "\nevent ",
          // Split along class definitions
          "\nclass ",
          "\nabstract ",
          // Split along method definitions
          "\npublic ",
          "\nprotected ",
          "\nprivate ",
          "\nstatic ",
          "\nreturn ",
          // Split along control flow statements
          "\nif ",
          "\ncontinue ",
          "\nfor ",
          "\nforeach ",
          "\nwhile ",
          "\nswitch ",
          "\nbreak ",
          "\ncase ",
          "\nelse ",
          // Split by exceptions
          "\ntry ",
          "\nthrow ",
          "\nfinally ",
          "\ncatch ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case SOL =>
        List(
          // Split along compiler information definitions
          "\npragma ",
          "\nusing ",
          // Split along contract definitions
          "\ncontract ",
          "\ninterface ",
          "\nlibrary ",
          // Split along method definitions
          "\nconstructor ",
          "\ntype ",
          "\nfunction ",
          "\nevent ",
          "\nmodifier ",
          "\nerror ",
          "\nstruct ",
          "\nenum ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\ndo while ",
          "\nassembly ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case COBOL =>
        List(
          // Split along divisions
          "\nIDENTIFICATION DIVISION.",
          "\nENVIRONMENT DIVISION.",
          "\nDATA DIVISION.",
          "\nPROCEDURE DIVISION.",
          // Split along sections within DATA DIVISION
          "\nWORKING-STORAGE SECTION.",
          "\nLINKAGE SECTION.",
          "\nFILE SECTION.",
          // Split along sections within PROCEDURE DIVISION
          "\nINPUT-OUTPUT SECTION.",
          // Split along paragraphs and common statements
          "\nOPEN ",
          "\nCLOSE ",
          "\nREAD ",
          "\nWRITE ",
          "\nIF ",
          "\nELSE ",
          "\nMOVE ",
          "\nPERFORM ",
          "\nUNTIL ",
          "\nVARYING ",
          "\nACCEPT ",
          "\nDISPLAY ",
          "\nSTOP RUN.",
          // Split by the normal type of lines
          "\n",
          " ",
          "",
        )
      case LUA =>
        List(
          // Split along variable and table definitions
          "\nlocal ",
          // Split along function definitions
          "\nfunction ",
          // Split along control flow statements
          "\nif ",
          "\nfor ",
          "\nwhile ",
          "\nrepeat ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case HASKELL =>
        List(
          // Split along function definitions
          "\nmain :: ",
          "\nmain = ",
          "\nlet ",
          "\nin ",
          "\ndo ",
          "\nwhere ",
          "\n:: ",
          "\n= ",
          // Split along type declarations
          "\ndata ",
          "\nnewtype ",
          "\ntype ",
          "\n:: ",
          // Split along module declarations
          "\nmodule ",
          // Split along import statements
          "\nimport ",
          "\nqualified ",
          "\nimport qualified ",
          // Split along typeclass declarations
          "\nclass ",
          "\ninstance ",
          // Split along case expressions
          "\ncase ",
          // Split along guards in function definitions
          "\n| ",
          // Split along record field declarations
          "\ndata ",
          "\n= {",
          "\n, ",
          // Split by the normal type of lines
          "\n\n",
          "\n",
          " ",
          "",
        )
      case POWERSHELL =>
        List(
          // Split along function definitions
          "\nfunction ",
          // Split along parameter declarations (escape parentheses)
          "\nparam ",
          // Split along control flow statements
          "\nif ",
          "\nforeach ",
          "\nfor ",
          "\nwhile ",
          "\nswitch ",
          // Split along class definitions (for PowerShell 5.0 and above)
          "\nclass ",
          // Split along try-catch-finally blocks
          "\ntry ",
          "\ncatch ",
          "\nfinally ",
          // Split by normal lines and empty spaces
          "\n\n",
          "\n",
          " ",
          "",
        )
    }
}
