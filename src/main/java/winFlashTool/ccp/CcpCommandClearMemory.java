/**
 * @file CcpCommandClearMemory.java
 * CCP command CLEAR_MEMORY.
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
/* Interface of class CcpCommandClearMemory
 *   CcpCommandClearMemory
 *   getRequiredTimeoutCroTillDto
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
import java.util.HashSet;


/**
 * CCP command CLEAR_MEMORY.
 */
public class CcpCommandClearMemory extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandClearMemory.class);

    /** All required command arguments, provided at object creation time. */
    private final CcpCommandArgs.ClearMemory args_;

    /**
     * A new instance of CcpCommandClearMemory is created and configured for the CCP
     * #CLEAR_MEMORY command.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandClearMemory(CcpCommandArgs.ClearMemory args)
    {
        args_ = args;

    } /* CcpCommandClearMemory.CcpCommandClearMemory */

    /**
     * Get the timeout value for the time span between sending CRO and receiving DTO, that
     * is required by the CLEAR_MEMORY command.
     *   @return
     * The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
     * is Milliseconds.
     */
    @Override
    int getRequiredTimeoutCroTillDto() {

        /* CLEAR_MEMORY is a blocking operation in our flash bootloader. The DTO is sent
           only on completion. This requires a timeout of several seconds. */
        return 30/*s*/ * 1000;

    } /* CcpCroTransmitter.CcpCroTransmitter */

    /**
     * The CCP command is initiated. After return from setup(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     */
    public void setup()
    {
        final byte[] payloadCroAry = payloadCroAry();

        /* Send CAN CRO message with command CLEAR_MEMORY. */
        payloadCroAry[0] = CroCommandId.CLEAR_MEMORY.getCode();

        /* Memory block size in MSB endianess. */
        payloadCroAry[2] = (byte)((args_.noBytesToErase() >> 24) & 0xFF);
        payloadCroAry[3] = (byte)((args_.noBytesToErase() >> 16) & 0xFF);
        payloadCroAry[4] = (byte)((args_.noBytesToErase() >>  8) & 0xFF);
        payloadCroAry[5] = (byte)((args_.noBytesToErase() >>  0) & 0xFF);

        sendCro(/*noContentBytes*/ 6);
        _logger.printf( Level.DEBUG
                      , "CRO message CLEAR_MEMORY(0x%06X) sent to ECU."
                      , args_.noBytesToErase()
                      );
    }

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
        final CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
            _logger.debug("ECU acknowledged CLEAR_MEMORY.");
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.error("Can't erase ECU memory. See previous error messages for"
                          + " details."
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
        return "CLEAR_MEMORY(noBytes=0x"
               + Integer.toHexString(args_.noBytesToErase()).toUpperCase()
               + ")";
    }
} /* End of class CcpCommandClearMemory definition. */
