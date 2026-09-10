package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'Pending' ORDER BY dueDate ASC")
    fun getPendingReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'Overdue' ORDER BY dueDate ASC")
    fun getOverdueReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status = 'Pending'")
    suspend fun getPendingRemindersList(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    suspend fun getAllRemindersList(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReminders(reminders: List<ReminderEntity>): List<Long>

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
    @Query("SELECT * FROM reminder_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ReminderCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ReminderCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: ReminderCategoryEntity)

    @Query("DELETE FROM reminder_categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    @Query("SELECT * FROM reminder_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<ReminderTemplateEntity>>

    @Query("SELECT * FROM reminder_templates WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getTemplatesByCategory(categoryId: Long): Flow<List<ReminderTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ReminderTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: ReminderTemplateEntity)

    @Query("DELETE FROM reminder_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("SELECT COUNT(*) FROM reminder_categories")
    suspend fun getCategoryCount(): Int
}
