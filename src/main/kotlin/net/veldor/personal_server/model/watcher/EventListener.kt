package net.veldor.personal_server.model

import net.veldor.personal_server.model.watcher.FileEvent
import java.util.*

interface FileListener : EventListener {
    fun onCreated(event: FileEvent?)
    fun onModified(event: FileEvent?)
    fun onDeleted(event: FileEvent?)
}