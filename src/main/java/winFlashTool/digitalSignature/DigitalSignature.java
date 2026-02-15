/**
 * @file DigitalSignature.java
 * 
 *
 * Copyright (C) 2026 Copilot & Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/* Interface of class DigitalSignature
 *   DigitalSignature
 */

package winFlashTool.digitalSignature;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.EdECPrivateKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import winFlashTool.basics.ErrorCounter;

/**
 * Digital signature helper for Ed25519.
 * <p>
 * Return value: Methods return success flags, arrays or {@code null} on error as
 * documented per method.
 * <p>
 * Parameters: See individual method Javadoc sections.
 *
 * Please note, This class has been widely designed and written by Copilot. Only subtle
 * modifiations have been made afterwards.
 */
public class DigitalSignature {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(DigitalSignature.class);

    /** The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;

    /** The secrect
    /**
     * Construct with an error counter for reporting.
     *   @param errCnt 
     * The shared error counter to increment on failures.
     */
    public DigitalSignature(final ErrorCounter errCnt) {
        this.errCnt_ = errCnt;
    }

    /**
     * Generate an Ed25519 key pair from locally mixed entropy and store the
     * 32-byte private key (seed) as a binary file. The corresponding
     * 32-byte public key is logged in hex.
     *   @return
     * Get {@code true} on success, otherwise {@code false}.
     *   @param outFileName 
     * The target file name for the 32-byte private key.
     */
    public boolean generateKeyPair(final String outFileName) {
        Path cpath;
        try {
            cpath = toCanonicalPath(outFileName);
        } catch (IOException e) {
            errCnt_.error();
            _logger.error("Output file {} can't be canonicalized. {}",
                          outFileName, e.getMessage());
            return false;
        }

        try {
            _logger.info("Generating Ed25519 private key seed (32 bytes).");
            byte[] seed = makeSeed(cpath);
            assert seed.length == 32;
for (int i=0; i<seed.length; ++i) {
    seed[i] = (byte)i;
}
            _logger.info("Deriving public key from seed and preparing output.");
            KeyPair kp = deriveKeyPairFromSeed(seed);
            byte[] pubSpki = kp.getPublic().getEncoded();
            byte[] pubRaw = Arrays.copyOfRange(pubSpki, pubSpki.length - 32, pubSpki.length);

            _logger.info("Writing private key to: {}", cpath.toString());
            Files.write(cpath, seed, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            _logger.info("Public key (hex, 32 bytes): {}", toHex(pubRaw));
            _logger.info("Key pair generation completed.");
            return true;
        } catch (IOException e) {
            errCnt_.error();
            _logger.error( "Output file {} can't be written. {}"
                         , safeString(cpath)
                         , e.getMessage()
                         );
            return false;
        } catch (GeneralSecurityException e) {
            errCnt_.error();
            _logger.error("Key generation failed. {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            errCnt_.error();
            _logger.error("Unexpected error during key generation. {}", e.getMessage());
            return false;
        }
    } /* generateKeyPair */

    /**
     * Read a previously generated 32-byte private key (seed) from a file.
     * <p>
     * Return value: The 32-byte private key as {@code byte[]}, or {@code null}
     * if an error occurs.
     * <p>
     * Parameters: inFileName The source file name of the 32-byte private key.
     */
    public byte[] readPrivateKey(final String inFileName) {
        Path cpath;
        try {
            cpath = toCanonicalPath(inFileName);
        } catch (IOException e) {
            errCnt_.error();
            _logger.error( "Input file {} can't be canonicalized. {}"
                         , inFileName
                         , e.getMessage()
                         );
            return null;
        }

        try {
            _logger.info("Reading private key from: {}", cpath.toString());
            byte[] data = Files.readAllBytes(cpath);
            if (data.length != 32) {
                errCnt_.error();
                _logger.error("Input file {} has wrong length: {} (expected 32).",
                              cpath.toString(), data.length);
                return null;
            }
            _logger.info("Private key successfully read (32 bytes).");
            return data;
        } catch (IOException e) {
            errCnt_.error();
            _logger.error("Input file {} can't be read. {}", cpath.toString(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            errCnt_.error();
            _logger.error("Unexpected error during key read. {}", e.getMessage());
            return null;
        }
    } /* readPrivateKey */

    /**
     * Calculate the Ed25519 signature (64 bytes) over a message.
     * <p>
     * Return value: The 64-byte signature as {@code byte[]}, or {@code null}
     * if an error occurs.
     * <p>
     * Parameters:
     * msg The message to sign as {@code byte[]}.
     * privKey32 The 32-byte Ed25519 private key (seed) as {@code byte[]}.
     */
    public byte[] calculateSignature(final byte[] msg, final byte[] privKey32) {
        try {
            if (msg == null) {
                errCnt_.error();
                _logger.error("Message is null.");
                return null;
            }
            if (privKey32 == null || privKey32.length != 32) {
                errCnt_.error();
                _logger.error("Private key must be 32 bytes (seed).");
                return null;
            }

            _logger.info("Preparing Ed25519 signature.");
KeyFactory kf = KeyFactory.getInstance("Ed25519");
PrivateKey pk = kf.generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, privKey32));
//            KeyPair kp = deriveKeyPairFromSeed(privKey32);

            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(pk);
//            sig.initSign(kp.getPrivate());
            sig.update(msg);
            byte[] signature = sig.sign();

            if (signature.length != 64) {
                errCnt_.error();
                _logger.error("Unexpected signature length: {} (expected 64).",
                              signature.length);
                return null;
            }

            _logger.info("Signature successfully calculated (64 bytes).");
            return signature;
        } catch (GeneralSecurityException e) {
            errCnt_.error();
            _logger.error("Signature calculation failed. {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            errCnt_.error();
            _logger.error("Unexpected error during signing. {}",
                          e.getMessage());
            return null;
        }
    } /* calculateSignature */

    /* --------------------------- helpers below --------------------------- */

    /*
     * Convert a file name to an absolute, canonicalized path.
     */
    private static Path toCanonicalPath(final String fileName)
        throws IOException {
        File f = new File(fileName);
        return f.getCanonicalFile().toPath();
    }

    /*
     * Build a 32-byte seed by hashing fluctuating local data. The sources are
     * intentionally obscured by hashing.
     */
    private static byte[] makeSeed(final Path canonicalPath)
        throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
            try (DataOutputStream dos = new DataOutputStream(bos)) {
                dos.writeLong(System.nanoTime());
                dos.writeUTF(canonicalPath.toString());
                dos.writeLong(System.currentTimeMillis());
                dos.writeInt(Runtime.getRuntime().availableProcessors());
                dos.writeLong(Runtime.getRuntime().freeMemory());
                dos.writeLong(Runtime.getRuntime().totalMemory());
                dos.writeLong(Thread.currentThread().getId());
                dos.writeInt(System.identityHashCode(new Object()));
                String pid = ManagementFactory.getRuntimeMXBean().getName();
                dos.writeUTF(pid);
            }
            byte[] mixed = bos.toByteArray();

            java.security.MessageDigest md =
                java.security.MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(mixed);

            return Arrays.copyOf(digest, 32);
        } catch (GeneralSecurityException e) {
            throw new IOException("Seed derivation failed: " + e.getMessage(), e);
        }
    }

    /*
     * Deterministically derive an Ed25519 KeyPair from a 32-byte seed by
     * feeding the seed via a custom SecureRandom to the generator. The public
     * key is implicitly derived by the provider's EC arithmetic.
     */
    private static KeyPair deriveKeyPairFromSeed(final byte[] seed)
        throws GeneralSecurityException {
        if (seed == null || seed.length != 32) {
            throw new GeneralSecurityException("Seed must be 32 bytes.");
        }

        /* Java's public EC arithmetic isn't directly exposed for
           derive-public-from-private for Ed25519. A widely-used workaround is to pass the 
           32-byte seed via a custom SecureRandom to KeyPairGenerator, and then take the
           derived public key from the generated pair. The code follows this approach and
           logs the raw 32-byte public key (last 32 bytes of the SPKI). */
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        SecureRandom seeded = new SecureRandom() {
            private static final long serialVersionUID = 1L;
            @Override
            public void nextBytes(byte[] bytes) {
                /* Supply the private key seed deterministically. */
_logger.info("SecureRandom: Asked for a seed of {} Byte.", bytes.length);
if (bytes.length == seed.length) {
                    System.arraycopy(seed, 0, bytes, 0, seed.length);
} else {
    throw new RuntimeException("Public key generation fails. SecureRandom is asked for"
                               + " seed length " + bytes.length + ", but " + seed.length
                               + " was expected for Ed25519."
                              );
}
//                System.arraycopy(seed, 0, bytes, 0, seed.length);
//                if (bytes.length > seed.length) {
//                    /* Fill remaining bytes with zeros to be explicit. */
//                    Arrays.fill(bytes, seed.length, bytes.length, (byte) 0);
//                }
            }
        };
        kpg.initialize(new NamedParameterSpec("Ed25519"), seeded);
        return kpg.generateKeyPair();
    }

    /*
     * Convert bytes to lowercase hexadecimal.
     */
    public static String toHex(final byte[] in) {
        char[] HEX = "0123456789ABCDEF".toCharArray();
        char[] out = new char[in.length * 3];
        for (int i = 0, j = 0; i < in.length; i++) {
            int v = in[i] & 0xff;
            out[j++] = ' ';
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    /*
     * Safe string conversion for paths in error logs.
     */
    private static String safeString(Path p) {
        return p == null ? "<null>" : p.toString();
    }
}