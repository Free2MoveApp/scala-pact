package com.itv.scalapact.shared

import scala.language.implicitConversions

object RightBiasEither {
  implicit def makeBetterEither[AA, BB](e: Either[AA, BB]): RightBiasEither[AA, BB] = new RightBiasEither(e)

  class RightBiasEither[AA, BB](e: Either[AA, BB]) {

    def bind[CC](f: BB => Either[AA, CC]): Either[AA, CC] = {
      e match {
        case Right(bb) => f(bb)
        case Left(aa) => Left(aa)
      }
    }

    def map[CC](f: BB => CC): Either[AA, CC] = {
      e match {
        case Right(bb) => Right(f(bb))
        case Left(aa) => Left (aa)
      }
    }

    def leftMap[CC](f: AA => CC): Either[CC, BB] = {
      e match {
        case Right(bb) => Right(bb)
        case Left(aa) => Left(f(aa))
      }
    }
    
  }
}