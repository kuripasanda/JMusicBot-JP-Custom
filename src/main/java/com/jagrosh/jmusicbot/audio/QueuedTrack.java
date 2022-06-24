/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.queue.Queueable;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.agent.GensokyoInfoAgent;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.json.XML;

import java.io.IOException;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueuedTrack implements Queueable {
    private final AudioTrack track;

    public QueuedTrack(AudioTrack track, User owner) {
        this(track, new RequestMetadata(owner));
    }

    public QueuedTrack(AudioTrack track, RequestMetadata rm) {
        this.track = track;
        this.track.setUserData(rm);
    }

    @Override
    public long getIdentifier() {
        return track.getUserData(RequestMetadata.class).getOwner();
    }

    public AudioTrack getTrack() {
        return track;
    }

    @Override
    public String toString() {

        if (track.getInfo().uri.contains("https://stream.gensokyoradio.net/")) {
            JSONObject data = null;
            try {
                data = XML.toJSONObject(GensokyoInfoAgent.getInfo()).getJSONObject("GENSOKYORADIODATA");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String title = data.getJSONObject("SONGINFO").getString("TITLE");
            String titleUrl = data.getJSONObject("MISC").getString("CIRCLELINK").equals("") ?
                    "https://gensokyoradio.net/" :
                    data.getJSONObject("MISC").getString("CIRCLELINK");
            return "`[" + FormatUtil.formatTime(data.getJSONObject("SONGTIMES").getInt("DURATION")) + "]` [**" + title + "**](" + titleUrl + ") - <@" + track.getUserData(RequestMetadata.class).getOwner() + ">";
        }
        return "`[" + FormatUtil.formatTime(track.getDuration()) + "]` [**" + track.getInfo().title + "**](" + track.getInfo().uri + ") - <@" + track.getUserData(RequestMetadata.class).getOwner() + ">";
    }
}
