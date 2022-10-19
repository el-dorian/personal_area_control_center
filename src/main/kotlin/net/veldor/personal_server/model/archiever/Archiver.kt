package net.veldor.personal_server.model.archiever

import javafx.concurrent.Task
import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.database.Database
import net.veldor.personal_server.model.handler.ConclusionHandler
import net.veldor.personal_server.model.handler.DicomHandler
import net.veldor.personal_server.model.handler.FirebaseHandler
import net.veldor.personal_server.model.selections.Patient
import java.util.*
import kotlin.collections.HashMap

class Archiver private constructor() {

    private val activeTimers: HashMap<Int, Timer> = hashMapOf()

    fun watch() {
        val task = object : Task<Void?>() {
            override fun call(): Void? {
                // get all active patients from database
                val patients = Database.instance.getActivePatients()
                // check if patients lifetime ends at this moment
                val currentTimestamp = (System.currentTimeMillis() / 1000).toInt()
                patients.forEach {
                    if (currentTimestamp > it.lifetimeEnd) {
                        archive(it)
                    } else {
                        ScheduledArchiver().plane(it)
                    }
                }
                return null
            }
        }
        Thread(task).start()
    }

    fun archive(patient: Patient) {
        println("Archiver 34 archive ${patient.examinationId}")
        val conclusions = Database.instance.getPatientConclusions(patient)
        if (conclusions.isNotEmpty()) {
            conclusions.forEach {
                ConclusionHandler().archiveConclusion(it)
            }
        }
        DicomHandler("${patient.examinationId}.zip").delete(patient)
        Database.instance.closePatient(patient)
        ExternalWebSocket.instance.sendPatientArchivedNotification(patient)
        FirebaseHandler.instance.sendExaminationOutOfDateNotification(patient)
        println("Archiver 46 archived ${patient.examinationId}")
        activeTimers.remove(patient.patientId)

    }

    fun register(patient: Patient) {
        val task = object : Task<Void?>() {
            override fun call(): Void? {
                ScheduledArchiver().plane(patient)
                return null
            }
        }
        Thread(task).start()
    }

    fun deletePatient(patientId: String): Patient? {
        val patient = Database.instance.getPatientById(patientId)
        if (patient != null) {
            println("Archiver 60 have patient")
            val conclusions = Database.instance.getPatientConclusions(patient)
            if (conclusions.isNotEmpty()) {
                conclusions.forEach {
                    ConclusionHandler().deleteConclusion(it)
                }
            }
            DicomHandler("${patient.examinationId}.zip").delete(patient)
            Database.instance.deletePatient(patient)
            return patient
        }
        return null
    }

    fun extendLifetime(examination: Patient) {
        activeTimers[examination.patientId]?.cancel()
        activeTimers.remove(examination.patientId)
        register(examination)
        println("Archiver 83 lifetime extended")
    }

    companion object {
        const val EXAMINATION_LIFETIME: Int = 432000
        val instance: Archiver = Archiver()
    }

    inner class ScheduledArchiver {
        fun plane(patient: Patient) {
            //todo вернуть нормальное время
            val timeToDelete = patient.lifetimeEnd.toLong() * 1000 - System.currentTimeMillis()
            println("Archiver 97 plane to delete ${patient.examinationId} in $timeToDelete")
            //val timeToDelete = 5000L
            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    archive(patient)
                    activeTimers.remove(patient.patientId)
                }
            }, timeToDelete)
            activeTimers[patient.patientId] = timer
        }

    }
}