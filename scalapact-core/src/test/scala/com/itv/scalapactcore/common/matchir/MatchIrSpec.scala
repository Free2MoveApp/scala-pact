package com.itv.scalapactcore.common.matchir

import org.scalatest.{FunSpec, Matchers}

class MatchIrSpec extends FunSpec with Matchers {

  def check(res: IrNodeEqualityResult): Unit =
    res match {
      case p @ IrNodesEqual => p shouldEqual IrNodesEqual
      case e: IrNodesNotEqual => fail(e.renderDifferences)
    }

  describe("Converting XML to MatchIR") {

    it("should be able to convert one node") {

      val xml: String = <fish></fish>.toString()

      val ir: IrNode =
        IrNode("fish").withPath(IrNodePathEmpty <~ "fish").markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

    it("should be able to detect an array") {

      val xml: String = <fish><breed>cod</breed><breed>haddock</breed></fish>.toString()

      val ir: IrNode =
        IrNode(
          "fish",
          List(
            IrNode("breed", IrStringNode("cod")).withPath(IrNodePathEmpty <~ "fish" <~ "breed" <~ 0).markAsXml,
            IrNode("breed", IrStringNode("haddock")).withPath(IrNodePathEmpty <~ "fish" <~ "breed" <~ 1).markAsXml
          )
        ).withPath(IrNodePathEmpty <~ "fish").markAsArray.markAsXml

      val expected = MatchIr.fromXml(xml).get

      expected.isArray shouldEqual true

      check(expected =<>= ir)

    }

    it("should be able to NOT detect an array") {

      val xml: String = <fish><breed>cod</breed><breed>haddock</breed><chips/></fish>.toString()

      val ir: IrNode =
        IrNode(
          "fish",
          List(
            IrNode("breed", IrStringNode("cod")).withPath(IrNodePathEmpty <~ "fish" <~ "breed").markAsXml,
            IrNode("breed", IrStringNode("haddock")).withPath(IrNodePathEmpty <~ "fish" <~ "breed").markAsXml,
            IrNode("chips").withPath(IrNodePathEmpty <~ "fish" <~ "chips").markAsXml
          )
        ).withPath(IrNodePathEmpty <~ "fish").markAsXml

      val expected = MatchIr.fromXml(xml).get

      expected.isArray shouldEqual false

      check(expected =<>= ir)

    }

    it("should be able to convert xml with a doctype node") {

      val xml: String = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><animals><alligator name=\"Mary\"/></animals>"

      val ir: IrNode =
        IrNode("animals",
          IrNode("alligator")
            .withAttributes(
              IrNodeAttributes(Map("name" -> IrNodeAttribute(IrStringNode("Mary"), IrNodePathEmpty <~ "animals" <~ "alligator" <@ "name")))
            )
            .withPath(IrNodePathEmpty <~ "animals" <~ "alligator").markAsXml
        ).withPath(IrNodePathEmpty <~ "animals").markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

    it("should be able to convert one node with content") {

      val xml: String = <fish>haddock</fish>.toString()

      val ir: IrNode =
        IrNode("fish", IrStringNode("haddock")).withPath(IrNodePathEmpty <~ "fish").markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

    it("should be able to convert one node with a namespace") {

      val xml: String = <ns1:fish>haddock</ns1:fish>.toString()

      val ir: IrNode =
        IrNode("fish", IrStringNode("haddock"))
          .withNamespace("ns1")
          .withPath(IrNodePathEmpty <~ "fish")
          .markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

    it("should be able to convert one node with attributes") {

      val xml: String = <fish id="3" description="A fish" endangered="false"></fish>.toString()

      val ir: IrNode =
        IrNode("fish")
          .withAttributes(
            IrNodeAttributes(
              Map(
                "id" -> IrNodeAttribute(IrNumberNode(3), IrNodePathEmpty <~ "fish" <@ "id"),
                "description" -> IrNodeAttribute(IrStringNode("A fish"), IrNodePathEmpty <~ "fish" <@ "description"),
                "endangered" -> IrNodeAttribute(IrBooleanNode(false), IrNodePathEmpty <~ "fish" <@ "endangered")
              )
            )
          )
          .withPath(IrNodePathEmpty <~ "fish")
          .markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

    it("should be able to convert two nested nodes and a value") {

      val xml: String = <fish><breed>cod</breed></fish>.toString()

      val ir: IrNode =
        IrNode("fish", IrNode("breed", IrStringNode("cod")).withPath(IrNodePathEmpty <~ "fish" <~ "breed").markAsXml).withPath(IrNodePathEmpty <~ "fish").markAsXml

      check(MatchIr.fromXml(xml).get =<>= ir)

    }

//    it("should be able to convert two nested nodes and two values") {
//      pending //TODO: Not sure if this is a thing we care about? It's technically valid...
//
//      val xml: String = <fish><breed>cod</breed>bait</fish>.toString()
//
//      val ir: IrNode =
//        IrNode(
//          label = "fish",
//          value = Option(IrStringNode("bait")),
//          children = List(IrNode("breed", IrStringNode("cod"))),
//          ns = None,
//          attributes = Map.empty[String, IrStringNode],
//          path = IrNodePathEmpty
//        )
//
//      MatchIr.fromXml(xml).get =~ ir match {
//        case r @ IrNodesEqual =>
//          r shouldEqual IrNodesEqual
//
//        case r: IrNodesNotEqual =>
//          fail(r.renderDifferences)
//      }
//
//    }

  }

  describe("Converting JSON to MatchIR") {

    it("should be able to convert one node") {
      val json: String =
        """
          |{
          |  "fish": {}
          |}
        """.stripMargin

      val ir: IrNode =
        IrNode(MatchIr.rootNodeLabel, IrNode("fish").withPath(IrNodePathEmpty <~ "fish"))

      check(MatchIr.fromJSON(json).get =<>= ir)
    }

    it("should be able to convert two nested nodes and a value") {

      val json: String =
        """
          |{
          |  "fish": {
          |    "breed": "cod"
          |  }
          |}
        """.stripMargin

      val ir: IrNode =
        IrNode(
          MatchIr.rootNodeLabel,
          IrNode(
            "fish",
            IrNode(
              "breed",
              IrStringNode("cod")
            ).withPath(IrNodePathEmpty <~ "fish" <~ "breed")
          ).withPath(IrNodePathEmpty <~ "fish")
        ).withPath(IrNodePathEmpty)

      check(MatchIr.fromJSON(json).get =<>= ir)

    }

    it("should convert an object with a primitive array") {

      val json: String =
        """
          |{"myDates":[20,5,70]}
        """.stripMargin

      val ir: IrNode =
        IrNode(
          MatchIr.rootNodeLabel,
          IrNode("myDates",
            IrNode("myDates", IrNumberNode(20)).withPath(IrNodePathEmpty <~ "myDates" <~ 0),
            IrNode("myDates", IrNumberNode(5)).withPath(IrNodePathEmpty <~ "myDates" <~ 1),
            IrNode("myDates", IrNumberNode(70)).withPath(IrNodePathEmpty <~ "myDates" <~ 2)
          ).withPath(IrNodePathEmpty <~ "myDates").markAsArray
        ).withPath(IrNodePathEmpty)

      check(MatchIr.fromJSON(json).get =<>= ir)

    }

    it("should convert a simple top level array") {

      val json: String =
        """
          |[1,2,3]
        """.stripMargin

      val ir: IrNode =
        IrNode(
          MatchIr.rootNodeLabel,
          IrNode(MatchIr.unnamedNodeLabel, IrNumberNode(1)).withPath(IrNodePathEmpty <~ 0),
          IrNode(MatchIr.unnamedNodeLabel, IrNumberNode(2)).withPath(IrNodePathEmpty <~ 1),
          IrNode(MatchIr.unnamedNodeLabel, IrNumberNode(3)).withPath(IrNodePathEmpty <~ 2)
        ).withPath(IrNodePathEmpty).markAsArray

      check(MatchIr.fromJSON(json).get =<>= ir)

    }

    it("should be able to convert a top level array with two nodes") {

      val json: String =
        """
          |[
          |  {
          |    "fish": {
          |      "breed": "cod"
          |    }
          |  },
          |  {
          |    "fish": {
          |      "breed": "haddock"
          |    }
          |  }
          |]
        """.stripMargin

      val ir: IrNode =
        IrNode(MatchIr.rootNodeLabel,
          IrNode(
            MatchIr.unnamedNodeLabel,
            IrNode(
              "fish",
              IrNode("breed", IrStringNode("cod")).withPath(IrNodePathEmpty <~ 0 <~ "fish" <~ "breed")
            ).withPath(IrNodePathEmpty <~ 0 <~ "fish")
          ).withPath(IrNodePathEmpty <~ 0),
          IrNode(
            MatchIr.unnamedNodeLabel,
            IrNode(
              "fish",
              IrNode("breed", IrStringNode("haddock")).withPath(IrNodePathEmpty <~ 1 <~ "fish" <~ "breed")
            ).withPath(IrNodePathEmpty <~ 1 <~ "fish")
          ).withPath(IrNodePathEmpty <~ 1)
        ).withPath(IrNodePathEmpty).markAsArray

      check(MatchIr.fromJSON(json).get =<>= ir)

    }

    it("should be able to convert a nested json structure with a nested array") {

      val json: String =
        """
          |{
          |  "river": [
          |    {
          |      "fish": {
          |        "breed": "cod"
          |      }
          |    },
          |    {
          |      "fish": {
          |        "breed": "haddock"
          |      }
          |    }
          |  ],
          |  "riverbank": {
          |    "grassy": true,
          |    "flowers": [ "poppies", "daisies", "dandelions" ],
          |    "error": "here!"
          |  }
          |}
        """.stripMargin

      val expected: IrNode =

        IrNode(
          MatchIr.rootNodeLabel,
          IrNode(
            "river",
            IrNode(
              "river",
              IrNode(
                "fish",
                IrNode("breed", IrStringNode("cod")).withPath(IrNodePathEmpty <~ "river" <~ 0 <~ "fish" <~ "breed")
              ).withPath(IrNodePathEmpty <~ "river" <~ 0 <~ "fish")
            ).withPath(IrNodePathEmpty <~ "river" <~ 0),
            IrNode(
              "river",
              IrNode(
                "fish",
                IrNode("breed", IrStringNode("haddock")).withPath(IrNodePathEmpty <~ "river" <~ 1 <~ "fish" <~ "breed")
              ).withPath(IrNodePathEmpty <~ "river" <~ 1 <~ "fish")
            ).withPath(IrNodePathEmpty <~ "river" <~ 1)
          ).withPath(IrNodePathEmpty <~ "river").markAsArray,
          IrNode(
            "riverbank",
            IrNode(
              "grassy",
              IrBooleanNode(true)
            ).withPath(IrNodePathEmpty <~ "riverbank" <~ "grassy"),
            IrNode(
              "flowers",
              IrNode(
                "flowers",
                IrStringNode("poppies")
              ).withPath(IrNodePathEmpty <~ "riverbank" <~ "flowers" <~ 0),
              IrNode(
                "flowers",
                IrStringNode("daisies")
              ).withPath(IrNodePathEmpty <~ "riverbank" <~ "flowers" <~ 1),
              IrNode(
                "flowers",
                IrStringNode("dandelions")
              ).withPath(IrNodePathEmpty <~ "riverbank" <~ "flowers" <~ 2)
            ).withPath(IrNodePathEmpty <~ "riverbank" <~ "flowers").markAsArray
          ).withPath(IrNodePathEmpty <~ "riverbank")
        ).withPath(IrNodePathEmpty)

//      check(MatchIr.fromJSON(json).get =<>= expected)
      (expected =<>= MatchIr.fromJSON(json).get).isEqual shouldEqual false

    }

  }

}
