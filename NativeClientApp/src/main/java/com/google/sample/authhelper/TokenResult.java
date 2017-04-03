/*
 * Copyright 2017 Google Inc.
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

package com.google.sample.authhelper;

import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;

/**
 * Class for returning the tokens to a native caller.
 */
public class TokenResult implements Result {
    private Status status;
    private String authCode;
    private String idToken;
    private String email;

    TokenResult() {
        status = null;
        authCode = null;
        idToken = null;
        email = null;
    }
    TokenResult(String authCode, String email, String idToken, int resultCode) {
        status = new Status(resultCode);
        this.authCode = authCode;
        this.idToken = idToken;
        this.email = email;
    }

    @Override
    public String toString() {
        return "Status: " + status + " email: " +
                (email==null?"<null>":email) + " id:" +
                (idToken==null?"<null>":idToken) + " access: " +
                (authCode==null?"<null>":authCode);
    }

    @Override
    public Status getStatus() {
        return status;
    }

    public int getStatusCode() { return status.getStatusCode();}

    public String getAuthCode() {
        return authCode == null ? "" : authCode;
    }

    public String getIdToken() {
        return idToken == null ? "" : idToken;
    }

    public String getEmail() {
        return email == null ? "" : email;
    }

    public void setStatus(int status) {
        this.status = new Status(status);
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
