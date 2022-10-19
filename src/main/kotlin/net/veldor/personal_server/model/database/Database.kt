package net.veldor.personal_server.model.database

import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.archiever.Archiver
import net.veldor.personal_server.model.exception.DicomNotRegisteredException
import net.veldor.personal_server.model.selections.DicomArchive
import net.veldor.personal_server.model.selections.Email
import net.veldor.personal_server.model.selections.Patient
import net.veldor.personal_server.model.selections.Review
import net.veldor.personal_server.model.utils.PreferencesHandler
import org.apache.commons.lang.StringEscapeUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.security.InvalidParameterException
import java.sql.*
import java.util.*
import kotlin.collections.ArrayList


class Database {

    @Throws(SQLException::class)
    private fun getStatement(): Statement {
        return mConnection!!.createStatement()
    }

    @Throws(SQLException::class)
    private fun executeQuery(query: String): ResultSet {
        val statement = getStatement()
        return statement.executeQuery(query)
    }

    @Throws(SQLException::class)
    private fun executeUpdateQuery(query: String) {
        val statement = getStatement()
        statement.executeUpdate(query)
    }


    fun executionRegistered(executionName: String): Boolean {
        // проверю, зарегистрирован ли в базе файл, ориентируясь на путь к файлу и дату его изменения
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`person` WHERE rdcnn.`person`.`username` = '%s';",
            executionName
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun conclusionRegistered(conclusion: Conclusion): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`conclusion` WHERE rdcnn.`conclusion`.`execution_number` = '%s' AND rdcnn.`conclusion`.`execution_area` = '%s';",
            conclusion.executionNumber,
            conclusion.executionArea
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun registerConclusion(conclusion: Conclusion) {
        var query = java.lang.String.format(
            Locale.ENGLISH,
            "INSERT INTO rdcnn.`conclusion` (" +
                    "`execution_number`," +
                    " `execution_date`," +
                    " `diagnostician`," +
                    " `conclusion_text`," +
                    " `patient_name`," +
                    " `patient_sex`," +
                    " `patient_birthdate`," +
                    " `execution_area`," +
                    " `contrast_info`," +
                    " `path_to_file`," +
                    " `hash`" +
                    ") VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
            conclusion.executionNumber,
            conclusion.executionDate,
            conclusion.diagnostician,
            StringEscapeUtils.escapeHtml(conclusion.conclusionText),
            conclusion.patientName,
            conclusion.patientSex,
            conclusion.patientBirthdate,
            conclusion.executionArea,
            conclusion.contrastInfo,
            conclusion.filePath,
            conclusion.hash
        )
        executeUpdateQuery(query)
        // fill conclusion id
        query = String.format(
            Locale.ENGLISH,
            "SELECT id FROM rdcnn.`conclusion` WHERE rdcnn.`conclusion`.`execution_number` = '%s' AND rdcnn.`conclusion`.`execution_area` = '%s';",
            conclusion.executionNumber,
            conclusion.executionArea
        )
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            conclusion.conclusionId = result.getInt("id")
        } else {
            throw InvalidParameterException()
        }
    }

