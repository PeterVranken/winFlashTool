/**
 * @file CcpCommandDownloadKey.java
 * Calculate and download the key for authentication to the ECU using CCP commands DOWNLOAD
 * and DOWNLOAD_6.
 *
 * Copyright (C) 2025-2026 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class CcpCommandDownloadKey
 *   CcpCommandDownloadKey
 *   setup
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.digitalSignature.DigitalSignature;
import winFlashTool.basics.Basics;

/**
 * Calculate and download the key for authentication to the ECU using CCP commands DOWNLOAD
 * and DOWNLOAD_6.
 */
public class CcpCommandDownloadKey extends CcpCommandsDownloadProgram {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandDownloadKey.class);

    /** The known size of the key. */
    private static final int SIZE_OF_KEY = 64;

    /** The buffer of known size for the key. */
    private byte[] dataKey_;
    
    /** The command arguments. */
    final CcpCommandArgs.CcpCommandDownloadKey cmdArgs_;
    
    /**
     * A new instance of CcpCommandDownloadKey is created and configured.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandDownloadKey(CcpCommandArgs.CcpCommandDownloadKey args) {
        super(/*isDownload*/ true);
        cmdArgs_ = args;
        
    } /* CcpCommandDownloadKey.CcpCommandDownloadKey */

    /**
     * The CCP command is initiated. After return from setup(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @return
     * Normally, the method returns "pending" to indicate that the CCP communication has
     * been successfully initiated but is still ongoing. In this case, the other method
     * step() will be called as long as it indicates as still ongoing communication
     * process.<p>
     *   If the initialization fails, it'll return an error code. In this situation,
     * everything is done and step() won't be called.<p>
     *   In rare situations, it may even return success. CCP communication has successfully
     * completed and step() must not be called any more. This may happen, e.g., if a
     * pointless UPLOAD of zero Byte is commanded.
     */
    public CcpCroTransmitter.ResultTransmission setup() {
        
        /* Fetch the seed from the preceding UPLOAD command. */
        byte[] seed = cmdArgs_.supplierSeed().get();
        
        /* Calculate the key from the seed. */
        DigitalSignature digSignature = cmdArgs_.digitalSignature();
        assert dataKey_ == null;
        byte[] dataKey_ = digSignature.calculateSignature(seed);
        if (dataKey_ != null) {
            /* Provide the data buffer for download to the super class. */
            setData(dataKey_);

            /* Communication setup is done by the normal DOWNLOAD command. */
            return super.setup();
        } else {            
            return CcpCroTransmitter.ResultTransmission.ERROR_AUTHENTICATION;
        }
            
        
        
    } /* setup */

} /* Class CcpCommandDownloadKey */




