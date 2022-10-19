package net.veldor.personal_server.model.utils

import net.veldor.personal_server.model.exception.WrongDirException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class PreferencesHandler private constructor() {

    private lateinit var mProperties: Properties

    init {
        // инициализирую хранилище настроек

        // инициализирую хранилище настроек
        try {
            val propertiesFile: File = getPropertiesFile()
            //Создаем объект свойст
            mProperties = Properties()
            //Загружаем свойства из файла
            mProperties.load(FileInputStream(propertiesFile))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun getPropertiesFile(): File {
        val propertiesFile = File("myProperties")
        if (!propertiesFile.isFile) {
            // если файл настроек ещё не создан-создам его
            if (!propertiesFile.createNewFile()) {
                throw IOException("Не смог создать файл настроек")
            }
        }
        return propertiesFile
    }

    private fun save() {
        try {
            mProperties.store(FileOutputStream(getPropertiesFile()), null)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    fun getExternalServerToken(): String? {
        return mProperties.getProperty("externalServerToken", null)

    }

    @Throws(WrongDirException::class)
    fun setDir(dirType: String, selectedDirectory: File?) {
        println("set " + dirType + " on " + selectedDirectory!!.absolutePath)
        if (selectedDirectory.isDirectory && selectedDirectory.exists()) {
            mProperties.setProperty(dirType, selectedDirectory.absolutePath)
            save()
        } else {
            throw WrongDirException()
        }
    }

    fun getDir(dirType: String): File? {
        val path = mProperties.getProperty(dirType, null)
        if (path != null) {
            return File(path)
        }
        return null
    }

    fun getInitialDir(dirType: String): File? {
        return getDir(dirType)
    }

    fun isTokenValid(token: String?): Boolean {
        val currentToken = getExternalServerToken() ?: return false
        return token == currentToken
    }

    fun getSoftwareVersion(): Int {
        val version = mProperties.getProperty(PREF_SOFTWARE_VERSION, "1")
        return version.toInt()
    }

    companion object {
        val instance: PreferencesHandler = PreferencesHandler()

        const val CONCLUSIONS_ARCHIVE_DIR_PATH = "conclusions archive dir"
        const val CONCLUSIONS_DIR_PATH = "conclusions dir"
        const val CONCLUSIONS_DESTINATION_DIR_PATH = "conclusions destination dir"
        const val DICOM_DIR_PATH = "dicom dir"
        const val DICOM_DESTINATION_DIR_PATH = "dicom destination dir"
        const val DICOM_ADDITIONS_DIR_PATH = "dicom additions dir"
        const val PREF_SOFTWARE_VERSION = "software version"
    }
}