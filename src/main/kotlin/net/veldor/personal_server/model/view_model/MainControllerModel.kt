package net.veldor.personal_server.model.view_model

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Modality
import javafx.stage.Stage
import net.veldor.personal_server.MyApplication
import net.veldor.personal_server.controller.InfoController
import net.veldor.personal_server.model.utils.FileUtils
import java.io.IOException

class MainControllerModel {
    fun selectDir(stage: Stage, dirType: String): Boolean {
        return FileUtils().changeDir(stage, dirType)
    }

    @Throws(IOException::class)
    fun createInfoWindow(message: String?, owner: Stage?) {
        val stage = Stage()
        val loader = FXMLLoader(MyApplication::class.java.getResource("info_window.fxml"))
        val root = loader.load<Parent>()
        stage.title = "Информация"
        stage.scene = Scene(root, 400.0, 100.0)
        if (owner != null) {
            stage.initModality(Modality.WINDOW_MODAL)
            stage.initOwner(owner)
        }
        val controller: InfoController = loader.getController()
        controller.init(stage)
        controller.setMessage(message)
        stage.show()
    }
}