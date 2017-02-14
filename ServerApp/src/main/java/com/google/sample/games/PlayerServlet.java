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

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.sample.games.model.Player;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Servlet to handle the player/ REST endpoints.
 * This is used to get and store information about the player, including
 * exchanging the authCode for the access token that is usable by this server.
 */
public class PlayerServlet extends HttpServlet {

    // Session attribute keys.
    private static final String PLAYER_ID_KEY = "p";

    // Static GSON object used to serialze/deserialize the objects passed via
    // the API.
    private static Gson GSON =
            new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
                    .create();

    // Static HTTP transport factory used by the API to communicate with the
    // Game Services API.
    private static NetHttpTransport HTTPTransport = new NetHttpTransport();


    // In-memory storage of the players.  This is used to make the
    // sample more simple by not having additional dependencies on persistence.
    // In an actual server side application, you'll want to persist this objects
    // in some sort of datastore.
    private static HashMap<String, Player> playerMap = new HashMap<>();

    /**
     * Called by the server (via the <code>service</code> method) to
     * allow a servlet to handle a GET request.
     * <p>
     * <p>Overriding this method to support a GET request also
     * automatically supports an HTTP HEAD request. A HEAD
     * request is a GET request that returns no body in the
     * response, only the request header fields.
     * <p>
     * <p>When overriding this method, read the request data,
     * write the response headers, get the response's writer or
     * output stream object, and finally, write the response data.
     * It's best to include content type and encoding. When using
     * a <code>PrintWriter</code> object to return the response,
     * set the content type before accessing the
     * <code>PrintWriter</code> object.
     * <p>
     * <p>The servlet container must write the headers before
     * committing the response, because in HTTP the headers must be sent
     * before the response body.
     * <p>
     * <p>Where possible, set the Content-Length header (with the
     * {@link ServletResponse#setContentLength} method),
     * to allow the servlet container to use a persistent connection
     * to return its response to the client, improving performance.
     * The content length is automatically set if the entire response fits
     * inside the response buffer.
     * <p>
     * <p>When using HTTP 1.1 chunked encoding (which means that the response
     * has a Transfer-Encoding header), do not set the Content-Length header.
     * <p>
     * <p>The GET method should be safe, that is, without
     * any side effects for which users are held responsible.
     * For example, most form queries have no side effects.
     * If a client request is intended to change stored data,
     * the request should use some other HTTP method.
     * <p>
     * <p>The GET method should also be idempotent, meaning
     * that it can be safely repeated. Sometimes making a
     * method safe also makes it idempotent. For example,
     * repeating queries is both safe and idempotent, but
     * buying a product online or modifying data is neither
     * safe nor idempotent.
     * <p>
     * <p>If the request is incorrectly formatted, <code>doGet</code>
     * returns an HTTP "Bad Request" message.
     *
     * @param req  an {@link HttpServletRequest} object that
     *             contains the request the client has made
     *             of the servlet
     * @param resp an {@link HttpServletResponse} object that
     *             contains the response the servlet sends
     *             to the client
     * @throws IOException      if an input or output error is
     *                          detected when the servlet handles
     *                          the GET request
     * @throws ServletException if the request for the GET
     *                          could not be handled
     * @see ServletResponse#setContentType
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String contextPath = getServletContext().getContextPath();
        String parts[] = req.getRequestURI().split("/");

        // When the path is parsed, it should start with the context path
        // configured by the servlet container.  In the case of this sample,
        // it is empty.
        if (!contextPath.equals(parts[0])) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "contextPath "
                    + contextPath + " != " + parts[0]);
            return;
        }

        // Always return JSON objects.
        resp.setContentType("application/json");

        // the only get we handle is /player/{playerid} which checks to see
        // if we have a record for the given player based on the GPGS playerid.
        if (parts.length == 3) {

            // Special case: /player/test - this is here to confirm that the
            // server is running.  It does not communicate to the play game
            // services.
            if (parts[2].equals("test")) {
                resp.setStatus(HttpServletResponse.SC_OK);
                Player player = new Player();
                player.setAltPlayerId("alt_player_123");
                player.setPlayerId("player_123");
                player.setDisplayName("Test player");
                player.setVisibleProfile(false);
                resp.getWriter().append(GSON.toJson(player));
                return;
            }

            // Check the session.  As a simple security method, there should be
            // 1 playerid per session.  The player id is set in the session
            // when the auth code is exchanged successfully.
            HttpSession session = req.getSession();
            if (!parts[2].equals(session.getAttribute(PLAYER_ID_KEY))) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid session state");
                session.invalidate();
                log("SESSION NOT SET CORRECTLY IN GET");
                return;
            }

            Player player = lookupPlayer(parts[2]);
            resp.setStatus(player == null ? HttpServletResponse
                    .SC_NOT_FOUND : HttpServletResponse.SC_OK);
            resp.getWriter().append(GSON.toJson(player));
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Could not parse request");
        }
    }

    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle a POST request.
     * <p>
     * The HTTP POST method allows the client to send
     * data of unlimited length to the Web server a single time
     * and is useful when posting information such as
     * credit card numbers.
     * <p>
     * <p>When overriding this method, read the request data,
     * write the response headers, get the response's writer or output
     * stream object, and finally, write the response data. It's best
     * to include content type and encoding. When using a
     * <code>PrintWriter</code> object to return the response, set the
     * content type before accessing the <code>PrintWriter</code> object.
     * <p>
     * <p>The servlet container must write the headers before committing the
     * response, because in HTTP the headers must be sent before the
     * response body.
     * <p>
     * <p>Where possible, set the Content-Length header (with the
     * {@link ServletResponse#setContentLength} method),
     * to allow the servlet container to use a persistent connection
     * to return its response to the client, improving performance.
     * The content length is automatically set if the entire response fits
     * inside the response buffer.
     * <p>
     * <p>When using HTTP 1.1 chunked encoding (which means that the response
     * has a Transfer-Encoding header), do not set the Content-Length header.
     * <p>
     * <p>This method does not need to be either safe or idempotent.
     * Operations requested through POST can have side effects for
     * which the user can be held accountable, for example,
     * updating stored data or buying items online.
     * <p>
     * <p>If the HTTP POST request is incorrectly formatted,
     * <code>doPost</code> returns an HTTP "Bad Request" message.
     *
     * @param req  an {@link HttpServletRequest} object that
     *             contains the request the client has made
     *             of the servlet
     * @param resp an {@link HttpServletResponse} object that
     *             contains the response the servlet sends
     *             to the client
     * @throws IOException      if an input or output error is
     *                          detected when the servlet handles
     *                          the request
     * @throws ServletException if the request for the POST
     *                          could not be handled
     * @see ServletOutputStream
     * @see ServletResponse#setContentType
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String contextPath = getServletContext().getContextPath();
        String parts[] = req.getRequestURI().split("/");

        // When the path is parsed, it should start with the context path
        // configured by the servlet container.  In the case of this sample,
        // it is empty.
        if (!contextPath.equals(parts[0])) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "contextPath "
                    + contextPath + " != " + parts[0]);
            return;
        }

        // The only post we handle is /player/{playerid}.
        // we look up the player, and if they new, we create a new object.
        // then in the data, we get the authCode and exchange that for
        // the access token, and then return the player object.
        if (parts.length == 3) {
            // Check the session.  As a simple security method, there should be
            // 1 playerid per session.  The player id is set in the session
            // when the auth code is exchanged successfully.
            HttpSession session = req.getSession();
            if (session.getAttribute(PLAYER_ID_KEY) != null &&
                    !parts[2].equals(session.getAttribute(PLAYER_ID_KEY))) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid session state");
                session.invalidate();
                return;
            }
            Player player = lookupPlayer(parts[2]);
            if (player == null) {
                player = createPlayer(parts[2]);
            }

            // Read the authcode from the client.
            String authCode = GSON.fromJson(req.getReader(), String.class);

            if (authCode != null) {
                int result = exchangeAuthCode(authCode, player);
                resp.setStatus(result);
                resp.getWriter().append(GSON.toJson(player));
                session.setAttribute(PLAYER_ID_KEY, player.getPlayerId());
            } else if (player.getCredential() == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse " +
                        "request contents");
            }

        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not parse " +
                    "request");
        }
    }

    private Player lookupPlayer(String playerId) {
        return playerMap.get(playerId);
    }

    private Player createPlayer(String playerId) {
        if (!playerMap.containsKey(playerId)) {
            Player p = new Player();
            p.setPlayerId(playerId);
            playerMap.put(playerId, p);
        }
        return playerMap.get(playerId);
    }

    private void savePlayer(Player player) {
        playerMap.put(player.getPlayerId(), player);
    }

    /**
     * Exchanges the authcode for an access token credential.  The credential
     * is the associated with the given player.
     *
     * @param authCode - the non-null authcode passed from the client.
     * @param player   - the player object which the given authcode is
     *                 associated with.
     * @return the HTTP response code indicating the outcome of the exchange.
     */
    private int exchangeAuthCode(String authCode, Player player) {
        try {

            // The client_secret.json file is downloaded from the Google API
            // console.  This is used to identify your web application.  The
            // contents of this file should not be shared.
            //
            // For the sample, this file is expected to be in the root of the
            // sample (at the same level as the top level build.gradle file).
            File secretFile = new File("client_secret.json");

            // If we don't have the file, we can't access any APIs, so return
            // an error.
            if (!secretFile.exists()) {
                log("Secret file : " + secretFile
                        .getAbsolutePath() + "  does not exist!");
                return HttpServletResponse.SC_FORBIDDEN;
            }

            GoogleClientSecrets clientSecrets =
                    GoogleClientSecrets.load(
                            JacksonFactory.getDefaultInstance(), new
                                    FileReader(secretFile));



            // For the sample server, make sure that the client secret file
            // has been updated with actual values.
            if (clientSecrets.getDetails().getClientId().equals("ReplaceMe")) {
                String message = "client_secret.json is not configured " +
                        "correctly!  Download your app's information and place " +
                        "it in client_secret.json";
                log("ERROR: " + message);
                throw new IllegalStateException(message);
            }

            // small hack here to extract the application id of the game from
            // the client id.
            String applicationId = extractApplicationId(clientSecrets
                    .getDetails().getClientId());

            GoogleTokenResponse tokenResponse =
                    new GoogleAuthorizationCodeTokenRequest(
                            HTTPTransport,
                            JacksonFactory.getDefaultInstance(),
                            "https://www.googleapis.com/oauth2/v4/token",
                            clientSecrets.getDetails().getClientId(),
                            clientSecrets.getDetails().getClientSecret(),
                            authCode,
                            "")
                            .execute();

            log("hasRefresh == " + (tokenResponse.getRefreshToken() != null));
            log("Exchanging authCode: " + authCode + " for token");
            Credential credential = new Credential.Builder(
                    BearerToken.authorizationHeaderAccessMethod())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setTransport(HTTPTransport)
                    .setTokenServerEncodedUrl("https://www.googleapis" +
                            ".com/oauth2/v4/token")
                    .setClientAuthentication(new HttpExecuteInterceptor() {
                        @Override
                        public void intercept(HttpRequest request) throws IOException {

                        }
                    })
                    .build()
                    .setFromTokenResponse(tokenResponse);

            player.setCredential(credential);

            // Now that we have a credential, we can access the Games API.
            PlayGamesAPI api = new PlayGamesAPI(player, applicationId,
                    HTTPTransport,
                    JacksonFactory.getDefaultInstance());

            // Call the verify method, which checks that the access token has
            // access to the Games API, and that the player id used by the
            // client matches the playerId associated with the accessToken.
            boolean ok = api.verifyPlayer();

            // This does not add much that is not available on the client, but
            // is used to demonstrate calling a Games API on the server.
            if (ok) {
                ok = api.updatePlayerInfo();
                if (ok) {
                    // persist the player.
                    savePlayer(api.getPlayer());
                }
            }

            return ok ? HttpServletResponse.SC_OK :
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    /**
     * Small helper function to parse the client id string and extract the
     * application Id to use with the Games API.
     *
     * @param clientId - the client id for the web app.
     * @return the applicationId, or empty string if a problem.
     */
    private String extractApplicationId(String clientId) {
        // Grab the digits before the -.
        int idx = clientId.indexOf("-");
        return (idx > 0) ? clientId.substring(0, idx) : "";
    }
}
