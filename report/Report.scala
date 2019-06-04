object Report extends App {
  def log = io.Source.fromFile(args(0))
  ClocReport(log)
  SuccessReport(log)
}

object ClocReport {

  // sometimes there's an extra "[info]" in the line, so we have to be careful
  val Regex = """\[([^\]]+)\] (?:\[info\] )?\*\* COMMUNITY BUILD LINE COUNT: (\d+)""".r

  def apply(log: io.Source): Unit = {
    val results = collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    for (Regex(project, count) <- log.getLines)
      results(project) += count.toInt
    println("Lines of Scala code recompiled during this run only:")
    for ((project, sum) <- results.toSeq.sortBy(_._2).reverse)
      println(f"$sum%9d $project")
    println(f"${results.values.sum}%9d TOTAL")
  }

}

object SuccessReport {

  // sample inputs:
  //   [info] Project foo-bar-baz---------------: DID NOT RUN (stuck on broken dependencies: frob, akka-grue, zorch)
  //   [info] Project utest---------------------: FAILED (MatchError: d48a6cde+20180920-1730 (of class java.lang.St...)
  // regex features used:
  //   \w    = word character
  //   ?:    = not a capturing group
  //   (?!-) = negative lookahead -- next character is not "-"
  val Regex = """\[info\] Project ((?:\w|-(?!-))+)-*: ([^\(]+) \((?:stuck on broken dependencies: )?(.*)\)""".r

  val expectedToFail = Set[String](
    // old list of reasons for the failures are at https://github.com/scala/bug/issues/11453
    // could be reapplied here but would need updating
    "akka-contrib-extra",  // no 2.13 upgrade attempted afaict
    "algebra",  // source incompatibility involving catalysts?
    "case-app",  // shapeless-related compilation errors, looks hairy
    "circe-jackson",  // overloading related compile errors
    "classutil",  // some Stream/LazyList confusion?
    "coursier",  // no 2.13 upgrade attempted afaict (Seq-related compile errors)
    "doobie",  // looks like we might be picking up the wrong version-specific sources?
    "elastic4s",  // no 2.13 upgrade attempted afaict (Seq-related compile errors)
    "enumeratum",  // strange dependency error, maybe dbuild-specific? idk
    "jawn-0-10",  // no 2.13 upgrade attempted afaik
    "kafka",  // no 2.13 upgrade attempted afaik
    "kittens",  // Failed tests: cats.derived.HashSuite
    "lift-json",  // no 2.13 upgrade attempted afaik
    "linter",  // no 2.13 upgrade attempted afaict
    "magnolia",  // no 2.13 upgrade attempted afaik
    "metrics-scala",  // scala.language.postfixOps
    "mockito-scala",  // no arguments allowed for nullary method
    "monix",  // no 2.13.0-RC1 upgrade attempted afaik
    "paradox",  // no 2.13 upgrade attempted afaict
    "parboiled2",  // ???
    "play-webgoat",  // "com.typesafe.play#play-omnidoc_2.13.0-pre-bec2441;2.7.0: not found" ?!
    "quicklens",  // no 2.13 upgrade attempted afaict
    "scala-gopher",  // no 2.13 upgrade attempted afaict
    "scala-refactoring",  // postfixOps
    "scala-stm",  // still using JavaConversions
    "scala-xml-quote",  // Unit companion object not allowed in source
    "scalajson",  // test don't compile, specs2 not found, maybe related to imports-shadow-current-package change
    "scalameta",  // no 2.13 upgrade attempted afaict
    "scalameter",  // no 2.13 upgrade attempted afaict
    "scalamock",  // macro implementations cannot have implicit parameters other than WeakTypeTag evidences
    "scalastyle",  // no 2.13 upgrade attempted afaict
    "scalatest-tests",  // module not found: org.scalactic#scalacticmacro;3.0.8-dbuildx1fc18ed8d484a103fd15bec043bb31de68d9b550 ?!
    "scapegoat",  // no 2.13 upgrade attempted afaict
    "scribe",  // invalid escape
    "sttp",  // no 2.13 upgrade attempted afaict
    "tsec",  // they haven't upgraded past 2.13.0-M5 yet
    "tut",  // org.scala-sbt#scripted-sbt_2.13.0-pre-06392a5;1.2.8: not found
    "twitter-util",  // no 2.13 upgrade (an unmerged third-party PR exists, we could try that?)
  )

  def apply(log: io.Source): Unit = {
    val lines = log.getLines.dropWhile(!_.contains("---==  Execution Report ==---"))
    var success, failed, didNotRun = 0
    val unexpectedSuccesses = collection.mutable.Buffer[String]()
    val unexpectedFailures = collection.mutable.Buffer[String]()
    val blockerCounts = collection.mutable.Map[String, Int]()
    for (Regex(name, status, blockers) <- lines)
      status match {
        case "SUCCESS" =>
          success += 1
          if (expectedToFail(name))
            unexpectedSuccesses += name
        case "FAILED" =>
          failed += 1
          if (!expectedToFail(name))
            unexpectedFailures += name
        case "DID NOT RUN" =>
          didNotRun += 1
          for (blocker <- blockers.split(',').map(_.trim))
            blockerCounts(blocker) = 1 + blockerCounts.getOrElse(blocker, 0)
      }
    val total = success + failed + didNotRun
    if (unexpectedFailures.isEmpty)
      println(s"SUCCESS $success")
    else {
      val uf = unexpectedFailures.mkString(",")
      println(s"SUCCESS $success FAILED?! $uf")
    }
    if (didNotRun > 0) {
      val blockers =
        blockerCounts.toList.sortBy(_._2).reverse
          .collect{case (blocker, count) => s"$count $blocker"}
          .mkString(", ")
      println(s"BLOCKERS: $blockers")
    }
    if (unexpectedSuccesses.nonEmpty) {
      val us = unexpectedSuccesses.mkString(",")
      println(s"UNEXPECTED SUCCESSES: $us")
    }
    println(s"FAILED $failed DID NOT RUN $didNotRun TOTAL $total")
  }

}
