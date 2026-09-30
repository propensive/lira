                                                                                                  /*
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                                                                                                  ┃
┃                                 ╭───╮╭───╮                                                       ┃
┃                                 │   ││   │                                                       ┃
┃                                 │   │╰───╯                                                       ┃
┃                                 │   │╭───╮╭───╮╌────╮╭─────────╮                                 ┃
┃                                 │   ││   ││   ╭──╮  ││   ╭─╮   │                                 ┃
┃                                 │   ││   ││   │  ╰──╯│   │ │   │                                 ┃
┃                                 │   ││   ││   │      │   │ │   │                                 ┃
┃                                 │   ││   ││   │      │   ╰─╯   │                                 ┃
┃                                 ╰───╯╰───╯╰───╯      ╰─────╌╰──╯                                 ┃
┃                                                                                                  ┃
┃    LIRA, version 0.1.0.                                                                          ┃
┃    © Copyright 2026 Jon Pretty, Propensive OÜ.                                                   ┃
┃                                                                                                  ┃
┃    The primary distribution site is:                                                             ┃
┃                                                                                                  ┃
┃        https://lira.nexus/                                                                       ┃
┃                                                                                                  ┃
┃    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file     ┃
┃    except in compliance with the License. You may obtain a copy of the License at                ┃
┃                                                                                                  ┃
┃        https://www.apache.org/licenses/LICENSE-2.0                                               ┃
┃                                                                                                  ┃
┃    Unless required by applicable law or agreed to in writing,  software distributed under the    ┃
┃    License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    ┃
┃    either express or implied. See the License for the specific language governing permissions    ┃
┃    and limitations under the License.                                                            ┃
┃                                                                                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                                                                  */
package lira

import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters.IteratorHasAsScala

import soundness.*
import galilei.Linux.pathOnLinux

import alphabets.hexLowerCase
import logging.silentLogging
import probates.cancelProbate
import strategies.throwUnsafely
import systems.javaBaseSystem
import temporaryDirectories.systemTemporaryDirectory
import threading.platformThreading

