package me.cference.dionysus.domain.batch

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class BatchMathSpec extends AnyFunSuite with Matchers:

  test("remainingPortions with no logged portions equals servingsMade") {
    BatchMath.remainingPortions(servingsMade = 4, loggedPortions = Seq.empty) shouldBe 4.0
  }

  test("remainingPortions decreases by one logged meal's portions") {
    BatchMath.remainingPortions(servingsMade = 4, loggedPortions = Seq(1.0)) shouldBe 3.0
  }

  test("remainingPortions reflects multiple meals across multiple days (the leftovers scenario)") {
    // servingsMade=4: one meal logs 1 portion on day 1, another logs 2 portions on day 3.
    // Remaining is timeless — 1 — regardless of which day is "today".
    BatchMath.remainingPortions(servingsMade = 4, loggedPortions = Seq(1.0, 2.0)) shouldBe 1.0
  }

  test("remainingPortions can go to exactly zero") {
    BatchMath.remainingPortions(servingsMade = 4, loggedPortions = Seq(4.0)) shouldBe 0.0
  }
