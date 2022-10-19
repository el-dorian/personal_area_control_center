package net.veldor.personal_server.model.exception

class EmailException(message: String?) : Exception(message) {
    constructor() : this(message = null)
}