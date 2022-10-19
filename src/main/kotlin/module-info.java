module net.veldor.personal_server {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;
    requires java.sql;
    requires pdf.parser;
    requires org.json;
    requires jasypt;
    requires Java.WebSocket;
    requires com.google.gson;
    requires org.apache.commons.codec;
    requires com.google.auth.oauth2;
    requires firebase.admin;
    requires zt.zip;
    requires pdf.archiver;
    requires org.apache.logging.log4j;
    requires commons.lang;
    requires org.apache.httpcomponents.httpclient;
    requires org.apache.httpcomponents.httpcore;
    requires java.net.http;


    opens net.veldor.personal_server.controller to javafx.fxml;
    opens net.veldor.personal_server to javafx.fxml;
    opens net.veldor.personal_server.model.selections;
    exports net.veldor.personal_server;
    exports net.veldor.personal_server.model.selections;
}