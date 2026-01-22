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
 *   CcpCommandsDownloadProgram (2 variants)
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
    private static final Logger _logger = LogManager.getLogger(CcpCommandsDownloadProgram.class);

    /** Is this a sequence of DOWNLOAD or of PROGRAM commands? */
    private final boolean isDownload_;

    /** The data to download. */
    private final byte[] dataToDownload_;

    /** Number of bytes from dataToDownload_, which have not been transmitted to the ECU
        yet. */
    private int noBytesToDownload_;

    /** Index of byte in dataToDownload_, which is transmitted next to the ECU. */
    private int readPos_;

    /** Number of bytes transmitted with the pending CRO message. */
    private int noBytesThisTime_;
    
    /**
     * A new instance of CcpCommandsDownloadProgram is created and configured for a number
     * of CCP DOWNLOAD commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandsDownloadProgram(CcpCommandArgs.Download args)
    {
        isDownload_ = true;
        dataToDownload_ = args.data();
        noBytesToDownload_ = 0;
        readPos_ = 0;
        noBytesThisTime_ = 0;

    } /* CcpCommandsDownloadProgram.CcpCommandsDownloadProgram */

    /**
     * A new instance of CcpCommandsDownloadProgram is created and configured for a number
     * of CCP PROGRAM commands.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandsDownloadProgram(CcpCommandArgs.Program args)
    {
        isDownload_ = false;
        dataToDownload_ = args.data();
        noBytesToDownload_ = 0;
        readPos_ = 0;
        noBytesThisTime_ = 0;

    } /* CcpCommandsDownloadProgram.CcpCommandsDownloadProgram */

    /**
     * Compose a CRO message with the next bytes to download/program and update status of
     * remaining data.
     */
    private void fillPayloadCro()
    {
        /* CCP command: DOWNLOAD vs. PROGRAM and use of fixed-size command as long as
           possible. */
        final byte ccpCmdId;
        if(noBytesToDownload_ >= 6)
        {
            noBytesThisTime_ = 6;
            if(isDownload_)
                ccpCmdId = CroCommandId.DOWNLOAD_6.getCode();
            else
                ccpCmdId = CroCommandId.PROGRAM_6.getCode();
        }
        else
        {
            noBytesThisTime_ = noBytesToDownload_;
            if(isDownload_)
                ccpCmdId = CroCommandId.DOWNLOAD.getCode();
            else
                ccpCmdId = CroCommandId.PROGRAM.getCode();
        }
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = ccpCmdId;
        final int idxByteWithData;
        if(noBytesThisTime_ < 6)
        {
            payloadCroAry[2] = (byte)noBytesThisTime_;
            idxByteWithData = 3;
        }
        else
            idxByteWithData = 2;

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
     */
    public void setup()
    {
        assert dataToDownload_ != null;
        noBytesToDownload_ = dataToDownload_.length;
        assert noBytesToDownload_ > 0: "Empty program is not supported";
        readPos_ = 0;

        /* Send CAN CRO message with appropriate command ID. */
        fillPayloadCro();
        sendCro(/*noContentBytes*/ 8);
        
        _logger.printf( Level.INFO
                      , "%s 0x%06X Byte %s memory address 0x%06X."
                      , isDownload_? "Download": "Program"
                      , noBytesToDownload_ 
                      , isDownload_? "to": "at"
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
    public CcpCroTransmitter.ResultTransmission step()
    {
        CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
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
            if(newMta == expectedNewMta)
            {
                /* Update the status, where we are with the download. */
                noBytesToDownload_ -= noBytesThisTime_;
                readPos_ += noBytesThisTime_;
                mta0(expectedNewMta);

                /* Send next chunk of data if there are bytes left. */
                if(noBytesToDownload_ > 0)
                {
                    fillPayloadCro();
                    sendCro(/*noContentBytes*/ 8);
                    resultTxRx = CcpCroTransmitter.ResultTransmission.PENDING;
                }
            }
            else
            {
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
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
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
        }
        else
        {
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
        final String cmdName = isDownload_? "DOWNLOAD": "PROGRAM";
        return cmdName + "(noBytes=" + dataToDownload_.length + ")";
    }
} /* End of class CcpCommandsDownloadProgram definition. */




