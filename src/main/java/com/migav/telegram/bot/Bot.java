package com.migav.telegram.bot;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
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
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.*;

import java.time.Duration;
import java.util.Scanner;

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
    public Long getSongDuration(String songId) throws JsonProcessingException {
        String urlApi = "https://youtube.googleapis.com/youtube/v3/videos?part=contentDetails&key=" +
            "AIzaSyA16HZgMgo30DsjZpXT3AsPyHr1YNF5PdA&id=" + songId;

        String resultJson = getHTMLFromUrl(urlApi);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(resultJson);

        String duration = rootNode.get("items").get(0).get("contentDetails").get("duration").asText();
        return Duration.parse(duration).getSeconds();
    }

    public String getSongIdByName(String songName) throws IOException {
        String songUrlApi = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                "&key=AIzaSyA16HZgMgo30DsjZpXT3AsPyHr1YNF5PdA&maxResults=1&q=" + songName.replaceAll(" ", "+");

        String resultJson = getHTMLFromUrl(songUrlApi);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(resultJson);
        System.out.println(rootNode.get("items").get(0).get("id").get("videoId").asText());
        return rootNode.get("items").get(0).get("id").get("videoId").asText();
    }

    public String getSongUrl(String songId) throws UnirestException, IOException {
        HttpResponse<String> response = Unirest.get("https://youtube-mp36.p.rapidapi.com/dl?id=" + songId)
                .header("X-RapidAPI-Host", "youtube-mp36.p.rapidapi.com")
                .header("X-RapidAPI-Key", "f5c9c95bb0msh7813baf09ef6f37p19f162jsn1ed0bc9b9383")
                .asString();
//        HttpResponse<String> response = Unirest.get("https://youtube-mp3-download1.p.rapidapi.com/dl?id=UxxajLWwzqY")
//                .header("X-RapidAPI-Host", "youtube-mp3-download1.p.rapidapi.com")
//                .header("X-RapidAPI-Key", "f5c9c95bb0msh7813baf09ef6f37p19f162jsn1ed0bc9b9383")
//                .asString();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(response.getBody());

        return rootNode.get("link").asText();

    }

    public String loadSong(String url) throws IOException, InterruptedException {
        String command = "/Library/Frameworks/Python.framework" +
                "/Versions/3.9/bin/youtube-dl" + " --extract-audio --audio-format mp3 --audio-quality 256K '" +
                url + "'";
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command}, null, null);
        StringBuilder output = new StringBuilder();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line + "\n");
        }

        int exitVal = process.waitFor();
        String res = output.toString();
        String fileName = res.split("Destination: ")[1].split("\n")[0];
        return fileName;
    }

    public String convertToMp3(String fileName) throws IOException, InterruptedException {
        String command = "/Library/Frameworks/Python.framework/Versions/3.9/bin/ffmpeg -i '" + fileName + "' -b:a 256k '" + fileName.split("\\.")[0] + ".mp3'";

        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command}, null, null);

        int exitVal = process.waitFor();
        int len;
        if ((len = process.getErrorStream().available()) > 0) {
            byte[] buf = new byte[len];
            process.getErrorStream().read(buf);
            System.err.println("Command error:\t\""+new String(buf)+"\"");
        }
        new File(fileName).delete();
        System.out.println(exitVal);
        fileName = fileName.split("\\.")[0] + ".mp3";
        System.out.println(fileName);
        return fileName;
    }


    public String downloadSong(String songName) throws IOException, InterruptedException {
        String songId = getSongIdByName(songName);

        long duration = getSongDuration(songId);
        System.out.println(duration);
        if (duration > 15 * 60) {
            return "0";
        }
        String url = "https://youtube.com/watch?v=" + songId;

        String fileName = loadSong(url);
        //String fileName = "Glass Animals - Heat Waves (Official Video)-mRD0-GxqHVo.webm";
        fileName = convertToMp3(fileName);

        return fileName;
    }


    public void downloadSongOld(String songName) throws IOException, InterruptedException, UnirestException {
        String songId = getSongIdByName(songName);

        String songLoaderUrl = getSongUrl(songId);
        HttpResponse<InputStream> response = Unirest.get(songLoaderUrl).asBinary();
        InputStream stream = response.getBody();

        try (BufferedInputStream in = new BufferedInputStream(stream);
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
    }
    @Override
    public void onUpdateReceived(Update update) {
        try {
            Message message = update.getMessage();
            if (message.getText().startsWith("/")) {
                return;
            }

            String songName = message.getText();

            Long chatId = message.getChatId();
            System.out.println(message.getText());
            songFile = downloadSong(songName);
            if (songFile != "0") {
                execute(new SendAudio().setChatId(chatId).setAudio(new File(songFile)));
            } else {
                execute(new SendMessage().setChatId(chatId).setText("The file exceeds duration limit"));
            }

        } catch (TelegramApiException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
        if (songFile != "0") {
            new File(songFile).delete();
            System.out.println("Audio was sent");
        }
    }

    // Геттеры, которые необходимы для наследования от TelegramLongPollingBot
    public String getBotUsername() {
        return botUsername;
    }

    public String getBotToken() {
        return botToken;
    }
}