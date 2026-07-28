package it.belloworld.mercurygram;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decrypts WebPush aesgcm (Draft 4) messages forwarded by the common-proxies /aesgcm gateway.
 *
 * The gateway serializes the WebPush HTTP headers into the body before forwarding to the
 * UnifiedPush distributor (which would otherwise strip them). Body format:
 *
 *   aesgcm\n
 *   Encryption: salt=<base64url>\n
 *   Crypto-Key: dh=<base64url>\n
 *   <binary ciphertext>
 *
 * Key derivation follows draft-ietf-webpush-encryption-04 (aesgcm, not aes128gcm).
 */
public class WebPushDecryptor {

    /**
     * Decrypt an aesgcm WebPush message in common-proxies body format.
     *
     * @param body           raw message bytes from UnifiedPush onMessage()
     * @param pkcs8PrivateKey PKCS#8-encoded P-256 private key
     * @param rawPublicKey   raw 65-byte uncompressed P-256 public key (04||X||Y)
     * @param authSecret     16-byte auth secret
     * @return decrypted plaintext bytes, or throws on failure
     */
    public static byte[] decrypt(byte[] body, byte[] pkcs8PrivateKey, byte[] rawPublicKey, byte[] authSecret) throws Exception {
        // Split at the 3rd newline: headers | ciphertext
        int newlineCount = 0;
        int splitPos = -1;
        for (int i = 0; i < body.length; i++) {
            if (body[i] == '\n') {
                newlineCount++;
                if (newlineCount == 3) {
                    splitPos = i + 1;
                    break;
                }
            }
        }
        if (splitPos < 0 || splitPos >= body.length) {
            throw new IllegalArgumentException("Invalid message format: missing header/ciphertext separator");
        }

        String headers = new String(body, 0, splitPos, StandardCharsets.UTF_8);
        byte[] ciphertext = Arrays.copyOfRange(body, splitPos, body.length);

        // Parse salt from "Encryption: salt=<base64url>"
        byte[] salt = null;
        for (String line : headers.split("\n")) {
            line = line.trim();
            if (line.startsWith("Encryption:")) {
                for (String part : line.substring("Encryption:".length()).trim().split(";")) {
                    part = part.trim();
                    if (part.startsWith("salt=")) {
                        salt = Base64.decode(part.substring(5), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                    }
                }
            }
        }
        if (salt == null) throw new IllegalArgumentException("Missing salt in Encryption header");

        // Parse dh (server ephemeral public key) from "Crypto-Key: dh=<base64url>"
        byte[] serverPub = null;
        for (String line : headers.split("\n")) {
            line = line.trim();
            if (line.startsWith("Crypto-Key:")) {
                for (String part : line.substring("Crypto-Key:".length()).trim().split(";")) {
                    part = part.trim();
                    if (part.startsWith("dh=")) {
                        serverPub = Base64.decode(part.substring(3), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                    }
                }
            }
        }
        if (serverPub == null) throw new IllegalArgumentException("Missing dh in Crypto-Key header");

        // Reconstruct private key from PKCS#8
        KeyFactory kf = KeyFactory.getInstance("EC");
        java.security.PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8PrivateKey));

        // Reconstruct server public key from raw 65-byte uncompressed P-256 point
        ECPublicKey serverPublicKey = rawToECPublicKey(serverPub);

        // ECDH: shared_secret = ECDH(client_private, server_public)
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privateKey);
        ka.doPhase(serverPublicKey, true);
        byte[] sharedSecret = ka.generateSecret();

        // HKDF-Extract: PRK = HMAC-SHA256(key=auth_secret, data=shared_secret)
        byte[] prk = hmacSha256(authSecret, sharedSecret);

        // HKDF-Expand: IKM = HMAC-SHA256(PRK, "Content-Encoding: auth\0" || 0x01)[:32]
        byte[] authInfo = "Content-Encoding: auth\0".getBytes(StandardCharsets.UTF_8);
        byte[] ikm = hkdfExpand(prk, authInfo, 32);

        // HKDF-Extract: PRK2 = HMAC-SHA256(key=salt, data=IKM)
        byte[] prk2 = hmacSha256(salt, ikm);

        // context = "P-256\0" || u16be(len(clientPub)) || clientPub || u16be(len(serverPub)) || serverPub
        byte[] context = buildContext(rawPublicKey, serverPub);

