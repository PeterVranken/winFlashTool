/**
 * @file CcpCommandUpload.java
 * Upload a number of bytes from the ECU using CCP command UPLOAD.
 *
 * Copyright (C) 2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class CcpCommandUpload
 *   CcpCommandUpload
 *   fillPayloadCro
 *   setup
 *   step
 *   toString
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.can.PCANBasicEx;

/**
 * Upload a number of bytes from the ECU using CCP command UPLOAD.
 */
public class CcpCommandUpload extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandUpload.class);

    /** The buffer of required size for the uploaded data. */
    private final byte[] dataUploaded_;

    /** Number of bytes from dataUploaded_, which have not been transmitted to the ECU
        yet. */
    private int noBytesToUpload_;

    /** Index of byte in dataUploaded_, which is fetched next from the ECU. */
    private int writePos_;

    /** Number of bytes transmitted with the pending CRO message. */
    private int noBytesThisTime_;
    
    /**
     * A new instance of CcpCommandUpload is created and configured for a number
     * of CCP UPLOAD commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandUpload(CcpCommandArgs.Upload args) {
        dataUploaded_ = args.data();
        noBytesToUpload_ = 0;
        writePos_ = 0;
        noBytesThisTime_ = 0;

    } /* CcpCommandUpload.CcpCommandUpload */

    /**
     * Compose a CRO message with the next bytes to upload and update status of
     * remaining data.
     */
    private void fillPayloadCro() {
        if(noBytesToUpload_ >= 5) {
            noBytesThisTime_ = 5;
        } else {
            noBytesThisTime_ = noBytesToUpload_;
        }
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = CroCommandId.UPLOAD.getCode();
        payloadCroAry[2] = (byte)noBytesThisTime_;

    } /* fillPayloadCro */
    
    /**
     * The CCP command is initiated. After return from setup(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     */
    public void setup() {
        assert dataUploaded_ != null;
        noBytesToUpload_ = dataUploaded_.length;
        assert noBytesToUpload_ > 0: "Empty upload is not supported";
        writePos_ = 0;

        /* Send CAN CRO message. */
        fillPayloadCro();
        sendCro(/*noContentBytes*/ 3);
        
        _logger.printf( Level.INFO
                      , "Upload 0x%06X Byte from memory address 0x%06X"
                      , noBytesToUpload_ 
                      , mta0()
                      );
    } /* setup */


    /**
     * All CCP commands are implemented as state machines. This method implements a single
     * calculation step of the FSM. It needs to be called regularly.
     *   @return
     * The method returns "pending" until the command has completed. The first time this
     * method returns anything other than "pending" needs to be the last time this method
     * is called -- until the command is reinitiated with setup() and executed again.
     */
    public CcpCroTransmitter.ResultTransmission step() {
        CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if (resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            /* The data is returned in Byte 3, 4, ... Copy it into the result buffer. */
            final byte[] payloadDtoAry = payloadDtoAry();
            System.arraycopy( payloadDtoAry
                            , 3
                            , dataUploaded_
                            , writePos_
                            , noBytesThisTime_
                            );

            /* Update the status, where we are with the upload. */
            noBytesToUpload_ -= noBytesThisTime_;
            writePos_ += noBytesThisTime_;
            mta0(mta0() + noBytesThisTime_);
            
            _logger.trace( "DTO message received with {} Byte of data. {} Byte remaining."
                           + " New MTA is 0x%06X."
                         , noBytesThisTime_
                         , noBytesToUpload_ - noBytesThisTime_
                         , mta0()
                         );

            /* Request next chunk of data if there are bytes left. */
            if (noBytesToUpload_ > 0) {
                fillPayloadCro();
                sendCro(/*noContentBytes*/ 3);
                resultTxRx = CcpCroTransmitter.ResultTransmission.PENDING;
            }
        } else if (resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.printf( Level.ERROR
                          , "Can't upload data from the ECU. Failing memory address is"
                            + " 0x%06X. See previous error messages for details." 
                          , mta0()
                          );
        } else {
            /* DTO has not been received yet. We continue polling. */
        }

        return resultTxRx;

    } /* step */


    /**
     * Display name and arguments of this CCP command.
     *   @return
     * Get a meaningful representation of this object.
     */
    @Override
    public String toString() {
        return "UPLOAD(noBytes=" + dataUploaded_.length + ")";
    }
} /* End of class CcpCommandUpload definition. */




