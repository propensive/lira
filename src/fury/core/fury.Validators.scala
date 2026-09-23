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
package fury

import soundness.*

// The validators the shipped schemas' scalars name (build.schema.tel and its siblings): each is
// a shape check on one atom, never a resolution — whether a module reference resolves is `fury
// resolve`'s business, and whether a form is registered is the registry's (fury.md §7).
object Validators:
  import Tel.Validator.{Diagnostic, Registry, Request, Response}

  val registry: Registry = Registry.withFallback:
    new Registry:
      override def apply(request: Request): Response = request match
        case Request.Scalar(method, value) => method.s match
          case "module-ref"    => moduleRef(value)
          case "selector"      => selector(value)
          case "glob"          => nonEmpty(value, t"a glob")
          case "form"          => form(value, t"a form")
          case "path"          => nonEmpty(value, t"a path")
          case "address"       => address(value)
          case "kind"          => kebab(value, t"a presumption kind")
          case "toolchain-ref" => toolchainRef(value)
          case "app-type"      => form(value, t"an application type")
          case "entry"         => form(value, t"an entry")
          case "discipline-id" => disciplineId(value)
          case "effect"        => effect(value)
          case _               => unknown(method)

        case Request.Struct(method, _) => unknown(method)

  private def unknown(method: Text): Response =
    Response.Invalid(Diagnostic.Scalar(t"unknown validator '$method'"))

  private def fail(value: Text, message: Text): Response =
    Response.Invalid(Diagnostic.Scalar(message, (0, value.s.length)))

  private def matches(value: Text, pattern: String): Boolean = value.s.matches(pattern)

  // One kebab-case segment: lowercase letters and digits, single hyphens within.
  private val segment: String = "[a-z0-9]+(-[a-z0-9]+)*"

  // A dotted name: kebab segments joined by `.` — a DNS domain (`soundness.dev`) or a vendor-named
  // contract (`java.base`, hosts.md §3).
  private val dotted: String = segment + "(\\." + segment + ")*"

  // A DNS-style domain: two or more dotted segments.
  private val domain: String = segment + "(\\." + segment + ")+"

  private def kebab(value: Text, what: Text): Response =
    if matches(value, segment) then Response.Valid
    else fail(value, t"$what must be kebab-case: lowercase letters, digits and single hyphens")

  private def nonEmpty(value: Text, what: Text): Response =
    if value.s.isEmpty then fail(value, t"$what must not be empty") else Response.Valid

  // A module name (kebab, or vendor-dotted for a host contract), a project-qualified
  // `<project>/<module>`, or an index coordinate `<domain>/<name>`.
  private def moduleRef(value: Text): Response =
    if matches(value, dotted) || matches(value, dotted + "/" + segment) then Response.Valid
    else fail(value, t"a module reference is a name, <project>/<module> or <domain>/<name>")

  // A version prefix `x[.y[.z]]`, or a tag name where the token is not version-shaped (§12.6): a
  // letter, then letters, digits, `-` and `.`.
  private def selector(value: Text): Response =
    if matches(value, "[0-9]+(\\.[0-9]+){0,2}") || matches(value, "[a-zA-Z][a-zA-Z0-9.-]*")
    then Response.Valid
    else fail(value, t"a selector is a version prefix x[.y[.z]] or a tag name")

  // A form name, possibly `<family>/<parameter>` with a bound value (`native-exe/x86_64-linux-gnu`)
  // or an angle-bracket placeholder (`oci-image/<platform>`).
  private def form(value: Text, what: Text): Response =
    if matches(value, segment + "(/([a-z0-9_-]+|<[a-z]+>))?") then Response.Valid
    else fail(value, t"$what is a kebab-case form, optionally with a /<parameter>")

  private def address(value: Text): Response =
    if matches(value, domain + "(/[a-zA-Z0-9._-]+)*") then Response.Valid
    else fail(value, t"an address is a DNS name with an optional /-separated path prefix")

  // A discipline identifier, `<name>/<version>` (spec §11.1): `tasty/1`, `classfile/1`.
  private def disciplineId(value: Text): Response =
    if matches(value, segment + "/[0-9]+") then Response.Valid
    else fail(value, t"a discipline identifier is <name>/<version>, e.g. tasty/1")

  // A setting's effect classification (tool.schema.tel): `output` or `nothing`.
  private def effect(value: Text): Response =
    if value == t"output" || value == t"nothing" then Response.Valid
    else fail(value, t"a setting affects `output` or `nothing`")

  private def toolchainRef(value: Text): Response =
    if matches(value, segment + "(/" + segment + ")*") then Response.Valid
    else fail(value, t"a toolchain reference is a /-separated path of kebab-case names")
