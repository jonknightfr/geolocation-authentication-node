
/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */
/**
 * jon.knight@forgerock.com
 *
 * A node that captures compatible browsers geolocation.
 */

package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import javax.security.auth.callback.TextOutputCallback;

import com.sun.identity.shared.debug.Debug;
import com.google.common.collect.ImmutableList;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import java.util.Optional;

import static org.forgerock.openam.auth.node.api.Action.send;
import org.forgerock.json.JsonValue;




@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
    configClass = GetNewGeoLocationNode.Config.class)
public class GetNewGeoLocationNode extends AbstractDecisionNode {

    private final static String DEBUG_FILE = "GetNewGeoLocationNode";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private static final String BUNDLE = "org/forgerock/openam/auth/nodes/GetNewGeoLocationNode";

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100)
        default Boolean nativeMethods() { return false; }
        @Attribute(order = 200)
        default String result() { return "location"; }

    }

    private final Config config;

    /**
     * Guice constructor.
     * @param config The node configuration.
     * @throws NodeProcessException If there is an error reading the configuration.
     */
    @Inject
    public GetNewGeoLocationNode(@Assisted Config config) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        String script =
            "var callbackScript = document.createElement(\"script\");\n" +
            "callbackScript.type = \"text/javascript\";\n" +
            "callbackScript.text = \"function completed() { document.querySelector(\\\"input[type=submit]\\\").click(); }\";\n" +
            "document.body.appendChild(callbackScript);\n" +
            "submitted = true;\n" +

            "collectGeolocationInfo = function (callback) {\n" +
            "var geolocationInfo = {},\n" +
            "   successCallback = function(position) {\n" +
            "   geolocationInfo.longitude = position.coords.longitude;\n" +
            "   geolocationInfo.latitude = position.coords.latitude;\n" +
            "   output.value = JSON.stringify(geolocationInfo);\n" +
            "   callback();\n" +
            "}, errorCallback = function(error) {\n" +
            "    console.warn(\"Cannot collect geolocation information. \" + error.code + \": \" + error.message);\n" +
            "    output.value = JSON.stringify(geolocationInfo);\n" +
            "    callback(geolocationInfo);\n" +
            "};\n" +
            "if (navigator && navigator.geolocation) {\n" +
            "    navigator.geolocation.getCurrentPosition(successCallback, errorCallback);\n" +
            "} else {\n" +
            "    console.warn(\"Cannot collect geolocation information. navigator.geolocation is not defined.\");\n" +
            "    callback(geolocationInfo);\n" +
            "}\n" +
            "}\n" +
            "function callback() {\n" +
            "    document.getElementById(\"loginButton_0\").style.display = \"none\";\n" +
            "    collectGeolocationInfo(completed);\n" +
            "}\n" +
            "\n" +
            "if (document.readyState !== 'loading') {\n" +
            "  callback();\n" +
            "} else {\n" +
            "  document.addEventListener(\"DOMContentLoaded\", callback);\n" +
            "}";
            
        debug.error("[" + DEBUG_FILE + "]: " + "Starting");

        Optional<HiddenValueCallback> result = context.getCallback(HiddenValueCallback.class);
        if (result.isPresent()) {
            JsonValue newSharedState = context.sharedState.copy();
            String location = result.get().getValue();
            newSharedState.put(config.result(), location);
            return goTo(!location.equals("{}")).replaceSharedState(newSharedState).build();
        } else {

            ScriptTextOutputCallback scriptAndSelfSubmitCallback;
            if (config.nativeMethods()) {
                scriptAndSelfSubmitCallback = new ScriptTextOutputCallback("GEOLOCATION");
            } else {
                String clientSideScriptExecutorFunction = createClientSideScriptExecutorFunction(script, "clientScriptOutputData");
                scriptAndSelfSubmitCallback =
                        new ScriptTextOutputCallback(clientSideScriptExecutorFunction);
            }

            HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("clientScriptOutputData");
            TextOutputCallback messageCallback = new TextOutputCallback(TextOutputCallback.INFORMATION,"Getting your location ...");

            ImmutableList<Callback> callbacks = ImmutableList.of(scriptAndSelfSubmitCallback, hiddenValueCallback, messageCallback);

            return send(callbacks).build();
        }
    }

    public static String createClientSideScriptExecutorFunction(String script, String outputParameterId) {
        return String.format(
                "(function(output) {\n" +
                "    var autoSubmitDelay = 0,\n" +
                "        submitted = false;\n" +
                "    function submit() {\n" +
                "        if (submitted) {\n" +
                "            return;\n" +
                "        }" +
                "        document.forms[0].submit();\n" +
                "        submitted = true;\n" +
                "    }\n" +
                "    %s\n" + // script
                "    setTimeout(submit, autoSubmitDelay);\n" +
                "}) (document.forms[0].elements['%s']);\n", // outputParameterId
                script,
                outputParameterId);
    }
}
