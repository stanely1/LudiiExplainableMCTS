package ai

import game.Game
import other.AI
import other.context.Context
import other.move.Move
import utils.AIUtils

import kotlin.random.Random

class RandomAI : AI() {
    private var player = -1

    init {
        friendlyName = "Kotlin AI"
    }

    override fun selectAction(
        game: Game,
        context: Context,
        maxSeconds: Double,
        maxIterations: Int,
        maxDepth: Int
    ): Move {
        var legalMoves = game.moves(context).moves()

        if (!game.isAlternatingMoveGame()) {
            legalMoves = AIUtils.extractMovesForMover(legalMoves, player)
        }

        val moveId = Random.nextInt(legalMoves.size())
        return legalMoves[moveId]
    }

    override fun initAI(game: Game, playerID: Int) {
        player = playerID
    }

    override fun generateAnalysisReport(): String {
        return "Kotlin AI workin :P"
    }
}
