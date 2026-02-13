/**
 * @file CcpCommandsDownloadProgram.java
 * Download a number of bytes to the ECU using CCP commands DOWNLOAD, DOWNLOAD_6, PROGRAM
 * and PROGRAM_6.
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
/* Interface of class CcpCommandsDownloadProgram
 *   CcpCommandsDownloadProgram (3 variants)
 *   setData
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
 * Download a number of bytes to the ECU using CCP commands DOWNLOAD, DOWNLOAD_6, PROGRAM
 * and PROGRAM_6.
 */
public class CcpCommandsDownloadProgram extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger
                                                        (CcpCommandsDownloadProgram.class);

    /** Is this a sequence of DOWNLOAD or of PROGRAM commands? */
    private final boolean isDownload_;

    /** The data to download. */
    private byte[] dataToDownload_;

    /** Number of bytes from dataToDownload_, which have not been transmitted to the ECU
        yet. */
    private int noBytesToDownload_;

    /** Index of byte in dataToDownload_, which is transmitted next to the ECU. */
    private int readPos_;

    /** Number of bytes transmitted with the pending CRO message. */
    private int noBytesThisTime_;

    /** Progress reporting: When this number of bytes is left to download, then the next
        progress message should be printed. */
    private int noBytesLeftWhenProgressMsg_;

    /** Progress reporting: Every this number of bytes a program message is written. */
    private int noBytesBetweenProgressMsgs_;

    /**
     * A new instance of CcpCommandsDownloadProgram is created and pre-configured.<p>
     *   No data to download or program is specified yet. You need to call setData() prior
     * to calling setup().
     *   @param isDownload
     * For a CCP DOWNLOAD pass true, for a CCP PROGRAM pass false.
     */
    protected CcpCommandsDownloadProgram(boolean isDownload) {
        isDownload_ = isDownload;
        setData(null);
        noBytesLeftWhenProgressMsg_ = 0;
        noBytesBetweenProgressMsgs_ = 0x8000;
    }
    
    /**
     * A new instance of CcpCommandsDownloadProgram is created and configured for a number
     * of CCP DOWNLOAD commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandsDownloadProgram(CcpCommandArgs.Download args) {
        isDownload_ = true;
        setData(args.data());
        noBytesLeftWhenProgressMsg_ = 0;
        noBytesBetweenProgressMsgs_ = 0x8000;

    } /* CcpCommandsDownloadProgram.CcpCommandsDownloadProgram */

    /**
     * A new instance of CcpCommandsDownloadProgram is created and configured for a number
     * of CCP PROGRAM commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandsDownloadProgram(CcpCommandArgs.Program args) {
        isDownload_ = false;
        setData(args.data());
        noBytesLeftWhenProgressMsg_ = 0;
        noBytesBetweenProgressMsgs_ = 0x8000;

    } /* CcpCommandsDownloadProgram.CcpCommandsDownloadProgram */

    /**
     * Set the data to download and program.<p>
     *   This methods is an option to set the data later then at construction time. If it
     * is used then this needs be happen before setup() is called.
     */
    protected void setData(byte[] data) {
        /* Replacing data is not a technical issue but it points to potential misuse of the
           class. */
        assert dataToDownload_ == null: "Suspicious redefinition of data for download";
        dataToDownload_ = data;
        noBytesToDownload_ = 0;
        readPos_ = 0;
        noBytesThisTime_ = 0;
        
    } /* setData */
    
    /**
     * Compose a CRO message with the next bytes to download/program and update status of
     * remaining data.
     */
    private void fillPayloadCro() {
        /* CCP command: DOWNLOAD vs. PROGRAM and use of fixed-size command as long as
           possible. */
        final byte ccpCmdId;
        if (noBytesToDownload_ >= 6) {
            noBytesThisTime_ = 6;
            if (isDownload_) {
                ccpCmdId = CroCommandId.DOWNLOAD_6.getCode();
            } else {
                ccpCmdId = CroCommandId.PROGRAM_6.getCode();
            }
        } else {
            noBytesThisTime_ = noBytesToDownload_;
            if(isDownload_)
                ccpCmdId = CroCommandId.DOWNLOAD.getCode();
            else
                ccpCmdId = CroCommandId.PROGRAM.getCode();
        }
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = ccpCmdId;
        final int idxByteWithData;
        if (noBytesThisTime_ < 6) {
            payloadCroAry[2] = (byte)noBytesThisTime_;
            idxByteWithData = 3;
        } else {
            idxByteWithData = 2;
        }
        
        /* Copy next block of bytes into the CRO message. */
        System.arraycopy( dataToDownload_
                        , readPos_
                        , payloadCroAry
                        , idxByteWithData
                        , noBytesThisTime_
                        );
        _logger.trace( "CRO message {} sent with {} Byte data. {} Byte remaining."
                     , CroCommandId.fromCode(payloadCroAry[0]).getCmdName()
                     , noBytesThisTime_
                     , noBytesToDownload_ - noBytesThisTime_
                     );
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
        assert dataToDownload_ != null;
        noBytesToDownload_ = dataToDownload_.length;
        assert noBytesToDownload_ > 0: "Empty program is not supported";
        readPos_ = 0;

        /* Progress reporting. The next watermark can be negative, which doesn't care.
             Note, a warning level is less specific, if the verbosity is higher or same! */
        if (_logger.getLevel().isLessSpecificThan(Level.DEBUG)) {
            noBytesBetweenProgressMsgs_ = 0x1000;
        } else {
            noBytesBetweenProgressMsgs_ = 0x8000;
        }
        noBytesLeftWhenProgressMsg_ = noBytesToDownload_ - noBytesBetweenProgressMsgs_;
        if(noBytesLeftWhenProgressMsg_ < 0) {
            noBytesLeftWhenProgressMsg_ = 0;
        }

        /* Send CAN CRO message with appropriate command ID. */
        fillPayloadCro();
        sendCro(/*noContentBytes*/ 8);

        _logger.printf( Level.INFO
                      , "%s 0x%X Byte %s memory address 0x%06X."
                      , isDownload_? "Download": "Program"
                      , noBytesToDownload_
                      , isDownload_? "to": "at"
                      , mta0()
                      );
        return CcpCroTransmitter.ResultTransmission.PENDING;
        
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
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            /* The new MTA is returned as 4 bytes with MSB endianess. */
            final byte[] payloadDtoAry = payloadDtoAry();
            final int newMta = (PCANBasicEx.b2i(payloadDtoAry[4]) << 24)
                               + (PCANBasicEx.b2i(payloadDtoAry[5]) << 16)
                               + (PCANBasicEx.b2i(payloadDtoAry[6]) <<  8)
                               + (PCANBasicEx.b2i(payloadDtoAry[7]) <<  0);

            _logger.printf( Level.TRACE
                          , "ECU acknowledges data transfer. New MTA is 0x%06X."
                          , newMta
                          );

            final long expectedNewMta = mta0() + noBytesThisTime_;
            if (newMta == expectedNewMta) {
                /* Update the status, where we are with the download. */
                noBytesToDownload_ -= noBytesThisTime_;
                readPos_ += noBytesThisTime_;
                setMta0(expectedNewMta);

                /* Progress reporting. To see nice round numbers, we don't report the
                   accurate number but the next reached threshold. */
                if (noBytesToDownload_ <= noBytesLeftWhenProgressMsg_) {
                    final int noBytesNow = dataToDownload_.length
                                           - noBytesLeftWhenProgressMsg_;
                    _logger.printf( Level.INFO
                                  , "0x%06X Byte %s (%.0f%%)."
                                  , noBytesNow
                                  , isDownload_? "downloaded": "programmed"
                                  , 100.0 * noBytesNow / (float)dataToDownload_.length
                                  );
                    noBytesLeftWhenProgressMsg_ -= noBytesBetweenProgressMsgs_;
                    if(noBytesLeftWhenProgressMsg_ < 0) {
                        noBytesLeftWhenProgressMsg_ = 0;
                    }
                }
                    
                /* Send next chunk of data if there are bytes left. */
                if (noBytesToDownload_ > 0) {
                    fillPayloadCro();
                    sendCro(/*noContentBytes*/ 8);
                    resultTxRx = CcpCroTransmitter.ResultTransmission.PENDING;
                }
            } else {
                /* There is a communication problem. We abort the download. */
                errCnt().error();
                _logger.printf( Level.ERROR
                              , "MTA update error during download/program to/in the ECU."
                                + " Expected MTA 0x%06X but received 0x%06X."
                              , expectedNewMta
                              , newMta
                              );
                resultTxRx = CcpCroTransmitter.ResultTransmission.ERROR_BAD_MTA_UPDATE;
            }
        }
        else if (resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.printf( Level.ERROR
                          , "Can't %s data %s the ECU. Failing memory address is 0x%06X. See"
                            + " previous error messages for details."
                          , isDownload_? "download": "program"
                          , isDownload_? "to": "in"
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
        final String cmdName = isDownload_? "DOWNLOAD": "PROGRAM"
                   , noBytes = dataToDownload_ != null? ""+dataToDownload_.length: "?";
        return cmdName + "(noBytes=" + noBytes + ")";
    }
} /* End of class CcpCommandsDownloadProgram definition. */




