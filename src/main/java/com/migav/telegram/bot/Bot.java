package com.migav.telegram.bot;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.migav.telegram.SongLoader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;


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
            //Message channelPost = update.getChannelPost();
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            removeOldUsers();
            for (int i = 0; i < recentUsers.size(); i++) {
                if (chatId == Long.parseLong(recentUsers.get(i).get(0))) {
                    execute(new SendMessage().setChatId(chatId).setText("You are not allowed to send more that one request per 5 seconds. Sorry."));
                    return;
                }

            }

            if (message.getText() != null && message.getText().startsWith("/")) {
                return;
            }




            String songName = (message.getText() + " official audio");
            songFile = songName + ".mp3";

            System.out.println(message.getText());
            String res = SongLoader.downloadSong(songName, songFile);
            if (!Objects.equals(res, "0") && !Objects.equals(res, "1")) {
                ArrayList<String> newUser = new ArrayList<>();
                newUser.add(chatId.toString());
                newUser.add(String.valueOf(Instant.now().toEpochMilli()));
                recentUsers.add(newUser);
                execute(new SendAudio().setChatId(chatId).setAudio(new File(songFile)));
            } else if (res.equals("0")) {
                execute(new SendMessage().setChatId(chatId).setText("The file exceeds duration limit"));
            } else {
                execute(new SendMessage().setChatId(chatId).setText("The file is unavailable"));
            }

        } catch (TelegramApiException | IOException | InterruptedException | UnirestException | URISyntaxException e) {
            e.printStackTrace();
        }

        if (forward) {
            chatIdNow = "";
            forward = false;
        }

        new File(songFile).delete();

    }

    // Геттеры, которые необходимы для наследования от TelegramLongPollingBot
    public String getBotUsername() {
        return botUsername;
    }

    public String getBotToken() {
        return botToken;
    }
}