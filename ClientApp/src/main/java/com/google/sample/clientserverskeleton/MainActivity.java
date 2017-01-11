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
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.sample.clientserverskeleton.client.BackendClient;
import com.google.sample.clientserverskeleton.client.ClientResultListener;
import com.google.sample.clientserverskeleton.model.ServerPlayer;

/**
 * Skeleton activity demonstrating how to sign into a Play Game Services game
 * and pass a serverAuthCode to a backend server, where it can be exchanged for
 * and access token.
 */
public class MainActivity extends Activity implements View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "clientserversample";
    private static final int RC_SIGN_IN = 9876;

    // UI elements
    TextView status;
    Button signinButton;
    Button serverButton;
    Button getPlayerButton;

    // GoogleAPI client object and related book-keeping objects.
    private boolean mSignInClicked;
    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingConnectionFailure;

    // Object encapsulating the communication with the backend server.
    private BackendClient backendClient;

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

        // Create the Google Api Client with access to Plus and Games
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .build();

        backendClient = new BackendClient(getString(R.string.host_url), this);

    }

    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        // Not needed in an actual app, but since there is a lot of external
        // configuration for this sample, check that it is configured.
        boolean shouldAutoConnect = validateSampleConfigurationSet();

        if (shouldAutoConnect) {
            mGoogleApiClient.connect();
        }
    }

    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected() called. Sign in successful!");
        signinButton.setText("Sign-out");

        serverButton.setEnabled(true);
        getPlayerButton.setEnabled(true);

        status.setText("Connected as " + Games.Players.getCurrentPlayer
                (mGoogleApiClient).getDisplayName());
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended() called: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed() called, result: " + result);

        if (mResolvingConnectionFailure) {
            Log.d(TAG, "onConnectionFailed() ignoring; already resolving.");
            return;
        }

        if (mSignInClicked) {
            mSignInClicked = false;
            if (result.hasResolution()) {
                try {
                    result.startResolutionForResult(this, RC_SIGN_IN);
                    mResolvingConnectionFailure = true;
                } catch (IntentSender.SendIntentException e) {
                    // The intent was canceled before it was sent.
                    // Return to the default state and attempt to connect to
                    // get an updated ConnectionResult.
                    mGoogleApiClient.connect();
                    mResolvingConnectionFailure = false;
                }
            } else {
                // not resolvable... so show an error message
                int errorCode = result.getErrorCode();
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
                mResolvingConnectionFailure = false;
            }
        }

        signinButton.setText(getString(R.string.signin));
        serverButton.setEnabled(false);
    }

    protected void onActivityResult(int requestCode, int responseCode,
                                    Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            mSignInClicked = false;
            mResolvingConnectionFailure = false;
            if (responseCode == RESULT_OK) {
                mGoogleApiClient.connect();
            } else {
                showActivityResultError(requestCode, responseCode);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.signin:
                handleSignin();
                break;
            case R.id.sendtoserver:
                handleSendToServer();
                break;
            case R.id.getplayer:
                handleGetServerPlayer();
                break;
        }
    }

    private void handleSignin() {
        // start the sign-in flow
        if (mGoogleApiClient.isConnected()) {
            // sign out.
            Log.d(TAG, "Sign-out button clicked");
            mSignInClicked = false;
            Games.signOut(mGoogleApiClient);
            mGoogleApiClient.disconnect();
            signinButton.setText(getString(R.string.signin));
            serverButton.setEnabled(false);
            getPlayerButton.setEnabled(false);
            status.setText("Disconnected");
        } else {
            Log.d(TAG, "Sign-in button clicked");
            mSignInClicked = true;
            mGoogleApiClient.connect();
        }
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
                    status.setText("Player info from server: " + player);
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
        String webClientId = getString(R.string.webclient_id);
        Games.getGamesServerAuthCode(mGoogleApiClient, webClientId)
                .setResultCallback(
                        new ResultCallback<Games.GetServerAuthCodeResult>() {
                            @Override
                            public void onResult(@NonNull Games
                                    .GetServerAuthCodeResult result) {
                                backendClient.sendAuthCode(
                                        Games.Players.getCurrentPlayerId
                                                (mGoogleApiClient),
                                        result.getCode(), onServerPlayerResult
                                );
                            }
                        }
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
     * Show a {@link android.app.Dialog} with the correct message
     * for a connection error.
     *
     * @param requestCode the request code from onActivityResult.
     * @param actResp     the response code from onActivityResult.
     */
    public void showActivityResultError(int requestCode, int actResp) {
        Dialog errorDialog = null;
        String message = getString(R.string.signin_other_error);

        switch (actResp) {
            case GamesActivityResultCodes.RESULT_APP_MISCONFIGURED:
                message = getString(R.string.app_misconfigured);
                break;
            case GamesActivityResultCodes.RESULT_SIGN_IN_FAILED:
                message = getString(R.string.signin_failed);
                break;
            case GamesActivityResultCodes.RESULT_LICENSE_FAILED:
                message = getString(R.string.license_failed);
                break;
            default:
                // No meaningful Activity response code,
                // so generate default Google Play services dialog
                GoogleApiAvailability instance =
                        GoogleApiAvailability.getInstance();
                final int errorCode = instance
                        .isGooglePlayServicesAvailable(this);
                errorDialog = instance
                        .getErrorDialog(this, errorCode, requestCode, null);
        }

        if (errorDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            errorDialog = builder.setMessage(message)
                    .setNeutralButton(android.R.string.ok, null)
                    .create();
        }

        errorDialog.show();
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
                                public void onClick(DialogInterface dialog, int which) {
                                    throw new IllegalStateException(
                                            getString(R.string.app_misconfigured));
                                }
                            }).show();
        }
        return message == null;
    }
}