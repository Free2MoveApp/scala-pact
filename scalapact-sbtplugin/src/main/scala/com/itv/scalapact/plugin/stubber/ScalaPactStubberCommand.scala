package com.itv.scalapact.plugin.stubber

import com.itv.scalapact.plugin.ScalaPactPlugin
import sbt._
import com.itv.scalapact.shared.http.PactStubService._
import com.itv.scalapactcore.stubber.InteractionManager
import com.itv.scalapact.shared.ColourOuput._
import com.itv.scalapact.shared.ScalaPactSettings
import com.itv.scalapactcore.common.LocalPactFileLoader._
import com.itv.scalapactcore.common.PactReaderWriter._

object ScalaPactStubberCommand {

  lazy val pactStubberCommandHyphen: Command = Command.args("pact-stubber", "<options>")(pactStubber)
  lazy val pactStubberCommandCamel: Command = Command.args("pactStubber", "<options>")(pactStubber)

  private lazy val pactStubber: (State, Seq[String]) => State = (state, args) => {

    println("*************************************".white.bold)
    println("** ScalaPact: Running Stubber      **".white.bold)
    println("*************************************".white.bold)

//    val pactTestedState = Command.process("pact-test", state)
    val testedState = state.copy(remainingCommands = Exec("pact-test", None) +: state.remainingCommands)

    runStubber(
      Project.extract(testedState).get(ScalaPactPlugin.autoImport.scalaPactEnv).toSettings + ScalaPactSettings.parseArguments(args),
      interactionManagerInstance
    )

    testedState
  }

  def interactionManagerInstance: InteractionManager = new InteractionManager

  def runStubber(scalaPactSettings: ScalaPactSettings, interactionManager: InteractionManager): Unit = {
    (loadPactFiles(pactReader)(scalaPactSettings.giveOutputPath) andThen interactionManager.addToInteractionManager andThen startServer(interactionManager)(pactReader, pactWriter))(scalaPactSettings)
  }

}