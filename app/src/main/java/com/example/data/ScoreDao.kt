package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores WHERE id = 1")
    fun getScoreFlow(): Flow<ScoreEntity?>

    @Query("SELECT * FROM scores WHERE id = 1")
    suspend fun getScore(): ScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: ScoreEntity)
}
