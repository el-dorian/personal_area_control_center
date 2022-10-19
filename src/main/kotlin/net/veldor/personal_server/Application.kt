package net.veldor.personal_server

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage
import net.veldor.personal_server.controller.MainController
import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.archiever.Archiver
import net.veldor.personal_server.model.utils.UpdateChecker
import net.veldor.personal_server.model.watcher.Watcher
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager


class MyApplication : Application() {

    override fun start(stage: Stage) {

        // check updates
        UpdateChecker.instance.startCheck()
        // launch websocket
        ExternalWebSocket.instance.launch()
        // launch conclusions dir listener
        Watcher.instance.watch()
        Archiver.instance.watch()

        val fxmlLoader = FXMLLoader(MyApplication::class.java.getResource("main-view.fxml"))
        val scene = Scene(fxmlLoader.load(), 600.0, 400.0)
        stage.title = "Hello!"
        stage.scene = scene
        stage.show()
        val controller: MainController = fxmlLoader.getController()
        controller.init(stage)
    }
}

fun main() {
    // disable loggers
    LogManager.getRootLogger().atLevel(Level.OFF)

    Application.launch(MyApplication::class.java)
}