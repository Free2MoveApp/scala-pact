package com.itv.scalapactcore.common.matchir

import com.itv.scalapactcore.MatchingRule
import com.itv.scalapactcore.common.matchir.PactPathParseResult.{PactPathParseFailure, PactPathParseSuccess}

import scala.util.Random

case class RuleProcessTracing(enabled: Boolean, id: String, context: Option[String]) {
  def withContext(ctx: String): RuleProcessTracing = this.copy(context = Option(ctx))
}

object RuleProcessTracing {
  val disabled: RuleProcessTracing = RuleProcessTracing(enabled = false, id = "", context = None)
  def enabled: RuleProcessTracing = RuleProcessTracing(enabled = true, id = Random.alphanumeric.take(5).mkString, context = None)

  def log(message: String)(implicit ruleProcessTracing: RuleProcessTracing): Unit =
    if(ruleProcessTracing.enabled)
      println(s"""  [${ruleProcessTracing.id}] ${ruleProcessTracing.context.map(ctx => s"[$ctx] ").getOrElse("")}$message""")
    else
      ()
}

case class IrNodeMatchingRules(rules: List[IrNodeRule], withTracing: RuleProcessTracing) {

  implicit private val ruleProcessTracing: RuleProcessTracing = withTracing

  def +(other: IrNodeMatchingRules): IrNodeMatchingRules =
    IrNodeMatchingRules(rules ++ other.rules, withTracing)

  def withProcessTracing: IrNodeMatchingRules = this.copy(withTracing = RuleProcessTracing.enabled)
  def withProcessTracing(context: String): IrNodeMatchingRules = this.copy(withTracing = RuleProcessTracing.enabled.withContext(context))

  def findForPath(path: IrNodePath, isXml: Boolean): List[IrNodeRule] = {
    val res =
      if(isXml) {
        rules.filter(_.path.noText === path.noText) match {
          case Nil =>
            val unmodifiedPathRules = path.withIndexes.flatMap { indexedPath =>
              rules.filter(_.path.noText === indexedPath.noText)
            }

            if(path.isArrayWildcard || path.isArrayIndex)  {
              val minusArray = path.parent.withIndexes.flatMap { indexedPath =>
                rules.filter(_.path.noText === indexedPath.noText)
              }

              unmodifiedPathRules ++ minusArray
            }
            else {
              unmodifiedPathRules
            }

          case l: List[IrNodeRule] =>
            l
        }
      } else {
        rules.filter(_.path.noText === path.noText)
      }

    RuleProcessTracing.log(s"findForPath [${path.renderAsString}]:" + res.map(_.renderAsString).mkString(", "))

    res
  }

  def validateNode(path: IrNodePath, expected: IrNode, actual: IrNode): List[IrNodeEqualityResult] = {
    RuleProcessTracing.log("validateNode at: " + path.renderAsString)

    findForPath(path, expected.isXml).flatMap {
      case r @ IrNodeTypeRule(_) =>
        RuleProcessTracing.log("Checking node level type rule against values...")

        val res = (expected.value, actual.value) match {
          case (Some(e), Some(a)) if expected.path.lastSegmentLabel == actual.path.lastSegmentLabel =>
            if(e.primitiveTypeName == a.primitiveTypeName) List(IrNodesEqual)
            else List(IrNodesNotEqual(s"Primitive type '${e.primitiveTypeName}' did not match actual '${a.primitiveTypeName}'", path))

          case (Some(_), Some(_)) =>
            List(IrNodesNotEqual(s"Miss aligned values (by path), could not check rule: " + r.renderAsString, path))

          case (Some(_), None) =>
            List(IrNodesNotEqual(s"Missing actual value, could not check rule: " + r.renderAsString, path))

          case (_, Some(_)) =>
            List(IrNodesNotEqual(s"Missing expected value, could not check rule: " + r.renderAsString, path))

          case (_, _) =>
            RuleProcessTracing.log(" ...no values")
            List(IrNodesEqual)
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").mkString(", ")}")

        res

      case r @ IrNodeRegexRule(_, _) =>
        RuleProcessTracing.log(s"Checking regex on '${actual.value.map(_.renderAsString).getOrElse("<missing>")}'...")

        val res = (expected.value, actual.value) match {
          case (Some(_), Some(a)) =>
            if (a.renderAsString.matches(r.regex)) List(IrNodesEqual)
            else List(IrNodesNotEqual(s"Regex '${r.regex}' did not match actual '${a.renderAsString}'", path))

          case (Some(_), None) =>
            List(IrNodesNotEqual(s"Missing actual value, could not check rule: " + r.renderAsString, path))

          case (_, Some(_)) =>
            List(IrNodesNotEqual(s"Missing expected value, could not check rule: " + r.renderAsString, path))

          case (_, _) =>
            Nil
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").mkString(", ")}")

        res

      case IrNodeMinArrayLengthRule(len, _) =>
        RuleProcessTracing.log("Checking min...")

        val res = List {
          if (actual.children.length >= len) IrNodesEqual
          else IrNodesNotEqual(s"Array '${expected.label}' did not meet minimum length requirement of '$len'", path)
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").mkString(", ")}")

        res
    }
  }

  def findAncestralTypeRule(path: IrNodePath, isXml: Boolean): IrNodeMatchingRules = {
    RuleProcessTracing.log("Finding ancestral type rule")

    val res: List[IrNodeRule] = (path, findForPath(path.parent, isXml).find(p => p.isTypeRule).toList) match {
      case (IrNodePathEmpty, l) =>
        l

      case (p, Nil) =>
        findAncestralTypeRule(p.parent, isXml).rules

      case (_, l) =>
        l
    }

    RuleProcessTracing.log(s"findAncestralTypeRule [${path.renderAsString}]: " + res.map(_.renderAsString).mkString(", "))

    IrNodeMatchingRules(res, withTracing)
  }

  def validatePrimitive(path: IrNodePath, expected: IrNodePrimitive, actual: IrNodePrimitive, checkParentTypeRule: Boolean, isXml: Boolean): List[IrNodeEqualityResult] = {
    RuleProcessTracing.log(s"validatePrimitive (checkParentTypeRule=$checkParentTypeRule) at: " + path.renderAsString)
    val parentTypeRules = if(checkParentTypeRule) findAncestralTypeRule(path, isXml).rules else Nil

    (parentTypeRules ++ findForPath(path, isXml)).map {
      case IrNodeTypeRule(_) =>
        RuleProcessTracing.log(s"Checking type... (${expected.primitiveTypeName} vs ${actual.primitiveTypeName})")

        val res = Option {
          if (expected.primitiveTypeName == actual.primitiveTypeName) IrNodesEqual
          else IrNodesNotEqual(s"Primitive type '${expected.primitiveTypeName}' did not match actual '${actual.primitiveTypeName}'", path)
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").getOrElse("n/a")}")

        res

      case IrNodeRegexRule(regex, _) if expected.isString && actual.isString =>
        RuleProcessTracing.log(s"Checking regex on String '${actual.asString.getOrElse("")}'...")

        val res = actual.asString.map { str =>
          if (regex.r.findAllIn(str).nonEmpty) IrNodesEqual
          else IrNodesNotEqual(s"String '$str' did not match pattern '$regex'", path)
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").getOrElse("n/a")}")

        res

      case IrNodeRegexRule(regex, p) =>
        RuleProcessTracing.log(s"Checking regex on non-String '${actual.renderAsString}'...")

        val res = Option {
          val str = actual.renderAsString

          if (regex.r.findAllIn(str).nonEmpty) IrNodesEqual
          else IrNodesNotEqual(s"Non-String value '$str' was checked but did not match pattern '$regex'", path)
        }

        RuleProcessTracing.log(s"  ...${res.map(p => if(p.isEqual) "success" else "failure").getOrElse("n/a")}")

        res

      case IrNodeMinArrayLengthRule(_, _) =>
        RuleProcessTracing.log("Checking min... (does nothing on primitives)")
        RuleProcessTracing.log(s"  ...n/a")
        None

      case _ =>
        RuleProcessTracing.log("Checking failed, unexpected condition met.")
        None
    }.collect { case Some(s) => s }
  }

  def findMinArrayLengthRule(path: IrNodePath, isXml: Boolean): IrNodeMatchingRules = {
    RuleProcessTracing.log("Finding min array length rule")

    val res = findForPath(path, isXml).find(p => p.isMinArrayLengthRule) match {
      case Some(r) =>
        List(r)

      case None =>
        Nil
    }

    RuleProcessTracing.log(s"findMinArrayLengthRule [${path.renderAsString}]: " + res.map(_.renderAsString).mkString(", "))

    IrNodeMatchingRules(res, withTracing)
  }

  def renderAsString: String = s"Rules:\n - ${rules.map(r => r.renderAsString).mkString("\n - ")}"

}

