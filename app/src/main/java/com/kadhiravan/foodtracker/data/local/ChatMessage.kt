package com.kadhiravan.foodtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

object CardStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val DISMISSED = "DISMISSED"
}

@Serializable
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    /** Local calendar date this message belongs to, formatted yyyy-MM-dd, the chat is scoped per day, like the Diary. */
    val chatDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cardStatus: String? = null
)
