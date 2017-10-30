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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.sample.clientserverskeleton.client.BackendClient;
import com.google.sample.clientserverskeleton.client.ClientResultListener;
import com.google.sample.clientserverskeleton.model.ServerPlayer;

import java.text.MessageFormat;

/**
 * Skeleton activity demonstrating how to sign into a Play Game Services game
 * and pass a mServerAuthCode to a backend server, where it can be exchanged for
 * and access token.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    // For logging.
    private static final String TAG = "clientserversample";

    // Request code for getting the result from the sign-in intent.
    private static final int RC_SIGN_IN = 9876;

    // UI elements
    TextView status;
    Button signinButton;
    Button serverButton;
    Button getPlayerButton;

    // GoogleSignInClient manages the user sign-in and server auth code access.
    private GoogleSignInClient mSignInClient;

    // Object encapsulating the communication with the backend server.
    private BackendClient backendClient;

    // The server auth code received from the Sign-In API, which is
    // passed to the backend server.
    private String mServerAuthCode;

    // The player Id from the Games API
    private String mCurrentPlayerId;


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

        String webclientId = getString(R.string.webclient_id);

        // Request authCode so we can send the code to the server.
        GoogleSignInOptions options = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                .requestServerAuthCode(webclientId)
                .build();

        mSignInClient = GoogleSignIn.getClient(this, options);

        mServerAuthCode = null;
        backendClient = new BackendClient(getString(R.string.host_url), this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Attempt to sign-in silently.
        signInSilently();
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
        validateSampleConfigurationSet();
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
                onSignedIn(result.getSignInAccount());
            } else {
                showExceptionMessage(new ApiException(result.getStatus()));
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
                if (GoogleSignIn.getLastSignedInAccount(this) == null) {
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

    /**
     * Attempt to sign in silently.  This should be called in onResume(), and whenever another
     * mServerAuthCode is needed.
     */
    private void signInSilently() {
        mSignInClient.silentSignIn().addOnCompleteListener(this,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            onSignedIn(task.getResult());
                        } else {
                            Log.d(TAG,"silent sign-in failed: " + task.getException());
                        }
                    }
                });
    }

    /**
     * Show a meaningful error when sign-in fails.
     *
     * @param exception - The exception.
     */
    public void showExceptionMessage(Exception exception) {
        Log.e(TAG, "Exception: " + exception.getMessage(), exception);
        String errorMessage = null;
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            int errorCode = apiException.getStatusCode();
            errorMessage = GoogleApiAvailability.getInstance().getErrorString(errorCode);
        }

        if (errorMessage == null) {
            errorMessage = "Exception encountered!";
        }
        (new AlertDialog.Builder(this))
                .setMessage(errorMessage)
                .setNeutralButton(android.R.string.ok, null)
                .show();
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
     */
    public void onSignedIn(GoogleSignInAccount acct) {
        Log.d(TAG, "onConnected() called. Sign in successful!");
        signinButton.setText(R.string.sign_out);

        PlayersClient playersClient = Games.getPlayersClient(this, acct);
        playersClient.getCurrentPlayer().addOnCompleteListener(new OnCompleteListener<Player>() {
            @Override
            public void onComplete(@NonNull Task<Player> task) {
                serverButton.setEnabled(task.isSuccessful());
                getPlayerButton.setEnabled(task.isSuccessful());
                if (task.isSuccessful()) {
                    mCurrentPlayerId = task.getResult().getPlayerId();
                    status.setText(
                            MessageFormat.format("Connected as {0}",
                                    task.getResult().getDisplayName()));
                } else {
                    Log.e(TAG,"Error getting player: " + task.getException());
                }
            }
        });

        // Save the server auth code to send to the server.
        mServerAuthCode = acct.getServerAuthCode();
    }

    private void handleSignOut() {
        // sign out.
        Log.d(TAG, "Sign-out button clicked");
        mSignInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                signinButton.setText(getString(R.string.signin));
                serverButton.setEnabled(false);
                getPlayerButton.setEnabled(false);
                mCurrentPlayerId = null;
                status.setText(R.string.disconnected);
            }
        });
    }

    private void handleSignin() {
        startActivityForResult(mSignInClient.getSignInIntent(), RC_SIGN_IN);
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
                }

                @Override
                public void onFailure(int error, String msg) {
                    status.setText("ERROR " + error +
                            ": " + msg);
                }
            };

    /**
     * Gets the server auth code from the sign-in client and then sends it to the server.
     */
    private void handleSendToServer() {
        // If we already have a code, use it.  Each auth code is single-use, so once we send it
        // null out the auth code, so next time we'll call sign-in silently to get a new code.
        if (mServerAuthCode != null) {
            backendClient.sendAuthCode(
                    mCurrentPlayerId,
                    mServerAuthCode,
                    onServerPlayerResult
            );
            mServerAuthCode = null;
        } else {
            // Call silent-signin and when it returns send the code to the server.
            mSignInClient.silentSignIn().addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
                @Override
                public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                    if (task.isSuccessful()) {
                        String authCode = task.getResult().getServerAuthCode();
                        backendClient.sendAuthCode(mCurrentPlayerId,
                                authCode,
                                onServerPlayerResult);
                    } else {
                        showExceptionMessage(task.getException());
                    }
                }
            });
        }
    }

    /**
     * Gets the player info from the server.  This will return 403 if
     * the authCode has not been sent to the server. This is done as a security
     * measure so not just anyone can send a request.  An actual server should
     * use SSL and make sure the cookies are secure.
     */
    private void handleGetServerPlayer() {
        backendClient.getPlayer(mCurrentPlayerId, onServerPlayerResult);
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
