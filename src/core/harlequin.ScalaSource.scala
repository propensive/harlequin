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

import rudiments.*
import anticipation.*
import gossamer.{slice as _, *}
import vacuous.*

import dotty.tools.dotc.*, core.*, parsing.*, util.*, reporting.*

import scala.collection.mutable as scm

case class ScalaSource
    (offset: Int,
     lines:  IArray[Seq[ScalaCode]],
     focus:  Optional[((Int, Int), (Int, Int))] = Unset):

  def lastLine: Int = offset + lines.length - 1
  def apply(line: Int): Seq[ScalaCode] = lines(line - offset)

  def fragment
      (startLine: Int, endLine: Int, focus: Optional[((Int, Int), (Int, Int))] = Unset)
          : ScalaSource =
    ScalaSource(startLine, lines.slice(startLine - offset, endLine - offset + 1), focus)

object ScalaSource:
  def accent(token: Int): Accent =
    if token <= 2 then Accent.Error
    else if token == 3 || token == 10 || token == 13 then Accent.String
    else if token >= 4 && token <= 9 then Accent.Number
    else if token == 14 || token == 15 then Accent.Ident
    else if token >= 20 && token <= 62 && Tokens.modifierTokens.contains(token) then Accent.Modifier
    else if token >= 20 && token <= 62 then Accent.Keyword
    else if token >= 63 && token <= 84 then Accent.Symbol
    else Accent.Parens

  def highlight(text: Text): ScalaSource =
    val source = SourceFile.virtual("<highlighting>", text.s)
    val ctx0 = Contexts.ContextBase().initialCtx.fresh.setReporter(Reporter.NoReporter)
    given Contexts.Context = ctx0.setCompilationUnit(CompilationUnit(source, mustExist = false)(using ctx0))
    val trees = Trees()
    val parser = Parsers.Parser(source)

    trees.traverse(parser.compilationUnit())
    val scanner = Scanners.Scanner(source)

    def untab(text: Text): LazyList[ScalaCode] =
      LazyList(ScalaCode(text.sub(t"\t", t"  "), Accent.Unparsed), ScalaCode.Newline)

    def stream(lastEnd: Int = 0): LazyList[ScalaCode] = scanner.token match
      case Tokens.EOF =>
        import gossamer.slice
        untab(text.slice(lastEnd, text.length)).filter(_.length > 0)

      case token =>
        import gossamer.slice
        val start = scanner.offset max lastEnd

        val unparsed: LazyList[ScalaCode] =
          if lastEnd != start
          then
            text.slice(lastEnd, start)
             .cut(t"\n")
             .to(LazyList)
             .flatMap(untab(_).filter(_.length > 0))
             .init

          else LazyList()

        scanner.nextToken()
        val end = scanner.lastOffset max start

        val content: LazyList[ScalaCode] =
          if start == end then LazyList()
          else
            text.slice(start, end).cut(t"\n").to(LazyList).flatMap: line =>
              LazyList
               (ScalaCode(line, trees(start, end).getOrElse(accent(token))), ScalaCode.Newline)
            .init

        unparsed #::: content #::: stream(end)

    def lines(seq: List[ScalaCode], acc: List[List[ScalaCode]] = Nil): List[List[ScalaCode]] =
      seq match
        case Nil => acc
        case xs  => xs.indexOf(ScalaCode.Newline) match
          case -1  => xs :: acc
          case idx => lines(xs.drop(idx + 1), xs.take(idx) :: acc)

    ScalaSource(1, IArray(lines(stream().to(List)).reverse*))


  private class Trees() extends ast.untpd.UntypedTreeTraverser:
    import ast.*, untpd.*

    private val trees: scm.HashMap[(Int, Int), Accent] = scm.HashMap()

    def apply(start: Int, end: Int): Option[Accent] = trees.get((start, end))

    def ignored(tree: NameTree): Boolean =
      val name = tree.name.toTermName
      name == StdNames.nme.ERROR || name == StdNames.nme.CONSTRUCTOR

    def traverse(tree: Tree)(using Contexts.Context): Unit =
      tree match
        case tree: NameTree if ignored(tree) =>
          ()

        case tree: ValOrDefDef =>
          if tree.nameSpan.exists
          then trees += (tree.nameSpan.start, tree.nameSpan.end) -> Accent.Term

        case tree: MemberDef =>
          if tree.nameSpan.exists
          then trees += (tree.nameSpan.start, tree.nameSpan.end) -> Accent.Typed

        case tree: Ident if tree.isType =>
          if tree.span.exists then trees += (tree.span.start, tree.span.end) -> Accent.Typed

        case _: TypTree =>
          if tree.span.exists then trees += (tree.span.start, tree.span.end) -> Accent.Typed

        case other =>
          ()

      traverseChildren(tree)
