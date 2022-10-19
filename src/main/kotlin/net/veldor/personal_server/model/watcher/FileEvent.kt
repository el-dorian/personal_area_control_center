package net.veldor.personal_server.model.watcher

import java.io.File
import java.util.*


class FileEvent(file: File?) : EventObject(file) {
    val file: File
        get() = getSource() as File
}