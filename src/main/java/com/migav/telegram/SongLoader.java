package com.migav.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SongLoader {

    private static String youtubeApiKey = "";
    private static String rapidApiKey = "";

    public static String getHTMLFromUrl(String url) {
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
    public static Long getSongDuration(String songId) throws JsonProcessingException {
        String urlApi = "https://youtube.googleapis.com/youtube/v3/videos?part=contentDetails&key=" +
                youtubeApiKey +  "&id=" + songId;

        String resultJson = getHTMLFromUrl(urlApi);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(resultJson);

        String duration = rootNode.get("items").get(0).get("contentDetails").get("duration").asText();
        return Duration.parse(duration).getSeconds();
    }

    public static String getSongIdByName(String songName) throws IOException {
        String songUrlApi = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                "&key=" + youtubeApiKey + "&regionCode=US&topicId=/m/04rlf&maxResults=5&q=" + URLEncoder.encode(songName, StandardCharsets.UTF_8.toString());
        //&regionCode=US&topicId=/m/04rlf
        String resultJson = getHTMLFromUrl(songUrlApi);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(resultJson);
        if (songName.toLowerCase().contains("remix") || songName.toLowerCase().contains("ремикс")) {
            System.out.println(rootNode.get("items").get(0).get("id").get("videoId").asText() + " - id");
            return rootNode.get("items").get(0).get("id").get("videoId").asText();
        } else {
            int resId = 0;
            for (int i = 0; i < 5; i++) {
                String title = rootNode.get("items").get(i).get("snippet").get("title").asText();
                if (!title.toLowerCase().contains("remix") && !title.toLowerCase().contains("ремикс") &&
                        !title.contains("8D")) {
                    resId = i;
                    break;
                }
            }

            System.out.println(rootNode.get("items").get(resId).get("id").get("videoId").asText() + " - id");
            return rootNode.get("items").get(resId).get("id").get("videoId").asText();
        }

    }

    public static String getSongUrl(String songId) throws IOException, UnirestException, InterruptedException {
        String rawLink = "";
        String res = "";
        ObjectMapper mapper = new ObjectMapper();
        while (rawLink == "") {
            HttpResponse<String> response = Unirest.get("https://youtube-mp3-download1.p.rapidapi.com/dl?id=" + songId)
                    .header("X-RapidAPI-Host", "youtube-mp3-download1.p.rapidapi.com")
                    .header("X-RapidAPI-Key", rapidApiKey)
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


    public static String downloadSong(String songName, String songFile) throws IOException, UnirestException, URISyntaxException, InterruptedException {
        String songId = getSongIdByName(songName);
        long duration = getSongDuration(songId);
        System.out.println(duration);
        if (duration > 15 * 60) return "0";

        String songLoaderUrl = getSongUrl(songId);

        if (songLoaderUrl.equals("0")) return "1";


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
}
