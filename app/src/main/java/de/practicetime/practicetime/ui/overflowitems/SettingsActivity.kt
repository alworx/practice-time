package de.practicetime.practicetime.ui.overflowitems

import android.content.Intent
import android.os.Bundle
import android.system.Os.chmod
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import de.practicetime.practicetime.R
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val CREATE_FILE = 1
private const val CREATE_FILE_SHM = 100
private const val CREATE_FILE_WAL = 101

private const val PICK_DB_FILE = 2
private const val PICK_DB_FILE_SHM = 200
private const val PICK_DB_FILE_WAL = 201


class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)


        findViewById<MaterialButton>(R.id.activity_settings_btn_export).setOnClickListener {
            exportDatabase()
        }
        findViewById<MaterialButton>(R.id.activity_settings_btn_export_shm).setOnClickListener {
            exportShm()
        }
        findViewById<MaterialButton>(R.id.activity_settings_btn_export_wal).setOnClickListener {
            exportWal()
        }

        findViewById<MaterialButton>(R.id.activity_settings_btn_import).setOnClickListener {
            importDatabase()
        }
        findViewById<MaterialButton>(R.id.activity_settings_btn_import_shm).setOnClickListener {
            importShm()
        }
        findViewById<MaterialButton>(R.id.activity_settings_btn_import_wal).setOnClickListener {
            importWal()
        }
    }

    private fun exportDatabase() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.sqlite3"
            putExtra(Intent.EXTRA_TITLE, "PracticeTime.sql")
        }
        startActivityForResult(intent, CREATE_FILE)
    }


    private fun exportShm() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "PracticeTime.sql-shm")
        }
        startActivityForResult(intent, CREATE_FILE_SHM)
    }
    private fun exportWal() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "PracticeTime.sql-wal")
        }
        startActivityForResult(intent, CREATE_FILE_WAL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK)
            return

        when (requestCode) {

            CREATE_FILE -> {
                data?.data?.also { uri ->
                    val srcFile = File(getDatabasePath("pt-database").absolutePath)
                    Files.copy(srcFile.toPath(), contentResolver.openOutputStream(uri))
                }
            }
            CREATE_FILE_SHM -> {
                data?.data?.also { uri ->
                    val srcFile = File(getDatabasePath("pt-database-shm").absolutePath)
                    Files.copy(srcFile.toPath(), contentResolver.openOutputStream(uri))
                }
            }
            CREATE_FILE_WAL -> {
                data?.data?.also { uri ->
                    val srcFile = File(getDatabasePath("pt-database-wal").absolutePath)
                    Files.copy(srcFile.toPath(), contentResolver.openOutputStream(uri))
                }
            }

            PICK_DB_FILE -> {
                data?.data?.also { uri ->
                    val srcFile = contentResolver.openInputStream(uri)
                    val dstFile = File(getDatabasePath("pt-database").absolutePath).toPath()
                    Runtime.getRuntime()
                        .exec("chmod 660 ${getDatabasePath("pt-database").absolutePath}")
                    Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            PICK_DB_FILE_SHM -> {
                data?.data?.also { uri ->
                    val srcFile = contentResolver.openInputStream(uri)
                    val dstFile = File(getDatabasePath("pt-database-shm").absolutePath).toPath()
                    Runtime.getRuntime()
                        .exec("chmod 600 ${getDatabasePath("pt-database-shm").absolutePath}")
                    Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            PICK_DB_FILE_WAL -> {
                data?.data?.also { uri ->
                    val srcFile = contentResolver.openInputStream(uri)
                    val dstFile = File(getDatabasePath("pt-database-wal").absolutePath).toPath()
                    Runtime.getRuntime()
                        .exec("chmod 600 ${getDatabasePath("pt-database-wal").absolutePath}")
                    Files.copy(srcFile, dstFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

    }

    private fun importDatabase() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
//            type = "application/vnd.sqlite3"
            type = "*/*"
        }
        startActivityForResult(intent, PICK_DB_FILE)
    }
    private fun importShm() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
//            type = "application/vnd.sqlite3"
            type = "*/*"
        }
        startActivityForResult(intent, PICK_DB_FILE_SHM)

    }
    private fun importWal() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
//            type = "application/vnd.sqlite3"
            type = "*/*"
        }
        startActivityForResult(intent, PICK_DB_FILE_WAL)

    }
}