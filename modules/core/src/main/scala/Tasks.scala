package dev.guardrail
package sbt

import _root_.sbt.{FeedbackProvidedException, WatchSource}
import cats.data.NonEmptyList
import cats.implicits._
import cats.~>
import dev.guardrail.{Args => ArgsImpl}
import scala.io.AnsiColor
import _root_.io.swagger.parser.SwaggerParserExtension
import java.nio.file.Path
import java.io.File

class CodegenFailedException extends FeedbackProvidedException

object Tasks {
  def guardrailTask(
    runner: Map[String,NonEmptyList[ArgsImpl]] => Target[List[Path]]
  )(tasks: List[Types.Args], sourceDir: File): Seq[File] = {
    // swagger-parser uses SPI to find extensions on the classpath (by default, only the OAPI2 -> OAPI3 converter)
    // See https://github.com/swagger-api/swagger-parser#extensions
    // That being said, Scala's classloader seems to have some issues finding SPI resources:
    // - https://github.com/scala/bug/issues/10247
    // - https://github.com/meetup/sbt-openapi/blob/363fa14/src/main/scala/com/meetup/sbtopenapi/Plugin.scala#L64-L71
    // As a result, we temporarily overwrite Scala's classloader to whichever one loaded swagger-parser, in the hopes
    // that it'll pick up the rest of the SPI classes.
    val oldClassLoader = Thread.currentThread().getContextClassLoader()
    Thread.currentThread().setContextClassLoader(classOf[SwaggerParserExtension].getClassLoader)

    val preppedTasks: Map[String, NonEmptyList[ArgsImpl]] = tasks.foldLeft(Map.empty[String, NonEmptyList[ArgsImpl]]) { case (acc, (language, args)) =>
      val prepped = args.copy(outputPath=Some(sourceDir.getPath))
      acc.updated(language, acc.get(language).fold(NonEmptyList.one(prepped))(_ :+ prepped))
    }

    val /*(logger,*/ paths/*)*/ =
      runner
        .apply(preppedTasks)
        .fold[List[java.nio.file.Path]]({
          case MissingArg(args, Error.ArgName(arg)) =>
            println(s"${AnsiColor.RED}Missing argument:${AnsiColor.RESET} ${AnsiColor.BOLD}${arg}${AnsiColor.RESET} (In block ${args})")
            throw new CodegenFailedException()
          case MissingDependency(name) =>
            println(s"""${AnsiColor.RED}Missing dependency:${AnsiColor.RESET} ${AnsiColor.BOLD}libraryDependencies += "dev.guardrail" %% "${name}" % "<check latest version>"${AnsiColor.RESET}""")
            throw new CodegenFailedException()
          case NoArgsSpecified =>
            List.empty
          case NoFramework =>
            println(s"${AnsiColor.RED}No framework specified${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case PrintHelp =>
            List.empty
          case UnknownArguments(args) =>
            println(s"${AnsiColor.RED}Unknown arguments: ${args.mkString(" ")}${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case UnparseableArgument(name, message) =>
            println(s"${AnsiColor.RED}Unparseable argument ${name}: ${message}${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case UnknownFramework(name) =>
            println(s"${AnsiColor.RED}Unknown framework specified: ${name}${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case RuntimeFailure(message) =>
            println(s"${AnsiColor.RED}Error:${AnsiColor.RESET}${message}")
            throw new CodegenFailedException()
          case UserError(message) =>
            println(s"${AnsiColor.RED}Error:${AnsiColor.RESET}${message}")
            throw new CodegenFailedException()
          case MissingModule(section, choices) =>
            println(s"${AnsiColor.RED}Error: Missing module ${section}. Options are: ${choices.mkString(", ")}${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case ModuleConflict(section) =>
            println(s"${AnsiColor.RED}Error: Too many modules specified for ${section}${AnsiColor.RESET}")
            throw new CodegenFailedException()
          case UnspecifiedModules(choices) =>
            val result =
              choices.toSeq.sortBy(_._1).foldLeft(Seq.empty[String]) { case (acc, (module, choices)) =>
                val nextLabel = Option(choices).filter(_.nonEmpty).fold("<no choices found>")(_.toSeq.sorted.mkString(", "))
                acc :+ s"  ${AnsiColor.BOLD}${AnsiColor.WHITE}${module}:${AnsiColor.RESET} [${AnsiColor.BLUE}${nextLabel}${AnsiColor.RESET}]"
              }
            println(s"${AnsiColor.RED}Unsatisfied module(s):${AnsiColor.RESET}")
            result.foreach(println)
            throw new CodegenFailedException()
          case UnusedModules(unused) =>
            println(s"${AnsiColor.RED}Unused modules specified:${AnsiColor.RESET} ${unused.toList.mkString(", ")}")
            throw new CodegenFailedException()
        }, identity)
        //.runEmpty

    Thread.currentThread().setContextClassLoader(oldClassLoader)
    paths.map(_.toFile).distinct
  }

  def watchSources(tasks: List[Types.Args]): Seq[WatchSource] = {
    tasks.flatMap(_._2.specPath.map(new File(_)).map(WatchSource(_))).toSeq
  }
}
