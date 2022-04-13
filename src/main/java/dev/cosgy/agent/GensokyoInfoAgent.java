/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
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

package dev.cosgy.agent;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GensokyoInfoAgent extends Thread {
    private static final Logger log = LoggerFactory.getLogger(GensokyoInfoAgent.class);
    private static final int INTERVAL_MILLIS = 5000; // 5 secs
    private static String info = null;
    private static String lastSong = "";

    public GensokyoInfoAgent() {
        setDaemon(true);
        setName("GensokyoInfoAgent");
    }

    @SuppressWarnings("UnusedReturnValue")
    private static String fetch() {
        try {
            info = Unirest.get("https://gensokyoradio.net/xml").asString().getBody();

            JSONObject data = XML.toJSONObject(GensokyoInfoAgent.getInfo()).getJSONObject("GENSOKYORADIODATA");

            String newSong = data.getJSONObject("SONGINFO").getString("TITLE");

            if (!newSong.equals(lastSong)) {
                log.info("再生中 " + newSong);
            }

            lastSong = data.getJSONObject("SONGINFO").getString("TITLE");

            return info;
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getInfo() {
        return info == null ? fetch() : info;
    }

    @Override
    public void run() {
        log.info("GensokyoInfoAgentを開始しました");

        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                fetch();
                sleep(INTERVAL_MILLIS);
            } catch (Exception e) {
                //log.error("情報を取得中に例外が発生しました！", e);
                try {
                    sleep(1000);
                } catch (InterruptedException e1) {
                    //log.error("エージェントの例外後にスリープ中に中断されました", e);
                    break;
                }
            }
        }
    }
}
