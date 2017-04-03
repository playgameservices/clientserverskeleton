/*
 * Copyright (C) Google Inc.
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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.games.Games;

/**
 * Activity fragment with no UI added to the parent activity in order to manage
 * the accessing of the player's email address and tokens.
 */
public class AuthHelperFragment extends Fragment {

    private static final String TAG = "TokenFragment";
    private static final String FRAGMENT_TAG = "gpg.AuthTokenSupport";
    private static final int RC_ACCT = 9002;

    // Pending token request.  There can be only one outstanding request at a
    // time.
    private static final Object lock = new Object();
    private static TokenRequest pendingTokenRequest;
    private static AuthHelperFragment helperFragment;
    private static long pendingCallbackFunction;
    private static long pendingCallbackData;

    private GoogleApiClient mGoogleApiClient;

    /**
     * Configure the authorization.  This must be called before authenticate().
     *
     * @param parentActivity - the parent activity to attach the fragment to.
     * @param useGamesConfig - true if the
     *                       GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
     *                       configuration should be used when building the
     *                       client.  If false, then DEFAULT_SIGN_IN is used.
     * @param webClientId - the client id of the associated web application.
     *                    This is required when requesting auth code or id
     *                    token.  The client id must be associated with the same
     *                    client as this application.
     * @param requestAuthCode - request a server auth code to exchange on the
     *                        server for an OAuth token.
     * @param forceRefreshToken - force refreshing the token when requesting
     *                          a server auth code.  This causes the consent
     *                          screen to be presented to the user, so it
     *                          should be used only when necessary (if at all).
     * @param requestEmail - request the email address of the user.  This
     *                     requires the consent of the user.
     * @param requestIdToken - request an OAuth ID token for the user.  This
     *                       requires the consent of the user.
     * @param hidePopups - Hides the popups during authentication and game
     *                   services events.  This done by calling
     *                   GamesOptions.setShowConnectingPopup and
     *                   GoogleAPIClient.setViewForPopups.  This is usful for
     *                   VR apps.
     * @param accountName - if non-null, the account name to use when
     *                    authenticating.
     * @param additionalScopes - Additional scopes to request.  These will most
     *                         likely require the consent of the user.
     * @return true for a valid configuration.
     */
    public static boolean configure(Activity parentActivity,
                                    boolean useGamesConfig,
                                    String webClientId,
                                    boolean requestAuthCode,
                                    boolean forceRefreshToken,
                                    boolean requestEmail,
                                    boolean requestIdToken,
                                    boolean hidePopups,
                                    String accountName,
                                    String[] additionalScopes) {
        TokenRequest request = new TokenRequest(
                useGamesConfig,
                webClientId,
                requestAuthCode,
                forceRefreshToken,
                requestEmail,
                requestIdToken,
                hidePopups,
                accountName,
                additionalScopes);

        AuthHelperFragment fragment = (AuthHelperFragment) parentActivity
                .getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            try {
                Log.d(TAG, "Creating fragment");
                fragment = new AuthHelperFragment();
                FragmentTransaction trans = parentActivity
                        .getFragmentManager().beginTransaction();
                trans.add(fragment, FRAGMENT_TAG);
                trans.commit();
            } catch (Throwable th) {
                Log.e(TAG, "Cannot launch token fragment:" +
                        th.getMessage(), th);
                return false;
            }
        }

