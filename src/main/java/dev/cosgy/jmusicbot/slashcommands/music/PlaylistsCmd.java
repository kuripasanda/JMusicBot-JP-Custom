/*
 * Copyright 2018 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlaylistsCmd extends MusicCommand {
    public PlaylistsCmd(Bot bot) {
        super(bot);
        this.name = "playlists";
        this.help = "利用可能な再生リストを表示します";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
        this.beListening = false;
        this.children = new MusicCommand[] { new PlayCmd(bot) };
    }

    @Override
    public void doCommand(CommandEvent event) {
        String guildID = event.getGuild().getId();
        if (!bot.getPlaylistLoader().folderExists())
            bot.getPlaylistLoader().createFolder();
        if (!bot.getPlaylistLoader().folderGuildExists(guildID))
            bot.getPlaylistLoader().createGuildFolder(guildID);
        if (!bot.getPlaylistLoader().folderExists()) {
            event.reply(event.getClient().getWarning() + " 再生リストフォルダが存在しないため作成できませんでした。");
            return;
        }
        if (!bot.getPlaylistLoader().folderGuildExists(guildID)) {
            event.reply(event.getClient().getWarning() + " このサーバーの再生リストフォルダが存在しないため作成できませんでした。");
            return;
        }
        List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildID);
        if (list == null)
            event.reply(event.getClient().getError() + " 利用可能な再生リストを読み込めませんでした。");
        else if (list.isEmpty())
            event.reply(event.getClient().getWarning() + " 再生リストフォルダにプレイリストがありません。");
        else {
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 利用可能な再生リスト:\n");
            list.forEach(str -> builder.append("`").append(str).append("` "));
            builder.append("\n`").append(event.getClient().getTextualPrefix()).append("play playlist <name>` と入力することで再生リストを再生できます。");
            event.reply(builder.toString());
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        String guildID = event.getGuild().getId();
        if (!bot.getPlaylistLoader().folderExists())
            bot.getPlaylistLoader().createFolder();
        if (!bot.getPlaylistLoader().folderGuildExists(guildID))
            bot.getPlaylistLoader().createGuildFolder(guildID);
        if (!bot.getPlaylistLoader().folderExists()) {
            event.reply(event.getClient().getWarning() + " 再生リストフォルダが存在しないため作成できませんでした。").queue();
            return;
        }
        if (!bot.getPlaylistLoader().folderGuildExists(guildID)) {
            event.reply(event.getClient().getWarning() + " このサーバーの再生リストフォルダが存在しないため作成できませんでした。").queue();
            return;
        }
        List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildID);
        if (list == null)
            event.reply(event.getClient().getError() + " 利用可能な再生リストを読み込めませんでした。").queue();
        else if (list.isEmpty())
            event.reply(event.getClient().getWarning() + " 再生リストフォルダにプレイリストがありません。").queue();
        else {
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " 利用可能な再生リスト:\n");
            list.forEach(str -> builder.append("`").append(str).append("` "));
            builder.append("\n`").append(event.getClient().getTextualPrefix()).append("play playlist <name>` と入力することで再生リストを再生できます。");
            event.reply(builder.toString()).queue();
        }
    }

    public class PlayCmd extends MusicCommand {
        public PlayCmd(Bot bot) {
            super(bot);
            this.name = "playlist";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "提供された再生リストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "プレイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + "再生リスト名を含めてください。");
                return;
            }
            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, event.getArgs());
            if (playlist == null) {
                event.replyError("`" + event.getArgs() + ".txt`を見つけられませんでした ");
                return;
            }
            event.getChannel().sendMessage(":calling: 再生リスト **" + event.getArgs() + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String guildId = event.getGuild().getId();
            if (event.getOption("name") == null) {
                event.reply(event.getClient().getError() + "再生リスト名を含めてください。").queue();
                return;
            }
            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, event.getOption("name").getAsString());
            if (playlist == null) {
                event.reply("`" + event.getOption("name").getAsString() + ".txt`を見つけられませんでした ");
                return;
            }
            event.getChannel().sendMessage(":calling: 再生リスト **" + event.getOption("name").getAsString() + "**を読み込んでいます... (" + playlist.getItems().size() + " 曲)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " 楽曲がロードされていません。"
                            : event.getClient().getSuccess() + "**" + playlist.getTracks().size() + "**曲読み込みました。");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\n以下の楽曲をロードできませんでした:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (以下略)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }
    }
}
