package com.infopublish.client.service;

public interface ClientFileEnvelopeSigner {

    byte[] sign(byte[] manifestBytes);
}