        if (request.isValid()) {
            synchronized (lock) {
                if (pendingTokenRequest != null) {
                    Log.e(TAG, "There is already a pending auth request!");
                    return false;
                }
                pendingTokenRequest = request;
            }
        } else {
            Log.e(TAG, "The request is not valid.");
            return false;
        }
        return true;
    }

    /**
     * Start the authentication process from native (C++) code.
     * @param callbackFunction - the pointer to the callback function.  This is
     *                         passed back to the native layer when there is a
     *                         result.
     * @param callbackData - context data for the callback.
     */
    public static void authenticate(long callbackFunction, long callbackData) {

        TokenRequest request;
        AuthHelperFragment fragment;
        synchronized (lock) {
            request = pendingTokenRequest;
            fragment = helperFragment;
            if (request != null && pendingCallbackFunction == 0) {
                pendingCallbackFunction = callbackFunction;
                pendingCallbackData = callbackData;
            }
        }

        if (callbackFunction == 0) {
            Log.e(TAG, "No callback function passed in! Failing " +
                    "authenticate");
            return;
        }

        if (request == null) {
            Log.e(TAG, "Request not configured! Failing authenticate");
            nativeOnAuthResult(callbackFunction, callbackData,
                    CommonStatusCodes.DEVELOPER_ERROR, null, null, null);
            return;
        }

        if (fragment != null) {
            fragment.processRequest();
        } else {
            Log.i(TAG, "Fragment not initialized yet, waiting to authenticate");
        }
    }

    /**
     * Native callback for the authentication result.
     * @param callbackFunction - the function pointer passed to authenticate.
     * @param callbackData - the context data passed to authenticate.
     * @param result - the authentication result.
     * @param authCode - the auth code or null.
     * @param email - the email address or null.
     * @param id_token the id token or null.
     */
    private static native void nativeOnAuthResult(long
                                                          callbackFunction,
                                                  long callbackData,
                                                  int result,
                                                  String authCode,
                                                  String email,
                                                  String id_token);


    /**
     * Signs out and resets the fragment.
     */
    public static void signOut() {
        synchronized (lock) {
            pendingTokenRequest = null;
            pendingCallbackData = 0;
            pendingCallbackFunction = 0;
        }
        if (helperFragment != null) {
            helperFragment.reset();
        }

    }

    /**
     * signs out and disconnects the client.
     */
    private void reset() {
        if (mGoogleApiClient != null) {
            if (mGoogleApiClient.hasConnectedApi(Games.API)) {
                Games.signOut(mGoogleApiClient);
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
            }
            mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    /**
     * Processes the token requests that are queued up.
     * First checking that the google api client is connected.
     */
    private void processRequest() {

        TokenRequest request;
        final long callbackFunction;
        final long callbackData;
        synchronized (lock) {
            request = pendingTokenRequest;
            callbackFunction = pendingCallbackFunction;
            callbackData = pendingCallbackData;
            if (request != null && pendingCallbackFunction != 0) {
                pendingCallbackFunction = 0;
                pendingCallbackData = 0;
            }
        }

        // no request, no need to continue.
        if (request == null) {
            Log.i(TAG, "No pending configuration, returning");
            return;
        }
        if (callbackFunction == 0) {
            Log.i(TAG, "No pending callback, returning");
            return;
        }

        request.getPendingResponse().setResultCallback(
                new ResultCallback<TokenResult>() {
                    @Override
                    public void onResult(@NonNull TokenResult tokenResult) {
                        nativeOnAuthResult(callbackFunction, callbackData,
                                tokenResult.getStatusCode(),
                                tokenResult.getAuthCode(),
                                tokenResult.getEmail(),
                                tokenResult.getIdToken());
                    }
                });

        // Build the GoogleAPIClient
        buildClient(request);
        // Sign-in, the result is processed in OnActivityResult()
        if (mGoogleApiClient == null) {
            throw new IllegalStateException("client is null!");
        }

        Intent signInIntent = Auth.GoogleSignInApi
                .getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_ACCT);

        Log.d(TAG, "Done with processRequest!");
    }

    private void buildClient(TokenRequest request) {
        GoogleSignInOptions.Builder builder;

        if (request.useGamesConfig) {
            builder = new GoogleSignInOptions
                    .Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        } else {
            builder = new GoogleSignInOptions
                    .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN);
        }

        if (request.doAuthCode) {
            if (!request.getWebClientId().isEmpty()) {
                builder.requestServerAuthCode(request.getWebClientId(),
                        request.getForceRefresh());
            } else {
                Log.e(TAG, "Web client ID is needed for Auth Code");
                request.setResult(CommonStatusCodes.DEVELOPER_ERROR);
                synchronized (lock) {
                    pendingTokenRequest = null;
                }
                return;
            }
        }

        if (request.doEmail) {
            builder.requestEmail();
        }

        if (request.doIdToken) {
            if (!request.getWebClientId().isEmpty()) {
                builder.requestIdToken(request.getWebClientId());
            } else {
                Log.e(TAG, "Web client ID is needed for ID Token");
                request.setResult(CommonStatusCodes.DEVELOPER_ERROR);
                synchronized (lock) {
                    pendingTokenRequest = null;
                }
                return;
            }
        }
        if (request.scopes != null) {
            for (String s : request.scopes) {
                builder.requestScopes(new Scope(s));
            }
        }

        if (request.hidePopups && request.useGamesConfig) {
            Log.d(TAG, "hiding popup views for games API");
            builder.addExtension(
                    Games.GamesOptions.builder().setShowConnectingPopup(false)
                            .build());
        }

        if (request.accountName != null) {
            builder.setAccountName(request.accountName);
        }

        GoogleSignInOptions options = builder.build();

        GoogleApiClient.Builder clientBuilder = new GoogleApiClient.Builder(
                getActivity())
                .addApi(Auth.GOOGLE_SIGN_IN_API, options);
        if (request.useGamesConfig) {
            clientBuilder.addApi(Games.API);
        }
        if (request.hidePopups) {
            View invisible = new View(getContext());
            invisible.setVisibility(View.INVISIBLE);
            invisible.setClickable(false);
            clientBuilder.setViewForPopups(invisible);
        }
        mGoogleApiClient = clientBuilder.build();
        mGoogleApiClient.connect(GoogleApiClient.SIGN_IN_MODE_OPTIONAL);
    }

    /**
     * Receive the result from a previous call to
     * {@link #startActivityForResult(Intent, int)}.  This follows the
     * related Activity API as described there in
     * {@link Activity#onActivityResult(int, int, Intent)}.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        if (requestCode == RC_ACCT) {
            GoogleSignInResult result =
                    Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            TokenRequest request;
            synchronized (lock) {
                request = pendingTokenRequest;
                pendingTokenRequest = null;
            }
            GoogleSignInAccount acct = result.getSignInAccount();
            if (request != null) {
                if (acct != null) {
                    request.setAuthCode(acct.getServerAuthCode());
                    request.setEmail(acct.getEmail());
                    request.setIdToken(acct.getIdToken());
                }
                request.setResult(result.getStatus().getStatusCode());
            } else {
                Log.w(TAG, "Pending request is null, can't return result!");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        // This just connects the client.  If there is no user signed in, you
        // still need to call Auth.GoogleSignInApi.getSignInIntent() to start
        // the sign-in process.
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect(GoogleApiClient.SIGN_IN_MODE_OPTIONAL);
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     * This is generally
     * tied to {@link Activity#onResume() Activity.onResume} of the containing
     * Activity's lifecycle.
     */
    @Override
    public void onResume() {
        Log.d(TAG, "onResume called");
        super.onResume();
        if (helperFragment == null) {
            helperFragment = this;
        }
        processRequest();
    }

    /**
     * Helper class containing the request for information.
     */
    private static class TokenRequest {
        private TokenPendingResult pendingResponse;
        private boolean useGamesConfig;
        private boolean doAuthCode;
        private boolean doEmail;
        private boolean doIdToken;
        private String webClientId;
        private boolean forceRefresh;
        private boolean hidePopups;
        private String accountName;
        private String[] scopes;

        public TokenRequest(boolean useGamesConfig, String webClientId,
                            boolean requestAuthCode, boolean forceRefreshToken,
                            boolean requestEmail, boolean requestIdToken,
                            boolean hidePopups, String accountName,
                            String[] additionalScopes) {
            pendingResponse = new TokenPendingResult();
            this.useGamesConfig = useGamesConfig;
            this.webClientId = webClientId;
            this.doAuthCode = requestAuthCode;
            this.forceRefresh = forceRefreshToken;
            this.doEmail = requestEmail;
            this.doIdToken = requestIdToken;
            this.hidePopups = hidePopups;
            this.accountName = accountName;
            if (additionalScopes != null && additionalScopes.length > 0) {
                scopes = new String[additionalScopes.length];
                System.arraycopy(additionalScopes, 0, scopes, 0, additionalScopes.length);
            } else {
                scopes = null;
            }
        }

        public PendingResult<TokenResult> getPendingResponse() {
            return pendingResponse;
        }

        public void setResult(int code) {
            pendingResponse.setStatus(code);
        }

        public void setEmail(String email) {
            pendingResponse.setEmail(email);
        }

        public void cancel() {
            pendingResponse.cancel();
        }

        public void setAuthCode(String authCode) {
            pendingResponse.setAuthCode(authCode);
        }

        public void setIdToken(String idToken) {
            pendingResponse.setIdToken(idToken);
        }

        public String getEmail() {
            return pendingResponse.result.getEmail();
        }

        public String getIdToken() {
            return pendingResponse.result.getIdToken();
        }

        public String getAuthCode() {
            return pendingResponse.result.getAuthCode();
        }

        @Override
        public String toString() {
            return Integer.toHexString(hashCode()) + " (a:" +
                    doAuthCode + " e:" + doEmail + " i:" + doIdToken +
                    ")";
        }

        public String getWebClientId() {
            return webClientId == null ? "" : webClientId;
        }

        public boolean getForceRefresh() {
            return forceRefresh;
        }

        public boolean isValid() {
            if (webClientId == null || webClientId.isEmpty()) {
                if (doAuthCode) {
                    Log.e(TAG, "Invalid configuration, auth code requires web " +
                            "client id");
                    return false;
                } else if (doIdToken) {
                    Log.e(TAG, "Invalid configuration, id token requires web " +
                            "client id");
                    return false;
                }
            }
            return true;
        }
    }
}
