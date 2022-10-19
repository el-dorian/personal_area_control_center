package net.veldor.personal_server.model.handler

import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.http.ConnectionToWebserver
import net.veldor.personal_server.model.utils.PreferencesHandler

class AuthorizationHandler {
    fun authorize(token: String): Boolean {
        return Database.instance.checkAuthorizationToken(token)
    }

    fun canManage(token: String?): Boolean {
        if (token == null) {
            return false
        }
        return token == PreferencesHandler.instance.getExternalServerToken()
    }

    fun registerNewExamination(examinationId: String) {
        if (!Database.instance.executionRegistered(examinationId)) {
            val parsedResult =
                JsonHandler().parseHttpMessage(ConnectionToWebserver().registerExecution(examinationId))
            if (parsedResult.status) {
                val patient = JsonHandler().parsePatientData(parsedResult.payload)
                if (patient == null) {
                    ExternalWebSocket.instance.sendPatientRegistrationFailedNotification("Не удалось зарегистрировать $examinationId!")
                    return
                }
                // зарегистрирую пациента для дальнейшего удаления
                net.veldor.personal_server.model.archiever.Archiver.instance.register(patient)
                ExternalWebSocket.instance.sendPatientRegisteredNotification(patient)
                ConclusionHandler().checkArchiveForPatientConclusions(patient)
            } else {
                // ошибка регистрации
                ExternalWebSocket.instance.sendPatientRegistrationFailedNotification("Не удалось зарегистрировать $examinationId!")

            }
        } else {
            ExternalWebSocket.instance.sendPatientRegistrationFailedNotification("Обследование $examinationId уже зарегистрировано!")
        }
    }

    fun registerNextExamination(center: String) {
        val parsedResult =
            JsonHandler().parseHttpMessage(ConnectionToWebserver().registerNextExecution(center))
        if (parsedResult.status) {
            val patient = JsonHandler().parsePatientData(parsedResult.payload)
            if (patient == null) {
                ExternalWebSocket.instance.sendPatientRegistrationFailedNotification("Не удалось зарегистрировать следующего пациента!")
                return
            }
            // зарегистрирую пациента для дальнейшего удаления
            net.veldor.personal_server.model.archiever.Archiver.instance.register(patient)
            ExternalWebSocket.instance.sendPatientRegisteredNotification(patient)
        } else {
            // ошибка регистрации
            ExternalWebSocket.instance.sendPatientRegistrationFailedNotification("Не удалось зарегистрировать следующего пациента!")

        }
    }
}