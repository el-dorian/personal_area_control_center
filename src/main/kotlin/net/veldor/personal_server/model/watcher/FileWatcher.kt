package net.veldor.personal_server.model.watcher

import net.veldor.personal_server.model.FileListener
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*


class FileWatcher(private val folder: File) : Runnable {

    private var watcherThread: Thread? = null
    private var poll: Boolean = false
    private var listeners: ArrayList<FileListener> = arrayListOf()

    fun watch() {
        if (folder.exists()) {
            watcherThread = Thread(this)
            watcherThread?.isDaemon = true
            watcherThread?.start()
        }
    }

    override fun run() {
        try {
            FileSystems.getDefault().newWatchService().use { watchService ->
                val path = Paths.get(folder.absolutePath)
                path.register(
                    watchService,
                    ENTRY_CREATE,
                    ENTRY_MODIFY,
                    ENTRY_DELETE
                )
                poll = true
                while (poll) {
                    poll = pollEvents(watchService)
                }
            }
        } catch (e: IOException) {
            Thread.currentThread().interrupt()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: ClosedWatchServiceException) {
            Thread.currentThread().interrupt()
        }
    }

    @Throws(InterruptedException::class)
    protected fun pollEvents(watchService: WatchService): Boolean {
        val key = watchService.take()
        val path = key.watchable() as Path
        for (event in key.pollEvents()) {
            notifyListeners(event.kind(), path.resolve(event.context() as Path).toFile())
        }
        return key.reset()
    }

    protected fun notifyListeners(kind: WatchEvent.Kind<*>, file: File) {
        val event = FileEvent(file)
        if (kind === ENTRY_CREATE) {
            for (listener in listeners) {
                listener.onCreated(event)
            }
        } else if (kind === ENTRY_MODIFY) {
            for (listener in listeners) {
                listener.onModified(event)
            }
        } else if (kind === ENTRY_DELETE) {
            for (listener in listeners) {
                listener.onDeleted(event)
            }
        }
    }

    fun addListener(listener: FileListener): FileWatcher {
        listeners.add(listener)
        return this
    }

    fun removeListener(listener: FileListener): FileWatcher {
        listeners.remove(listener)
        return this
    }

    fun setListeners(listeners: ArrayList<FileListener>): FileWatcher {
        this.listeners = listeners
        return this
    }

    fun cancel() {
        watcherThread?.interrupt()
    }
}