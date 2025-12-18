/**
 * @file CcpCommandConnect.java
 * CCP command CONNECT.
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
/* Interface of class CcpCommandConnect
 *   CcpCommandConnect
 *   isSkippedInDryRun
 *   getRequiredTimeoutCroTillDto
 *   setup
 *   step
 *   toString
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import java.util.Set;
import java.util.HashSet;


/**
 * CCP command CONNECT.
 */
public class CcpCommandConnect extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandConnect.class);

    /** All required command arguments, provided at object creation time. */
    private final CcpCommandArgs.Connect args_;
    
    /**
     * A new instance of CcpCommandConnect is created and configured for the CCP
     * #CONNECT command.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandConnect(CcpCommandArgs.Connect args)
    {
        args_ = args;

    } /* CcpCommandConnect.CcpCommandConnect */

    /**
     * The CCP CONNECT command can be safely done even in dry-run.
     * {@inheritDoc}
     */
    @Override
    protected boolean isSkippedInDryRun() {
        return false;
    }
    
    /**
     * Get the timeout value for the time span between sending CRO and receiving DTO, that
     * is required by CCP command CONNECT.
     *   @return
     * The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
     * is Milliseconds.
     */
    @Override
    int getRequiredTimeoutCroTillDto() {
    
        /* Connect should not have a long timeout. In the quite normal situation, that
           there is not MCU waiting for a CCP CONNECT, the blocking states resulting from a
           long timeout are annoying. Delayed powering of the target board is better
           handled by repeated connect attempts than by a single one with very long
           blocking timeout. */
        return 250;

    } /* CcpCroTransmitter.CcpCroTransmitter */

    /**
     * The CCP command is initiated. After return from setup(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     */
    public void setup()
    {
        /* Send CAN CRO message with command CONNECT. */
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = CroCommandId.CONNECT.getCode();
        payloadCroAry[2] = (byte)(args_.stationAddr() & 0x00FF);
        payloadCroAry[3] = (byte)((args_.stationAddr() & 0xFF00) >> 8);
        sendCro(/*noContentBytes*/ 4);
        _logger.debug("CRO message CONNECT sent to station {}.", args_.stationAddr());
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
            _logger.info("ECU is connected.");
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.error("Can't connect to the ECU. See previous error messages for"
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
        return "CONNECT(stationAddress=" + args_.stationAddr() + ")";
    }
} /* End of class CcpCommandConnect definition. */




