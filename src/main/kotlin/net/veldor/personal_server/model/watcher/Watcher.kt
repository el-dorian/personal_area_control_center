package net.veldor.personal_server.model.watcher

import javafx.concurrent.Task
import net.veldor.personal_server.controller.MainController
import net.veldor.personal_server.model.handler.ConclusionHandler
import net.veldor.personal_server.model.utils.PreferencesHandler
import java.io.File

class Watcher private constructor() {

    private var mCurrentWatcher: FileWatcher? = null
    private var mCurrentTask: Task<Void?>? = null
    var isWatchInProgress = false
    private val fileList: HashMap<File, String> = hashMapOf()

    fun watch() {
        val conclusionSourceDir = PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DIR_PATH)
        if (conclusionSourceDir != null && conclusionSourceDir.isDirectory) {
            // для каждого файла в папке запущу процесс обработки
            conclusionSourceDir.listFiles()?.forEach {
                if (it.isFile) {
                    val handlerTask = object : Task<Void?>() {
                        override fun call(): Void? {
                            ConclusionHandler().handle(it)
                            return null
                        }
                    }
                    Thread(handlerTask).start()
                }
            }

            // запущу отслеживание папки в отдельном потоке
            mCurrentTask = object : Task<Void?>() {
                override fun call(): Void? {
                    mCurrentWatcher = FileWatcher(conclusionSourceDir)
                    mCurrentWatcher?.addListener(object : FileAdapter() {
                        override fun onCreated(event: FileEvent?) {
                            // put file to list
                            if (event != null) {
                                fileList[event.file] = "created"
                            }
                        }

                        override fun onModified(event: FileEvent?) {
                            if (event != null) {
                                if (fileList[event.file] == "created") {
                                    fileList[event.file] = "handled"
                                    val handlerTask = object : Task<Void?>() {
                                        override fun call(): Void? {
                                            ConclusionHandler().handle(event.file)
                                            return null
                                        }
                                    }
                                    Thread(handlerTask).start()
                                }
                            }
                        }

                        override fun onDeleted(event: FileEvent?) {
                            if (event != null) {
                                fileList.remove(event.file)
                            }
                        }
                    })?.watch()
                    return null
                }
            }
            Thread(mCurrentTask).start()
            isWatchInProgress = true
        } else {
            println("Watcher 66 conclusion dir is not a dir")
            isWatchInProgress = false
        }
    }

    fun refreshWatch() {
        mCurrentTask?.cancel(true)
        mCurrentWatcher?.cancel()
        instance.watch()
    }

    companion object {
        val instance: Watcher = Watcher()
    }
}