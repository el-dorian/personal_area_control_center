package net.veldor.personal_server.model.utils

import org.apache.http.HttpEntity
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.http.HttpResponse
import java.util.*

class UpdateChecker private constructor() {
    fun startCheck() {
        Timer().schedule(
            object : TimerTask() {
                override fun run() {
                    val currentVersion = PreferencesHandler.instance.getSoftwareVersion()
                    println("UpdateChecker 11 $currentVersion")
                    // check version on server
                    var updateAvailable = false
                    val httpclient: CloseableHttpClient = HttpClients.createDefault()
                    val httpget = HttpGet(GITHUB_RELEASES_URL)
                    try {
                        // кастомный обработчик ответов
                        val responseHandler: ResponseHandler<String> =
                            ResponseHandler<String> {
                                val status = it.statusLine.statusCode
                                if (status in 200..299) {
                                    val entity: HttpEntity = it.entity
                                    try {
                                        val body: String = EntityUtils.toString(entity)
                                        val releaseInfo = JSONObject(body)
                                        val lastVersion: String =
                                            releaseInfo.getString(GITHUB_APP_VERSION)
                                        println("UpdateChecker 38 $lastVersion")
                                        if (lastVersion.toInt() > currentVersion) {
                                            updateAvailable = true
                                        }
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    } catch (e: JSONException) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    // неверный ответ с сервера
                                }
                                null
                            }
                        // выполню запрос
                        httpclient.execute(httpget, responseHandler)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        try {
                            // по-любому закрою клиент
                            httpclient.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }, 0, CHECK_PERIOD
        )
    }

    companion object {
        const val CHECK_PERIOD: Long = 60000
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/veldor/FlibustaTest/releases/latest"
        private const val GITHUB_APP_VERSION = "tag_name"
        val instance: UpdateChecker = UpdateChecker()
    }
}