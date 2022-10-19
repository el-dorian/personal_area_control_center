package net.veldor.personal_server.model.selections

data class Patient(val patientId: Int, val examinationId: String, var lifetimeEnd: Int, val password: String?, val accessToken: String?)