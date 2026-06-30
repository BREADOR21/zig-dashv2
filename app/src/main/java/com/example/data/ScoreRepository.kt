package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScoreRepository(private val scoreDao: ScoreDao) {
    val bestScoreFlow: Flow<Int> = scoreDao.getScoreFlow().map { it?.bestScore ?: 0 }

    suspend fun getBestScore(): Int {
        return scoreDao.getScore()?.bestScore ?: 0
    }

    suspend fun updateBestScore(newScore: Int) {
        val currentBest = getBestScore()
        if (newScore > currentBest) {
            scoreDao.insertScore(ScoreEntity(bestScore = newScore))
        }
    }
}
