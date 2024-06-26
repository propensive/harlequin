/*
    Harlequin, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package harlequin

import escapade.*
import iridescence.*
import spectacular.*
import vacuous.*
import gossamer.*
import hieroglyph.*, textMetrics.uniform

package syntaxHighlighting:
  import Accent.*

  given Token is Teletypeable =
    case Token.Unparsed(text)       => text.teletype
    case Token.Markup(text)         => text.teletype
    case Token.Newline              => e"\n"
    case Token.Code(text, Error)    => e"${rgb"#cc0033"}($text)"
    case Token.Code(text, Number)   => e"${rgb"#cc3366"}($text)"
    case Token.Code(text, Modifier) => e"${rgb"#ff9966"}($text)"
    case Token.Code(text, Keyword)  => e"${rgb"#ff6633"}($text)"
    case Token.Code(text, Ident)    => e"${rgb"#ffcc99"}($text)"
    case Token.Code(text, Term)     => e"${rgb"#ffcc33"}($text)"
    case Token.Code(text, Typed)    => e"${rgb"#00cc99"}($text)"
    case Token.Code(text, String)   => e"${rgb"#99ffff"}($text)"
    case Token.Code(text, Parens)   => e"${rgb"#cc6699"}($text)"
    case Token.Code(text, Symbol)   => e"${rgb"#cc3366"}($text)"

  given ScalaSource is Teletypeable as numbered = source =>
    val indent = source.lastLine.show.length
    lazy val error = e"${rgb"#cc0033"}(║)"

    val markup = source.focus.lay(e""):
      case ((startLine, startColumn), (endLine, endColumn)) =>
        if startLine != endLine then e"\n" else
          if startColumn == endColumn then e"\n${t" "*(startColumn + indent + 2)}${rgb"#ff0033"}(╱╲)"
          else e"\n${t" "*(startColumn + indent + 3)}${rgb"#ff0033"}(${t"‾"*(endColumn - startColumn)})"

    (source.offset to source.lastLine).map: lineNo =>
      val content = source(lineNo).map(_.teletype).join
      source.focus.mask:
        case ((startLine, _), (endLine, _)) => startLine != endLine && lineNo > startLine && lineNo <= endLine + 1
      .let: focus =>
        e"${Bg(rgb"#003333")}(${rgb"#99cc99"}(${lineNo.show.pad(indent, Rtl)})${rgb"#336666"}(┋))  $content"
      .or:
        e"${Bg(rgb"#003333")}(${rgb"#99cc99"}(${lineNo.show.pad(indent, Rtl)})${rgb"#336666"}(┋)) $error$content"

    .join(e"", e"\n", markup)

  given ScalaSource is Teletypeable as unnumbered = source =>
    (source.offset to source.lastLine).map: lineNo =>
      source(lineNo).map(_.teletype).join
    .join(e"\n")
