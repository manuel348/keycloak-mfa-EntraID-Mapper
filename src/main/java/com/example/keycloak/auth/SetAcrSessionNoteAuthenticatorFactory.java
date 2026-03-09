package com.example.keycloak.auth;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class SetAcrSessionNoteAuthenticatorFactory implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {

    public static final String PROVIDER_ID = "set-acr-session-note";

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of(
            createProperty(
                    SetAcrSessionNoteAuthenticator.SESSION_NOTE_NAME,
                    "Session note name",
                    "User session note name to set after this execution succeeds.",
                    SetAcrSessionNoteAuthenticator.DEFAULT_NOTE_NAME
            ),
            createProperty(
                    SetAcrSessionNoteAuthenticator.SESSION_NOTE_VALUE,
                    "Session note value",
                    "User session note value to set after this execution succeeds.",
                    SetAcrSessionNoteAuthenticator.DEFAULT_NOTE_VALUE
            )
    );

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Set ACR Session Note";
    }

    @Override
    public String getReferenceCategory() {
        return "acr-session-note";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Sets a user session note, intended to mark brokered logins as MFA after OTP succeeds.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SetAcrSessionNoteAuthenticator.SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    private static ProviderConfigProperty createProperty(String name, String label, String helpText, String defaultValue) {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName(name);
        property.setLabel(label);
        property.setHelpText(helpText);
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(defaultValue);
        return property;
    }
}
