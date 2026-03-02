package it.isprambiente.pdnd.model;

public record TokenData(
    String token,
    long exp
) {}