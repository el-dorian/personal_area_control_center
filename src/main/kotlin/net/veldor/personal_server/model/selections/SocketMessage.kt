package net.veldor.personal_server.model.selections

class SocketMessage {
    lateinit var command: String
    lateinit var payload: String
    var token: String? = null
}