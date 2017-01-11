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
package com.google.sample.games.model;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gson.annotations.Expose;

/**
 * Server side representation of a Player.  This includes holding the
 * OAuth2 credential that is used to make Game Services API calls.
 * <p>
 *     This sample uses GSON to serialize this class, but that is not required.
 * </p>
 */
public class Player {

    @Expose
    private String playerId;

    @Expose
    private String displayName;

    // Hide the tokens, these are for the server side only.
    private Credential credential;

    // Alt PlayerId is only used to migrate to Games lite namespace.
    private String altPlayerId;

    @Expose
    private boolean visibleProfile;

    @Expose
    private String title;

    // Set to true if the refresh token is somehow lost.
    // This flag should be inspected by the client and
    // re-authenticate next time to set up a refresh token.
    @Expose
    private boolean needRefreshToken;

    public Player() {
        playerId = "";
        displayName = "";
        altPlayerId = "";
        needRefreshToken = true;
        title = "";
    }
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
        needRefreshToken = credential.getRefreshToken() == null;
    }

    public Credential getCredential() {
        return credential;
    }

    public String getAltPlayerId() {
        return altPlayerId;
    }

    public void setAltPlayerId(String altPlayerId) {
        this.altPlayerId = altPlayerId==null?"":altPlayerId;
    }

    public void setVisibleProfile(boolean visibleProfile) {
        this.visibleProfile = visibleProfile;
    }

    public boolean isVisibleProfile() {
        return visibleProfile;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean getNeedRefreshToken() {
       return needRefreshToken;
    }
}
