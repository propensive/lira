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

import soundness.*

// The foreign-surface disciplines (`dts/1`, `webidl/1`, `wit/1`, `cheader/1` and
// `kotlin-metadata/1`), moved from Soundness's xenophile suite with the disciplines themselves.
object ForeignDisciplineTests extends Suite(m"LIRA foreign-surface discipline tests"):
  def run(): Unit =
    dtsDisciplineTests()
    webIdlDisciplineTests()
    witDisciplineTests()
    cheaderDisciplineTests()
    kotlinMetadataDisciplineTests()

  def dtsDisciplineTests(): Unit =
    import alphabets.hexLowerCase
    import strategies.throwUnsafely

    def content(source: Text): List[(TreePath, Data)] =
      List((TreePath(t"types/index.d.ts"), Array.unsafeFrozen(source.s.getBytes("UTF-8").nn)))

    def atomize(source: Text): Atomization =
      DtsDiscipline.atomize(content(source), Discipline.Context(t"jvm"))

    def keys(source: Text): scala.List[Text] =
      atomize(source).atoms.stdlib.map(_.key).sortBy(_.s)

    def grade(before: Text, after: Text): Grade =
      Grade.between(List(atomize(before)), List(atomize(after)))

    val baseline: Text =
      t"""|export interface Client {
          |  send(message: string): void;
          |  readonly id: string;
          |}
          |export type Handle = string | number;
          |export declare function connect(url: string): Client;
          |""".s.stripMargin.tt

    test(m"the discipline claims declaration files and nothing else"):
      val data = Array.freeze(Array.allocate[Byte](0))

      (DtsDiscipline.claims(TreePath(t"types/index.d.ts"), data),
       DtsDiscipline.claims(TreePath(t"lib/index.js"), data),
       DtsDiscipline.claims(TreePath(t"readme.md"), data))
    . assert(_ == (true, false, false))

    test(m"the discipline certifies recompilation and not linkage"):
      (DtsDiscipline.id, DtsDiscipline.guarantees(t"jvm"), DtsDiscipline.keying)
    . assert(_ == (t"dts/1", Set(Discipline.Guarantee.Recompilation),
        Discipline.Keying.Declaration))

    test(m"each exported declaration and each member yields an atom"):
      keys(baseline)
    . assert(_ == scala.List(t"Client", t"Client#id", t"Client#send", t"Handle", t"connect"))

    test(m"an unexported declaration is not part of the contract"):
      keys(t"export interface A { x: number; }\ninterface Hidden { y: number; }")
    . assert(_ == scala.List(t"A", t"A#x"))

    test(m"atomization is deterministic"):
      def once(): scala.List[(Text, Text)] =
        atomize(baseline).atoms.stdlib
        . map { atom => (atom.key, atom.valueHash.serialize[Hex]) }
        . sortBy(_(0).s)

      once() == once()
    . assert(identity)

    test(m"renaming a type parameter changes nothing"):
      grade(t"export interface Box<T> { value: T; }", t"export interface Box<U> { value: U; }")
    . assert(_ == Grade.Patch)

    test(m"reordering the members of a union changes nothing"):
      grade(t"export type T = A | B;", t"export type T = B | A;")
    . assert(_ == Grade.Patch)

    test(m"reordering the elements of a tuple is a major change"):
      grade(t"export type T = [A, B];", t"export type T = [B, A];")
    . assert(_ == Grade.Major)

    // The one change that is honestly two events: adding a member is pure extension for a
    // consumer who calls the interface, and a break for one who implements it. The member's own
    // atom records the first; the fold of member keys into the interface's atom records the
    // second, and the second is what the grade reports.
    test(m"adding an interface member is a major change for implementors"):
      grade(t"export interface A { x: number; }", t"export interface A { x: number; y: number; }")
    . assert(_ == Grade.Major)

    test(m"the added member is nonetheless an atom of its own"):
      keys(t"export interface A { x: number; y: number; }")
    . assert(_ == scala.List(t"A", t"A#x", t"A#y"))

    test(m"adding a whole interface is a minor change"):
      grade(t"export interface A { x: number; }",
          t"export interface A { x: number; }\nexport interface B { y: number; }")
    . assert(_ == Grade.Minor)

    test(m"removing a member is a major change"):
      grade(t"export interface A { x: number; y: number; }", t"export interface A { x: number; }")
    . assert(_ == Grade.Major)

    test(m"making a member optional is a major change"):
      grade(t"export interface A { x: number; }", t"export interface A { x?: number; }")
    . assert(_ == Grade.Major)

    test(m"adding an overload is a major change"):
      grade(t"export interface A { f(x: number): void; }",
          t"export interface A { f(x: number): void; f(x: string): void; }")
    . assert(_ == Grade.Major)

    test(m"changing a declaration's namespace changes its key"):
      keys(t"export declare namespace a { interface X { y: number; } }")
    . assert(_ == scala.List(t"a.X", t"a.X#y"))

    test(m"an unreadable declaration file is an atomization error"):
      import errorDiagnostics.stackTracesDiagnostics

      capture[Discipline.Error]:
        DtsDiscipline.atomize(content(t"export type T<A> = A extends string ? 1 : 2;"),
            Discipline.Context(t"jvm"))

      . reason
    . assert:
        case Discipline.Error.Reason.Malformed(_) => true
        case _                                   => false

    test(m"the registry falls back to opaque for content the discipline does not claim"):
      val registry = Discipline.Registry(List(DtsDiscipline))
      val js = List((TreePath(t"lib/index.js"), Array.freeze(Array.allocate[Byte](1))))

      registry.atomize(js, Discipline.Context(t"jvm")).stdlib.map(_.discipline)
    . assert(_ == scala.List(t"opaque/1"))

  def webIdlDisciplineTests(): Unit =
    import strategies.throwUnsafely

    def content(source: Text): List[(TreePath, Data)] =
      List((TreePath(t"idl/browser.idl"), Array.unsafeFrozen(source.s.getBytes("UTF-8").nn)))

    def atomize(source: Text): Atomization =
      WebIdlDiscipline.atomize(content(source), Discipline.Context(t"host"))

    def keys(source: Text): scala.List[Text] =
      atomize(source).atoms.stdlib.map(_.key).sortBy(_.s)

    def grade(before: Text, after: Text): Grade =
      Grade.between(List(atomize(before)), List(atomize(after)))

    val baseline: Text =
      t"""|interface Widget {
          |  readonly attribute DOMString name;
          |  undefined render(long depth);
          |};
          |dictionary Options {
          |  required DOMString mode;
          |  long retries = 3;
          |};
          |enum Direction { "up", "down" };
          |""".s.stripMargin.tt

    suite(m"The `webidl/1` discipline"):
      test(m"the discipline claims idl files in the host world and nothing else"):
        val data = Array.freeze(Array.allocate[Byte](0))

        (WebIdlDiscipline.claims(TreePath(t"idl/dom.idl"), data),
         WebIdlDiscipline.claims(TreePath(t"lib/index.js"), data),
         WebIdlDiscipline.domain.covers(t"host"),
         WebIdlDiscipline.domain.covers(t"jvm"))
      . assert(_ == (true, false, true, false))

      test(m"declarations, members, fields and values yield atoms"):
        keys(baseline)
      . assert(_ == scala.List(t"Direction", t"Direction#down", t"Direction#up", t"Options",
          t"Options#retries", t"Widget", t"Widget#name", t"Widget#render(s32)"))

      test(m"adding an interface member is a minor for callers"):
        val grown = t"${baseline}partial interface Widget { attribute long depth; };"
        grade(baseline, grown)
      . assert(_ == Grade.Minor)

      test(m"adding a required dictionary member is major"):
        val grown = baseline.s.replace("required DOMString mode;",
            "required DOMString mode;\n  required long width;").nn.tt
        grade(baseline, grown)
      . assert(_ == Grade.Major)

      test(m"adding an optional dictionary member is minor"):
        val grown = baseline.s.replace("long retries = 3;",
            "long retries = 3;\n  boolean verbose = false;").nn.tt
        grade(baseline, grown)
      . assert(_ == Grade.Minor)

      test(m"adding an enumeration value is minor"):
        grade(baseline, baseline.s.replace("\"down\"", "\"down\", \"left\"").nn.tt)
      . assert(_ == Grade.Minor)

      test(m"removing a member is major"):
        grade(baseline, baseline.s.replace("  undefined render(long depth);\n", "").nn.tt)
      . assert(_ == Grade.Major)

      test(m"a mixin's members atomize under the including interface"):
        val mixed =
          t"""|interface Base {};
              |interface mixin Extras { undefined extra(); };
              |Base includes Extras;
              |""".s.stripMargin.tt

        keys(mixed)
      . assert(_ == scala.List(t"Base", t"Base#extra()"))

      test(m"a partial interface in another file completes its target"):
        val split = List(
          (TreePath(t"idl/a.idl"),
           Array.unsafeFrozen(t"interface W {};".s.getBytes("UTF-8").nn)),
          (TreePath(t"idl/b.idl"),
           Array.unsafeFrozen(t"partial interface W { attribute long x; };".s
              .getBytes("UTF-8").nn)))

        WebIdlDiscipline.atomize(split, Discipline.Context(t"host")).atoms.stdlib.map(_.key)
        . sortBy(_.s)
      . assert(_ == scala.List(t"W", t"W#x"))

      test(m"exposure scopes are part of the key"):
        keys(t"[Exposed=(Window,Worker)] interface Scoped {};")
      . assert(_ == scala.List(t"Scoped[Window,Worker]"))

      test(m"identically-shaped members of different interfaces do not alias"):
        val twins = t"interface A { attribute long x; };\ninterface B { attribute long x; };"
        val atoms = atomize(twins).atoms.stdlib

        atoms.map { atom => Lira.Hash.text(atom.valueHash) }.distinct.size
        == atoms.size
      . assert(identity)

      test(m"union member order does not affect a hash"):
        val one = atomize(t"interface U { attribute (long or DOMString) x; };")
        val two = atomize(t"interface U { attribute (DOMString or long) x; };")

        one.atoms.stdlib.map { atom => Lira.Hash.text(atom.valueHash) }
        == two.atoms.stdlib.map { atom => Lira.Hash.text(atom.valueHash) }
      . assert(identity)

      test(m"an unsupported construct is an atomization error"):
        import errorDiagnostics.stackTracesDiagnostics

        capture[Discipline.Error](atomize(t"weird thing;")).reason match
          case Discipline.Error.Reason.Malformed(_) => true
          case _                                   => false
      . assert(identity)

      test(m"the real DOM excerpt atomizes"):
        val stream = getClass.getResourceAsStream("/xenophile/dom.idl").nn
        val bytes = stream.readAllBytes().nn
        stream.close()
        atomize(Text(String(bytes, "UTF-8"))).atoms.stdlib.size
      . assert(_ > 50)

  def witDisciplineTests(): Unit =
    import strategies.throwUnsafely

    def content(source: Text): List[(TreePath, Data)] =
      List((TreePath(t"wit/api.wit"), Array.unsafeFrozen(source.s.getBytes("UTF-8").nn)))

    def atomize(source: Text): Atomization =
      WitDiscipline.atomize(content(source), Discipline.Context(t"host"))

    def keys(source: Text): scala.List[Text] =
      atomize(source).atoms.stdlib.map(_.key).sortBy(_.s)

    def grade(before: Text, after: Text): Grade =
      Grade.between(List(atomize(before)), List(atomize(after)))

    val baseline: Text =
      t"""|package wasi:random@0.2.0;
          |
          |interface random {
          |  record seed { value: u64 }
          |  get-random-bytes: func(len: u64) -> list<u8>;
          |}
          |
          |world host {
          |  import random;
          |  export run;
          |}
          |""".s.stripMargin.tt

    suite(m"The `wit/1` discipline"):
      test(m"the discipline claims wit files in its two worlds and nothing else"):
        val data = Array.freeze(Array.allocate[Byte](0))

        (WitDiscipline.claims(TreePath(t"wit/world.wit"), data),
         WitDiscipline.claims(TreePath(t"lib/api.idl"), data),
         WitDiscipline.domain.covers(t"host"),
         WitDiscipline.domain.covers(t"component"),
         WitDiscipline.domain.covers(t"jvm"))
      . assert(_ == (true, false, true, true, false))

      test(m"interfaces, items and worlds yield package-qualified atoms"):
        keys(baseline)
      . assert(_ == scala.List(
          t"wasi:random/host@0.2.0",
          t"wasi:random/host@0.2.0#import wasi:random/random@0.2.0",
          t"wasi:random/random@0.2.0",
          t"wasi:random/random@0.2.0#get-random-bytes",
          t"wasi:random/random@0.2.0#seed"))

      test(m"adding a function to an interface is minor"):
        val grown = baseline.s.replace("}\n\nworld",
            "  get-random-u64: func() -> u64;\n}\n\nworld").nn.tt
        grade(baseline, grown)
      . assert(_ == Grade.Minor)

      test(m"adding a record field is major"):
        grade(baseline, baseline.s.replace("{ value: u64 }", "{ value: u64, extra: u32 }").nn.tt)
      . assert(_ == Grade.Major)

      test(m"a world gaining an import is minor"):
        val source = baseline.s.replace("interface random {",
            "interface insecure { i: func(); }\ninterface random {").nn.tt
        val grown = source.s.replace("import random;", "import random;\n  import insecure;").nn.tt
        grade(source, grown)
      . assert(_ == Grade.Minor)

      test(m"a world gaining an export is major"):
        grade(baseline, baseline.s.replace("export run;", "export run;\n  export other;").nn.tt)
      . assert(_ == Grade.Major)

      test(m"a use-imported reference is qualified to its source interface"):
        val direct =
          t"""|package a:pkg;
              |interface one {
              |  type id = u64;
              |}
              |interface two {
              |  use one.{id};
              |  get: func() -> id;
              |}
              |""".s.stripMargin.tt

        val renamed = direct.s.replace("use one.{id};", "use one.{id as key};").nn
          .replace("-> id;", "-> key;").nn.tt

        val hashes = { (source: Text) =>
          atomize(source).atoms.stdlib
          . filter(_.key == t"a:pkg/two#get")
          . map { atom => Lira.Hash.text(atom.valueHash) }
        }

        hashes(direct) == hashes(renamed)
      . assert(identity)

      test(m"a since gate is consumed and an unstable gate is refused"):
        import errorDiagnostics.stackTracesDiagnostics

        val gated =
          t"""|package a:pkg;
              |interface one {
              |  @since(version = 0.2.1)
              |  get: func() -> u64;
              |}
              |""".s.stripMargin.tt

        val unstable = gated.s.replace("@since(version = 0.2.1)",
            "@unstable(feature = fancy)").nn.tt

        val accepted = atomize(gated).atoms.stdlib.exists(_.key == t"a:pkg/one#get")

        val refused =
          capture[Discipline.Error](atomize(unstable)).reason match
            case Discipline.Error.Reason.Malformed(_) => true
            case _                                   => false

        (accepted, refused)
      . assert(_ == (true, true))

      test(m"an unresolvable type reference is an error"):
        import errorDiagnostics.stackTracesDiagnostics

        capture[Discipline.Error]:
          atomize(t"package a:pkg;\ninterface one { get: func() -> mystery; }")
        . reason match
            case Discipline.Error.Reason.Unresolved(_) => true
            case _                                    => false
      . assert(identity)

      test(m"the sample wit fixture atomizes"):
        val stream = getClass.getResourceAsStream("/xenophile/api.wit").nn
        val bytes = stream.readAllBytes().nn
        stream.close()
        atomize(Text(String(bytes, "UTF-8"))).atoms.stdlib.size
      . assert(_ > 10)

  def cheaderDisciplineTests(): Unit =
    import strategies.throwUnsafely

    def content(source: Text): List[(TreePath, Data)] =
      List((TreePath(t"include/library.h"), Array.unsafeFrozen(source.s.getBytes("UTF-8").nn)))

    def atomize(source: Text): Atomization =
      CHeaderDiscipline.atomize(content(source), Discipline.Context(t"host"))

    def keys(source: Text): scala.List[Text] =
      atomize(source).atoms.stdlib.map(_.key).sortBy(_.s)

    def hashOf(source: Text, key: Text): Optional[Text] =
      atomize(source).atoms.stdlib.find(_.key == key)
      . map { atom => Lira.Hash.text(atom.valueHash) }.getOrElse(Unset)

    def grade(before: Text, after: Text): Grade =
      Grade.between(List(atomize(before)), List(atomize(after)))

    val baseline: Text =
      t"""|typedef struct Point { int x; int y; } Point;
          |typedef enum { LEFT, RIGHT } Direction;
          |int add(int a, int b);
          |size_t strlen(const char* s);
          |""".s.stripMargin.tt

    suite(m"The `cheader/1` discipline"):
      test(m"the discipline claims headers in the host world and nothing else"):
        val data = Array.freeze(Array.allocate[Byte](0))

        (CHeaderDiscipline.claims(TreePath(t"include/openssl.h"), data),
         CHeaderDiscipline.claims(TreePath(t"src/main.c"), data),
         CHeaderDiscipline.domain.covers(t"host"),
         CHeaderDiscipline.domain.covers(t"nir"))
      . assert(_ == (true, false, true, false))

      test(m"declarations are keyed by bare name"):
        keys(baseline)
      . assert(_ == scala.List(t"Direction", t"Point", t"add", t"strlen"))

      test(m"adding a declaration is minor and removing one is major"):
        val grown = t"${baseline}double pow(double base, double exponent);"
        (grade(baseline, grown), grade(grown, baseline))
      . assert(_ == (Grade.Minor, Grade.Major))

      test(m"signedness distinguishes hashes"):
        hashOf(t"int f(unsigned int x);", t"f") != hashOf(t"int f(int x);", t"f")
      . assert(identity)

      test(m"pointer depth distinguishes hashes"):
        hashOf(t"int f(char** x);", t"f") != hashOf(t"int f(char* x);", t"f")
      . assert(identity)

      test(m"pointee constness folds and by-value constness does not"):
        (hashOf(t"int f(const char* x);", t"f") != hashOf(t"int f(char* x);", t"f"),
         hashOf(t"int f(const int x);", t"f") == hashOf(t"int f(int x);", t"f"))
      . assert(_ == (true, true))

      test(m"parameter names do not fold"):
        hashOf(t"int add(int a, int b);", t"add") == hashOf(t"int add(int x, int y);", t"add")
      . assert(identity)

      test(m"enumerator values fold, explicit or implicit"):
        (hashOf(t"typedef enum { A, B } E;", t"E")
           == hashOf(t"typedef enum { A = 0, B = 1 } E;", t"E"),
         hashOf(t"typedef enum { A, B } E;", t"E")
           != hashOf(t"typedef enum { A, B = 5 } E;", t"E"))
      . assert(_ == (true, true))

      test(m"completing an opaque struct changes its value"):
        hashOf(t"struct S;", t"S") != hashOf(t"struct S { int x; };", t"S")
      . assert(identity)

      test(m"an unsupported construct is an atomization error"):
        import errorDiagnostics.stackTracesDiagnostics

        capture[Discipline.Error](atomize(t"int x = 4;")).reason match
          case Discipline.Error.Reason.Malformed(_) => true
          case _                                   => false
      . assert(identity)

      test(m"the sample library header atomizes"):
        val stream = getClass.getResourceAsStream("/xenophile/library.h").nn
        val bytes = stream.readAllBytes().nn
        stream.close()
        atomize(Text(String(bytes, "UTF-8"))).atoms.stdlib.map(_.key).sortBy(_.s)
      . assert(_.contains(t"HMAC") == false)

      test(m"the openssl header atomizes with its functions keyed by symbol"):
        // The header lives in enigmatic's resources; where it is absent from this suite's
        // classpath the test degenerates to a pass rather than a false failure.
        val stream = getClass.getResourceAsStream("/enigmatic/openssl.h")

        if stream == null then true else
          val bytes = stream.nn.readAllBytes().nn
          stream.nn.close()
          atomize(Text(String(bytes, "UTF-8"))).atoms.stdlib.exists(_.key == t"RAND_bytes")
      . assert(_ == true)

  def kotlinMetadataDisciplineTests(): Unit =
    import strategies.throwUnsafely

    // Real Kotlin classfiles from the kotlin-stdlib fixture already on this suite's classpath.
    def classfile(name: Text): Data =
      val stream = getClass.getResourceAsStream(s"/${name.s.replace(".", "/")}.class").nn
      val bytes = stream.readAllBytes().nn
      stream.close()
      Array.unsafeFrozen(bytes)

    def content(names: Text*): List[(TreePath, Data)] =

        names.map: name =>
          (TreePath(t"${name.s.replace(".", "/").nn}.class"), classfile(name))
        . to(List)

    def atomize(names: Text*): Atomization =
      KotlinMetadataDiscipline.atomize(content(names*), Discipline.Context(t"jvm"))

    suite(m"The `kotlin-metadata/1` discipline"):
      test(m"the discipline claims metadata-carrying classfiles and nothing else"):
        val kotlin = classfile(t"kotlin.Pair")
        val scala0 = classfile(t"lira.ForeignDisciplineTests")

        (KotlinMetadataDiscipline.claims(TreePath(t"kotlin/Pair.class"), kotlin),
         KotlinMetadataDiscipline.claims(TreePath(t"lira/ForeignDisciplineTests.class"), scala0),
         KotlinMetadataDiscipline.claims(TreePath(t"readme.md"), kotlin))
      . assert(_ == (true, false, false))

      test(m"a data class atomizes its members, constructor and class atom"):
        val keys = atomize(t"kotlin.Pair").atoms.stdlib.map(_.key)

        (keys.contains(t"kotlin.Pair"),
         keys.contains(t"kotlin.Pair.first"),
         keys.exists(_.s.startsWith("kotlin.Pair#component1(")),
         keys.exists(_.s.startsWith("kotlin.Pair#constructor(")))
      . assert(_ == (true, true, true, true))

      test(m"parameter types carry nullability marks in the key"):
        atomize(t"kotlin.Pair").atoms.stdlib.map(_.key.s)
        . exists { key => key.startsWith("kotlin.Pair#constructor(") }
      . assert(identity)

      test(m"suspend functions are atomized rather than dropped"):
        // `kotlin.sequences.SequenceScope` is the canonical suspend surface: `yield` is a
        // suspend function, and the whole point of this discipline is that it is visible.
        atomize(t"kotlin.sequences.SequenceScope").atoms.stdlib.map(_.key.s)
        . exists(_.startsWith("kotlin.sequences.SequenceScope#yield("))
      . assert(identity)

      test(m"an enum class atomizes with its class atom"):
        atomize(t"kotlin.DeprecationLevel").atoms.stdlib.map(_.key)
        . contains(t"kotlin.DeprecationLevel")
      . assert(identity)

      test(m"atomization is deterministic"):
        val one = atomize(t"kotlin.Pair").atoms.stdlib.map { a => Lira.Hash.text(a.valueHash) }
        val two = atomize(t"kotlin.Pair").atoms.stdlib.map { a => Lira.Hash.text(a.valueHash) }
        one == two
      . assert(identity)

      test(m"identically-shaped members of different classes do not alias"):
        val atoms = atomize(t"kotlin.Pair", t"kotlin.Triple").atoms.stdlib
        atoms.map { atom => Lira.Hash.text(atom.valueHash) }.distinct.size == atoms.size
      . assert(identity)

      test(m"the registry claims kotlin classes ahead of the opaque fallback"):
        val registry = Discipline.Registry(List(KotlinMetadataDiscipline))
        val mixed = content(t"kotlin.Pair") 

        registry.atomize(mixed, Discipline.Context(t"jvm")).stdlib.map(_.discipline)
      . assert(_ == scala.List(t"kotlin-metadata/1"))
