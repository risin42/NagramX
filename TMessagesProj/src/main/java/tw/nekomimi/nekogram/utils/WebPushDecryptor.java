package tw.nekomimi.nekogram.utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class WebPushDecryptor {

    private WebPushDecryptor() {
    }

    public static byte[] decrypt(byte[] body, byte[] pkcs8PrivateKey, byte[] rawPublicKey, byte[] authSecret) throws Exception {
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

        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8PrivateKey));

        ECPublicKey serverPublicKey = rawToECPublicKey(serverPub);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privateKey);
        ka.doPhase(serverPublicKey, true);
        byte[] sharedSecret = ka.generateSecret();

        byte[] prk = hmacSha256(authSecret, sharedSecret);

        byte[] authInfo = "Content-Encoding: auth\0".getBytes(StandardCharsets.UTF_8);
        byte[] ikm = hkdfExpand(prk, authInfo, 32);

        byte[] prk2 = hmacSha256(salt, ikm);

        byte[] context = buildContext(rawPublicKey, serverPub);

        byte[] cekInfo = concat("Content-Encoding: aesgcm\0".getBytes(StandardCharsets.UTF_8), context);
        byte[] cek = hkdfExpand(prk2, cekInfo, 16);

        byte[] nonceInfo = concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), context);
        byte[] nonce = hkdfExpand(prk2, nonceInfo, 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] decrypted = cipher.doFinal(ciphertext);

        if (decrypted.length < 2) throw new IllegalArgumentException("Decrypted payload too short");
        int paddingLen = ((decrypted[0] & 0xFF) << 8) | (decrypted[1] & 0xFF);
        if (2 + paddingLen > decrypted.length) throw new IllegalArgumentException("Invalid padding length");
        return Arrays.copyOfRange(decrypted, 2 + paddingLen, decrypted.length);
    }

    public static byte[] extractRawPublicKey(ECPublicKey key) {
        ECPoint w = key.getW();
        byte[] xb = w.getAffineX().toByteArray();
        byte[] yb = w.getAffineY().toByteArray();
        byte[] raw = new byte[65];
        raw[0] = 0x04;
        if (xb.length >= 32) System.arraycopy(xb, xb.length - 32, raw, 1, 32);
        else System.arraycopy(xb, 0, raw, 1 + (32 - xb.length), xb.length);
        if (yb.length >= 32) System.arraycopy(yb, yb.length - 32, raw, 33, 32);
        else System.arraycopy(yb, 0, raw, 33 + (32 - yb.length), yb.length);
        return raw;
    }

    private static ECPublicKey rawToECPublicKey(byte[] rawPoint) throws Exception {
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