object IrNodeMatchingRules {

  implicit val defaultEmptyRules: IrNodeMatchingRules = IrNodeMatchingRules.empty

  def empty: IrNodeMatchingRules = IrNodeMatchingRules(Nil, RuleProcessTracing.disabled)

  def apply(rule: IrNodeRule): IrNodeMatchingRules = IrNodeMatchingRules(List(rule), RuleProcessTracing.disabled)

  def apply(rules: IrNodeRule*): IrNodeMatchingRules = IrNodeMatchingRules(rules.toList, RuleProcessTracing.disabled)

  //TODO: Fails inline and carries on... not sure how I feel about that.
  def fromPactRules(rules: Option[Map[String, MatchingRule]]): IrNodeMatchingRules = {
    val l = rules match {
      case None =>
        List(empty)

      case Some(ruleMap) =>
        ruleMap.toList.map { pair =>
          (IrNodePath.fromPactPath(pair._1), pair._2) match {
            case (e: PactPathParseFailure, _) =>
              println(e.errorString)
              empty

            case (PactPathParseSuccess(path), MatchingRule(Some("type"), None, None)) =>
              IrNodeMatchingRules(IrNodeTypeRule(path))

            case (PactPathParseSuccess(path), MatchingRule(Some("type"), None, Some(len))) =>
              IrNodeMatchingRules(IrNodeTypeRule(path)) + IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(Some("type"), Some(regex), Some(len))) =>
              IrNodeMatchingRules(IrNodeTypeRule(path)) + IrNodeMatchingRules(IrNodeRegexRule(regex, path)) + IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(Some("regex"), Some(regex), None)) =>
              IrNodeMatchingRules(IrNodeRegexRule(regex, path))

            case (PactPathParseSuccess(path), MatchingRule(Some("regex"), Some(regex), Some(len))) =>
              IrNodeMatchingRules(IrNodeRegexRule(regex, path)) + IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(None, Some(regex), None)) =>
              IrNodeMatchingRules(IrNodeRegexRule(regex, path))

            case (PactPathParseSuccess(path), MatchingRule(None, Some(regex), Some(len))) =>
              IrNodeMatchingRules(IrNodeRegexRule(regex, path)) + IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(Some("min"), None, Some(len))) =>
              IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(Some("min"), Some(regex), Some(len))) =>
              IrNodeMatchingRules(IrNodeRegexRule(regex, path)) + IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (PactPathParseSuccess(path), MatchingRule(None, None, Some(len))) =>
              IrNodeMatchingRules(IrNodeMinArrayLengthRule(len, path))

            case (p, r) =>
              println("Failed to read rule: " + r.renderAsString + s" for path '$p'")
              empty
          }
        }
    }

    l.foldLeft(empty)(_ + _)
  }

}

