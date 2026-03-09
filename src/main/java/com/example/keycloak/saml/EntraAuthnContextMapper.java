package com.example.keycloak.saml;

import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AuthnContextClassRefType;
import org.keycloak.dom.saml.v2.assertion.AuthnContextType;
import org.keycloak.dom.saml.v2.assertion.AuthnStatementType;
import org.keycloak.dom.saml.v2.assertion.StatementAbstractType;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.protocol.saml.mappers.SAMLLoginResponseMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class EntraAuthnContextMapper extends AbstractSAMLProtocolMapper implements SAMLLoginResponseMapper {

    private static final Logger LOG = Logger.getLogger(EntraAuthnContextMapper.class);

    public static final String PROVIDER_ID = "entra-authncontext-login-response-mapper";

    public static final String SESSION_NOTE_NAME = "session.note.name";
    public static final String DEFAULT_CLASS_REF = "default.class.ref";
    public static final String MFA_CLASS_REF = "mfa.class.ref";
    public static final String MFA_NOTE_VALUES = "mfa.note.values";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty p1 = new ProviderConfigProperty();
        p1.setName(SESSION_NOTE_NAME);
        p1.setLabel("Session note name");
        p1.setType(ProviderConfigProperty.STRING_TYPE);
        p1.setDefaultValue("ACR");
        p1.setHelpText("User session note to inspect, e.g. ACR.");
        CONFIG_PROPERTIES.add(p1);

        ProviderConfigProperty p2 = new ProviderConfigProperty();
        p2.setName(DEFAULT_CLASS_REF);
        p2.setLabel("Default AuthnContextClassRef");
        p2.setType(ProviderConfigProperty.STRING_TYPE);
        p2.setDefaultValue("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
        p2.setHelpText("Used when MFA is not detected.");
        CONFIG_PROPERTIES.add(p2);

        ProviderConfigProperty p3 = new ProviderConfigProperty();
        p3.setName(MFA_CLASS_REF);
        p3.setLabel("MFA AuthnContextClassRef");
        p3.setType(ProviderConfigProperty.STRING_TYPE);
        p3.setDefaultValue("http://schemas.microsoft.com/claims/multipleauthn");
        p3.setHelpText("Used when MFA is detected for the session.");
        CONFIG_PROPERTIES.add(p3);

        ProviderConfigProperty p4 = new ProviderConfigProperty();
        p4.setName(MFA_NOTE_VALUES);
        p4.setLabel("MFA note values");
        p4.setType(ProviderConfigProperty.STRING_TYPE);
        p4.setDefaultValue("2,mfa,multipleauthn");
        p4.setHelpText("Comma-separated session note values treated as MFA.");
        CONFIG_PROPERTIES.add(p4);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Entra AuthnContext Login Response Mapper";
    }

    @Override
    public String getDisplayCategory() {
        return "SAML Mapper";
    }

    @Override
    public String getHelpText() {
        return "Rewrites SAML AuthnStatement/AuthnContextClassRef based on a user session note so Microsoft Entra can consume MFA or 1FA correctly.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public ResponseType transformLoginResponse(
            ResponseType response,
            ProtocolMapperModel mappingModel,
            KeycloakSession session,
            UserSessionModel userSession,
            ClientSessionContext clientSessionCtx) {

        String noteName = mappingModel.getConfig().getOrDefault(SESSION_NOTE_NAME, "ACR");
        String defaultClassRef = mappingModel.getConfig().getOrDefault(
                DEFAULT_CLASS_REF,
                "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport"
        );
        String mfaClassRef = mappingModel.getConfig().getOrDefault(
                MFA_CLASS_REF,
                "http://schemas.microsoft.com/claims/multipleauthn"
        );
        String mfaValuesCsv = mappingModel.getConfig().getOrDefault(MFA_NOTE_VALUES, "2,mfa,multipleauthn");

        String noteValue = userSession.getNote(noteName);
        boolean isMfa = isMfaValue(noteValue, mfaValuesCsv);

        String selectedClassRef = isMfa ? mfaClassRef : defaultClassRef;
        ClientModel client = clientSessionCtx == null ? null : clientSessionCtx.getClientSession().getClient();

        LOG.infov(
                "EntraAuthnContextMapper invoked. clientId={0}, userSessionId={1}, noteName={2}, noteValue={3}, isMfa={4}, selectedClassRef={5}",
                client == null ? "<unknown>" : client.getClientId(),
                userSession == null ? "<null>" : userSession.getId(),
                noteName,
                noteValue,
                isMfa,
                selectedClassRef
        );

        if (response == null || response.getAssertions() == null) {
            LOG.warn("EntraAuthnContextMapper received a response without assertions.");
            return response;
        }

        int rewrittenStatements = 0;
        for (ResponseType.RTChoiceType assertionChoice : response.getAssertions()) {
            AssertionType assertion = assertionChoice.getAssertion();
            if (assertion == null) {
                LOG.debug("Skipping encrypted or null assertion choice.");
                continue;
            }
            if (assertion.getStatements() == null) {
                LOG.debugv("Assertion {0} has no statements.", assertion.getID());
                continue;
            }

            for (StatementAbstractType statement : assertion.getStatements()) {
                if (!(statement instanceof AuthnStatementType)) {
                    continue;
                }

                AuthnStatementType authnStatement = (AuthnStatementType) statement;

                AuthnContextType authnContext = new AuthnContextType();
                AuthnContextType.AuthnContextTypeSequence sequence =
                        new AuthnContextType.AuthnContextTypeSequence();

                sequence.setClassRef(new AuthnContextClassRefType(URI.create(selectedClassRef)));
                authnContext.setSequence(sequence);

                authnStatement.setAuthnContext(authnContext);
                rewrittenStatements++;
                LOG.infov(
                        "Rewrote AuthnContextClassRef for assertionId={0} to {1}",
                        assertion.getID(),
                        selectedClassRef
                );
            }
        }

        if (rewrittenStatements == 0) {
            LOG.warn("EntraAuthnContextMapper did not find any AuthnStatement to rewrite.");
        }

        return response;
    }

    private boolean isMfaValue(String value, String csv) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase();

        for (String candidate : csv.split(",")) {
            if (normalized.equals(candidate.trim().toLowerCase())) {
                return true;
            }
        }

        // Numeric LoA convention: >= 2 means MFA
        try {
            return Integer.parseInt(normalized) >= 2;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}