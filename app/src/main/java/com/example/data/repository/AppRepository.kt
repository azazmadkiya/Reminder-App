package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val partyDao: PartyDao,
    private val transactionDao: TransactionDao,
    private val reminderDao: ReminderDao
) {
    val allParties: Flow<List<PartyEntity>> = partyDao.getAllParties()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val pendingReminders: Flow<List<ReminderEntity>> = reminderDao.getPendingReminders()
    
    fun getTransactionsForParty(partyId: Long): Flow<List<TransactionEntity>> = transactionDao.getTransactionsForParty(partyId)
    
    suspend fun getPartyById(id: Long): PartyEntity? = partyDao.getPartyById(id)
    
    suspend fun insertParty(party: PartyEntity): Long = partyDao.insertParty(party)
    suspend fun updateParty(party: PartyEntity) = partyDao.updateParty(party)
    suspend fun deletePartyById(id: Long) = partyDao.deletePartyById(id)
    
    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        // Update party balance logic here or in ViewModel
        return id
    }
    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteTransactionById(id)
    
    suspend fun getReminderById(id: Long): ReminderEntity? = reminderDao.getReminderById(id)
    suspend fun getPendingRemindersList(): List<ReminderEntity> = reminderDao.getPendingRemindersList()

    suspend fun insertReminder(reminder: ReminderEntity): Long = reminderDao.insertReminder(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.updateReminder(reminder)
    suspend fun deleteReminderById(id: Long) = reminderDao.deleteReminderById(id)

    // Backup & Restore
    suspend fun getAllPartiesList(): List<PartyEntity> = partyDao.getAllPartiesList()
    suspend fun getAllTransactionsList(): List<TransactionEntity> = transactionDao.getAllTransactionsList()
    suspend fun getAllRemindersList(): List<ReminderEntity> = reminderDao.getAllRemindersList()

    suspend fun restoreData(
        parties: List<PartyEntity>,
        transactions: List<TransactionEntity>,
        reminders: List<ReminderEntity>,
        replaceExisting: Boolean
    ) {
        if (replaceExisting) {
            // Cascade: delete transactions first, then reminders, then parties
            transactionDao.deleteAllTransactions()
            reminderDao.deleteAllReminders()
            partyDao.deleteAllParties()
        }

        if (parties.isNotEmpty()) {
            partyDao.insertAllParties(parties)
        }
        if (transactions.isNotEmpty()) {
            transactionDao.insertAllTransactions(transactions)
        }
        if (reminders.isNotEmpty()) {
            reminderDao.insertAllReminders(reminders)
        }
    }

    // Categories and Templates
    val allCategories: Flow<List<ReminderCategoryEntity>> = reminderDao.getAllCategories()
    val allTemplates: Flow<List<ReminderTemplateEntity>> = reminderDao.getAllTemplates()

    fun getTemplatesByCategory(categoryId: Long): Flow<List<ReminderTemplateEntity>> = reminderDao.getTemplatesByCategory(categoryId)

    suspend fun insertCategory(category: ReminderCategoryEntity): Long = reminderDao.insertCategory(category)
    suspend fun updateCategory(category: ReminderCategoryEntity) = reminderDao.updateCategory(category)
    suspend fun deleteCategoryById(id: Long) = reminderDao.deleteCategoryById(id)

    suspend fun insertTemplate(template: ReminderTemplateEntity): Long = reminderDao.insertTemplate(template)
    suspend fun updateTemplate(template: ReminderTemplateEntity) = reminderDao.updateTemplate(template)
    suspend fun deleteTemplateById(id: Long) = reminderDao.deleteTemplateById(id)

    suspend fun initializeDefaultCategories() {
        if (reminderDao.getCategoryCount() == 0) {
            val defaults = listOf(
                "GST" to listOf("GSTR-1 Return Filing", "GSTR-3B Tax Filing", "GST Challan Payment", "QRMP Scheme Filing"),
                "Income Tax" to listOf("Advance Tax Installment", "TDS Monthly Deposit", "ITR Filing Deadline", "Form 16 Issuance"),
                "Payment Receive" to listOf("Follow-up Client Invoice", "Collect Outstanding Balance", "Quarterly Retainer Fee"),
                "Payment Paid" to listOf("Vendor Bill Due", "Office Rent Payment", "Electricity / Utility Bill"),
                "Custom" to listOf("Quarterly Compliance", "Statutory Audit Reminder", "Bank Statement Verification")
            )
            for ((catName, tmpls) in defaults) {
                val catId = insertCategory(ReminderCategoryEntity(name = catName))
                for (tName in tmpls) {
                    insertTemplate(ReminderTemplateEntity(categoryId = catId, name = tName))
                }
            }
        }
    }
}
