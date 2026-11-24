package com.example.scheduler.model.enums;

/**
 * How a stored secret is applied to an outgoing request:
 *  - BEARER: "Authorization: Bearer {secret}"
 *  - BASIC: "Authorization: Basic base64(username:password)"
 *  - HEADER: an arbitrary named header, e.g. "X-Api-Key: {secret}"
 *  - HMAC: signs the request body and sends the signature in headerName,
 *    e.g. "X-Signature: sha256=<hex>"
 */
public enum CredentialType {
    BEARER,
    BASIC,
    HEADER,
    HMAC
}
