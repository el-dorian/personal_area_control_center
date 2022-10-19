package net.veldor.personal_server.model.exception

class DicomNotRegisteredException(message: String?) : Exception(message) {
    constructor() : this(message = null)
}