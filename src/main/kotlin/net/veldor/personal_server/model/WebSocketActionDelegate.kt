package net.veldor.personal_server.model

interface WebSocketActionDelegate {
    fun clientConnected()
    fun clientDisconnected()
}