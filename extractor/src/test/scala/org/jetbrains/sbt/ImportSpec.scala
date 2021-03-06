package org.jetbrains.sbt

import java.io.{File, PrintWriter}

import difflib._
import org.specs2.matcher.{MatchResult, XmlMatchers}
import org.specs2.mutable._

import scala.collection.JavaConverters._
import scala.xml._

import structure._
import structure.XmlSerializer._

class ImportSpec extends Specification with XmlMatchers {

  val testDataRoot = new File("extractor/src/test/data/" + BuildInfo.sbtVersion)
  val androidHome = Option(System.getenv.get("ANDROID_HOME"))

  def testProject(project: String,  download: Boolean = true, sbtVersion: String = BuildInfo.sbtVersionFull) = {

    val base = new File(testDataRoot, project)

    val expectedStr = {
      val testDataFile = new File(base, "structure-" + BuildInfo.sbtVersionFull + ".xml")
      if (!testDataFile.exists())
        failure("No test data for version " + BuildInfo.sbtVersionFull + " found!")
      val text = read(testDataFile).mkString("\n")
      val androidHome = this.androidHome getOrElse ""
      text
        .replace("$BASE", base.getCanonicalPath)
        .replace("$ANDROID_HOME", androidHome)
        .replace("~/", System.getProperty("user.home") + "/")
    }

    val actualStr = Loader.load(base, download, sbtVersion, verbose = true).mkString("\n")

    val actualXml = XML.loadString(actualStr)
    val expectedXml = XML.loadString(expectedStr)
    val actual = actualXml.deserialize[StructureData].right.get
    val expected = expectedXml.deserialize[StructureData].right.get

    def onXmlFail = {
      import scala.collection.JavaConversions._

      val act = new PrintWriter(new File(base, "actual.xml"))
      act.write(actualStr)
      act.close()
      val diff = DiffUtils.diff(expectedStr.lines.toList, actualStr.lines.toList)
      println("DIFF: " + project)
      diff.getDeltas foreach { delta =>
        println("ORIGINAL:")
        delta.getOriginal.getLines.asScala.foreach(println)
        println("ACTUAL:")
        delta.getRevised.getLines.asScala.foreach(println)
        println
      }
      "xml files are not equal, compare 'actual.xml' and 'structure-" + BuildInfo.sbtVersionFull + ".xml'"
    }

    def onEqualsFail = {
      val act = new PrintWriter(new File(base, "actual.txt"))
      act.write(prettyPrintCaseClass(actual))
      act.close()
      val exp = new PrintWriter(new File(base, "expected.txt"))
      exp.write(prettyPrintCaseClass(expected))
      exp.close()
      "objects are not equal, compare 'actual.txt' and 'expected.txt'"
    }

    (actual == expected).must(beTrue.updateMessage(_ => onEqualsFail))
    actualXml must beEqualToIgnoringSpace(expectedXml).updateMessage(_ => onXmlFail)
  }

  def prettyPrintCaseClass(toPrint: Product): String = {
    val step = "  "
    def print0(what: Any, indent: String): String = what match {
      case p : Product =>
        if (p.productArity == 0) {
          indent + p.productPrefix
        } else {
          indent + p.productPrefix + ":\n" +
            p.productIterator.map {
              case s : Seq[_] => s.map(x => print0(x, indent + step)).mkString("\n")
              case pp : Product => print0(pp, indent + step)
              case other => indent + step + other.toString
            }.mkString("\n")
        }
      case other => indent + other.toString
    }

    print0(toPrint, step)
  }

  def sbt13only = BuildInfo.sbtVersion must be_==("0.13").orSkip("This test is for SBT 0.13 only")

  def onlyFor(version: String) = BuildInfo.sbtVersionFull must be_==(version).orSkip("This test if for SBT " + version + " only")

  def hasAndroidDefined = androidHome must beSome.orSkip("ANDROID_HOME is not defined")

  def equalExpectedOneIn[T](projectName: String)(block: String => MatchResult[T]) =
    ("equal expected one in '" + projectName + "' project [" + BuildInfo.sbtVersionFull + "]") in block(projectName)

  "Actual structure" should {

    sequential // running 10 sbt instances at once is a bad idea unless you have >16G of ram

    equalExpectedOneIn("bare")(testProject(_))
    equalExpectedOneIn("multiple")(testProject(_))
    equalExpectedOneIn("simple")(testProject(_))
    equalExpectedOneIn("dependency")(testProject(_))
    equalExpectedOneIn("classifiers")(sbt13only and testProject(_))
    equalExpectedOneIn("optional")(sbt13only and testProject(_))
    equalExpectedOneIn("play")(sbt13only and testProject(_, download = false, sbtVersion = "0.13.5"))
    equalExpectedOneIn("android")(p => sbt13only and (hasAndroidDefined and testProject(p)))
    equalExpectedOneIn("ide-settings")(onlyFor("0.13.7") and testProject(_))
    equalExpectedOneIn("sbt-idea")(sbt13only and testProject(_))
  }
}
