/**
 * @file CcpCommandUpload.java
 * Upload a number of bytes from the ECU using CCP command UPLOAD.
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
import java.util.function.Supplier;
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

    /** Operation mode: Save data or compare with expected data (verify). */
    private final boolean isVerify_;
    
    /** The buffer of required size for the uploaded data. */
    private byte[] dataUploaded_;

    /** A lambda, which provides the data buffer for the upload. (And implicitly the total
        number of bytes to upload.) */
    private final Supplier<byte[]> supplierDataBuffer_;

    /** Number of bytes from dataUploaded_, which have not been transmitted to the ECU
        yet. */
    private int noBytesToUpload_;

    /** Index of byte in dataUploaded_, which is fetched next from the ECU. */
    private int writePos_;

    /** Number of bytes transmitted with the pending CRO message. */
    private int noBytesThisTime_;
    
    /** Progress reporting: When this number of bytes is left to download, then the next
        progress message should be printed. */
    private int noBytesLeftWhenProgressMsg_;

    /** Progress reporting: Every this number of bytes a program message is written. */
    private int noBytesBetweenProgressMsgs_;

    /**
     * A new instance of CcpCommandUpload is created and configured for a number
     * of CCP UPLOAD commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandUpload(CcpCommandArgs.Upload args) {
        isVerify_ = args.verify();
        supplierDataBuffer_ = args.supplierDataBuffer();
        dataUploaded_ = null;
        noBytesToUpload_ = 0;
        writePos_ = 0;
        noBytesThisTime_ = 0;
        noBytesLeftWhenProgressMsg_ = 0;
        noBytesBetweenProgressMsgs_ = 0x8000;

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
        dataUploaded_ = supplierDataBuffer_.get();
        assert dataUploaded_ != null;
        noBytesToUpload_ = dataUploaded_.length;
        if (noBytesToUpload_ > 0) {
            writePos_ = 0;

            /* Progress reporting. The next watermark can be negative, which doesn't care.
                 Note, a warning level is less specific, if the verbosity is higher or same! */
            if (_logger.getLevel().isLessSpecificThan(Level.DEBUG)) {
                noBytesBetweenProgressMsgs_ = 0x1000;
            } else {
                noBytesBetweenProgressMsgs_ = 0x8000;
            }
            noBytesLeftWhenProgressMsg_ = noBytesToUpload_ - noBytesBetweenProgressMsgs_;

            /* Send CAN CRO message. */
            fillPayloadCro();
            sendCro(/*noContentBytes*/ 3);

// @todo The MTA is unknown if UPLOAD acts after DIAG_SERVICE. Command DIAG_SERVICE could set MTA to an invalid value and here we check if MTA is avlid and choose the right mesage format
            _logger.printf( Level.INFO
                          , "Upload 0x%X Byte from memory address 0x%06X."
                          , noBytesToUpload_ 
                          , mta0()
                          );
            return CcpCroTransmitter.ResultTransmission.PENDING;
        } else {
// @todo Check what happens with empty upload result. Then decide if warning or error
            errCnt().warning();
            _logger.warn("Upload 0x0 Byte from memory is not supported");
            return CcpCroTransmitter.ResultTransmission.ERROR_NO_BYTES_TO_PROCESS;
        }
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
            final byte[] payloadDtoAry = payloadDtoAry();
            if (isVerify_) {
                for (int i=0, iUpload=writePos_; i<noBytesThisTime_; ++i, ++iUpload) {
                    if (payloadDtoAry[3+i] != dataUploaded_[iUpload]) {
                        errCnt().error();
                        _logger.printf( Level.ERROR
                                      , "Verify error. First failing memory address is"
                                        + " 0x%06X. Got 0x%02X, expect 0x%02X."
                                      , mta0() + (long)i
                                      , payloadDtoAry[3+i]
                                      , dataUploaded_[writePos_]
                                      );
                        resultTxRx = CcpCroTransmitter.ResultTransmission.ERROR_VERIFY_ERROR;
                        break;
                    }
                }
            } else {
                /* The data is returned in Byte 3, 4, ... Copy it into the result buffer. */
                System.arraycopy( payloadDtoAry
                                , 3
                                , dataUploaded_
                                , writePos_
                                , noBytesThisTime_
                                );
            }

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
            if ( resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS
                 &&  noBytesToUpload_ > 0
               ) {
                /* Progress reporting. */
                if (noBytesToUpload_ <= noBytesLeftWhenProgressMsg_) {
                    final int noBytesNow = dataUploaded_.length - noBytesLeftWhenProgressMsg_;
                    _logger.printf( Level.INFO
                                  , "0x%06X Byte uploaded (%.0f%%)."
                                  , noBytesNow
                                  , 100.0 * noBytesNow / (float)dataUploaded_.length
                                  );
                    noBytesLeftWhenProgressMsg_ -= noBytesBetweenProgressMsgs_;
                }
                    
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
        return "UPLOAD(noBytes=" + (dataUploaded_ == null? "?": ""+dataUploaded_.length) + ")";
    }
} /* End of class CcpCommandUpload definition. */




