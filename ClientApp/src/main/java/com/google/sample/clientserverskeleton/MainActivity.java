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
package com.google.sample.clientserverskeleton;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.sample.clientserverskeleton.client.BackendClient;
import com.google.sample.clientserverskeleton.client.ClientResultListener;
import com.google.sample.clientserverskeleton.model.ServerPlayer;

import java.text.MessageFormat;

/**
 * Skeleton activity demonstrating how to sign into a Play Game Services game
 * and pass a serverAuthCode to a backend server, where it can be exchanged for
 * and access token.
 */
public class MainActivity extends Activity implements View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // For logging.
    private static final String TAG = "clientserversample";

    // Request code for getting the result from the sign-in intent.
    private static final int RC_SIGN_IN = 9876;

    // UI elements
    TextView status;
    Button signinButton;
    Button serverButton;
    Button getPlayerButton;

    // GoogleAPI client object.
    private GoogleApiClient mGoogleApiClient;

    // Object encapsulating the communication with the backend server.
    private BackendClient backendClient;

    // The server auth code received from the Sign-In API, which is
    // passed to the backend server.
    private String serverAuthCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.statustext);

        signinButton = (Button) findViewById(R.id.signin);
        signinButton.setOnClickListener(this);

        serverButton = (Button) findViewById(R.id.sendtoserver);
        serverButton.setOnClickListener(this);
        serverButton.setEnabled(false);

        getPlayerButton = (Button) findViewById(R.id.getplayer);
        getPlayerButton.setOnClickListener(this);
        getPlayerButton.setEnabled(false);

        // Check the local preferences to see if the server needs a fresh
        // refresh token.  Ususally, you should not force a refresh token since
        // it will force the user to be shown the the consent screen again
        // and they will need to accept.  Instead, you should persist the
        // refresh token on the server and use it when the access token has
        // expired.
        boolean forceCodeForRefreshToken = shouldForceRefresh();

        String webclientId = getString(R.string.webclient_id);
        // Request authCode so we can send the code to the server.
        GoogleSignInOptions options = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestServerAuthCode(webclientId, forceCodeForRefreshToken)
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Games.API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, options)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        serverAuthCode = null;
        backendClient = new BackendClient(getString(R.string.host_url), this);
    }

    /**
     * onStart is overridden to manage the state of the google API client.
     * Another option rather than managing it explicitly is to move the
     * client management to a Fragment and use the autoManage option.
     */
    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        // Not needed in an actual app, but since there is a lot of external
        // configuration for this sample, check that it is configured.
        boolean shouldAutoConnect = validateSampleConfigurationSet();

        // This just connects the client.  If there is no user signed in, you
        // still need to call Auth.GoogleSignInApi.getSignInIntent() to start
        // the sign-in process.
        if (shouldAutoConnect) {
            mGoogleApiClient.connect(GoogleApiClient.SIGN_IN_MODE_OPTIONAL);
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode,
                                    Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            Log.d(TAG, "onActivityResult RC_SIGN_IN, responseCode="
                    + responseCode + ", intent=" + intent);
            GoogleSignInResult result =
                    Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
            if (result.isSuccess()) {
                onSignedIn(result.getSignInAccount(), null);
            } else {
                showSignInError(result.getStatus().getStatusCode());
            }
        } else {
            super.onActivityResult(requestCode, responseCode, intent);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.signin:
                // start the sign-in flow
                if (!mGoogleApiClient.hasConnectedApi(Games.API)) {
                    Log.d(TAG, "Sign-in button clicked");
                    handleSignin();
                } else {
                    handleSignOut();
                }
                break;
            case R.id.sendtoserver:
                handleSendToServer();
                break;
            case R.id.getplayer:
                handleGetServerPlayer();
                break;
        }
    }

    @Override
    public void onConnected(@Nullable final Bundle connectionHint) {
        if (mGoogleApiClient.hasConnectedApi(Games.API)) {
            Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient).setResultCallback(
                    new ResultCallback<GoogleSignInResult>() {
                        @Override
                        public void onResult(
                                @NonNull GoogleSignInResult googleSignInResult) {
                            if (googleSignInResult.isSuccess()) {
                                onSignedIn(googleSignInResult.getSignInAccount(),
                                        connectionHint);
                            } else {
                                Log.e(TAG, "Error with silentSignIn: " +
                                        googleSignInResult.getStatus());
                                // Don't show a message here, this only happens
                                // when the user can be authenticated, but needs
                                // to accept consent requests.
                                handleSignOut();
                            }
                        }
                    }
            );
        } else {
            handleSignOut();
        }
    }

    /**
     * Does nothing, but the interface requires an implementation.  A typical
     * application would disable any UI that requires a connection.  When the
     * connection is restored, onConnected will be called.
     *
     * @param cause - The reason of the disconnection.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended() called: " + cause);
    }

    /**
     * Show a message that it failed.
     *
     * @param result - The result information.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed() called, result: " + result);

        showSignInError(result.getErrorCode());

        signinButton.setText(getString(R.string.signin));
        serverButton.setEnabled(false);
    }

    /**
     * Show a meaningful error when sign-in fails.
     *
     * @param errorCode - The errorCode.
     */
    public void showSignInError(int errorCode) {
        Dialog dialog = GoogleApiAvailability.getInstance()
                .getErrorDialog(this, errorCode, RC_SIGN_IN);
        if (dialog != null) {
            dialog.show();
        } else {
            // no built-in dialog: show the fallback error message
            (new AlertDialog.Builder(this))
                    .setMessage("Could not sign in!")
                    .setNeutralButton(android.R.string.ok, null)
                    .show();
        }
    }

    /**
     * Handle being signed in successfully.  The account information is
     * populated from the Auth API, and now Games APIs can be called.
     * <p/>
     * Here the server auth code is read from the account.
     * <p>
     * </p>
     *
     * @param acct   - the Google account information.
     * @param bundle - the connection Hint.
     */
    public void onSignedIn(GoogleSignInAccount acct, @Nullable Bundle bundle) {
        Log.d(TAG, "onConnected() called. Sign in successful!");
        signinButton.setText(R.string.sign_out);

        serverButton.setEnabled(true);
        getPlayerButton.setEnabled(true);

        status.setText(
                MessageFormat.format("Connected as {0}",
                        Games.Players.getCurrentPlayer(
                                mGoogleApiClient).getDisplayName()));
        serverAuthCode = acct.getServerAuthCode();
        setForceRefresh(false);
    }

    private void handleSignOut() {
        // sign out.
        Log.d(TAG, "Sign-out button clicked");
        if (mGoogleApiClient.hasConnectedApi(Games.API)) {
            Games.signOut(mGoogleApiClient);
            Auth.GoogleSignInApi.signOut(mGoogleApiClient);
        }

        /// dont do this  mGoogleApiClient.disconnect();
        signinButton.setText(getString(R.string.signin));
        serverButton.setEnabled(false);
        getPlayerButton.setEnabled(false);
        status.setText(R.string.disconnected);
    }

    private void handleSignin() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    /**
     * Check to see if we set a flag to force the user to re-consent and allow
     * the backend server to get another refresh token.  It is stored in the
     * preferences so we can consult its value when the application is
     * restarted.
     * @return true to force getting a refresh token.
     */
    private boolean shouldForceRefresh() {
        return getPreferences(MODE_PRIVATE).getBoolean("ForceRefresh", false);
    }

    /**
     * Sets the state of forcing the user to re-consent when the application
     * starts.  This flag is set based on the response from the backend server
     * which will pass a flag when it requires the refresh token to be sent.
     * @param flag the value.
     */
    private void setForceRefresh(boolean flag) {
        getPreferences(MODE_PRIVATE).edit().putBoolean
                ("ForceRefresh", flag).apply();
    }

    /**
     * Listener member for handling results when requesting the ServerPlayer
     * object from the backend server.  It is broken out into a separate member
     * since it is used in multiple places.
     */
    private ClientResultListener<ServerPlayer> onServerPlayerResult =
            new ClientResultListener<ServerPlayer>() {
                @Override
                public void onSuccess(ServerPlayer player) {
                    status.setText(MessageFormat.format(
                            "Player info from server: {0}", player));
                    // If the server indicates it needs a refresh token for this
                    // player, record that for next time.  The user will need
                    // to sign back in and re-consent, so it should only be done
                    // when it makes sense in terms of app experience.
                    setForceRefresh(player.getNeedRefreshToken());
                }

                @Override
                public void onFailure(int error, String msg) {
                    status.setText("ERROR " + error +
                            ": " + msg);
                }
            };

    /**
     * Gets the server auth code from the Games API, and then passes it along
     * to the backend server.
     */
    private void handleSendToServer() {
        backendClient.sendAuthCode(
                Games.Players.getCurrentPlayerId(mGoogleApiClient),
                serverAuthCode,
                onServerPlayerResult
        );
    }

    /**
     * Gets the player info from the server.  This will return 403 if
     * the authCode has not been sent to the server. This is done as a security
     * measure so not just anyone can send a request.  An actual server should
     * use SSL and make sure the cookies are secure.
     */
    private void handleGetServerPlayer() {
        String playerId = Games.Players.getCurrentPlayerId(mGoogleApiClient);
        backendClient.getPlayer(playerId, onServerPlayerResult);
    }

    /**
     * Just for use in the sample app, this checks that the external
     * configurations were changed (hopefully to correct values!).
     */
    private boolean validateSampleConfigurationSet() {

        String message = null;

        if (getPackageName().endsWith(".replaceme")) {
            message = "applicationId in build.gradle not set to an actual " +
                    "value!";
        } else if (getString(R.string.host_url).equals("ReplaceMe")) {
            message = "host_url in res/values/serverconfig.xml not set to an " +
                    "actual value!";
        } else if (getString(R.string.webclient_id).equals("ReplaceMe")) {
            message = "webclient_id in res/values/serverconfig.xml not set to" +
                    " an actual value!";
        } else if (getString(R.string.app_id).equals("ReplaceMe")) {
            message = "app_id in res/values/game-ids.xml not set to" +
                    " an actual value!";
        }

        Log.e(TAG, "Error in configuration: " + message);
        if (message != null) {
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setNeutralButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        DialogInterface dialog, int which) {
                                    throw new IllegalStateException(
                                            getString(
                                                    R.string.app_misconfigured));
                                }
                            }).show();
        }
        return message == null;
    }
}