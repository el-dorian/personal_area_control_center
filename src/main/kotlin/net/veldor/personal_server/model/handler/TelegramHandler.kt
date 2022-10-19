package net.veldor.personal_server.model.handler

import net.veldor.pdf_parser.model.selection.Conclusion
import net.veldor.personal_server.model.selections.SocketMessage
import org.java_websocket.WebSocket

class TelegramHandler {
    fun sendFailedAuthorizationDebug(webSocket: WebSocket, payload: String) {
        println("TelegramHandler 7 i sent telegram debug")
    }

    fun sendUnauthorizedAccessDebug(webSocket: WebSocket, message: SocketMessage) {
        println("TelegramHandler 12 try to unauthorized access with command ${message.command} and payload ${message.payload}")
    }

    fun sendUnsuccessfulFileDeleteDebug(path: String) {
        println("TelegramHandler 16 telegram send file not deleted debug")
    }

    fun sendUnsuccessfulArchiveConclusionDebug(conclusion: Conclusion, t: Throwable) {
        println("TelegramHandler 21 send can't archive conclusion ${conclusion.executionNumber} ${conclusion.executionArea} for ${t.message}")
    }

    fun sendUnsuccessfulArchiveConclusionDebug(conclusion: Conclusion) {
        println("TelegramHandler 21 send can't archive conclusion ${conclusion.executionNumber} ${conclusion.executionArea}")
    }

    fun sendUnsuccessfulDeleteConclusionDebug(conclusion: Conclusion) {
        println("TelegramHandler 21 send can't delete conclusion ${conclusion.executionNumber} ${conclusion.executionArea}")
    }
}