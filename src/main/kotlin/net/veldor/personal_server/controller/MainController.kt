package net.veldor.personal_server.controller

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.stage.Stage
import net.veldor.personal_server.model.ExternalWebSocket
import net.veldor.personal_server.model.WebSocketActionDelegate
import net.veldor.personal_server.model.exception.WrongDirException
import net.veldor.personal_server.model.utils.PreferencesHandler
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.CONCLUSIONS_ARCHIVE_DIR_PATH
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.CONCLUSIONS_DESTINATION_DIR_PATH
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.CONCLUSIONS_DIR_PATH
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.DICOM_ADDITIONS_DIR_PATH
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.DICOM_DESTINATION_DIR_PATH
import net.veldor.personal_server.model.utils.PreferencesHandler.Companion.DICOM_DIR_PATH
import net.veldor.personal_server.model.view_model.MainControllerModel
import net.veldor.personal_server.model.watcher.Watcher
import java.io.IOException

class MainController : Controller, WebSocketActionDelegate {

    private lateinit var viewModel: MainControllerModel
    private lateinit var mStage: Stage

    @FXML
    lateinit var webSocketState: Label

    @FXML
    lateinit var errorState: Label

    @FXML
    lateinit var conclusionDirState: Label

    @FXML
    lateinit var mainState: Label

    override fun init(owner: Stage) {
        mStage = owner
        viewModel = MainControllerModel()
        mainState.text = "Запущено!"
        if (Watcher.instance.isWatchInProgress) {
            conclusionDirState.text = "Заключения отслеживаются"
        } else {
            conclusionDirState.text = "Заключения не отслеживаются"
        }
        ExternalWebSocket.instance.registerDelegate(this)
        checkDirFilling()
    }

    private fun checkDirFilling() {
        if (PreferencesHandler.instance.getDir(CONCLUSIONS_DIR_PATH) == null) {
            errorState.text = "Не выбрана транзитная папка заключений"
        } else if (PreferencesHandler.instance.getDir(CONCLUSIONS_DESTINATION_DIR_PATH) == null) {
            errorState.text = "Не выбрана конечная папка заключений"
        } else if (PreferencesHandler.instance.getDir(DICOM_DIR_PATH) == null) {
            errorState.text = "Не выбрана транзитная папка DICOM"
        } else if (PreferencesHandler.instance.getDir(DICOM_DESTINATION_DIR_PATH) == null) {
            errorState.text = "Не выбрана конечная папка DICOM"
        } else {
            errorState.text = ""
        }
    }

    fun selectConclusionDir() {
        selectDir(CONCLUSIONS_DIR_PATH)
    }

    fun selectConclusionDestinationDir() {
        selectDir(CONCLUSIONS_DESTINATION_DIR_PATH)
    }

    fun selectDicomDir() {
        selectDir(DICOM_DIR_PATH)
    }

    fun selectDicomDestinationDir() {
        selectDir(DICOM_DESTINATION_DIR_PATH)
    }

    fun selectDicomAdditionsDir() {
        selectDir(DICOM_ADDITIONS_DIR_PATH)
    }

    override fun clientConnected() {
        Platform.runLater {
            webSocketState.text = "Соединено клиентов: ${ExternalWebSocket.instance.clientsCount}"
        }
    }

    override fun clientDisconnected() {
        Platform.runLater {
            webSocketState.text = "Соединено клиентов: ${ExternalWebSocket.instance.clientsCount}"
        }
    }

    fun selectConclusionArchiveDir() {
        selectDir(CONCLUSIONS_ARCHIVE_DIR_PATH)
    }

    private fun selectDir(dirName: String) {
        try {
            if (viewModel.selectDir(mStage, dirName)) {
                try {
                    viewModel.createInfoWindow("Папка назначена", mStage)
                    checkDirFilling()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        } catch (e: WrongDirException) {
            e.printStackTrace()
            try {
                viewModel.createInfoWindow("Не удалось добавить папку, попробуйте ещё раз", mStage)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

}