sealed trait IrNodeRule {
  val path: IrNodePath

  def isTypeRule: Boolean
  def isRegexRule: Boolean
  def isMinArrayLengthRule: Boolean

  def renderAsString: String =
    this match {
      case IrNodeTypeRule(p) =>
        s"""Type rule [${p.renderAsString}]"""

      case IrNodeRegexRule(r, p) =>
        s"""Regex rule [$r] [${p.renderAsString}]"""

      case IrNodeMinArrayLengthRule(l, p) =>
        s"""Min array length rule [$l] [${p.renderAsString}]"""
    }

}
case class IrNodeTypeRule(path: IrNodePath) extends IrNodeRule {
  def isTypeRule: Boolean = true
  def isRegexRule: Boolean = false
  def isMinArrayLengthRule: Boolean = false
}
case class IrNodeRegexRule(regex: String, path: IrNodePath) extends IrNodeRule {
  def isTypeRule: Boolean = false
  def isRegexRule: Boolean = true
  def isMinArrayLengthRule: Boolean = false
}
case class IrNodeMinArrayLengthRule(length: Int, path: IrNodePath) extends IrNodeRule {
  def isTypeRule: Boolean = false
  def isRegexRule: Boolean = false
  def isMinArrayLengthRule: Boolean = true
}
