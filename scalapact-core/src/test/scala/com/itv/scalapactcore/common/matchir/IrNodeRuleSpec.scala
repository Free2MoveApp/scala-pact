package com.itv.scalapactcore.common.matchir

import com.itv.scalapactcore.MatchingRule
import org.scalatest.{FunSpec, Matchers}

import scala.language.postfixOps

class IrNodeRuleSpec extends FunSpec with Matchers {

  def check(res: IrNodeEqualityResult): Unit =
    res match {
      case p @ IrNodesEqual => p shouldEqual IrNodesEqual
      case e: IrNodesNotEqual => fail(e.renderDifferences)
    }

  describe("creating a rule set") {

    it("should be able to convert pact matching rules into IrNodeRules") {

      val pactRules: Option[Map[String, MatchingRule]] = Option {
        Map(
          ".fish" -> MatchingRule(Some("type"), None, None),
          ".fish.breed" -> MatchingRule(Some("regex"), Some("cod|haddock"), None),
          ".fish.fins" -> MatchingRule(Some("min"), None, Some(1))
        )
      }

      val expected: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeTypeRule(IrNodePathEmpty <~ "fish"),
          IrNodeRegexRule("cod|haddock", IrNodePathEmpty <~ "fish" <~ "breed"),
          IrNodeMinArrayLengthRule(1, IrNodePathEmpty <~ "fish" <~ "fins")
        )

      IrNodeMatchingRules.fromPactRules(pactRules) shouldEqual expected

    }

    it("should be able to convert more advanced pact matching rules into IrNodeRules") {

      val pactRules: Option[Map[String, MatchingRule]] = Option {
        Map(
          ".fish[*]" -> MatchingRule(Some("type"), None, None),
          ".fish[2]" -> MatchingRule(Some("type"), None, None),
          ".fish['#text']" -> MatchingRule(Some("type"), None, None),
          ".fish['@id']" -> MatchingRule(Some("type"), None, None)
        )
      }

      val expected: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "*"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ 2),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" text),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <@ "id")
        )

      IrNodeMatchingRules.fromPactRules(pactRules) shouldEqual expected

    }

  }

  describe("Validating a node using rules") {

    // Here for completeness, but no rule is needed to check this.
    it("should be able to compare node types") {

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": {}
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": []
          |}
        """.stripMargin
      ).get

      (expected =<>= actual).isEqual shouldEqual false

    }

    it("should be able to compare node primitive types") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeTypeRule(IrNodePathEmpty <~ "fish")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": "cod"
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": "haddock"
          |}
        """.stripMargin
      ).get

      check(expected =<>= actual)

    }

    // This is plainly nonsense
    it("should not validate a node using regex") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeRegexRule("\\[\\]", IrNodePathEmpty <~ "fish")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": {}
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": []
          |}
        """.stripMargin
      ).get

      (expected =<>= actual).isEqual shouldEqual false
    }

    it("should be able to validate a node primitive using regex") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeRegexRule("cod|haddock", IrNodePathEmpty <~ "fish")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": "cod"
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": "haddock"
          |}
        """.stripMargin
      ).get

      check(expected =<>= actual)

    }

    it("should be able to check an array node is of minimum length") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeMinArrayLengthRule(1, IrNodePathEmpty <~ "fish"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "*"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "*" <~ "id")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [{"id":1}, {"id":2}, {"id":3}]
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [{"id":27}]
          |}
        """.stripMargin
      ).get

      withClue("objects in an array") {
        check(expected =<>= actual)
      }

      val expected2: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [1,2,3]
          |}
        """.stripMargin
      ).get

      val actual2: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [27]
          |}
        """.stripMargin
      ).get

      withClue("integers in an array") {
        check(expected2 =<>= actual2)
      }
    }

    // Again, this is nonsense, just here for completeness.
    it("should not attempt to check the array length of a primitive") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeMinArrayLengthRule(1, IrNodePathEmpty <~ "fish")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": "cod"
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": 1
          |}
        """.stripMargin
      ).get

      (expected =<>= actual).isEqual shouldEqual false
    }
  }

  describe("Validating a node using rules in XML specific cases") {

    it("should be able to validate an xml element") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeRegexRule("cod|haddock", IrNodePathEmpty <~ "fish" <~ "breed")
        )

      val expected: IrNode = MatchIr.fromXml(
        <fish><breed>cod</breed></fish>.toString()
      ).get

      val actual: IrNode = MatchIr.fromXml(
        <fish><breed>cod</breed></fish>.toString()
      ).get

      check(expected =<>= actual)

    }

    it("should be able to validate an xml attribute using regex") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeRegexRule("abc123|def456", IrNodePathEmpty <~ "fish" <~ "breed" <@ "id")
        )

      val expected: IrNode = MatchIr.fromXml(
        <fish><breed id="abc123">cod</breed></fish>.toString()
      ).get

      val actual: IrNode = MatchIr.fromXml(
        <fish><breed id="def456">cod</breed></fish>.toString()
      ).get

      check(expected =<>= actual)

    }

    it("should be able to validate an xml text element using regex") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeRegexRule("cod|haddock", IrNodePathEmpty <~ "fish" <~ "breed" text)
        )

      val expected: IrNode = MatchIr.fromXml(
        <fish><breed>cod</breed></fish>.toString()
      ).get

      val actual: IrNode = MatchIr.fromXml(
        <fish><breed>haddock</breed></fish>.toString()
      ).get

      check(expected =<>= actual)

    }

  }

  describe("structural checks") {

    it("should be able to compare node types") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeTypeRule(IrNodePathEmpty <~ "fish"),
          IrNodeMinArrayLengthRule(2, IrNodePathEmpty <~ "fish"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "breed" <~ 0),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "breed" <~ 1)
        ).withProcessTracing("should be able to compare node types")

      val expected: IrNode = MatchIr.fromXml(
        <fish><breed id="abc123">cod</breed></fish>.toString()
      ).get

      val actual: IrNode = MatchIr.fromXml(
        <fish><breed id="def456">haddock</breed><breed id="ghi789">plaice</breed></fish>.toString()
      ).get

      check(expected =<>= actual)

    }

    it("should be able to check types by wildcard") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeMinArrayLengthRule(1, IrNodePathEmpty <~ "fish"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish" <~ "*" <*)
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [
          |    { "name": "a" }
          |  ]
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [
          |    { "name": 10 }
          |  ]
          |}
        """.stripMargin
      ).get

      (expected =<>= actual).isEqual shouldEqual false
    }

    it("should be able to check regex by wildcard") {

      implicit val rules: IrNodeMatchingRules =
        IrNodeMatchingRules(
          IrNodeMinArrayLengthRule(1, IrNodePathEmpty <~ "fish"),
          IrNodeTypeRule(IrNodePathEmpty <~ "fish"),
          IrNodeRegexRule("\\d+", IrNodePathEmpty <~ "fish" <~ "*" <~ "name")
        )

      val expected: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [
          |    { "name": "10" }
          |  ]
          |}
        """.stripMargin
      ).get

      val actual: IrNode = MatchIr.fromJSON(
        """
          |{
          |  "fish": [
          |    { "name": "10" },
          |    { "name": "abc" }
          |  ]
          |}
        """.stripMargin
      ).get

      val res = expected =<>= actual

      println(res.renderAsString)

      res.isEqual shouldEqual false
    }

  }

}
