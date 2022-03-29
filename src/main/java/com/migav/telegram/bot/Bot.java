package com.migav.telegram.bot;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.migav.telegram.SongLoader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.awt.*;
import java.io.*;
import java.net.*;

import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

// Аннотация @Component необходима, чтобы наш класс распознавался Spring, как полноправный Bean
@Component
// Наследуемся от TelegramLongPollingBot - абстрактного класса Telegram API
public class Bot extends TelegramLongPollingBot {
    // Аннотация @Value позволяет задавать значение полю путем считывания из com.migav.telegram.bot.application.yaml
    @Value("${bot.name}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    private String songFile = "audio.mp3";

    private ArrayList<ArrayList<String>> recentUsers = new ArrayList<>();

    private String chatIdNow = "";
    private boolean forward = false;
    /* Перегружаем метод интерфейса LongPollingBot
    Теперь при получении сообщения наш бот будет отвечать сообщением Hi!
    */

    public void removeOldUsers() {
        Long nowTime = Instant.now().toEpochMilli();
        ArrayList<Integer> toRemove = new ArrayList<Integer>();
        for (int i = 0; i < recentUsers.size(); i++) {
            if (nowTime -  Long.parseLong(recentUsers.get(i).get(1)) >= 5000) toRemove.add(i);
        }
        for (int i = 0; i < toRemove.size(); i++) recentUsers.remove(toRemove.get(i).intValue());
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            Message channelPost = update.getChannelPost();
            Message message = update.getMessage();
            if (message != null) {
                while (!chatIdNow.equals("")) Thread.sleep(10);
                Long chatId = message.getChatId();
                removeOldUsers();
                for (int i = 0; i < recentUsers.size(); i++) {
                    if (chatId == Long.parseLong(recentUsers.get(i).get(0))) {
                        execute(new SendMessage().setChatId(chatId).setText("You are not allowed to send more that one request per 5 seconds. Sorry."));
                        return;
                    }

                }
                String songId = SongLoader.getSongIdByName(message.getText() + " official audio");
                String songUrl = "https://youtube.com/watch?v=" + songId;
                Process process = Runtime.getRuntime().exec(
                        "/Library/Frameworks/Python.framework/Versions/3.9/bin/python3 download_song.py '" + songUrl + "'", null, null);

                chatIdNow = message.getChatId().toString();
            } else if (channelPost != null) {
                forward = true;
                String from = "-1001608346356";
                ArrayList<String> newUser = new ArrayList<>();
                newUser.add(chatIdNow);
                newUser.add(String.valueOf(Instant.now().toEpochMilli()));
                recentUsers.add(newUser);
                execute(new ForwardMessage().setFromChatId(from).setChatId(chatIdNow).setMessageId(channelPost.getMessageId()));
            }
//            if (message.getText() != null && message.getText().startsWith("/")) {
//                return;
//            }



//
//            String songName = (message.getText() + " official audio");
//            songFile = songName + ".mp3";
//
//            System.out.println(message.getText());
//            String res = SongLoader.downloadSong(songName, songFile);
//            if (!Objects.equals(res, "0") && !Objects.equals(res, "1")) {
//                ArrayList<String> newUser = new ArrayList<>();
//                newUser.add(chatId.toString());
//                newUser.add(String.valueOf(Instant.now().toEpochMilli()));
//                recentUsers.add(newUser);
//                execute(new SendAudio().setChatId(chatId).setAudio(new File(songFile)));
//            } else if (res.equals("0")) {
//                execute(new SendMessage().setChatId(chatId).setText("The file exceeds duration limit"));
//            } else {
//                execute(new SendMessage().setChatId(chatId).setText("The file is unavailable"));
//            }
//
        } catch (TelegramApiException | IOException | InterruptedException e) {
            e.printStackTrace();
        }

        if (forward) {
            chatIdNow = "";
            forward = false;
        }
//
//        System.out.println(recentUsers);
//        System.out.println("<- recent users");
//
//        new File(songFile).delete();
//        System.out.println("Audio was sent");

    }

    // Геттеры, которые необходимы для наследования от TelegramLongPollingBot
    public String getBotUsername() {
        return botUsername;
    }

    public String getBotToken() {
        return botToken;
    }
}