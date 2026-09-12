package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseBackupHelper {

    private const val DB_NAME = "reminder_database"

    suspend fun backupDatabase(context: Context, targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Force a checkpoint to ensure all data is written
            AppDatabase.getDatabase(context).query("PRAGMA wal_checkpoint(FULL)", null)?.close()

            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file not found"))
            }

            // Create MasterKey for encryption
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // We need a temporary file to write the encrypted data before copying to targetUri
            val tempEncryptedFile = File(context.cacheDir, "backup_temp.enc")
            if (tempEncryptedFile.exists()) tempEncryptedFile.delete()

            val encryptedFile = EncryptedFile.Builder(
                context,
                tempEncryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            // Encrypt and write DB contents to temp file
            FileInputStream(dbFile).use { inputStream ->
                encryptedFile.openFileOutput().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Copy temp encrypted file to the user-selected Uri
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(tempEncryptedFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open target URI"))

            tempEncryptedFile.delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreDatabase(context: Context, sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Force a checkpoint
            AppDatabase.getDatabase(context).query("PRAGMA wal_checkpoint(FULL)", null)?.close()

            // Create MasterKey
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            // Copy encrypted file from URI to temp file
            val tempEncryptedFile = File(context.cacheDir, "restore_temp.enc")
            if (tempEncryptedFile.exists()) tempEncryptedFile.delete()

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempEncryptedFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open source URI"))

            val encryptedFile = EncryptedFile.Builder(
                context,
                tempEncryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            val dbFile = context.getDatabasePath(DB_NAME)
            
            // Decrypt temp file and write directly to DB file
            val tempDecryptedFile = File(context.cacheDir, "decrypted_temp.db")
            if (tempDecryptedFile.exists()) tempDecryptedFile.delete()

            encryptedFile.openFileInput().use { inputStream ->
                FileOutputStream(tempDecryptedFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // If decryption succeeded, replace the actual DB file
            if (dbFile.exists()) {
                dbFile.delete()
            }
            tempDecryptedFile.copyTo(dbFile, overwrite = true)
            tempDecryptedFile.delete()
            tempEncryptedFile.delete()
            
            // Delete journal file if it exists so we don't rollback
            val journalFile = context.getDatabasePath("$DB_NAME-journal")
            if (journalFile.exists()) journalFile.delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
