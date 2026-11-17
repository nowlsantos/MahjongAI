package com.nowlsantos.mahjongai.engine.mcts

import com.nowlsantos.mahjongai.model.MahjongAction
import com.nowlsantos.mahjongai.model.MahjongGameState

class MctsEngine(private val maxIterations: Int = 1000) {

    fun findBestAction(rootState: MahjongGameState): MahjongAction? {
        val rootNode = MctsNode(rootState, null, null)

        repeat(maxIterations) {
            var node = select(rootNode)

            if (!node.isTerminal && !node.isFullyExpanded) {
                node = expand(node)
            }

            val simulationResult = simulate(node.state)
            backpropagate(node, simulationResult)
        }

        // Select the child with the highest visit count (robust child)
        return rootNode.children.maxByOrNull { it.visitCount }?.actionFromParent
    }

    private fun select(node: MctsNode): MctsNode {
        var current = node
        while (current.isFullyExpanded && current.children.isNotEmpty()) {
            current = current.children.maxBy { it.uctValue(node.visitCount) }
        }
        return current
    }

    private fun expand(node: MctsNode): MctsNode {
        val action = node.untriedActions.randomOrNull() ?: return node
        val nextState = node.state.applyAction(action)
        return node.addChild(action, nextState)
    }

    private fun simulate(state: MahjongGameState): Double {
        var currentState = state
        // Random rollout simulation until terminal state or depth limit
        var steps = 0
        while (!currentState.isTerminal && steps < 10) {
            val legalActions = currentState.getLegalActions()
            if (legalActions.isEmpty()) break
            val randomAction = legalActions.random()
            currentState = currentState.applyAction(randomAction)
            steps++
        }
        return currentState.scoreEvaluation
    }

    private fun backpropagate(node: MctsNode, score: Double) {
        var currentNode: MctsNode? = node
        while (currentNode != null) {
            currentNode.update(score)
            currentNode = currentNode.parent
        }
    }
}