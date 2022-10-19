package net.veldor.personal_server.model.selections

data class ArchiveConclusion(
    val execution_identifier: String,
    val doctor: String,
    val pdf_path: String,
    val patient_birthdate: String,
    val execution_date: String,
    val execution_area: String,
    val patient_name: String,
    val execution_number: String,
    val contrast_info: String,
)
