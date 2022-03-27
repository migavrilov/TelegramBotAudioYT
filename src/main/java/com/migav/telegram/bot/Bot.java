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

import java.awt.*;
import java.io.*;
import java.net.*;

import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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
        System.out.println(rootNode.get("items").get(0).get("id").get("videoId").asText() + " - id");
        return rootNode.get("items").get(0).get("id").get("videoId").asText();
    }

    public String getSongUrlOld(String songId) throws UnirestException, IOException {
        HttpResponse<String> response = Unirest.get("https://youtube-mp3-download1.p.rapidapi.com/dl?id="+songId)
                .header("X-RapidAPI-Host", "youtube-mp3-download1.p.rapidapi.com")
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
                "/Versions/3.9/bin/yt-dlp" + " --extract-audio --audio-format mp3 --audio-quality 256K '" +
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

    public String getSongUrl(String songId) throws IOException, UnirestException, InterruptedException {
        String rawLink = "";
        String res = "";
        ObjectMapper mapper = new ObjectMapper();
        while (rawLink == "") {
            HttpResponse<String> response = Unirest.get("https://youtube-mp3-download1.p.rapidapi.com/dl?id=" + songId)
                    .header("X-RapidAPI-Host", "youtube-mp3-download1.p.rapidapi.com")
                    .header("X-RapidAPI-Key", "f5c9c95bb0msh7813baf09ef6f37p19f162jsn1ed0bc9b9383")
                    .asString();

            res = response.getBody();
            System.out.println(res);

            JsonNode node = mapper.readTree(res);
            if (Objects.equals(node.get("status").asText(), "ok")) rawLink = node.get("link").asText();
            if (Objects.equals(node.get("status").asText(), "fail")) return "0";
            else TimeUnit.MILLISECONDS.sleep(200);
        }

        HttpResponse<String> response2 = Unirest.get(rawLink).asString();
        res = response2.getBody();
        res = res.split("<script async>")[1];
        int start = res.indexOf("[");
        int end = res.indexOf("]") + 1;
        res = res.substring(start, end);

        JsonNode codes = mapper.readTree(res);
        int offset = codes.get(0).asInt() - 60;
        String directLink = "";
        for (int i = 21; i < codes.size() - 15; i++) {
            directLink += (char)(codes.get(i).asInt() - offset);
        }
        System.out.println(directLink);
        return directLink;
    }


    public String downloadSongNew(String songName) throws IOException, UnirestException, URISyntaxException, InterruptedException {
        String songId = getSongIdByName(songName);
        long duration = getSongDuration(songId);
        System.out.println(duration);
        if (duration > 15 * 60) return "0";

        String songLoaderUrl = getSongUrl(songId);

        if (songLoaderUrl == "0") return "1";


        HttpResponse<InputStream> response = Unirest.get(songLoaderUrl).asBinary();
        InputStream stream = response.getBody();
        //InputStream stream = new URL(songLoaderUrl).openStream();
        try (BufferedInputStream in = new BufferedInputStream(stream);
             FileOutputStream fileOutputStream = new FileOutputStream(songFile)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        //System.out.println(songLoaderUrl);

        System.out.println("Audio was downloaded");
        return "ok";
    }
    @Override
    public void onUpdateReceived(Update update) {
        try {
            Message message = update.getMessage();
            if (message.getText().startsWith("/")) {
                return;
            }
            Long chatId = message.getChatId();
            Long nowTime = Instant.now().toEpochMilli();
            ArrayList<Integer> toRemove = new ArrayList<Integer>();
            for (int i = 0; i < recentUsers.size(); i++) {
                if (nowTime -  Long.parseLong(recentUsers.get(i).get(1)) >= 5000) toRemove.add(i);
            }
            for (int i = 0; i < toRemove.size(); i++) recentUsers.remove(toRemove.get(i).intValue());
            for (int i = 0; i < recentUsers.size(); i++) {
                if (chatId == Long.parseLong(recentUsers.get(i).get(0))) {
                    execute(new SendMessage().setChatId(chatId).setText("You are not allowed to send more that one request in 5 seconds. Sorry."));
                    return;
                }

            }

            String songName = (message.getText() + " audio").replace(" ", "+");
            songFile = songName + ".mp3";

            System.out.println(message.getText());
            String res = downloadSongNew(songName);
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

        } catch (TelegramApiException | IOException | UnirestException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(recentUsers);
        System.out.println("<- recent users");

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