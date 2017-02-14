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

import org.apache.log4j.BasicConfigurator;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.SessionHandler;

/**
 * This is a simplistic Servlet server implemented using Jetty.
 * It is not intended to be a fully featured web server application, but
 * be a minimal container to host the servlets that implement the REST API
 * for demonstrating how to make calls to a backend server on behalf of an
 * authenticated player on a client.
 */
public class GameServer {

    // Port to handle HTTP requests on, change as needed.
    private static final int DEFAULT_HTTP_PORT = 8765;

    /**
     * Register all endpoints that we'll handle in our server.
     * @param args Command-line arguments.
     * @throws Exception from Jetty if the component fails to start
     */
    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();

        int port = DEFAULT_HTTP_PORT;
        for(int i=0;i<args.length;i++) {
            if (args[i].equalsIgnoreCase("-p")) {
                if (i +1 < args.length) {
                    try {
                        port = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        usage();
                        return;
                    }
                } else {
                    usage();
                    return;
                }
            } else {
                usage();
                return;
            }
        }
        Server server = new Server(port);
        ServletHandler servletHandler = new ServletHandler();
        SessionHandler sessionHandler = new SessionHandler();
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.addHandler(servletHandler);
        sessionHandler.setHandler(contextHandler);
        server.addHandler(sessionHandler);

        // Map the servlets to the REST API.
        servletHandler.addServletWithMapping(PlayerServlet.class, "/player/*");

        // Start the server, and then wait for it to end.
        server.start();
        server.join();
    }

    private static void usage() {
        System.err.println("Usage: " + GameServer.class.getName());
        System.err.println("\t [-p portnum]\t listens on <portnum> for " +
                "requests.  Uses " + DEFAULT_HTTP_PORT + " if not specified");
    }
}
