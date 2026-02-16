/**
 * @file DigitalSignature.java
 * This class provides generation of new key pairs, writing keys to file and loading them
 * and the generation of a digital signature for a message using the private key.
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
 *   generateKeyPair
 *   readPrivateKey
 *   calculateSignature
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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import winFlashTool.basics.Basics;
import winFlashTool.basics.ErrorCounter;

/**
 * Digital signature helper for Ed25519.
 *   This class provides generation of new key pairs, writing keys to file and loading them
 * and the generation of a digital signature for a message using the private key.
 *   Please note, This class has been widely designed and written by Copilot. Only minor
 * modifiations have been made afterwards.
 */
public class DigitalSignature {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(DigitalSignature.class);

    /** The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;

    /** The private key, which is used for making the signature. */
    private byte[] sk_;

    /**
     * Construct with an error counter for reporting.
     *   @param errCnt 
     * The shared error counter to increment on failures.
     */
    public DigitalSignature(final ErrorCounter errCnt) {
        this.errCnt_ = errCnt;
        
        /* The private key is read from file prior to use. */
        sk_ = null;
    }

    /**
     * Generate an Ed25519 key pair from locally mixed entropy and store the
     * 32-byte private key (seed) as a binary file. The corresponding
     * 32-byte public key is logged in hex.<p>
     *   This method is a convenience function only, it is not used for normal operation.
     *   @return
     * Get {@code true} on success, otherwise {@code false}.
     *   @param outFileName 
     * The target file name for the 32-byte private key.
     */
    public boolean generateKeyPair(final String keyFileName) {
        Path cpath;
        try {
            cpath = toCanonicalPath(keyFileName);
        } catch (IOException e) {
            errCnt_.error();
            _logger.error( "Output file {} can't be canonicalized. {}"
                         , keyFileName
                         , e.getMessage()
                         );
            return false;
        }

        try {
            _logger.info("Generating Ed25519 private key pair.");
            byte[] seed = makeSeed(cpath.toString());
            assert seed.length == 32;
//for (int i=0; i<seed.length; ++i) {
//    seed[i] = (byte)i;
//}

            KeyPair kp = deriveKeyPairFromSeed(seed);
            byte[] pubSpki = kp.getPublic().getEncoded();
            /* pubSpki has 44 Byte but only the last 32 are the public key of Ed25519. */
assert pubSpki.length == 44;
            byte[] pubRaw = Arrays.copyOfRange(pubSpki, pubSpki.length - 32, pubSpki.length);

            _logger.info("Writing private key to file {}.", cpath.toString());
            Files.write(cpath, seed, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            _logger.info("Public key (hex, 32 Byte):{}", Basics.byteArrayToHex(pubRaw));
            return true;

        } catch (IOException e) {
            errCnt_.error();
            _logger.error( "Private key file {} can't be written. {}"
                         , cpath
                         , e.getMessage()
                         );
            return false;
        } catch (GeneralSecurityException e) {
            errCnt_.error();
            _logger.error("Key pair generation failed. {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            errCnt_.error();
            _logger.error("Unexpected error during key pair generation. {}", e.getMessage());
            return false;
        }
    } /* generateKeyPair */

    /**
     * Read a 32-byte private key (seed) from a file. Only if the function succeeds,
     * message can be signed using calculateSignature.
     *   @return
     * Get {@code true} if the key could be read, or {@code false} if an error occurs.
     *   @param keyFileName
     * The source file name of the 32-byte private key.
     */
    public boolean readPrivateKey(final String keyFileName) {   
        assert keyFileName != null  &&  keyFileName.length() > 0;
        Path cpath;
        try {
            cpath = toCanonicalPath(keyFileName);
        } catch (IOException e) {
            errCnt_.error();
            _logger.error( "File name {} can't be canonicalized. {}"
                         , keyFileName
                         , e.getMessage()
                         );
            return false;
        }

        try {
            byte[] fileContents = Files.readAllBytes(cpath);
            if (fileContents.length != 32) {
                errCnt_.error();
                _logger.error( "Key file {} has wrong length {} Byte, but expected 32 Byte."
                             , cpath.toString()
                             , fileContents.length
                             );
                return false;
            }
            _logger.info("Authentication key successfully read from {}.", cpath.toString());
            sk_ = fileContents;
            return true;

        } catch (IOException e) {
            errCnt_.error();
            _logger.error("Input file {} can't be read. {}", cpath.toString(), e.getMessage());
            return false;
        } catch (RuntimeException e) {
            errCnt_.error();
            _logger.error("Unexpected error during key read. {}", e.getMessage());
            return false;
        }
    } /* readPrivateKey */

    /**
     * Calculate the Ed25519 signature (64 bytes) over a message.
     *   @return
     * Get the 64-byte signature as {@code byte[]}, or {@code null} if an error occurs.
     *   @param msg
     * The message to sign as {@code byte[]}.
     */
    public byte[] calculateSignature(final byte[] msg) {
        assert msg != null  &&  sk_ != null  &&  sk_.length == 32;

        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PrivateKey pk = kf.generatePrivate
                                    (new EdECPrivateKeySpec( NamedParameterSpec.ED25519, sk_));
            final Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(pk);
            sig.update(msg);
            final byte[] signature = sig.sign();
            
            if (signature.length != 64) {
                throw new GeneralSecurityException("Implementation of Ed25519 delivered"
                                                   + " the unexpected signature length "
                                                   + signature.length + " Byte, but expected"
                                                   + " 64 Byte."
                                                  );
            }
            _logger.info ( "TODO Make this verbosity trace! Signature for msg{} successfully calculated:{}."
                         , Basics.byteArrayToHex(msg)
                         , Basics.byteArrayToHex(signature)
                         );
            return signature;

        } catch (GeneralSecurityException e) {
            errCnt_.error();
            _logger.error("Signature calculation failed. {}", e.getMessage());
            return null;
        }
    } /* calculateSignature */

    /**
     * Convert a file name to an absolute, canonicalized path.
     *   @return
     * Get the canonicalized path.
     *   @param fileName
     * The file path, which may be relative or contain "." and ".." as path elements..
     */
    private static Path toCanonicalPath(final String fileName)
        throws IOException {
        
        File f = new File(fileName);
        return f.getCanonicalFile().toPath();
        
    } /* toCanonicalPath */

    /*
     * Build a 32-byte seed by hashing fluctuating local data. The sources are
     * intentionally obscured by hashing.
     *   @return
     * Get the random seed of 32 Byte length.
     *   @param filePath
     * Some file path, which is taken into account for the random seed generation. Use-case
     * is passing in the user provided file name of the key file, but could actually be any
     * (variable, little predictable) string. 
     */
    private static byte[] makeSeed(final String filePath)
        throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
            try (DataOutputStream dos = new DataOutputStream(bos)) {
                dos.writeLong(System.nanoTime());
                dos.writeUTF(filePath);
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
    } /* makeSeed */

    /**
     * Deterministically derive an Ed25519 KeyPair from a 32-byte seed by
     * feeding the seed via a custom SecureRandom to the generator. The public
     * key is implicitly derived by the provider's EC arithmetic.
     *   @return
     * Get the key pair.
     *   @param seed
     * The seed of the key pair and at the same time the secret key.
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
                if (bytes.length == seed.length) {
                    System.arraycopy(seed, 0, bytes, 0, seed.length);
                } else {
                    throw new RuntimeException("Public key generation fails. SecureRandom"
                                               + " is asked for seed length " + bytes.length
                                               + ", but " + seed.length + " was expected"
                                               + " for Ed25519."
                                              );
                }
            } /* nextBytes */
        }; /* Anonymous sub-class of SecureRandom */

        kpg.initialize(new NamedParameterSpec("Ed25519"), seeded);
        return kpg.generateKeyPair();
        
    } /* deriveKeyPairFromSeed */
} /* DigitalSignature */