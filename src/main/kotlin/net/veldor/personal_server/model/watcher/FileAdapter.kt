package net.veldor.personal_server.model.watcher

import net.veldor.personal_server.model.FileListener

abstract class FileAdapter : FileListener {
    override fun onCreated(event: FileEvent?) {
        //реализация не предусмотрена
    }

    override fun onModified(event: FileEvent?) {
        //реализация не предусмотрена
    }

    override fun onDeleted(event: FileEvent?) {
        //реализация не предусмотрена
    }
}