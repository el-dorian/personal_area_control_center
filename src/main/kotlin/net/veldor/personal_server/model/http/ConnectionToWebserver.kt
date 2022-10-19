package net.veldor.personal_server.model.http

import net.veldor.personal_server.model.exception.HttpConnectionException
import net.veldor.personal_server.model.selections.ArchiveConclusion
import net.veldor.personal_server.model.selections.Email
import net.veldor.personal_server.model.selections.Patient
import net.veldor.personal_server.model.utils.PreferencesHandler
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ConnectionToWebserver {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0"

        //todo fix url
        private const val EXTERNAL_SERVER_URL = "https://rdcnn.my/api/external"

    }

    fun registerExecution(executionNumber: String): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "register_execution")
        requestBody.put("execution_number", executionNumber)
        return silentSendWithAnswer(requestBody)
    }


    fun checkArchiveConclusions(patient: Patient): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "check_archive_conclusions")
        requestBody.put("execution_number", patient.examinationId)
        return silentSendWithAnswer(requestBody)
    }


    fun registerNextExecution(center: String): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "register_next_execution")
        requestBody.put("center", center)
        return silentSendWithAnswer(requestBody)
    }

    fun changeUserPassword(executionNumber: String): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "change_patient_password")
        requestBody.put("execution_number", executionNumber)
        return silentSendWithAnswer(requestBody)
    }


    fun requestSendMail(email: Email): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "send_mail")
        requestBody.put("email_id", email.id)
        return silentSendWithAnswer(requestBody)
    }


    fun retrieveArchiveConclusion(archiveConclusion: ArchiveConclusion): String {
        val requestBody = JSONObject()
        requestBody.put("cmd", "retrieve_archive_conclusion")
        requestBody.put("archive_conclusion_id", archiveConclusion.execution_identifier)
        return silentSendWithAnswer(requestBody)
    }

    private fun silentSendWithAnswer(requestBody: JSONObject): String {
        requestBody.put("token", PreferencesHandler.instance.getExternalServerToken())
        val message = requestBody.toString()
        var failedTryCounter = 0
        while (true) {
            try {
                val answer: String? = sendPost(message)
                if (!answer.isNullOrEmpty()) {
                    val answerData = JSONObject(answer)
                    if (!answerData.isNull("status") && answerData.getBoolean("status")) {
                        return answerData.toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (failedTryCounter > 20) {
                break
            }
            ++failedTryCounter
            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        throw HttpConnectionException("Не удалось зарегистрировать нового пациента")
    }

    /**
     * Буду отправлять данные на сервер, пока не отправлю, с задержкой в 5 секунд при неудачной попытке 10 раз
     * @param requestBody Тело запроса
     */
    private fun silentSend(requestBody: JSONObject): Boolean {
        try {
            silentSendWithAnswer(requestBody)
            return true
        } catch (_: Throwable) {
        }
        return false
    }

    @Throws(java.lang.Exception::class)
    fun sendPost(message: String): String? {
        val obj = URL(EXTERNAL_SERVER_URL)
        val con = obj.openConnection() as HttpsURLConnection

        //add request header
        con.requestMethod = "POST"
        con.setRequestProperty("User-Agent", USER_AGENT)
        con.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.5")
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

        // Send post request
        con.doOutput = true
        val wr = DataOutputStream(con.outputStream)
        wr.writeBytes(message)
        wr.flush()
        wr.close()
        val responseCode = con.responseCode
        if (responseCode == 200) {
            val `in` = BufferedReader(
                InputStreamReader(con.inputStream)
            )
            var inputLine: String?
            val response = StringBuilder()
            while (`in`.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            `in`.close()
            return response.toString()
        }
        return null
    }

}
