# Play Game Services Client Server Skeleton Sample
_Copyright (c) 2017 Google Inc. All rights reserved._


## Overview

This sample demonstrates how to obtain an OAuth server auth code on an
Android device and send it to a backend server application.  The backend server
then exchanges the auth code for an access token, which can be used to make
calls to the Play Game Services Web API on behalf of the authenticated user.

The client is an Android application that uses the Play Game Services API to
sign in and interact with the Games API as normal.  It requests the OAuth
server auth code that is sent to the server.

The server in the sample is a Java application that runs Jetty as the servlet
container.  This is done for the sample since it is easy to start and run.
In practice, you would run the Servlet on a real server or on
  [Google App Engine](https://cloud.google.com/appengine/docs).

## Configuring the game on the Developer Console

Configuring a game with a backend server requires three steps:
1. [Configure the game](https://developers.google.com/games/services/console/enabling)
  in the Google Play Developer Console.
2. Follow the instructions for [creating a client ID for Android](https://developers.google.com/games/services/android/quickstart#step_2_set_up_the_game_in_the_dev_console).
 Be particularly careful when entering your package name and your certificate
 fingerprints, since mistakes on those screens can be difficult to recover from.
3. Create an associated web app.  If you don't know the address of the
  server when creating credentials for the web app on the console,
  you can enter a valid URL (localhost is not accepted!).  Once the address is
  known, you can modify the credentials.

## Building the server application

### Add the Run/Debug configuration for the server

1. In Android Studio, click Run/Run... on the menu and select
'0 Edit Configurations...'.
2. Click the plus (+) above the list of configurations and select 'Application'.
3. Change the name to 'Server'.
4. Click the selection button next to Main Class and select
'GameServer (com.google.sample.games)'.
5. In the dropdown named "Classpath of Module", select 'ServerApp'.
6. Press 'Run'.

This should start the server app an in the run window in Android Studio.
You should see some logging messages, such as `Started SocketConnector@0.0.0.0:8765`.

To verify that the server is running, retrieve the test player at
[http://localhost:8765/player/test](http://localhost:8765/player/test).

### Making the server accessible to the client
In order for the client application on the device to access the sample
server application, the server application needs to be running on a server
which has an address that resolvable from the device.  You can test this on the device
by using the web browser.

Some common ways to set this up are:
* On a public web server.
* A web server that is known to the DNS servers configured by your local wifi.
* IP address that reachable from the device.
* Using a port forwarder application such as [Ngrok](https://ngrok.com/).

Once you have the address of the running server application.  Add it to the
credentials of your web app on the Play Console.
Once that is done, download the client_secret.json file
for your application and replace the sample client_secret.json with yours.

Restart your server application so it will be ready for the client application.

## Building the client application

You'll need to configure the external resources for the client app in order
to run the sample:

1. In res/values/serverconfig.xml set the webclient_id to the client id of
the associated web app configured in the Google Play Developer Console.  Also
set host_url to the address of the server running the sample backend app.
2. In the Google Play Developer Console, edit the web app credentials and add
the host_url value to the list of addresses for the web app.
3. In the Google Play Developer Console, select a game resource (such as Events)
and click on 'Get Resources'. Copy the Android resources to the clipboard and
paste the resources into res/values/game-ids.xml.
4. Change the applicationId in ClientApp/build.gradle to match the package name
configured for the associated Android App in the console.

Once the above steps are done, run the ClientApp on a device.

## Testing the client-server flow.

To validate that everything is working:

1. Make sure the server app is running by starting it and going to
    <host_url>/player/test.
2. Start the client app, and press 'Sign In'.  This will sign in the client to
Play Game Services on the device.
3. In the client app, press Post AuthCode to server.  This will get an authcode
and send it to the server, which will respond with the player object.



## Questions? Problems?
Post questions to this [Github project](https://github.com/playservices/clientserverskeleton/issues).