// The `tasty/1` discipline over a real compilation, moved from Soundness's degustation suite with
// the discipline itself. Skipped where no proscala release is cached to compile the fixture with.
object TastyDisciplineTests extends Suite(m"LIRA tasty/1 discipline tests"):
  def proscalaLibrary(): Optional[java.nio.file.Path] =
    val home = java.lang.System.getProperty("user.home").nn
    val root = Paths.get(home, ".cache", "soundness", "proscala").nn

    if !Files.isDirectory(root) then Unset else
      Files.list(root).nn.iterator.nn.asScala.to(scala.List).sortBy(_.toString).reverse
      . map(_.resolve("lib").nn)
      . find { lib => Files.isDirectory(lib) && Files.exists(lib.resolve("scala3-library.jar")) }
      . getOrElse(Unset)

  val fixture: Text =
    t"""|package fixture
        |
        |trait Openish:
        |  def abstractOne: Int
        |  def concrete: Int = 1
        |
        |sealed trait Choice
        |case class Alpha(x: Int) extends Choice
        |object Beta extends Choice
        |
        |class Overloads:
        |  def f(x: Int): Int = x
        |  def f(x: String): String = x
        |  private def hidden: Int = 0
        |
        |object Tops:
        |  val value: Int = 3
        |
        |inline def double(n: Int): Int = n * 2
        |""".s.stripMargin.tt

  def run(): Unit = proscalaLibrary().let: lib =>
    val jars = scala.List("scala-library.jar", "scala3-library.jar").map(lib.resolve(_).nn)
    val classpath = LocalClasspath(jars.map { jar => Classpath.Entry.Jar(jar.toString.tt) }*)
    val libraryPaths = jars.map { jar => Text(jar.toString) }

    def compileWith(source: Text, deps: LocalClasspath, libs: scala.List[Text], sjs: Boolean)
    :   (List[Text], List[Text], Text) =

      supervise:
        val out: soundness.Path on Linux = unsafely(temporaryDirectory / Uuid())
        Files.createDirectories(Paths.get(out.encode.s))

        val process =
          if sjs then
            Scalac[3.9](List()).targeting[Universe.Sjsir]
              (deps)(Map(t"fixture.scala" -> source), out)
          else Scalac[3.9](List())(deps)(Map(t"fixture.scala" -> source), out)

        process.complete()

        val tastyFiles = Files.walk(Paths.get(out.encode.s)).nn.iterator.nn.asScala
          . to(scala.List)
          . filter { path => path.toString.endsWith(".tasty") }
          . map { path => Text(path.toString) }

        (tastyFiles.to(List), (Text(out.encode.s) :: libs).to(List), out.encode)

    def compile(source: Text): (List[Text], List[Text]) =
      val (tastyFiles, classpath0, _) = compileWith(source, classpath, libraryPaths, false)
      (tastyFiles, classpath0)

    test(m"the discipline adapter claims tasty and derived binaries"):
      val path = TreePath(t"fixture/Alpha.tasty")

      (TastyDiscipline.claims(path, Array.freeze(Array.allocate[Byte](0))),
       TastyDiscipline.claims(TreePath(t"fixture/Alpha.class"), Array.freeze(Array.allocate[Byte](0))),
       TastyDiscipline.claims(TreePath(t"readme.md"), Array.freeze(Array.allocate[Byte](0))))
    . assert(_ == (true, true, false))

    test(m"a jvm-only lira assembles from a real compilation and verifies"):
      val (_, _, out) = compileWith(fixture, classpath, libraryPaths, false)

      val compilation =
        Compilation[Universe.Classfile](unsafely(out.s.tt.as[soundness.Path on Linux]), classpath)

      val input = LiraBundle(compilation)
      val registry = Discipline.Registry(List(TastyDiscipline))

      val bytes = LiraAssembler.assemble
        ( t"fixture-core",
          List(input),
          registry,
          toolchain = List(LiraBundle.tool[Universe.Classfile](t"3.9.0")),
          owns      = List(t"fixture"),
          classpath = { _ => (Text(out.s) :: libraryPaths).to(List) } )

      val lira = Lira.read(bytes)
      val report = Verification.install(lira)

      (lira.manifest.module,
       lira.manifest.api.stdlib.map(_.discipline),
       lira.manifest.section.stdlib.map(_.realm),
       lira.manifest.section.stdlib.forall(_.derivative.present),
       report.atomizations.stdlib.map(_.discipline))
    . assert(_ == (t"fixture-core", scala.List(t"tasty/1"), scala.List(t"jvm"), true,
        scala.List(t"tasty/1")))

    val sjsJars = scala.List("scala3-library_sjs1.jar", "scalajs-scalalib_2.13.jar")
      . map(lib.resolve(_).nn)
      . filter(Files.exists(_))
      . ++ (Files.list(lib).nn.iterator.nn.asScala.to(scala.List).filter: path =>
          val name = path.getFileName.nn.toString
          name.startsWith("scalajs-library_2.13") || name.startsWith("scalajs-javalib"))

    if sjsJars.size >= 3 then
      val sjsClasspath = LocalClasspath
        ((jars ++ sjsJars).map { jar => Classpath.Entry.Jar(jar.toString.tt) }*)

      val sjsLibraryPaths = (jars ++ sjsJars).map { jar => Text(jar.toString) }

      test(m"a two-universe lira upholds the cross-universe invariant"):
        val (_, _, jvmOut) = compileWith(fixture, classpath, libraryPaths, false)
        val (_, _, sjsOut) = compileWith(fixture, sjsClasspath, sjsLibraryPaths, true)

        val jvmInput = LiraBundle(Compilation[Universe.Classfile]
          (unsafely(jvmOut.s.tt.as[soundness.Path on Linux]), classpath))

        val sjsInput = LiraBundle(Compilation[Universe.Sjsir]
          (unsafely(sjsOut.s.tt.as[soundness.Path on Linux]), sjsClasspath))

        val registry = Discipline.Registry(List(TastyDiscipline))

        def contextClasspath(universe: Text): List[Text] =
          if universe == t"sjsir" then (Text(sjsOut.s) :: sjsLibraryPaths).to(List)
          else (Text(jvmOut.s) :: libraryPaths).to(List)

        val bytes = LiraAssembler.assemble
          ( t"fixture-core",
            List(jvmInput, sjsInput),
            registry,
            toolchain = List(LiraBundle.tool[Universe.Classfile](t"3.9.0")),
            classpath = { input => contextClasspath(input.realm) } )

        val lira = Lira.read(bytes)
        val report = Verification.install(lira)
        val sjsSection = lira.manifest.section.stdlib.find(_.realm == t"sjsir")

        (lira.manifest.section.stdlib.map(_.realm),
         report.materialized.stdlib.map(_(0).realm),
         report.materialized.stdlib.find(_(0).realm == t"sjsir")
           . map(_(1).entries.stdlib.exists(_.path.text.s.endsWith(".sjsir"))))
      . assert(_ == (scala.List(t"jvm", t"sjsir"), scala.List(t"jvm", t"sjsir"),
          scala.Some(true)))

    test(m"the discipline adapter atomizes tasty and holds binaries atomless"):
      val (tastyFiles, _) = compile(fixture)

      val content = tastyFiles.map: file =>
        val name = Text(Paths.get(file.s).nn.getFileName.nn.toString)
        val data = Array.unsafeFrozen(Files.readAllBytes(Paths.get(file.s)).nn)
        (TreePath(t"fixture/$name"), data)

      val binary = (TreePath(t"fixture/Alpha.class"), Array.freeze(Array.allocate[Byte](4)))
      val all = (content.stdlib :+ binary).to(List)
      val context = Discipline.Context(t"jvm", classpath = libraryPaths.to(List))
      val atomization = TastyDiscipline.atomize(all, context)

      (atomization.discipline,
       atomization.atoms.stdlib.exists(_.key.s.startsWith("fixture.Overloads.f(")),
       atomization.atoms.stdlib.exists(_.key.s.contains("Alpha.class")))
    . assert(_ == (t"tasty/1", true, false))
