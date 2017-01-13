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
package com.google.sample.clientserverskeleton.client;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.sample.clientserverskeleton.model.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * BackendClient encapsulates the configuration and the interactions
 * with the backend server via REST calls made using the Volley library.
 * <p>
 * It is for sample purposes only, it only handles 1 server, which is
 * assumed to be running Servlets that use JSESSIONID cookies to do session
 * identification.
 * <p>
 * If your backend is using something else to track sessions, then you'll
 * need to make the appropriate changes to the cookie handling.
 * </p>
 */
public class BackendClient {

    private static final String TAG = "BackendClient";

    private static HashMap<String, String> sessionIds = new HashMap<>();

    // Static GSON object used to serialze/deserialize the objects passed via
    // the API.
    private static Gson GSON =
            new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
                    .create();

    // The host URL.  Specific REST endpoint paths are appended to this URL.
    private final String hostURL;

    // The Volley request queue.
    private final RequestQueue queue;

    /**
     * Creates the backend client.  This client can be used by multiple requests
     * (but may have some multi-threaded issues dealing with cookies).
     *
     * @param hostURL - the URL to the host.
     * @param context - the Android context to pass to Volley when creating the
     *                queue.
     */
    public BackendClient(String hostURL, Context context) {
        this.hostURL = hostURL;
        queue = Volley.newRequestQueue(context);
    }

    /**
     * Posts the authCode for the given player to the server.  The server then
     * can exchange the authCode for an access token.  The response is the
     * player object held by the server.
     *
     * @param playerId - the playerId uniquely identifying the player.
     * @param authCode - the authCode to pass.
     * @param listener - the listener object invoked when the call has
     *                 completed.
     */
    public void sendAuthCode(String playerId, String authCode,
                             final ClientResultListener<ServerPlayer> listener) {
        String path = "player/" + playerId;
        String payload = "\"" + authCode + "\"";
        String getPlayerURL = hostURL + "/" + path;

        Log.d(TAG, "Using URL: " + getPlayerURL);

        BackendRequest<ServerPlayer> req = new BackendRequest<>(
                Request.Method.POST,
                getPlayerURL,
                payload,
                ServerPlayer.class,
                new VolleyListener<>(listener)
        );

        // Exchanging the authcode is a one time thing.  It can't be cached,
        // and in most cases cannot be retried.
        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(req);
    }

    /**
     * Gets the given player from the backend server.
     *
     * @param playerId - the PlayerID uniquely identifying the player
     * @param listener - the listener object invoked when the call has
     *                 completed.
     */
    public void getPlayer(String playerId,
                          ClientResultListener<ServerPlayer> listener) {
        String path = "player/" + playerId;
        String getPlayerURL = hostURL + "/" + path;
        BackendRequest<ServerPlayer> req = new BackendRequest<>(
                Request.Method.GET,
                getPlayerURL,
                null,
                ServerPlayer.class,
                new VolleyListener<>(listener)
        );
        queue.add(req);
    }

    /**
     * Helper class that implements the Volley response listener interfaces
     * in the same class.
     *
     * @param <T> The return object type.
     */
    private static class VolleyListener<T> implements Response.ErrorListener,
            Response.Listener<T> {
        private final ClientResultListener<T> listener;

        VolleyListener(ClientResultListener<T> listener) {
            this.listener = listener;
        }

        /**
         * Callback method that an error has been occurred with the
         * provided error code and optional user-readable message.
         *
         * @param error - the VolleyError object containing the response and the
         *              error.
         */
        @Override
        public void onErrorResponse(VolleyError error) {
            if (error.networkResponse != null) {
                listener.onFailure(error.networkResponse.statusCode,
                        error.getMessage());
            } else {
                listener.onFailure(555,error.getMessage());
            }
        }

        /**
         * Called when a response is received.
         *
         * @param response - the  object deserialized from the JSON response.
         */
        @Override
        public void onResponse(T response) {
            listener.onSuccess(response);
        }
    }

    /**
     * Volley JSON request with JSESSION cookie handling.  This adds
     * reading and posting JSESSIONID cookies on the requests and responses.
     *
     * @param <T> - the type of object in the response.
     */
    private static class BackendRequest<T> extends JsonRequest<T> {

        private Class<T> type;

        BackendRequest(int method, String url, String requestBody,
                       Class<T> type, VolleyListener<T> listener) {
            super(method, url, requestBody, listener, listener);
            this.type = type;
        }

        @Override
        protected Response<T> parseNetworkResponse(NetworkResponse response) {
            handleSessionCookie(response.headers);
            return Response.success(GSON.fromJson(new String(response.data),
                    type), null);
        }

        /**
         * Subclasses can override this method to parse 'networkError' and
         * return a more specific error.
         * <p>The default implementation just returns the passed
         * 'networkError'.</p>
         *
         * @param volleyError the error retrieved from the network
         * @return an NetworkError augmented with additional information
         */
        @Override
        protected VolleyError parseNetworkError(VolleyError volleyError) {
            if (volleyError.networkResponse != null) {
                handleSessionCookie(volleyError.networkResponse.headers);
            }
            return super.parseNetworkError(volleyError);
        }

        private static void handleSessionCookie(Map<String, String> headers) {
            if (headers.containsKey("Set-Cookie")) {
                String cookieset = headers.get("Set-Cookie");
                Log.d(TAG, " cookies: " + cookieset);
                String parts[] = cookieset.split("=");
                if (parts[0].equals("JSESSIONID")) {
                    String crumbles[] = parts[1].split(";");
                    String path = parts[2];
                    String id = crumbles[0];
                    sessionIds.put(path, id);
                }
            }
        }

        /**
         * Add the session cookie to the requests.
         *
         * @return Headers for the request.
         * @throws AuthFailureError
         */
        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> headers = new HashMap<>();
            headers.putAll(super.getHeaders());
            for (Map.Entry<String, String> ent : sessionIds.entrySet()) {
                if (this.getUrl().contains(ent.getKey())) {
                    if (headers.containsKey("Cookie")) {
                        headers.put("Cookie", headers.get("Cookie") + ";" +
                                "JESSIONID=" + ent.getValue());
                    } else {
                        headers.put("Cookie", "JSESSIONID=" + ent.getValue());
                    }
                }
            }
            return headers;
        }
    }
}
