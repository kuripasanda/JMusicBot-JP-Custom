/*
 *  Copyright 2021 Cosgy Dev (info@cosgy.dev).
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

package dev.cosgy.JMusicBot.slashcommands.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.JMusicBot.slashcommands.AdminCommand;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

import java.util.List;

/**
 * @author Kosugi_kun
 */
public class ServerListCmd extends AdminCommand {
    protected Bot bot;

    public ServerListCmd(Bot bot){
        this.name = "slist";
        this.help = "ボットコマンドを使用できる役割DJを設定します。";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if(checkAdminPermission(client, event)){
            event.reply(client.getWarning()+"権限がないため実行できません。").queue();
            return;
        }

        List<Guild> guilds = event.getJDA().getGuilds();

        StringBuilder stringBuilder = new StringBuilder();
        for (Guild guild : guilds) {
            stringBuilder.append(guild.getName()).append(guild.getId()).append("\n");
        }

        event.reply(stringBuilder.toString()).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        List<Guild> guilds = event.getJDA().getGuilds();

        StringBuilder stringBuilder = new StringBuilder();
        for (Guild guild : guilds) {
            stringBuilder.append(guild.getName()).append(guild.getId()).append("\n");
        }

        event.reply(stringBuilder.toString());
    }
}
