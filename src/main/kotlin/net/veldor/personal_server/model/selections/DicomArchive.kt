package net.veldor.personal_server.model.selections

data class DicomArchive (val archiveId: Int, val examinationId: String, val pathToFile: String, val hash: String)