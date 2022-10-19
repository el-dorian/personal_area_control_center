package net.veldor.personal_server.model.utils

import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import net.veldor.personal_server.controller.MainController
import net.veldor.personal_server.model.exception.WrongDirException
import org.zeroturnaround.zip.commons.FileUtils
import java.io.File

class FileUtils {


    @Throws(WrongDirException::class)
    fun changeDir(owner: Stage?, dirType: String): Boolean {
        val directoryChooser = DirectoryChooser()
        val initialDir = PreferencesHandler.instance.getInitialDir(dirType)
        if (initialDir != null && initialDir.isDirectory) {
            directoryChooser.initialDirectory = initialDir
        }
        val selectedDirectory = directoryChooser.showDialog(owner)
        if (selectedDirectory != null) {
            PreferencesHandler.instance.setDir(dirType, selectedDirectory)
            return true
        }
        return false
    }

    fun getConclusionDestinationFileName(conclusionFileName: String): File? {
        val conclusionDir = PreferencesHandler.instance.getDir(PreferencesHandler.CONCLUSIONS_DESTINATION_DIR_PATH)
        if (conclusionDir?.isDirectory == true) {
            val subDirectory = if (conclusionFileName.startsWith("T")) {
                File(conclusionDir, "КТ")
            } else if (conclusionFileName.startsWith("A")) {
                File(conclusionDir, "Аврора")
            } else {
                File(conclusionDir, "НВН")
            }
            if(!subDirectory.exists()){
                if(!subDirectory.mkdirs()){
                    println("FileUtils 42 не удалось создать папку для заключений")
                    return null
                }
            }
            return File(subDirectory, conclusionFileName)
        }
        return null
    }

    companion object {
        fun readFile(dicomdirFile: File): String {
            return FileUtils.readFileToString(dicomdirFile, "UTF-8")
        }
    }

}