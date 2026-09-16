package com.kadhiravan.foodtracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatDate = :date ORDER BY timestamp ASC")
    fun observeForDate(date: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatDate = :date ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentForDate(date: String, limit: Int): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Insert
    suspend fun insertAll(messages: List<ChatMessage>)

    @Query("SELECT * FROM chat_messages")
    suspend fun getAll(): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("UPDATE chat_messages SET cardStatus = :status WHERE id = :id")
    suspend fun updateCardStatus(id: Long, status: String)

    /** Used on confirm, alongside marking the card CONFIRMED, to splice any edits made in
     * the card UI back into the stored content — see ChatRepository.confirmCard. */
    @Query("UPDATE chat_messages SET content = :content, cardStatus = :status WHERE id = :id")
    suspend fun updateContentAndStatus(id: Long, content: String, status: String)
}
