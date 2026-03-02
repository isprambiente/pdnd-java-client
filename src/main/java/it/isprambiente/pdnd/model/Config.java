package it.isprambiente.pdnd.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Config(
    String kid,
    String issuer,
    String clientId,
    String purposeId,
    String privKeyPath
) {}