package net.veldor.personal_server.model

import com.google.gson.GsonBuilder
import javafx.concurrent.Task
import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.archiever.Archiver
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.exception.EmailException
import net.veldor.personal_server.model.exception.HttpConnectionException
import net.veldor.personal_server.model.handler.*
import net.veldor.personal_server.model.http.ConnectionToWebserver
import net.veldor.personal_server.model.selections.DicomArchive
import net.veldor.personal_server.model.selections.Email
import net.veldor.personal_server.model.selections.Patient
import net.veldor.personal_server.model.selections.SocketMessage
import net.veldor.personal_server.model.utils.PreferencesHandler
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class ExternalWebSocket private constructor() : WebSocketServer(InetSocketAddress("127.0.0.1", 27015)) {

    val clientsCount: Int
        get() {
            return authorizedClients.size
        }

    private var delegate: WebSocketActionDelegate? = null
    private val authorizedClients: HashMap<WebSocket, String> = hashMapOf()


    override fun onOpen(webSocket: WebSocket, clientHandshake: ClientHandshake?) {
        // run check after 10 seconds, if client not authenticated- kick it
        checkSocketAuthorization(webSocket)
    }

    private fun checkSocketAuthorization(socket: WebSocket) {
        val task = object : Task<Void?>() {
            override fun call(): Void? {
                Thread.sleep(10000)
                if (!authorizedClients.containsKey(socket)) {
                    println("ExternalWebSocket 34 client kicked")
                    sendKickUnauthorizedClientNotification(socket)
                    socket.close()
                }
                return null
            }
        }
        Thread(task).start()
    }


    override fun onClose(webSocket: WebSocket?, i: Int, s: String?, b: Boolean) {
        // тут будет действие при потере соединения с клиентом
        println("client disconnected")
        authorizedClients.remove(webSocket)
        delegate?.clientDisconnected()
    }

    override fun onMessage(webSocket: WebSocket, s: String) {
        println("ExternalWebSocket 35 received message $s")
        try {
            val message = SocketMessageHandler().parseMessage(s)
            if (!authorizedClients.containsKey(webSocket) && message.command != "authorize") {
                sendAuthorizationRequiredMessage(webSocket)
                TelegramHandler().sendUnauthorizedAccessDebug(webSocket, message)
                webSocket.close()
            }
            when (message.command) {
                "register_examination" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        AuthorizationHandler().registerNewExamination(message.payload)
                    }
                }

                "register_next_examination" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        AuthorizationHandler().registerNextExamination(message.payload)
                    }
                }

                "dicom_sent" -> {
                    if (PreferencesHandler.instance.isTokenValid(message.token)) {
                        DicomHandler(message.payload).handle()
                    }
                }

                "authorize" -> {
                    if (AuthorizationHandler().authorize(message.payload)) {
                        println("ExternalWebSocket 69 client authenticated")
                        delegate?.clientConnected()
                        authorizedClients[webSocket] = message.payload
                    } else {
                        sendAccessDeniedMessage(webSocket)
                        webSocket.close()
                        TelegramHandler().sendFailedAuthorizationDebug(webSocket, message.payload)
                    }
                }

                "delete_patient" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val patient = Database.instance.getPatientById(message.payload)
                        val msg = SocketMessage()
                        msg.command = "patient_deleted"
                        sendMessageToPatient(patient, msg)
                        // kick patient if connected
                        val patientConnections = ArrayList<WebSocket>()
                        authorizedClients.forEach { connection ->
                            if (connection.value === patient?.accessToken) {
                                patientConnections.add(connection.key)
                            }
                        }
                        patientConnections.forEach {
                            it.close()
                            authorizedClients.remove(it)
                        }
                        val deletedPatient = Archiver.instance.deletePatient(message.payload)
                        if (deletedPatient != null) {
                            println("ExternalWebSocket 87 have patient for delete")
                            val notification = SocketMessage()
                            notification.command = "patient_deleted"
                            notification.payload = JsonHandler().toJson(deletedPatient)
                            sendInternalMessage(notification)
                        } else {
                            println("ExternalWebSocket 94 can't find patient")
                        }
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "delete_conclusion" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val deletedConclusion = ConclusionHandler().deleteConclusionById(message.payload)
                        if (deletedConclusion != null) {
                            val notification = SocketMessage()
                            notification.command = "conclusion_deleted"
                            notification.payload = JsonHandler().toJson(deletedConclusion)
                            sendInternalMessage(notification)
                            val patient = Database.instance.getPatientByName(deletedConclusion.executionNumber)
                            sendMessageToPatient(patient, message)
                        } else {
                            println("ExternalWebSocket 94 can't find patient")
                        }
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "delete_dicom" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val dicomArchive = Database.instance.getDicomArchiveById(message.payload)
                        DicomHandler("${dicomArchive.examinationId}.zip").deleteArchive(dicomArchive)
                        val notification = SocketMessage()
                        notification.command = "dicom_archive_deleted"
                        notification.payload = JsonHandler().toJson(dicomArchive)
                        sendInternalMessage(notification)
                        val patient = Database.instance.getPatientByName(dicomArchive.examinationId)
                        sendMessageToPatient(patient, message)
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "edit_patient_email" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val editMailRequest = JsonHandler().parseAddMailRequest(message.payload)
                        try {
                            val patient = Database.instance.getPatientById(editMailRequest.patientId.toString())
                            if (patient != null) {
                                PatientMailHandler().editEmails(editMailRequest, patient)
                            }
                        } catch (e: EmailException) {
                            sendEmailRegistrationErrorNotification(e)
                        }
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "send_email" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val patient = Database.instance.getPatientById(message.payload)
                        if (patient != null) {
                            // get patient emails
                            val emails = Database.instance.getPatientEmails(patient.patientId.toString())
                            if (emails.isNotEmpty()) {
                                emails.forEach {
                                    try {
                                        val result = ConnectionToWebserver().requestSendMail(it)
                                        val parsedResult = JsonHandler().parseHttpMessage(result)
                                        if (parsedResult.status) {
                                            val notification = SocketMessage()
                                            notification.command = "success_mail_send_response"
                                            notification.payload = JsonHandler().toJson(it)
                                            sendInternalMessage(notification)
                                        } else {
                                            val notification = SocketMessage()
                                            notification.command = "error_mail_send_response"
                                            notification.payload = parsedResult.payload!!
                                            sendInternalMessage(notification)
                                        }
                                    } catch (t: Throwable) {
                                        val notification = SocketMessage()
                                        notification.command = "failed_mail_send_response"
                                        notification.payload = JsonHandler().toJson(patient)
                                        sendInternalMessage(notification)
                                    }
                                }
                            } else {
                                val notification = SocketMessage()
                                notification.command = "no_email_have_response"
                                notification.payload = JsonHandler().toJson(patient)
                                sendInternalMessage(notification)
                            }
                        }
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "change_patient_password" -> {
                    if (AuthorizationHandler().canManage(authorizedClients[webSocket])) {
                        val parsedResult =
                            JsonHandler().parseHttpMessage(ConnectionToWebserver().changeUserPassword(message.payload))
                        if (parsedResult.status) {
                            println("ConclusionHandler 56 password changed")
                            val patient = JsonHandler().parsePatientData(parsedResult.payload)
                                ?: throw HttpConnectionException("Не удалось получить данные пациента")
                            sendPatientPasswordChangedNotification(patient)
                        } else {
                            println("ConclusionHandler 63 error handle patient password change")
                        }
                    } else {
                        sendAccessDeniedMessage(webSocket)
                    }
                }

                "check_lifetime_request" -> {
                    val examination = Database.instance.getPatientByToken(message.payload)
                    if (examination != null) {
                        val notification = SocketMessage()
                        notification.command = "check_lifetime_response"
                        notification.payload = examination.lifetimeEnd.toString()
                        val builder = GsonBuilder()
                        val gson = builder.create()
                        webSocket.send(gson.toJson(notification, SocketMessage::class.java))
                    }
                }

                "review_request" -> {
                    val examination = Database.instance.getPatientByToken(message.token!!)
                    if (examination != null) {
                        val review = JsonHandler().parseReview(message.payload)
                        if (review.starring != null || review.text != null) {
                            // save review
                            Database.instance.saveReview(examination, review)
                            val notification = SocketMessage()
                            notification.command = "review_response"
                            notification.payload = message.payload
                            sendMessageToPatient(examination, notification)
                            sendInternalMessage(notification)
                        }
                    }
                }

                "force_archive_request" -> {
                    val examination = Database.instance.getPatientByToken(message.payload)
                    if (examination != null) {
                        Archiver.instance.archive(examination)
                    }
                }

                "lifetime_extend_request" -> {
                    val examination = Database.instance.getPatientByToken(message.payload)
                    if (examination != null) {
                        Database.instance.extendLifetime(examination)
                        Archiver.instance.extendLifetime(examination)
                        val notification = SocketMessage()
                        notification.command = "lifetime_extend_response"
                        notification.payload = examination.lifetimeEnd.toString()
                        val builder = GsonBuilder()
                        val gson = builder.create()
                        webSocket.send(gson.toJson(notification, SocketMessage::class.java))

                        notification.command = "check_lifetime_response"
                        notification.payload = examination.lifetimeEnd.toString()
                        webSocket.send(gson.toJson(notification, SocketMessage::class.java))
                    }
                }
            }
        } catch (t: Throwable) {
            //t.printStackTrace()
            println("ExternalWebSocket 40 error when read message")
        }
        // расшифрую сообщение
        //webSocket.send(SocketMessageHandler().handle(s))
    }

    private fun sendEmailsChangedNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "patient_emails_changed"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
    }

    fun sendEmailsDeletedNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "emails_deleted"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
    }

    fun sendEmailsRegisteredNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "emails_registered"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
    }

    private fun sendEmailRegistrationErrorNotification(e: EmailException) {
        println("ExternalWebSocket 173 email registration error, send it")
        val message = SocketMessage()
        message.command = "email_registration_error"
        message.payload = e.message ?: "Неизвестная ошибка"
        sendInternalMessage(message)
    }

    private fun sendPatientPasswordChangedNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "patient_password_changed"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
    }

    private fun sendAuthorizationRequiredMessage(webSocket: WebSocket) {
        val message = SocketMessage()
        message.command = "authorization_required"
        message.payload = "Please, send message with command 'authorize' and access token in payload"
        val builder = GsonBuilder()
        val gson = builder.create()
        webSocket.send(gson.toJson(message, SocketMessage::class.java))
    }

    override fun onError(webSocket: WebSocket?, e: Exception) {
        e.printStackTrace()
        println("client error")
    }

    override fun onStart() {
        println("EXTERNAL SOCKET SERVER started!")
        connectionLostTimeout = 100
        println("ExternalWebSocket 47 ${address.hostString}")
    }

    fun sendForRegistered(message: String?) {
        println("ExternalWebSocket 49 send message for registered : $message")
        //broadcast(message)
    }

    fun launch() {
        // testing options
        //todo uncommit for wss
        /*val ks = KeyStore.getInstance("JKS")
        ks.load(Files.newInputStream(Paths.get("C:/cert/rdcnn.jks")), "123456".toCharArray())
        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(ks, "123456".toCharArray())
        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
        tmf.init(ks)
        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)
        setWebSocketFactory(DefaultSSLWebSocketServerFactory(sslContext))*/
        start()
    }

    fun sendConclusionRegistered(conclusion: Conclusion) {
        val message = SocketMessage()
        message.command = "conclusion_registered"
        message.payload = JsonHandler().toJson(conclusion)
        sendInternalMessage(message)
        val patient = Database.instance.getPatientByName(conclusion.executionNumber)
        sendMessageToPatient(patient, message)
    }

    private fun sendMessageToPatient(patient: Patient?, message: SocketMessage) {
        println("ExternalWebSocket 310 $patient")
        if (patient != null) {
            val jsonMessage = JsonHandler().toJson(message)
            authorizedClients.forEach { (socket, token) ->
                if (token == patient.accessToken) {
                    socket.send(jsonMessage)
                }
            }
        }
    }

    fun sendConclusionUpdated(conclusion: Conclusion) {
        val message = SocketMessage()
        message.command = "conclusion_updated"
        message.payload = JsonHandler().toJson(conclusion)
        sendInternalMessage(message)
        val patient = Database.instance.getPatientByName(conclusion.executionNumber)
        sendMessageToPatient(patient, message)
    }

    fun sendWrongDicomDirNotification(fileName: String) {
        val message = SocketMessage()
        message.command = "wrong_dicom_dir_received"
        message.payload = fileName
        sendInternalMessage(message)
    }

    fun sendDicomArchiveInfoUpdated(dicomArchive: DicomArchive) {
        val message = SocketMessage()
        message.command = "dicom_archive_updated"
        message.payload = JsonHandler().toJson(dicomArchive)
        sendInternalMessage(message)
    }

    fun sendDicomArchiveInfoRegistered(dicomArchive: DicomArchive) {
        val message = SocketMessage()
        message.command = "dicom_archive_registered"
        message.payload = JsonHandler().toJson(dicomArchive)
        sendInternalMessage(message)
        val patient = Database.instance.getPatientByName(dicomArchive.examinationId)
        sendMessageToPatient(patient, message)
    }

    private fun sendAccessDeniedMessage(webSocket: WebSocket) {
        val message = SocketMessage()
        message.command = "failed_authorization"
        message.payload = "Invalid access token"
        val builder = GsonBuilder()
        val gson = builder.create()
        webSocket.send(gson.toJson(message, SocketMessage::class.java))
    }


    private fun sendKickUnauthorizedClientNotification(socket: WebSocket) {
        val message = SocketMessage()
        message.command = "kick"
        message.payload = "You are not be authenticated with 10 seconds and will be kicked"
        val builder = GsonBuilder()
        val gson = builder.create()
        socket.send(gson.toJson(message, SocketMessage::class.java))
    }

    fun registerDelegate(delegate: WebSocketActionDelegate) {
        this.delegate = delegate
    }

    fun sendPatientRegisteredNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "patient_registered"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
    }

    private fun sendInternalMessage(message: SocketMessage) {
        val jsonMessage = JsonHandler().toJson(message)
        authorizedClients.forEach { (socket, token) ->
            if (token == PreferencesHandler.instance.getExternalServerToken()) {
                socket.send(jsonMessage)
            }
        }
    }

    fun sendPatientRegistrationFailedNotification(reason: String) {
        val message = SocketMessage()
        message.command = "patient_registration_failed"
        message.payload = reason
        sendInternalMessage(message)
    }

    fun sendPatientArchivedNotification(patient: Patient) {
        val message = SocketMessage()
        message.command = "patient_archived"
        message.payload = JsonHandler().toJson(patient)
        sendInternalMessage(message)
        sendMessageToPatient(patient, message)
    }

    fun sendConclusionAddError(reason: String) {
        val message = SocketMessage()
        message.command = "conclusion_add_error"
        message.payload = reason
        sendInternalMessage(message)
    }

    fun sendInvalidEmailRegistrationNotification(email: String, patient: Patient) {
        val message = SocketMessage()
        message.command = "invalid_email_notification"
        message.payload =
            "$email- не является правильным адресом электронной почты (обследование ${patient.examinationId})"
        sendInternalMessage(message)
    }

    fun sendEmailDeletedNotification(email: Email, patient: Patient) {
        val message = SocketMessage()
        message.command = "email_deleted_notification"
        message.payload =
            "Удалён адрес электронной почты ${email.address}, обследование ${patient.examinationId}"
        sendInternalMessage(message)
    }

    fun sendEmailAddNotification(newMail: Email, patient: Patient) {
        val message = SocketMessage()
        message.command = "email_add_notification"
        message.payload =
            "Добавлен адрес электронной почты ${newMail.address}, обследование ${patient.examinationId}"
        sendInternalMessage(message)
    }

    fun sendConclusionArchiveDuplicate(conclusion: Conclusion) {
        val message = SocketMessage()
        message.command = "archive_conclusion_duplicate_notification"
        message.payload =
            "Возникла ошибка при архивации обследования ${conclusion.executionNumber}. Заключение для ${conclusion.executionArea} уже находится в базе данных"
        sendInternalMessage(message)
    }

    companion object {
        val instance: ExternalWebSocket = ExternalWebSocket()
    }
}