/**
 * @file CcpCommandDiagService.java
 * Initiate a diagnostc service in the ECU using CCP command DIAG_SERVICE.
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
/* Interface of class CcpCommandDiagService
 *   CcpCommandDiagService
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
 * Initiate a diagnostc service in the ECU using CCP command DIAG_SERVICE.
 */
public class CcpCommandDiagService extends CcpCommandBase {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandDiagService.class);

    /** The buffer of required size for the (later) uploaded service result data. */
    private byte[] dataServiceResult_;
    
    /** The command arguments. */
    final CcpCommandArgs.DiagService cmdArgs_;
    
    /** Information extracted from the CCP command response: How many bytes to uplaod for
        the result of the diagnostic service? */
    private int sizeOfServiceResponse_;
    
    /**
     * A new instance of CcpCommandDiagService is created and configured.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandDiagService(CcpCommandArgs.DiagService args) {
        cmdArgs_ = args;
        sizeOfServiceResponse_ = 0;
        dataServiceResult_ = null;
        
    } /* CcpCommandDiagService.CcpCommandDiagService */

    /**
     * A getter for the result buffer, which will contain the uploaded result from the
     * diagnostic service.<p> 
     *   This function links this CCP command with the UPLOAD command which has to follow
     * up to upload the result bytes of the diagnostic service. For this purpose, the
     * UPLOAD command will use this function to get the buffer where to place the result
     * data. This must not happen before the DIAG_SERVICE command has completed; only then,
     * the required buffer size is known.<p>
     *   Later, after completion of the UPLOAD command, anyone can use this method to fetch
     * the service result.
     *   @return
     * On first call, get the buffer for storing the service result data. On subsequent
     * calls, get the service result.
     */
    byte[] getServiceResult() {
        if (dataServiceResult_ == null) {
            /* First request of result buffer: Create the buffer. This request is made by
               the CCP UPLOAD command before it starts. It requires the still empty buffer
               for collecting the uploaded data. Implicitly, it receives the number of
               bytes to upload from this DIAG_SERVICE command through the length of the
               returned buffer. */
            dataServiceResult_ = new byte[sizeOfServiceResponse_];
        } else {
            /* Subsequent requests come from the application in order to get the actual
               UPLOAD result. */
        }
        return dataServiceResult_;
    }

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
        /* Invalidate an earlier result. */
        sizeOfServiceResponse_ = 0;
        dataServiceResult_ = null;
        
        /* Compose a CRO message.
             For both, DIAG_SERVICE and ACTION_SERVICE, the CCP spec (2.1 as of Feb 18,
           1999) says in the command description that the service number would be 16 Bit
           but the illustrated example on the same page shows it as an 8 Bit value. An
           I-net query clearly stated that the 8 Bit example is correct. */
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = CroCommandId.DIAG_SERVICE.getCode();
        payloadCroAry[2] = cmdArgs_.serviceNum();

        /* Send CAN CRO message. */
        sendCro(/*noContentBytes*/ 3);
        
        _logger.printf( Level.DEBUG
                      , "Initiate diagnostic service 0x%02X."
                      , (int)cmdArgs_.serviceNum()
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
        if (resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            final byte[] payloadDtoAry = payloadDtoAry();
            sizeOfServiceResponse_ = PCANBasicEx.b2i(payloadDtoAry[3]);

        } else if (resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* Invalidate the result. */
            assert sizeOfServiceResponse_ == 0;
            
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.printf( Level.ERROR
                          , "Diagnostic service request 0x%02X is rejected by the ECU."
                          , (int)cmdArgs_.serviceNum()
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
        return "DIAG_SERVICE(serviceNumber=0x" 
               + Integer.toHexString(PCANBasicEx.b2i(cmdArgs_.serviceNum())) + ")";
    }
} /* End of class CcpCommandDiagService definition. */




