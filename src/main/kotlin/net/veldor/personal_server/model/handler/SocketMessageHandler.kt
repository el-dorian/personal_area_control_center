package net.veldor.personal_server.model.handler

import com.google.gson.GsonBuilder
import net.veldor.personal_server.model.selections.SocketMessage


class SocketMessageHandler {
    fun handle(request: String): String {
        val builder = GsonBuilder()
        val gson = builder.create()
        val response: SocketMessage = gson.fromJson(request, SocketMessage::class.java)
        when (response.command) {
            COMMAND_AUTH -> {
                // проверяю токен аутентификации и выдаю токен доступа
            }
        }
        return ""
    }

    fun parseMessage(rawMessage: String): SocketMessage {
        val builder = GsonBuilder()
        val gson = builder.create()
        return gson.fromJson(rawMessage, SocketMessage::class.java)
    }

    companion object {
        const val COMMAND_AUTH = "auth"
    }
}