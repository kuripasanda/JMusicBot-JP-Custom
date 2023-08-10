/*
 *  Copyright 2023 Cosgy Dev (info@cosgy.dev).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyCmd extends MusicCommand {

    Logger log = LoggerFactory.getLogger(this.name);
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final String SPOTIFY_TRACK_URL_PREFIX = "https://open.spotify.com/track/";
    private static final String SPOTIFY_AUTH_URL = "https://accounts.spotify.com/api/token";

    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    private String accessToken = null;
    private long accessTokenExpirationTime;

    public SpotifyCmd(Bot bot) {
        super(bot);
        this.name = "spotify";
        this.arguments = "<title|URL|subcommand>";
        this.help = "指定された曲を再生します";
        this.beListening = true;
        this.bePlaying = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "tracklink", "Spotifyの曲のURL", true));
        this.options = options;

        // Spotify のユーザー名とパスワードを取得
        String clientId  = bot.getConfig().getSpotifyClientId();
        String clientSecret  = bot.getConfig().getSpotifyClientSecret();

        if(clientId.isEmpty() || clientSecret.isEmpty()){
            return;
        }
        // ACCESS_TOKEN の発行
        accessToken = getAccessToken(clientId, clientSecret);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        String trackUrl = event.getOption("tracklink").getAsString();

        if(accessToken == null){
            event.reply("このコマンドは使用できません。このコマンドを有効にするにはボットの所有者による設定が必要です。").queue();
            return;
        }

        // アクセストークンが有効期限切れの場合は再度発行する
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
        }

        if (!isSpotifyTrackUrl(trackUrl)) {
            event.reply("Error: 指定されたURLはSpotifyの曲のURLではありません").queue();
            return;
        }

        String trackId = extractTrackIdFromUrl(trackUrl);
        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+ accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String trackName = json.getString("name");
            String albumName = json.getJSONObject("album").getString("name");
            String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
            String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

            // Audio Features エンドポイントを使用して曲の情報を取得
            endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
            request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer "+ accessToken)
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            json = new JSONObject(response.body());
            double trackColor = json.getDouble("valence");

            int hue = (int) (trackColor * 360);
            Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Track Information");
            embed.addField("Track Name", trackName, true);
            embed.addField("Album Name", albumName, true);
            embed.addField("Artist Name", artistName, true);
            embed.setImage(albumImageUrl);
            embed.setColor(color);

            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

            event.reply("`[" + trackName + "]`を読み込み中です…").queue(m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:"+trackName + " " + artistName, new SlashResultHandler(m, event)));
        } catch (IOException | InterruptedException e) {
            event.reply("Error: " + e.getMessage()).queue();
        }
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + " 再生リスト名を含めてください。");
            return;
        }
        String trackUrl = event.getArgs();

        if(accessToken == null){
            event.reply("このコマンドは使用できません。このコマンドを有効にするにはボットの所有者による設定が必要です。");
            return;
        }

        // アクセストークンが有効期限切れの場合は再度発行する
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
        }

        if (!isSpotifyTrackUrl(trackUrl)) {
            event.reply("Error: 指定されたURLはSpotifyの曲のURLではありません");
            return;
        }

        String trackId = extractTrackIdFromUrl(trackUrl);
        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer "+ accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String trackName = json.getString("name");
            String albumName = json.getJSONObject("album").getString("name");
            String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
            String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

            // Audio Features エンドポイントを使用して曲の情報を取得
            endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
            request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer "+ accessToken)
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            json = new JSONObject(response.body());
            double trackColor = json.getDouble("valence");

            int hue = (int) (trackColor * 360);
            Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Track Information");
            embed.addField("Track Name", trackName, true);
            embed.addField("Album Name", albumName, true);
            embed.addField("Artist Name", artistName, true);
            embed.setImage(albumImageUrl);
            embed.setColor(color);

            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

            event.reply("`[" + trackName + "]`を読み込み中です…", m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:"+trackName + " " + artistName, new ResultHandler(m, event)));
        } catch (IOException | InterruptedException e) {
            event.reply("Error: " + e.getMessage());
        }
    }

    public static String extractTrackIdFromUrl(String url) {
        String trackId = null;

        Pattern pattern = Pattern.compile("track/(\\w+)");
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            trackId = matcher.group(1);
        }

        return trackId;
    }

    public boolean isSpotifyTrackUrl(String url) {
        Pattern pattern = Pattern.compile("https://open\\.spotify\\.com/(intl-ja/)?track/\\w+");
        Matcher matcher = pattern.matcher(url);

        return matcher.matches();
    }

    private String getAccessToken(String clientId, String clientSecret) {
        String encodedCredentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .uri(URI.create(SPOTIFY_AUTH_URL))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            accessTokenExpirationTime = System.currentTimeMillis() + json.getInt("expires_in") * 1000L;
            return json.getString("access_token");
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }


    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**`は許可されている最大長より長いです。"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
            m.editOriginal(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "を追加しました。"
                    : "を" + pos + "番目の再生待ちに追加しました。"))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            AudioTrack track = playlist.getTracks().get(0);

            for(int i = 0; i < playlist.getTracks().size(); i++){
                log.info((i + 1) +" Title:"+ playlist.getTracks().get(i).getInfo().title + " Artist:"+playlist.getTracks().get(i).getInfo().author);
            }

            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(bot.getConfig().getWarning() + "この曲 (**" + track.getInfo().title + "**) は、許容される最大長より長いです。: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`").queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
            m.editOriginal(bot.getConfig().getSuccess() + "**" + FormatUtil.filter(track.getInfo().title)
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "の再生を開始します。"
                    : "を" + pos + "番目の再生待ちに追加しました。")).queue();
        }

        @Override
        public void noMatches() {
            m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " 曲を検索しましたが見つかりませんでした。 `")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {

            if (throwable.severity == FriendlyException.Severity.COMMON)
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました: " + throwable.getMessage()).queue();
            else
                m.editOriginal(event.getClient().getError() + " 読み込み中にエラーが発生しました").queue();
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;

        private ResultHandler(Message m, CommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " この曲 (**" + track.getInfo().title + "**) は許可されている最大長よりも長いです。 `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + " **" + track.getInfo().title
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "の再生を開始します。"
                    : "を" + pos + "番目の再生待ちに追加しました。"))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        @Override
        public void noMatches() {
            m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " `" + event.getArgs() + "`に該当する結果は見つかりませんでした。")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == FriendlyException.Severity.COMMON)
                m.editMessage(event.getClient().getError() + " 読み込みエラー: " + throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError() + " 曲の読み込みに失敗しました。").queue();
        }
    }
}

