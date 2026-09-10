package com.example.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PartyEntity
import com.example.data.local.ReminderEntity
import com.example.data.repository.AppRepository
import com.example.notification.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val repository: AppRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val uiState: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val parties: StateFlow<List<PartyEntity>> = repository.allParties
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<com.example.data.local.ReminderCategoryEntity>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val templates: StateFlow<List<com.example.data.local.ReminderTemplateEntity>> = repository.allTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.initializeDefaultCategories()
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(com.example.data.local.ReminderCategoryEntity(name = name))
        }
    }

    fun updateCategory(category: com.example.data.local.ReminderCategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategoryById(id)
        }
    }

    fun addTemplate(categoryId: Long, name: String) {
        viewModelScope.launch {
            repository.insertTemplate(com.example.data.local.ReminderTemplateEntity(categoryId = categoryId, name = name))
        }
    }

    fun updateTemplate(template: com.example.data.local.ReminderTemplateEntity) {
        viewModelScope.launch {
            repository.updateTemplate(template)
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            repository.deleteTemplateById(id)
        }
    }

    fun addReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            val insertedId = repository.insertReminder(reminder)
            val updatedReminder = reminder.copy(id = insertedId)
            alarmScheduler.scheduleReminder(updatedReminder)
        }
    }

    fun processVoiceCommand(text: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onMessage("Analyzing spoken task...")
                val parser = com.example.data.network.VoiceParserService()
                val result = parser.createTaskFromVoice(text, repository, alarmScheduler)
                result.fold(
                    onSuccess = { reminder ->
                        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(reminder.dueDate))
                        val party = reminder.partyId?.let { repository.getPartyById(it) }
                        val partyInfo = if (party != null) " for ${party.name}" else ""
                        val amountInfo = if (reminder.amount != null && reminder.amount > 0) " (₹${reminder.amount.toLong()})" else ""
                        onMessage("✓ Created [${reminder.tag}] Task: '${reminder.title}'$partyInfo$amountInfo due $dateStr")
                    },
                    onFailure = { error ->
                        onMessage("Failed to create task: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                onMessage("Voice processing failed: ${e.message}")
            }
        }
    }

    fun updateReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder)
            alarmScheduler.scheduleReminder(reminder)
        }
    }

    suspend fun getReminder(id: Long): ReminderEntity? {
        return repository.getReminderById(id)
    }

    fun completeReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(status = "Completed", updatedAt = System.currentTimeMillis()))
            alarmScheduler.cancel(reminder.id)

            if (reminder.repeatType != "One-time") {
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = reminder.dueDate
                when (reminder.repeatType) {
                    "Every Month" -> cal.add(java.util.Calendar.MONTH, 1)
                    "Quarterly" -> cal.add(java.util.Calendar.MONTH, 3)
                    "Yearly" -> cal.add(java.util.Calendar.YEAR, 1)
                }
                val newDueDate = cal.timeInMillis

                val timeDiff = reminder.dueDate - (reminder.dueTime ?: reminder.dueDate)
                val newDueTime = newDueDate - timeDiff

                val newReminder = reminder.copy(
                    id = 0,
                    dueDate = newDueDate,
                    dueTime = newDueTime,
                    status = "Pending",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = repository.insertReminder(newReminder)
                alarmScheduler.scheduleReminder(newReminder.copy(id = newId))
            }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminderById(id)
            alarmScheduler.cancel(id)
        }
    }

    fun scheduleTestAlarm(title: String, category: String, delaySeconds: Int = 5) {
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
        val tempId = System.currentTimeMillis() % 100000
        alarmScheduler.schedule(
            reminderId = tempId,
            title = title,
            category = category,
            triggerAtMillis = triggerTime,
            notes = "Test alarm triggered after $delaySeconds seconds"
        )
    }
}

class RemindersViewModelFactory(
    private val repository: AppRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RemindersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RemindersViewModel(repository, alarmScheduler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
