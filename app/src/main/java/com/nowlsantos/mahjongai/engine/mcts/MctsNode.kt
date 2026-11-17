package com.nowlsantos.mahjongai.engine.mcts

import com.nowlsantos.mahjongai.model.MahjongAction
import com.nowlsantos.mahjongai.model.MahjongGameState
import kotlin.math.ln
import kotlin.math.sqrt

class MctsNode(
    val state: MahjongGameState,
    val actionFromParent: MahjongAction?,
    val parent: MctsNode?
) {
    val children: MutableList<MctsNode> = mutableListOf()
    var visitCount: Int = 0
        private set
    var totalScore: Double = 0.0
        private set

    val untriedActions: MutableList<MahjongAction> = state.getLegalActions().toMutableList()

    val isFullyExpanded: Boolean
        get() = untriedActions.isEmpty()

    val isTerminal: Boolean
        get() = state.isTerminal

    fun uctValue(totalVisitCount: Int, explorationParam: Double = 1.41): Double {
        if (visitCount == 0) return Double.MAX_VALUE
        val exploitation = totalScore / visitCount
        val exploration = explorationParam * sqrt(ln(totalVisitCount.toDouble()) / visitCount)
        return exploitation + exploration
    }

    fun addChild(action: MahjongAction, childState: MahjongGameState): MctsNode {
        untriedActions.remove(action)
        val childNode = MctsNode(childState, action, this)
        children.add(childNode)
        return childNode
    }

    fun update(score: Double) {
        visitCount++
        totalScore += score
    }
}