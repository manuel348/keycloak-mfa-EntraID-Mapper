package com.example.keycloak.auth;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.jboss.logging.Logger;

import java.util.Map;

public class SetAcrSessionNoteAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(SetAcrSessionNoteAuthenticator.class);

    public static final String SESSION_NOTE_NAME = "session.note.name";
    public static final String SESSION_NOTE_VALUE = "session.note.value";
    public static final String DEFAULT_NOTE_NAME = "ACR";
    public static final String DEFAULT_NOTE_VALUE = "2";

    public static final SetAcrSessionNoteAuthenticator SINGLETON = new SetAcrSessionNoteAuthenticator();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String noteName = getConfigValue(context, SESSION_NOTE_NAME, DEFAULT_NOTE_NAME);
        String noteValue = getConfigValue(context, SESSION_NOTE_VALUE, DEFAULT_NOTE_VALUE);

        // This step is intended to run after OTP succeeds, so reaching it marks the session as MFA.
        context.getAuthenticationSession().setUserSessionNote(noteName, noteValue);
        LOG.infov(
                "SetAcrSessionNoteAuthenticator stored session note. realm={0}, user={1}, noteName={2}, noteValue={3}, authSessionParent={4}",
                context.getRealm() == null ? "<unknown>" : context.getRealm().getName(),
                context.getUser() == null ? "<unknown>" : context.getUser().getUsername(),
                noteName,
                noteValue,
                context.getAuthenticationSession() == null ? "<unknown>" : context.getAuthenticationSession().getParentSession().getId()
        );
        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        context.success();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }

    private String getConfigValue(AuthenticationFlowContext context, String key, String defaultValue) {
        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        if (configModel == null) {
            return defaultValue;
        }

        Map<String, String> config = configModel.getConfig();
        if (config == null) {
            return defaultValue;
        }

        String value = config.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
