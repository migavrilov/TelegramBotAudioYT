package com.migav.telegram.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private String songLoaderUrl = "https://econtools.ru/bots/audio.mp3";
    private String songFile = "audio.mp3";

    /* Перегружаем метод интерфейса LongPollingBot
    Теперь при получении сообщения наш бот будет отвечать сообщением Hi!
    */
    public String getHTMLFromUrl(String url) {
        String content = null;
        URLConnection connection = null;
        try {
            connection =  new URL(url).openConnection();
            Scanner scanner = new Scanner(connection.getInputStream());
            scanner.useDelimiter("\\Z");
            content = scanner.next();
            scanner.close();
        }catch ( Exception ex ) {
            ex.printStackTrace();
        }
        return content;
    }

    public String getSongIdByName(String songName) throws IOException {
        String songUrlApi = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                "&key=AIzaSyA16HZgMgo30DsjZpXT3AsPyHr1YNF5PdA&maxResults=1&q=" + songName;

        String resultJson = getHTMLFromUrl(songUrlApi);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(resultJson);
        System.out.println(rootNode.get("items").get(0).get("id").get("videoId").asText());
        return rootNode.get("items").get(0).get("id").get("videoId").asText();
    }

    public void downloadSong(String songName) throws IOException, InterruptedException {
        String songId = getSongIdByName(songName);

        URL url = new URL("https://econtools.ru/bots/yttomp3.php?id=" + songId);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestMethod("POST"); // PUT is another valid option
        http.setDoOutput(true);

        try (BufferedInputStream in = new BufferedInputStream(new URL(songLoaderUrl).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(songFile)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            // handle exception
        }
        System.out.println("Audio was downloaded");
        ((HttpURLConnection) con).disconnect();
    }
    @Override
    public void onUpdateReceived(Update update) {
        try {
            Message message = update.getMessage();
            songFile = message.getText() + ".mp3";


            Long chatId = message.getChatId();
            String songName = message.getText();
            System.out.println(message.getText());
            downloadSong(songName);
            execute(new SendAudio().setChatId(chatId).setAudio(new File(songFile)));


        } catch (TelegramApiException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
        new File(songFile).delete();
        System.out.println("Audio was sent");
    }

    // Геттеры, которые необходимы для наследования от TelegramLongPollingBot
    public String getBotUsername() {
        return botUsername;
    }

    public String getBotToken() {
        return botToken;
    }
}