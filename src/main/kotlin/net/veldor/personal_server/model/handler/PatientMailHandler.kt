package net.veldor.personal_server.model.handler

import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.selections.AddMailRequest
import net.veldor.personal_server.model.selections.Email
import net.veldor.personal_server.model.selections.Patient
import java.util.regex.Pattern.compile

class PatientMailHandler {

    fun editEmails(changeMailRequest: AddMailRequest, patient: Patient) {
        // get registered emails
        val existentEmails = Database.instance.getPatientEmails(changeMailRequest.patientId.toString())
        // parse new emails
        val emailsList = changeMailRequest.emailAddresses.split(Regex(" "))
        val newEmailAddresses = ArrayList<String>()
        emailsList.forEach {
            // handle only not empty strings
            if (it.trim().isNotEmpty()) {
                if (emailRegex.matcher(it.trim()).matches()) {
                    newEmailAddresses.add(it.trim())
                } else {
                    ExternalWebSocket.instance.sendInvalidEmailRegistrationNotification(it, patient)
                }
            }
        }
        if (existentEmails.isNotEmpty() && newEmailAddresses.isEmpty()) {
            // all emails removed, delete all existent
            existentEmails.forEach {
                Database.instance.deleteEmail(it)
                ExternalWebSocket.instance.sendEmailDeletedNotification(it, patient)
            }
            ExternalWebSocket.instance.sendEmailsDeletedNotification(patient)
        } else if (existentEmails.isEmpty() && newEmailAddresses.isNotEmpty()) {
            // simple add all emails
            newEmailAddresses.forEach {
                val newMail = Email(it, patient.patientId, null)
                Database.instance.registerEmail(newMail)
                ExternalWebSocket.instance.sendEmailAddNotification(newMail, patient)
            }
            ExternalWebSocket.instance.sendEmailsRegisteredNotification(patient)
        } else {
            // first time, remove addresses, which is not exists anymore
            if (existentEmails.isNotEmpty()) {
                existentEmails.forEach {
                    if (!newEmailAddresses.contains(it.address)) {
                        Database.instance.deleteEmail(it)
                        ExternalWebSocket.instance.sendEmailDeletedNotification(it, patient)
                    }
                }
            }
            // now, add addresses, which not in list yet
            newEmailAddresses.forEach { newAddress ->
                var addressExists = false
                existentEmails.forEach inner@{
                    if (it.address == newAddress) {
                        addressExists = true
                        return@inner
                    }
                }
                if (!addressExists) {
                    // add address
                    val newEmail = Email(
                        address = newAddress,
                        examinationId = changeMailRequest.patientId,
                        null
                    )
                    Database.instance.registerEmail(newEmail)
                    ExternalWebSocket.instance.sendEmailAddNotification(newEmail, patient)
                }
            }
        }
    }

    private val emailRegex = compile(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
                "@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )
}