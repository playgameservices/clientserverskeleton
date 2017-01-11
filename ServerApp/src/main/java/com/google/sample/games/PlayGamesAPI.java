/*
 * Copyright (C) 2017 Google Inc.
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
package com.google.sample.games;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.games.Games;
import com.google.api.services.games.model.ApplicationVerifyResponse;
import com.google.sample.games.model.Player;

import java.io.IOException;


/**
 * A wrapper around the Play Game Services Web API.  This may be a little
 * excessive, but for the sake of clarity in the sample, the API calls to the
 * Game Services are encapsulated here.
 */
public class PlayGamesAPI {

    private Player player;
    private String applicationId;
    private Games gamesAPI;

    /**
     * Creates an instance of the PlayGamesAPI.  This instance is specific
     * to the given player and application ID.
     *
     * @param player        - the player including credentials representing the
     *                      client-side player.
     * @param applicationId - the application id of the game configuration.
     * @param transport     - the HTTP transport factory the API should use.
     * @param jsonFactory   - the JSON serializer factory the API should use.
     */
    public PlayGamesAPI(Player player, String applicationId, NetHttpTransport
            transport, JsonFactory jsonFactory) {
        this.player = player;
        this.applicationId = applicationId;
        this.gamesAPI = new Games(transport, jsonFactory, player
                .getCredential());
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Calls Games.applications.verify.  This verifies the credentials with the
     * game configuration found using the application Id.  The player id
     * returned from verify must match the playerId or alternate player id in
     * the player object.
     *
     * @return true if successful, false if the ids returned do not match the
     * ids in the player object.
     * @throws IOException if there is a problem with the call.
     */
    public boolean verifyPlayer() throws IOException {

        ApplicationVerifyResponse resp = gamesAPI.applications().verify
                (applicationId).execute();
        if (player.getPlayerId().equals(resp.getPlayerId()) ||
                player.getAltPlayerId().equals(resp.getAlternatePlayerId())) {
            player.setAltPlayerId(resp.getAlternatePlayerId());
            return true;
        }
        return false;
    }

    /**
     * Calls Games.players.get() using the player object's player Id.
     * The response is used to set some fields on the player object.
     *
     * @return true if successful, false if the ids returned do not match the
     * ids in the player object.
     * @throws IOException - if there is a problem making the games API call.
     */
    public boolean updatePlayerInfo() throws IOException {

        com.google.api.services.games.model.Player gpgPlayer =
                gamesAPI.players().get(player.getPlayerId()).execute();

        player.setDisplayName(gpgPlayer.getDisplayName());
        player.setVisibleProfile(gpgPlayer.getProfileSettings()
                .getProfileVisible());
        player.setTitle(gpgPlayer.getTitle());

        // Handle 'games-lite' player id migration.
        if (!player.getPlayerId().equals(gpgPlayer.getPlayerId())) {
            // Check the original player id and set the alternate id to it.
            if (player.getPlayerId().equals(gpgPlayer.getOriginalPlayerId())) {
                player.setAltPlayerId(gpgPlayer.getPlayerId());
            } else {
                return false;
            }
        } else if (gpgPlayer.getOriginalPlayerId() != null &&
                !gpgPlayer.getOriginalPlayerId().equals(player.getAltPlayerId())) {
            player.setAltPlayerId(gpgPlayer.getOriginalPlayerId());
        }

        return true;
    }
}
