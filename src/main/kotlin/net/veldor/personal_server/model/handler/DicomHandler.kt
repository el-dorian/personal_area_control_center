package net.veldor.personal_server.model.handler

import javafx.concurrent.Task
import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.exception.HttpConnectionException
import net.veldor.personal_server.model.http.ConnectionToWebserver
import net.veldor.personal_server.model.selections.DicomArchive
import net.veldor.personal_server.model.selections.Patient
import net.veldor.personal_server.model.utils.FileUtils
import net.veldor.personal_server.model.utils.Grammar
import net.veldor.personal_server.model.utils.PreferencesHandler
import org.apache.commons.codec.digest.DigestUtils
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


class DicomHandler(val fileName: String) {
    fun handle() {
        // run task for handle dicom files
        val task = object : Task<Void?>() {
            override fun call(): Void? {
                // move file to temp dir
                val currentFile = File(PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_DIR_PATH), fileName)
                if (currentFile.isFile) {
                    println("DicomHandler 17 have dicom zip file!")
                    // first, move file to temp
                    val tempFile = File.createTempFile(Grammar.getRandomString(32), ".zip")
                    tempFile.delete()
                    var tryCounter = 0
                    while (!currentFile.renameTo(tempFile) && tryCounter < 10) {
                        println("try move file")
                        tryCounter++
                        Thread.sleep(3000)
                    }
                    if (tryCounter == 10) {
                        println("DicomHandler 27 can't move file")
                    } else {
                        // unzip file to temp directory
                        val tempDir = File(tempFile.parent, Grammar.getRandomString(32))
                        tempDir.mkdirs()
                        unzip(tempFile, tempDir)
                        tempFile.delete()
                        println("DicomHandler 35 unpacked in ${tempDir.absolutePath}")
                        // проверю, что переданы верные данные
                        try {
                            val dicomdirFile = tempDir.toPath().resolve("DICOMDIR").toFile()
                            if (dicomdirFile.exists() && dicomdirFile.isFile) {
                                // get required info from file
                                val content = FileUtils.readFile(dicomdirFile)
                                val clearContent = Grammar.clearUnreadableChars(content)
                                val executionId = Grammar.getDicomExecutionId(clearContent)
                                if (executionId != null) {
                                    // проверю, зарегистрирован ли пациент
                                    if (!Database.instance.executionRegistered(executionId)) {
                                        val parsedResult =
                                            JsonHandler().parseHttpMessage(
                                                ConnectionToWebserver().registerExecution(
                                                    executionId
                                                )
                                            )
                                        if (parsedResult.status) {
                                            val patient = JsonHandler().parsePatientData(parsedResult.payload)
                                                ?: throw HttpConnectionException("Не удалось получить данные зарегистрированного пациента")
                                            // зарегистрирую пациента для дальнейшего удаления
                                            net.veldor.personal_server.model.archiever.Archiver.instance.register(
                                                patient
                                            )
                                            ExternalWebSocket.instance.sendPatientRegisteredNotification(patient)
                                            ConclusionHandler().checkArchiveForPatientConclusions(patient)
                                        } else {
                                            // ошибка регистрации
                                            throw HttpConnectionException("Не удалось зарегистрировать нового пациента")
                                        }
                                    }
                                    // создам итоговый .zip, в который добавлю необходимые файлы, после чего помещу его в папку с результатами обследований и зарегистрирую в базе
                                    val additions =
                                        PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_ADDITIONS_DIR_PATH)
                                    if (additions != null && additions.isDirectory) {
                                        org.zeroturnaround.zip.commons.FileUtils.copyDirectory(additions, tempDir)
                                    }
                                    // prepare zip
                                    val outputZipFile = File.createTempFile(Grammar.getRandomString(32), ".zip")
                                    ZipUtil.pack(tempDir, outputZipFile)
                                    tempDir.deleteRecursively()
                                    // get file hash

                                    val hash: String
                                    Files.newInputStream(Paths.get(outputZipFile.toURI())).use { `is` ->
                                        hash = DigestUtils.md5Hex(`is`)
                                    }
                                    val destination = File(
                                        PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_DESTINATION_DIR_PATH),
                                        "$executionId.zip"
                                    )
                                    // проверю, что архив ещё не зарегистрирован
                                    if (destination.exists() && destination.isFile) {
                                        if (Database.instance.dicomArchiveRegistered(executionId) && Database.instance.dicomArchivesIdentical(
                                                executionId,
                                                hash
                                            )
                                        ) {
                                            // Попытка повторной загрузки одного и того же файла. Просто ничего не делаю.
                                            outputZipFile.delete()
                                            println("DicomHandler 90 try to upload identical dicom archive")
                                            return null
                                        }
                                        // файл уже загружался, обновлю информацию о нём
                                        while (!destination.delete()) {
                                            println("DicomHandler 75 try to delete previous destination")
                                            Thread.sleep(5000)
                                        }
                                    }
                                    outputZipFile.renameTo(destination)
                                    if (Database.instance.dicomArchiveRegistered(executionId)) {
                                        val dicomArchive = Database.instance.updateDicomArchiveInfo(executionId, hash)
                                        ExternalWebSocket.instance.sendDicomArchiveInfoUpdated(dicomArchive)
                                        FirebaseHandler.instance.sendArchiveInfoUpdatedMessage(destination, executionId)
                                    } else {
                                        val dicomArchive =
                                            Database.instance.registerDicomArchive(destination, executionId, hash)
                                        ExternalWebSocket.instance.sendDicomArchiveInfoRegistered(dicomArchive)
                                        FirebaseHandler.instance.sendArchiveInfoRegisteredMessage(
                                            destination,
                                            executionId
                                        )
                                    }
                                    println("DicomHandler 109 execution registered!")
                                }
                            }
                        } catch (t: Throwable) {
                            //t.printStackTrace()
                            println("DicomHandler 45 can't find DICOMDIR file, remove dir")
                            tempDir.deleteRecursively()
                            tempFile.delete()
                            ExternalWebSocket.instance.sendWrongDicomDirNotification(fileName)
                        }
                    }
                }
                return null
            }
        }
        Thread(task).start()
    }

    private fun unzip(tempFile: File, tempDir: File) {
        val buffer = ByteArray(1024)
        val zis = ZipInputStream(FileInputStream(tempFile))
        var zipEntry = zis.nextEntry
        while (zipEntry != null) {
            val newFile: File = newFile(tempDir, zipEntry)
            if (zipEntry.isDirectory) {
                if (!newFile.isDirectory && !newFile.mkdirs()) {
                    throw IOException("Failed to create directory $newFile")
                }
            } else {
                // fix for Windows-created archives
                val parent = newFile.parentFile
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Failed to create directory $parent")
                }

                // write file content
                val fos = FileOutputStream(newFile)
                var len: Int
                while (zis.read(buffer).also { len = it } > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.nextEntry
        }
        zis.closeEntry()
        zis.close()
    }

    @Throws(IOException::class)
    fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        val destFile = File(destinationDir, zipEntry.name)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }

    fun delete(patient: Patient) {
        val currentFile =
            File(PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_DESTINATION_DIR_PATH), fileName)
        if (currentFile.exists() && currentFile.isFile) {
            println("DicomHandler 176 dicom archive exists, delete")
            if (!currentFile.delete()) {
                TelegramHandler().sendUnsuccessfulFileDeleteDebug(currentFile.path)
            }
        }
        if (Database.instance.dicomArchiveRegistered(patient.examinationId)) {
            println("DicomHandler 184 dicom archive registered, remove from DB")
            Database.instance.deleteDicomArchive(patient)
        }
    }

    fun deleteArchive(dicomArchive: DicomArchive) {
        val currentFile =
            File(PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_DESTINATION_DIR_PATH), fileName)
        if (currentFile.exists() && currentFile.isFile) {
            if (!currentFile.delete()) {
                TelegramHandler().sendUnsuccessfulFileDeleteDebug(currentFile.path)
            }
        }
        Database.instance.deleteDicomArchive(dicomArchive)
    }
}