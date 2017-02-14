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
package com.google.sample.clientserverskeleton.model;

import com.google.gson.annotations.Expose;

/**
 * POJO for serialize/deserialize to JSON using GSON.  This is the
 * simplistic representation of a player.  This model matches the model sent
 * by the sever.
 */
public class ServerPlayer {
    @Expose
    private String playerId;

    @Expose
    private String displayName;

    @Expose
    private boolean visibleProfile;

    @Expose
    private String title;

    // Set to true if the refresh token is somehow lost.
    // This flag should be inspected by the client and
    // re-authenticate next time to set up a refresh token.
    @Expose
    private boolean needRefreshToken;

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

    @Override
    public String toString() {
        return displayName + "(" + playerId + ") - " + title + " needsRefresh: "
                + needRefreshToken;
    }
}
