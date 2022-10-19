package net.veldor.personal_server.model.handler

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.selections.*
import kotlin.collections.ArrayList

class JsonHandler {
    private val gsonWithExclusion: Gson
        get() {
            return GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create()
        }

    private val gson: Gson
        get() {
            return GsonBuilder().create()
        }

    fun parseHttpMessage(stringContent: String): HttpMessage {
        return gson.fromJson(stringContent, HttpMessage::class.java)
    }

    fun parsePatientData(stringContent: String?): Patient? {
        if (stringContent != null) {
            return gson.fromJson(stringContent, Patient::class.java)
        }
        return null
    }

    fun toJson(message: SocketMessage): String {
        return gson.toJson(message, SocketMessage::class.java)
    }

    fun toJson(message: Patient): String {
        return gson.toJson(message, Patient::class.java)
    }

    fun toJson(conclusion: Conclusion): String {
        return gsonWithExclusion.toJson(conclusion, Conclusion::class.java)
    }

    fun toJson(dicomArchive: DicomArchive): String {
        return gson.toJson(dicomArchive, DicomArchive::class.java)
    }

    fun parseAddMailRequest(raw: String): AddMailRequest {
        return gson.fromJson(raw, AddMailRequest::class.java)
    }

    fun toJson(registeredEmails: ArrayList<Email>): String {
        return gson.toJson(registeredEmails)
    }

    fun parseArchiveConclusions(answer: String): ArchiveConclusions {
        return gson.fromJson(answer, ArchiveConclusions::class.java)
    }

    fun parseConclusion(raw: String?): Conclusion? {
        if (raw != null) {
            return gson.fromJson(raw, Conclusion::class.java)
        }
        return null
    }

    fun parseReview(raw: String): Review {
        return gson.fromJson(raw, Review::class.java)
    }

    fun toJson(mail: Email): String {
        return gson.toJson(mail, Email::class.java)
    }
}