    fun getPreviousVersion(conclusion: Conclusion): Conclusion? {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT * FROM rdcnn.`conclusion` WHERE rdcnn.`conclusion`.`execution_number` = '%s' AND rdcnn.`conclusion`.`execution_area` = '%s';",
            conclusion.executionNumber,
            conclusion.executionArea
        )
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return conclusion(result)
        }
        return null
    }

    private fun conclusion(result: ResultSet): Conclusion {
        val oldConclusion = Conclusion()
        oldConclusion.conclusionId = result.getInt("id")
        oldConclusion.executionNumber = result.getString("execution_number")
        oldConclusion.executionDate = result.getString("execution_date")
        oldConclusion.diagnostician = result.getString("diagnostician")
        oldConclusion.conclusionText = result.getString("conclusion_text")
        oldConclusion.patientName = result.getString("patient_name")
        oldConclusion.patientSex = result.getString("patient_sex")
        oldConclusion.executionArea = result.getString("execution_area")
        oldConclusion.contrastInfo = result.getString("contrast_info")
        oldConclusion.filePath = result.getString("path_to_file")
        oldConclusion.hash = result.getString("hash")
        return oldConclusion
    }

    fun updateConclusion(conclusion: Conclusion) {
        val request = "UPDATE rdcnn.`conclusion` SET" +
                " `execution_number` = \'${conclusion.executionNumber}\' ," +
                " `execution_date` = \'${conclusion.executionDate}\' ," +
                " `diagnostician` = \'${conclusion.diagnostician}\' ," +
                " `conclusion_text` = \'${StringEscapeUtils.escapeSql(conclusion.conclusionText)}\' ," +
                " `patient_name` = \'${conclusion.patientName}\' ," +
                " `patient_sex` = \'${conclusion.patientSex}\' ," +
                " `execution_area` = \'${conclusion.executionArea}\' ," +
                " `contrast_info` = \'${conclusion.contrastInfo}\' ," +
                " `path_to_file` = \'${conclusion.filePath}\' ," +
                " `hash` = \'${conclusion.hash}\' " +
                "WHERE `id` = \'${conclusion.conclusionId}\';"
        executeUpdateQuery(request)
    }

    fun registerDicomArchive(destination: File, executionId: String, hash: String): DicomArchive {
        var query = java.lang.String.format(
            Locale.ENGLISH,
            "INSERT INTO rdcnn.`execution` (" +
                    "`execution_number`," +
                    " `path_to_file`," +
                    " `hash`" +
                    ") VALUES ('%s', '%s', '%s');",
            executionId,
            destination.absolutePath.replace(
                PreferencesHandler.instance.getDir(PreferencesHandler.DICOM_DESTINATION_DIR_PATH)!!.absolutePath,
                ""
            ).replace("\\", "\\\\"),
            hash
        )
        executeUpdateQuery(query)
        query = "SELECT * FROM rdcnn.`execution` WHERE rdcnn.`execution`.`execution_number` = '$executionId';"
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return DicomArchive(
                archiveId = result.getInt("id"),
                examinationId = executionId,
                pathToFile = result.getString("path_to_file"),
                hash = result.getString("hash")
            )
        }
        throw ClassNotFoundException("В базе данных не найдены сведения об архиве заключения")
    }

    fun dicomArchiveRegistered(executionId: String): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`execution` WHERE rdcnn.`execution`.`execution_number` = '%s';",
            executionId
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun updateDicomArchiveInfo(executionId: String, hash: String): DicomArchive {
        // get execution id
        val query = String.format(
            Locale.ENGLISH,
            "SELECT * FROM rdcnn.`execution` WHERE rdcnn.`execution`.`execution_number` = '%s';",
            executionId
        )
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            val id = result.getInt("id")
            val request = "UPDATE rdcnn.`execution` SET" +
                    " `hash` = \"$hash\" " +
                    "WHERE `id` = \"$id\";"
            executeUpdateQuery(request)
            return DicomArchive(
                archiveId = result.getInt("id"),
                examinationId = result.getString("execution_number"),
                pathToFile = result.getString("path_to_file"),
                hash = result.getString("hash")
            )
        }
        throw DicomNotRegisteredException()
    }

    fun dicomArchivesIdentical(executionId: String, hash: String): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT hash FROM rdcnn.`execution` WHERE rdcnn.`execution`.`execution_number` = '%s';",
            executionId
        )
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            println("Database 197 current hash is $hash, previous is ${result.getString("hash")}")
            return result.getString("hash") == hash
        }
        return false
    }

    fun checkAuthorizationToken(token: String): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`person` WHERE rdcnn.`person`.`access_token` = '%s';",
            token
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun getActivePatients(): ArrayList<Patient> {
        val query = "SELECT * FROM rdcnn.`person` WHERE rdcnn.`person`.`status` = '1';"
        val result: ResultSet = executeQuery(query)
        val patients: ArrayList<Patient> = arrayListOf()
        var currentPatient: Patient
        while (result.next()) {
            currentPatient = Patient(
                patientId = result.getInt("id"),
                examinationId = result.getString("username"),
                lifetimeEnd = result.getInt("updated_at") + Archiver.EXAMINATION_LIFETIME,
                password = null,
                result.getString("access_token")
            )
            patients.add(currentPatient)
        }
        return patients
    }

    fun closePatient(patient: Patient) {
        val request = "UPDATE rdcnn.`person` SET `status` = 2 WHERE `id` = \"${patient.patientId}\";"
        executeUpdateQuery(request)
    }

    fun deleteDicomArchive(patient: Patient) {
        var request = "DELETE FROM rdcnn.`execution` WHERE `execution_number` = \"${patient.examinationId}\";"
        executeUpdateQuery(request)

        request =
            "DELETE FROM rdcnn.`temp_download_links` WHERE `file_type` = \"execution\" AND `execution_number` = \"${patient.patientId}\";"
        executeUpdateQuery(request)
    }


    fun deleteDicomArchive(dicomArchive: DicomArchive) {
        val request = "DELETE FROM rdcnn.`execution` WHERE `id` = \"${dicomArchive.archiveId}\";"
        executeUpdateQuery(request)
    }

    fun getPatientConclusions(patient: Patient): ArrayList<Conclusion> {
        val answer: ArrayList<Conclusion> = arrayListOf()
        val query =
            "SELECT * FROM rdcnn.`conclusion` WHERE rdcnn.`conclusion`.`execution_number` = '${patient.examinationId}';"
        val result: ResultSet = executeQuery(query)
        while (result.next()) {
            answer.add(conclusion(result))
        }
        return answer
    }

    fun deleteConclusion(conclusion: Conclusion) {
        var request =
            "DELETE FROM rdcnn.`conclusion` WHERE `execution_number` = \"${conclusion.executionNumber}\" AND `execution_area` = \"${conclusion.executionArea}\";"
        executeUpdateQuery(request)
        // delete conclusion links
        request =
            "DELETE FROM rdcnn.`temp_download_links` WHERE `file_type` = \"conclusion\" AND `file_name` = \"${conclusion.conclusionId}\";"
        executeUpdateQuery(request)
    }

    fun getPatientById(patientId: String): Patient? {
        val query =
            "SELECT * FROM rdcnn.`person` WHERE rdcnn.`person`.`id` = $patientId;"
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return Patient(
                patientId = result.getInt("id"),
                examinationId = result.getString("username"),
                lifetimeEnd = result.getInt("updated_at") + Archiver.EXAMINATION_LIFETIME,
                password = null,
                result.getString("access_token")
            )
        }
        return null
    }

    fun getPatientByName(patientId: String): Patient? {
        val query =
            "SELECT * FROM rdcnn.`person` WHERE rdcnn.`person`.`username` = \"$patientId\";"
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return Patient(
                patientId = result.getInt("id"),
                examinationId = result.getString("username"),
                lifetimeEnd = result.getInt("updated_at") + Archiver.EXAMINATION_LIFETIME,
                password = null,
                result.getString("access_token")
            )
        }
        return null
    }

    fun deletePatient(patient: Patient) {
        val request =
            "DELETE FROM rdcnn.`person` WHERE `id` = \"${patient.patientId}\";"
        executeUpdateQuery(request)
    }

    fun getConclusion(conclusionId: String): Conclusion? {
        val query =
            "SELECT * FROM rdcnn.`conclusion` WHERE rdcnn.`conclusion`.`id` = '${conclusionId}';"
        val result: ResultSet = executeQuery(query)
        while (result.next()) {
            return conclusion(result)
        }
        return null
    }

    fun getDicomArchiveById(id: String): DicomArchive {
        val query =
            "SELECT * FROM rdcnn.`execution` WHERE rdcnn.`execution`.`id` = '${id}';"
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return DicomArchive(
                archiveId = result.getInt("id"),
                examinationId = result.getString("execution_number"),
                pathToFile = result.getString("path_to_file"),
                hash = result.getString("hash")
            )
        }
        throw ClassNotFoundException("В базе данных не найдены сведения об архиве заключения")
    }

    fun haveEmails(patientId: Int): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`mailing` WHERE rdcnn.`mailing`.`patient_id` = '%s';",
            patientId,
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun patientExists(executionId: Int): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`person` WHERE rdcnn.`person`.`id` = '%d';",
            executionId,
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun isEmailExists(email: Email): Boolean {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT COUNT(id) FROM rdcnn.`mailing` WHERE rdcnn.`mailing`.`address` = '%s' AND rdcnn.`mailing`.`patient_id` = '%d';",
            email.address,
            email.examinationId,
        )
        val result: ResultSet = executeQuery(query)
        return if (result.next()) {
            result.getInt(1) > 0
        } else false
    }

    fun registerEmail(emailForRegistration: Email): Boolean {
        val query = java.lang.String.format(
            Locale.ENGLISH,
            "INSERT INTO rdcnn.`mailing` (" +
                    "`address`," +
                    " `patient_id`," +
                    " `mailed_yet`" +
                    ") VALUES ('%s', '%d', '%d');",
            emailForRegistration.address,
            emailForRegistration.examinationId,
            0
        )
        executeUpdateQuery(query)
        return isEmailExists(emailForRegistration)
    }

    fun getPatientEmails(patientId: String): ArrayList<Email> {
        val query = String.format(
            Locale.ENGLISH,
            "SELECT * FROM rdcnn.`mailing` WHERE rdcnn.`mailing`.`patient_id` = '%s';",
            patientId,
        )
        val result: ResultSet = executeQuery(query)
        val answer = ArrayList<Email>()
        while (result.next()) {
            answer.add(
                Email(
                    address = result.getString("address"),
                    examinationId = result.getInt("patient_id"),
                    id = result.getInt("id")
                )
            )
        }
        return answer;
    }

    fun deleteEmail(email: Email) {
        val request =
            "DELETE FROM rdcnn.`mailing` WHERE `id` = \"${email.id}\";"
        executeUpdateQuery(request)
    }

    fun getPatientByToken(token: String): Patient? {
        val query =
            "SELECT * FROM rdcnn.`person` WHERE rdcnn.`person`.`access_token` = '$token';"
        val result: ResultSet = executeQuery(query)
        if (result.next()) {
            return Patient(
                patientId = result.getInt("id"),
                examinationId = result.getString("username"),
                lifetimeEnd = result.getInt("updated_at") + Archiver.EXAMINATION_LIFETIME,
                password = null,
                result.getString("access_token")
            )
        }
        return null
    }

    fun extendLifetime(examination: Patient) {
        val request = "UPDATE rdcnn.`person` SET" +
                " `updated_at` = \'${examination.lifetimeEnd}\' " +
                "WHERE `id` = \'${examination.patientId}\';"

        executeUpdateQuery(request)
        examination.lifetimeEnd += Archiver.EXAMINATION_LIFETIME
    }

    fun saveReview(examination: Patient, review: Review) {
        val query = java.lang.String.format(
            Locale.ENGLISH,
            "INSERT INTO rdcnn.`reviews` (" +
                    "`patient_id`," +
                    " `rate`," +
                    " `review`" +
                    ") VALUES ('%s', '%d', '%s');",
            examination.patientId,
            review.starring,
            review.text
        )
        executeUpdateQuery(query)
    }

    private var mConnection: Connection?
    private lateinit var password: String
    private lateinit var user: String
    private lateinit var url: String

    init {
        // подключусь к базе данных
        // opening database connection to MySQL server
        // read db data from file
        val dbSettingsFile = File("db.conf")
        if (dbSettingsFile.isFile) {
            try {
                BufferedReader(FileReader(dbSettingsFile)).use { br ->
                    var line: String?
                    var lineCount = 0
                    while (br.readLine().also { line = it } != null) {
                        if (lineCount == 0) {
                            // this is db address
                            url = line!!
                        } else if (lineCount == 1) {
                            // this is db address
                            user = line!!
                        } else if (lineCount == 2) {
                            // this is db address
                            password = line!!
                        }
                        lineCount++
                    }
                }
            } catch (t: Throwable) {
                println("Database 69 i have error!! ${t.message}")
            }
        } else {
            val create = dbSettingsFile.createNewFile()
            println(create)
            throw Exception("Нет настроек дб!")
        }
        mConnection = DriverManager.getConnection(url, user, password)
    }

    companion object {
        var instance: Database = Database()
    }
}