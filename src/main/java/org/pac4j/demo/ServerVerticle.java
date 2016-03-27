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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.templ.HandlebarsTemplateEngine;
import org.pac4j.core.config.Config;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.handler.impl.CallbackHandler;
import org.pac4j.vertx.handler.impl.Pac4jAuthHandlerOptions;
import org.pac4j.vertx.handler.impl.RequiresAuthenticationHandler;

import java.util.Optional;

/**
 * @author Jeremy Prime
 * @since 1.0.0
 */
public class ServerVerticle extends AbstractVerticle {

    private static final String SESSION_KEY_ORIGINALLY_REQUESTED_URL = "demoOriginalUrl";
    private static final String LOGIN_URL = "/login";
    private static final String DEFAULT_PROTECTED_LANDING_URL = "/protected/index.html";
    private final Pac4jAuthProvider authProvider = new Pac4jAuthProvider(); // We don't need to instantiate this on demand
    private final HandlebarsTemplateEngine templateEngine = HandlebarsTemplateEngine.create();


    @Override
    public void start() throws Exception {
        super.start();
        final Config config = new Pac4jConfigFactory().build();

        final Router router = Router.router(vertx);

        final SessionStore sessionStore = LocalSessionStore.create(vertx);
        final SessionHandler sessionHandler = SessionHandler.create(sessionStore);

        // Only use the following handlers where we want to use sessions - this is enforced by the regexp
        router.route().handler(io.vertx.ext.web.handler.CookieHandler.create());
        router.route().handler(sessionHandler);
        router.route().handler(UserSessionHandler.create(authProvider));

        router.route("/login").handler(loginHandler(templateEngine));
        router.get("/loginForm").handler(loginFormHandler(config));

        router.route("/protected/*").handler(redirectHandler(authProvider));
        final Pac4jAuthHandlerOptions options = new Pac4jAuthHandlerOptions().withClientName("FormClient,FacebookClient");
        router.get("/protected/*").handler(new RequiresAuthenticationHandler(vertx, config, authProvider,
                options));
        final CallbackHandler callbackHandler = new CallbackHandler(vertx, config);
        router.get("/callback").handler(callbackHandler); // This will deploy the callback handler
        router.post("/callback").handler(BodyHandler.create().setMergeFormAttributes(true));
        router.post("/callback").handler(callbackHandler);

        final StaticHandler staticHandler = StaticHandler.create("webapp").setCachingEnabled(false);
        router.route("/*").handler(staticHandler);

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }

    private static Handler<RoutingContext> redirectHandler(final AuthProvider authProvider) {
        //return RedirectAuthHandler.create(authProvider, LOGIN_URL, SESSION_KEY_ORIGINALLY_REQUESTED_URL);
        // Can't use classic vert.x RedirectAuthHandler as we want to filter on whether client_name param is set
        return rc -> {
            if (rc.user() == null && !(rc.request().params().contains("client_name"))) {
                if(rc.session() == null) {
                    System.out.println("No session, have you properly configured sessions and cookies?");
                } else {
                    rc.session().put(SESSION_KEY_ORIGINALLY_REQUESTED_URL, rc.request().path());
                    rc.response().putHeader("location", LOGIN_URL).setStatusCode(302).end();
                }

            } else {
                rc.next(); // We'll delegate to pac4j now without doing any more
            }
        };
    }

    private static Handler<RoutingContext> loginFormHandler(final Config config) {
        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create();
        final FormClient formClient = (FormClient) config.getClients().findClient("FormClient");
        final String url = formClient.getCallbackUrl();

        return rc -> {
            rc.put("url", url);
            engine.render(rc, "templates/loginForm.hbs", res -> {
                if (res.succeeded()) {
                    rc.response().end(res.result());
                } else {
                    rc.fail(res.cause());
                }
            });
        };
    }


    private static Handler<RoutingContext> loginHandler(final HandlebarsTemplateEngine templateEngine) {
        // Use a template to generate the urls we'll hit for the different login mechanisms
        return rc -> {
            // if we already have an authenticated user, redirect to the landing resource
            if (rc.user() != null) {
                rc.response().putHeader("location", DEFAULT_PROTECTED_LANDING_URL).setStatusCode(302).end();
            } else {
                // Using remove in the line below because the login handler is responsible for using this url
                final String origUrl = Optional.ofNullable(rc.session().<String>remove(SESSION_KEY_ORIGINALLY_REQUESTED_URL))
                        .orElse(DEFAULT_PROTECTED_LANDING_URL);
                rc.put("originalUrl", origUrl);
                // Now let's use a template to generate the appropriate urls for the different authentication mechanisms
                // and now delegate to the engine to render it.
                templateEngine.render(rc, "templates/login.hbs", res -> {
                    if (res.succeeded()) {
                        rc.response().end(res.result());
                    } else {
                        rc.fail(res.cause());
                    }
                });
            }

        };
    }
}
