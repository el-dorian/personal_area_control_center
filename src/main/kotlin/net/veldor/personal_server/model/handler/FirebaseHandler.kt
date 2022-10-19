package net.veldor.personal_server.model.handler

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.selections.Patient
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FirebaseHandler private constructor() {

    init {
        val options: FirebaseOptions
        try {
            val serviceAccount = FileInputStream("personal-area-firebase-adminsdk.json")
            options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount)).build()
            app = FirebaseApp.initializeApp(options)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendMessage() {
        println("FirebaseHandler 27 send message")
        val message = Message.builder()
            .build()
        /*        println("prepare message")
            // See documentation on defining a message payload.
                    val message = Message.builder()
                        .putData("type", "alert")
                        .putData("devEui", "test")
                        .putData("pin", "test")
                        .putData("pinStatus", "test")
                        .putData("actionTime", "test")
                        .putData("rawData", "test")
                        .putData("cottageNumber", "test")
                        .setToken(d)
                        .build()
                    println("message prepared")

            // Send a message to the device corresponding to the provided
            // registration token.
                    send(message)*/
    }

    private fun send(message: Message) {
        var response: String? = null
        try {
            response = FirebaseMessaging.getInstance(app).send(message)
        } catch (e: Exception) {
            println("we have problem")
            e.printStackTrace()
        }
    }

    fun sendConclusionRegisteredMessage(conclusion: Conclusion) {
        // сначала-найду получателей сообщения
        val message = Message.builder()
            .setToken("fQFawC0OTCCWFHBTU5zKA6:APA91bHgxzwTmY007CFLuK9W-ZvHet-5Yj9ay2nwi31vFupqT_O4nVM4tjB_QFRpMnNlKRi8IprInKCGqEz2pHkSEpU87U7JEzXJQSLvTWenG70EKqzCuoaSso-IqyUl3X4DyobIF4CV")
            .setNotification(
                Notification.builder().setTitle("Добавлено заключение врача")
                    .setBody("Добавлено заключение врача по ${conclusion.executionArea}").build()
            )
            .build()
        send(message)
    }

    fun sendConclusionUpdatedMessage(conclusion: Conclusion) {
        val message = Message.builder()
            .setToken("fQFawC0OTCCWFHBTU5zKA6:APA91bHgxzwTmY007CFLuK9W-ZvHet-5Yj9ay2nwi31vFupqT_O4nVM4tjB_QFRpMnNlKRi8IprInKCGqEz2pHkSEpU87U7JEzXJQSLvTWenG70EKqzCuoaSso-IqyUl3X4DyobIF4CV")
            .setNotification(
                Notification.builder().setTitle("Обновление данных")
                    .setBody("Обновлены данные по заключению ${conclusion.executionArea}").build()
            )
            .build()
        send(message)
    }

    fun sendArchiveInfoUpdatedMessage(destination: File, executionId: String) {
        val message = Message.builder()
            .setToken("fQFawC0OTCCWFHBTU5zKA6:APA91bHgxzwTmY007CFLuK9W-ZvHet-5Yj9ay2nwi31vFupqT_O4nVM4tjB_QFRpMnNlKRi8IprInKCGqEz2pHkSEpU87U7JEzXJQSLvTWenG70EKqzCuoaSso-IqyUl3X4DyobIF4CV")
            .setNotification(
                Notification.builder().setTitle("Обновлён архив изображений")
                    .setBody("Обновлён архив изображений обследования №$executionId").build()
            )
            .build()
        send(message)
    }

    fun sendArchiveInfoRegisteredMessage(destination: File, executionId: String) {
        val message = Message.builder()
            .setToken("fQFawC0OTCCWFHBTU5zKA6:APA91bHgxzwTmY007CFLuK9W-ZvHet-5Yj9ay2nwi31vFupqT_O4nVM4tjB_QFRpMnNlKRi8IprInKCGqEz2pHkSEpU87U7JEzXJQSLvTWenG70EKqzCuoaSso-IqyUl3X4DyobIF4CV")
            .setNotification(
                Notification.builder().setTitle("Добавлен архив изображений")
                    .setBody("Добавлен архив изображений обследования №$executionId").build()
            )
            .build()
        send(message)
    }

    fun sendExaminationOutOfDateNotification(patient: Patient) {
        val message = Message.builder()
            .setToken("fQFawC0OTCCWFHBTU5zKA6:APA91bHgxzwTmY007CFLuK9W-ZvHet-5Yj9ay2nwi31vFupqT_O4nVM4tjB_QFRpMnNlKRi8IprInKCGqEz2pHkSEpU87U7JEzXJQSLvTWenG70EKqzCuoaSso-IqyUl3X4DyobIF4CV")
            .setNotification(
                Notification.builder().setTitle("Учётная запись удалена")
                    .setBody("В целях безопасности ваши данные удалены с нашего сервера. Если вам нужен будет повторный доступ к данным- вы можете обратиться к нам за повторным размещением")
                    .build()
            )
            .build()
        send(message)
    }

    companion object {
        var instance = FirebaseHandler()
        private var app: FirebaseApp? = null
    }
}