        // HKDF-Expand: CEK = HMAC-SHA256(PRK2, cek_info || 0x01)[:16]
        byte[] cekInfo = concat("Content-Encoding: aesgcm\0".getBytes(StandardCharsets.UTF_8), context);
        byte[] cek = hkdfExpand(prk2, cekInfo, 16);

        // HKDF-Expand: nonce = HMAC-SHA256(PRK2, nonce_info || 0x01)[:12]
        byte[] nonceInfo = concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), context);
        byte[] nonce = hkdfExpand(prk2, nonceInfo, 12);

        // AES-128-GCM decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] decrypted = cipher.doFinal(ciphertext);

        // Strip aesgcm Draft 4 padding: first 2 bytes = big-endian padding length
        if (decrypted.length < 2) throw new IllegalArgumentException("Decrypted payload too short");
        int paddingLen = ((decrypted[0] & 0xFF) << 8) | (decrypted[1] & 0xFF);
        if (2 + paddingLen > decrypted.length) throw new IllegalArgumentException("Invalid padding length");
        return Arrays.copyOfRange(decrypted, 2 + paddingLen, decrypted.length);
    }

    /**
     * Extract the raw 65-byte uncompressed P-256 public key point (04||X||Y) from an ECPublicKey.
     */
    public static byte[] extractRawPublicKey(ECPublicKey key) {
        java.security.spec.ECPoint w = key.getW();
        byte[] xb = w.getAffineX().toByteArray();
        byte[] yb = w.getAffineY().toByteArray();
        byte[] raw = new byte[65];
        raw[0] = 0x04;
        // Normalize each coordinate to exactly 32 bytes (BigInteger may add a sign byte or be shorter)
        if (xb.length >= 32) System.arraycopy(xb, xb.length - 32, raw, 1, 32);
        else System.arraycopy(xb, 0, raw, 1 + (32 - xb.length), xb.length);
        if (yb.length >= 32) System.arraycopy(yb, yb.length - 32, raw, 33, 32);
        else System.arraycopy(yb, 0, raw, 33 + (32 - yb.length), yb.length);
        return raw;
    }

    // Wrap raw 65-byte P-256 point in X.509 SubjectPublicKeyInfo for KeyFactory
    private static ECPublicKey rawToECPublicKey(byte[] rawPoint) throws Exception {
        // DER header for P-256 public key: SEQUENCE { SEQUENCE { OID ecPublicKey, OID P-256 }, BIT STRING }
        byte[] header = {
                0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, (byte)0x86, 0x48, (byte)0xce,
                0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a, (byte)0x86, 0x48, (byte)0xce, 0x3d,
                0x03, 0x01, 0x07, 0x03, 0x42, 0x00
        };
        byte[] encoded = new byte[header.length + rawPoint.length];
        System.arraycopy(header, 0, encoded, 0, header.length);
        System.arraycopy(rawPoint, 0, encoded, header.length, rawPoint.length);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }

    // context for CEK/nonce info: "P-256\0" || u16be(clientPubLen) || clientPub || u16be(serverPubLen) || serverPub
    private static byte[] buildContext(byte[] clientPub, byte[] serverPub) {
        byte[] label = "P-256\0".getBytes(StandardCharsets.UTF_8);
        byte[] ctx = new byte[label.length + 2 + clientPub.length + 2 + serverPub.length];
        int off = 0;
        System.arraycopy(label, 0, ctx, off, label.length); off += label.length;
        ctx[off++] = (byte) (clientPub.length >> 8);
        ctx[off++] = (byte) (clientPub.length);
        System.arraycopy(clientPub, 0, ctx, off, clientPub.length); off += clientPub.length;
        ctx[off++] = (byte) (serverPub.length >> 8);
        ctx[off++] = (byte) (serverPub.length);
        System.arraycopy(serverPub, 0, ctx, off, serverPub.length);
        return ctx;
    }

    // HKDF-Expand: T(1) = HMAC-SHA256(PRK, info || 0x01), truncated to `length` bytes
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        byte[] input = Arrays.copyOf(info, info.length + 1);
        input[info.length] = 0x01;
        return Arrays.copyOfRange(hmacSha256(prk, input), 0, length);
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
