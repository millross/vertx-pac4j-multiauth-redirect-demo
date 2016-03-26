/*
  Copyright 2015 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.demo;

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.oauth.client.FacebookClient;

/**
 * @author Jeremy Prime
 * @since 1.0.0
 */
public class Pac4jConfigFactory implements ConfigFactory {

    @Override
    public Config build() {

        final String baseUrl = "http://localhost:8080";
        final Clients clients = new Clients(baseUrl + "/callback",
                // oAuth clients
                facebookClient(),
                formClient(baseUrl));
        final Config config = new Config(clients);
        return config;
    }

    public static FacebookClient facebookClient() {
        // Reusing parameters from vertx-pac4j-demo
        final String fbId = "161172184232419";
        final String fbSecret = "282fb4ff040eead3c049e3c665117ef8";
        return new FacebookClient(fbId, fbSecret);
    }

    public static FormClient formClient(final String baseUrl) {
        return new FormClient(baseUrl + "/loginForm", new SimpleTestUsernamePasswordAuthenticator());
    }

}
