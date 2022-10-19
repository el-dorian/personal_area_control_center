package net.veldor.personal_server.model.handler

import net.veldor.pdf_archiver.model.Archiver
import net.veldor.pdf_archiver.utils.ExistsInDbException
import net.veldor.pdf_parser.model.handler.Handler
import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.exception.HttpConnectionException
import net.veldor.personal_server.model.http.ConnectionToWebserver
import net.veldor.personal_server.model.selections.Patient
import net.veldor.personal_server.model.utils.FileUtils
import net.veldor.personal_server.model.utils.Grammar
import net.veldor.personal_server.model.utils.PreferencesHandler
import net.veldor.personal_server.model.utils.RandomString
import java.io.File
import java.nio.file.Files

class ConclusionHandler {
    fun handle(file: File) {
        if (file.isFile && file.exists()) {
            val destinationDir = PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)
            if (destinationDir == null) {
                ExternalWebSocket.instance.sendForRegistered("Не назначена папка для хранения заключений")
                return
            }
            val realName = file.name
            // перемещу файл во временную папку
            val tempFile =
                File(System.getProperty("java.io.tmpdir"), "${RandomString().nextString()}.${file.extension}")
            while (true) {
                val renameResult = file.renameTo(tempFile)
                if (renameResult) {
                    break
                }
                Thread.sleep(500)
            }
            if (tempFile.extension != "pdf") {
                tempFile.delete()
                ExternalWebSocket.instance.sendConclusionAddError("${file.name} : Обрабатываются только PDF файлы")
                return
            }
            try {
                val conclusion = Handler(tempFile).parse()
                // проверю, создана ли учётная запись для данного обследования
                if (!Database.instance.executionRegistered(conclusion.executionNumber)) {
                    val parsedResult =
                        JsonHandler().parseHttpMessage(ConnectionToWebserver().registerExecution(conclusion.executionNumber))
                    if (parsedResult.status) {
                        val patient = JsonHandler().parsePatientData(parsedResult.payload)
                            ?: throw HttpConnectionException("Не удалось получить данные зарегистрированного пациента")
                        // зарегистрирую пациента для дальнейшего удаления
                        net.veldor.personal_server.model.archiever.Archiver.instance.register(patient)
                        ExternalWebSocket.instance.sendPatientRegisteredNotification(patient)
                        // проверю наличие заключений этого обследования в архиве
                        checkArchiveForPatientConclusions(patient)
                    } else {
                        // ошибка регистрации
                        throw HttpConnectionException("Не удалось зарегистрировать нового пациента")
                    }
                }
                // добавлю информацию о заключении
                val clearExecutionArea =
                    conclusion.executionArea.replace("[^\\p{IsAlphabetic}\\p{IsDigit}]", "").lowercase()
                        .replace(" ", "-")
                val conclusionFileName = "${conclusion.executionNumber}_$clearExecutionArea.pdf"
                val destination = FileUtils().getConclusionDestinationFileName(conclusionFileName)
                println("ConclusionHandler 63 asked destination is ${destination?.path}")
                if (destination == null) {
                    ExternalWebSocket.instance.sendForRegistered("$realName - ошибка обработки. Не удалось переместить файл в конечную папку")
                    ExternalWebSocket.instance.sendConclusionAddError("${file.name} : Не удалось получить конечное имя файла")
                    return
                }
                if (destination.exists() && destination.isFile) {
                    ExternalWebSocket.instance.sendForRegistered("$realName - Найден файл заключения, обновляю информацию")
                    val previousVersion = Database.instance.getPreviousVersion(conclusion)
                    if (previousVersion != null) {
                        if (previousVersion.hash == conclusion.hash) {
                            // файлы полностью идентичны, ничего не делаю
                            return
                        }
                        // если заключения разные-заменю прошлые данные на новые
                        destination.delete()
                        if (tempFile.renameTo(destination)) {
                            conclusion.file = destination
                            conclusion.filePath = conclusion.file.absolutePath.replace(
                                PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)!!.absolutePath,
                                ""
                            ).replace("\\", "\\\\")
                            // регистрирую заключение
                            Database.instance.updateConclusion(conclusion)
                            ExternalWebSocket.instance.sendForRegistered("${conclusion.executionNumber}-успешно обновлено заключение для ${conclusion.executionArea}")
                            ExternalWebSocket.instance.sendConclusionUpdated(conclusion)
                            FirebaseHandler.instance.sendConclusionUpdatedMessage(conclusion)
                        } else {
                            ExternalWebSocket.instance.sendForRegistered("$realName - не удалось переместить в конечную папку")
                        }
                        return
                    } else {
                        //нет данных о файле, удалю его и зарегистрирую новый
                        destination.delete()
                    }
                }
                if (tempFile.renameTo(destination)) {
                    conclusion.file = destination
                    conclusion.filePath = conclusion.file.absolutePath.replace(
                        PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)!!.absolutePath,
                        ""
                    ).replace("\\", "\\\\")
                    // проверю, что в базе ещё нет данных по заключению
                    if (Database.instance.conclusionRegistered(conclusion)) {
                        ExternalWebSocket.instance.sendForRegistered("Данные по заключению уже в базе")
                        return
                    }
                    // регистрирую заключение
                    Database.instance.registerConclusion(conclusion)
                    ExternalWebSocket.instance.sendForRegistered("${conclusion.executionNumber}-успешно добавлено заключение для ${conclusion.executionArea}")
                    ExternalWebSocket.instance.sendConclusionRegistered(conclusion)
                    FirebaseHandler.instance.sendConclusionRegisteredMessage(conclusion)
                    println("ConclusionHandler 119 conclusion registered")
                } else {
                    ExternalWebSocket.instance.sendConclusionAddError("${file.name} : Ошибка копирования файла")
                }

            } catch (t: Throwable) {
                //t.printStackTrace()
                ExternalWebSocket.instance.sendConclusionAddError("${file.name} : ${t.message}")
                tempFile.delete()
            }
            tempFile.delete()
            file.delete()
        }
    }

    fun checkArchiveForPatientConclusions(patient: Patient) {
        val archiveConclusions = Archiver().getConclusionsFromArchive(
            patient.examinationId,
            PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_ARCHIVE_DIR_PATH)
        )
        if (archiveConclusions.isNotEmpty()) {
            archiveConclusions.forEach {
                println("ConclusionHandler 141 add archive conclusion ${it.name}")
                if (it.exists() && it.isFile) {
                    val tempFile = File.createTempFile(Grammar.getRandomString(32), ".pdf")
                    val outputStream = tempFile.outputStream()
                    Files.copy(it.toPath(), outputStream)
                    outputStream.close()
                    handle(tempFile)
                }
            }
        }
    }

    fun archiveConclusion(conclusion: Conclusion) {
        val conclusionFile =
            File(PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)!!.absolutePath + conclusion.filePath)
        if (conclusionFile.isFile) {
            println("ConclusionHandler 133 have conclusion file, archive with hash")
            try {
                val archivationResult = Archiver().archive(
                    conclusionFile,
                    PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_ARCHIVE_DIR_PATH)
                )
                if(archivationResult){
                    println("ConclusionHandler 137 file archived")
                } else {
                    TelegramHandler().sendUnsuccessfulArchiveConclusionDebug(conclusion)
                }
            }
            catch (e: ExistsInDbException){
                ExternalWebSocket.instance.sendConclusionArchiveDuplicate(conclusion)
                //todo сделать что-то с дубликатом
            }
            Database.instance.deleteConclusion(conclusion)

        }
    }

    fun deleteConclusion(conclusion: Conclusion) {
        val conclusionFile =
            File(PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)!!.absolutePath + conclusion.filePath)
        if (conclusionFile.isFile) {
            if (!conclusionFile.delete()) {
                TelegramHandler().sendUnsuccessfulDeleteConclusionDebug(conclusion)
            }
        }
    }

    fun deleteConclusionById(conclusionId: String): Conclusion? {
        val conclusion = Database.instance.getConclusion(conclusionId)
        if (conclusion != null) {
            deleteConclusion(conclusion)
            Database.instance.deleteConclusion(conclusion)
            return conclusion
        }
        return null
    }
}