package net.veldor.personal_server.controller

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Stage

class InfoController : Controller {
    @FXML
    var infoContainer: Label? = null

    @FXML
    var confirmBtn: Button? = null
    private var mOwner: Stage? = null
    var cancelled = false

    override fun init(owner: Stage) {
        mOwner = owner
        cancelled = false
    }

    fun setMessage(message: String?) {
        Platform.runLater { infoContainer!!.text = message }
    }

    @FXML
    fun closeMe() {
        cancelled = true
        mOwner!!.close()
    }

    fun keyPressed(keyEvent: KeyEvent) {
        if (keyEvent.code == KeyCode.ENTER) {
            closeMe()
        }
    }
}