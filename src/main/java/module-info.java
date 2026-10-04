module omut.aichat {
    requires org.commonmark;
    requires javafx.web;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j.simple;
    requires langchain4j.ollama;
    requires langchain4j.core;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires atlantafx.base;
    requires java.desktop;

    exports omut.aichat;
    exports omut.aichat.chat;
    exports omut.aichat.config;
    exports omut.aichat.service;
    exports omut.aichat.ui;
}