/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.parser.v2_0.ast

import org.neo4j.cypher.internal.parser.v2_0._
import org.neo4j.cypher.internal.{commands, mutation}
import org.neo4j.cypher.internal.parser.{AbstractPattern, Action, On, OnAction}
import org.neo4j.cypher.internal.commands.{CreateUniqueAst, MergeAst}
import org.neo4j.cypher.internal.mutation.{UpdateAction, ForeachAction}
import org.neo4j.cypher.internal.symbols._

sealed trait Clause extends AstNode with SemanticCheckable

case class Start(items: Seq[StartItem], token: InputToken) extends Clause {
  def semanticCheck = items.semanticCheck
}

case class Match(patterns: Seq[Pattern], token: InputToken) extends Clause {
  def semanticCheck = patterns.semanticCheck(Pattern.SemanticContext.Match)
}

case class Where(expression: Expression, token: InputToken) extends Clause {
  def semanticCheck =
    expression.semanticCheck(Expression.SemanticContext.Simple) then
    expression.constrainType(AnyType()) // TODO: should constrain to boolean, when coercion is possible
}


trait UpdateClause extends Clause {
  def toLegacyUpdateActions : Seq[mutation.UpdateAction]
}

case class Create(patterns: Seq[Pattern], token: InputToken) extends UpdateClause {
  def semanticCheck = {
    patterns.semanticCheck(Pattern.SemanticContext.Update)
  }

  def toLegacyStartItems: Seq[commands.UpdatingStartItem] = toLegacyUpdateActions.map {
    case createNode: mutation.CreateNode                 => commands.CreateNodeStartItem(createNode)
    case createRelationship: mutation.CreateRelationship => commands.CreateRelationshipStartItem(createRelationship)
  }

  def toLegacyUpdateActions = patterns.flatMap(_.toLegacyCreates)

  def toLegacyNamedPaths: Seq[commands.NamedPath] = patterns.flatMap {
    case n: NamedPattern => n.toLegacyNamedPath
    case _               => None
  }
}

case class CreateUnique(patterns: Seq[Pattern], token: InputToken) extends UpdateClause {
  def semanticCheck = patterns.semanticCheck(Pattern.SemanticContext.Update)

  private def toCommand = {
    val abstractPatterns: Seq[AbstractPattern] = patterns.flatMap(_.toAbstractPatterns).map(_.makeOutgoing)
    CreateUniqueAst(abstractPatterns)
  }

  def toLegacyStartItems: Seq[commands.StartItem] = toCommand.nextStep()._1

  def toLegacyUpdateActions:Seq[mutation.UpdateAction] = toCommand.nextStep()._1.map(_.inner)

  def toLegacyNamedPaths: Seq[commands.NamedPath] = toCommand.nextStep()._2
}


case class Delete(expressions: Seq[Expression], token: InputToken) extends UpdateClause {
  def semanticCheck =
    expressions.semanticCheck(Expression.SemanticContext.Simple) then
    warnAboutDeletingLabels then
    expressions.constrainType(NodeType(), RelationshipType(), PathType())

  def warnAboutDeletingLabels =
    expressions.filter(_.isInstanceOf[HasLabels]) map {
      e => SemanticError("DELETE doesn't support removing labels from a node. Try REMOVE.", e.token)
    }

  def toLegacyUpdateActions = expressions.map(e => mutation.DeleteEntityAction(e.toCommand))
}


case class SetClause(items: Seq[SetItem], token: InputToken) extends UpdateClause {
  def semanticCheck = items.semanticCheck

  def toLegacyUpdateActions = items.map(_.toLegacyUpdateAction)
}


case class Remove(items: Seq[RemoveItem], token: InputToken) extends UpdateClause {
  def semanticCheck = items.semanticCheck

  def toLegacyUpdateActions = items.map(_.toLegacyUpdateAction)
}

case class Merge(patterns: Seq[Pattern], actions: Seq[MergeAction], token: InputToken) extends UpdateClause {
  def semanticCheck = patterns.semanticCheck(Pattern.SemanticContext.Update)

  def toCommand: MergeAst = MergeAst(patterns.flatMap(_.toAbstractPatterns), actions.map(_.toAction))

  def toLegacyUpdateActions: Seq[UpdateAction] = toCommand.nextStep()
}

abstract class MergeAction(identifier: Identifier, action: SetClause, token: InputToken) extends AstNode {
  def children = Seq(identifier, action)
  def verb: Action
  def toAction = OnAction(verb, identifier.name, action.toLegacyUpdateActions)
}

case class OnCreate(identifier: Identifier, action: SetClause, token: InputToken)
  extends MergeAction(identifier, action, token) {
  def verb: Action = On.Create
}

case class OnMatch(identifier: Identifier, action: SetClause, token: InputToken)
  extends MergeAction(identifier, action, token) {
  def verb: Action = On.Match
}


case class Foreach(identifier: Identifier, expression: Expression, updates: Seq[UpdateClause], token: InputToken) extends UpdateClause with SemanticChecking {
  def semanticCheck =
    expression.semanticCheck(Expression.SemanticContext.Simple) then
    expression.constrainType(CollectionType(AnyType())) then withScopedState {
      val innerTypes : TypeGenerator = expression.types(_).map(_.iteratedType)
      identifier.declare(innerTypes) then updates.semanticCheck
    }

  def toLegacyUpdateActions = Seq(ForeachAction(expression.toCommand, identifier.name, updates.flatMap { _.toLegacyUpdateActions }))
}
