package ai

import game.Game
import other.context.Context
import other.AI
import other.move.Move
import utils.AIUtils

import scala.util.Random

class RandomAI extends AI {
    var player = -1
    friendlyName = "Scala AI"

    override def selectAction(
        game: Game,
        context: Context,
        maxSeconds: Double,
        maxIterations: Int,
        maxDepth: Int
    ): Move = {
        var legalMoves = game.moves(context).moves()

        if (!game.isAlternatingMoveGame()) {
            legalMoves = AIUtils.extractMovesForMover(legalMoves, player)
        }

        val moveId = Random.nextInt(legalMoves.size())
        legalMoves.get(moveId)
    }

    override def initAI(game: Game, playerID: Int): Unit = {
        player = playerID
    }

    override def generateAnalysisReport(): String = {
        "Scala AI workin :P"
    }
}
