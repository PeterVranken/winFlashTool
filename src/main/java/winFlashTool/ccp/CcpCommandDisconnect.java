/**
 * @file CcpCommandDisconnect.java
 * CCP command DISCONNECT.
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
/* Interface of class CcpCommandDicconnect
 *   CcpCommandDisconnect
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;


/**
 * CCP command CONNECT.
 */
public class CcpCommandDisconnect extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandDisconnect.class);

    /** All required command arguments, provided at object creation time. */
    private final CcpCommandArgs.Disconnect args_;
        
    /**
     * A new instance of CcpCommandDisconnect is created and configured for the CCP
     * #DISCONNECT command.
     *   @param args
     * A record with all required configuration data.
     */
    protected CcpCommandDisconnect(CcpCommandArgs.Disconnect args)
    {
        args_ = args;
        
    } /* CcpCommandDisconnect.CcpCommandDisconnect */


    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     */
    public void start()
    {
        final byte[] payloadCroAry = payloadCroAry();
        
        /* Send CAN CRO message with command DISCONNECT. */
        payloadCroAry[0] = CroCommandId.DISCONNECT.getCode();
        
        /* 0: temporary, 1: end of session. */
        payloadCroAry[2] = (byte)(args_.isEndOfSession()? 0x01: 0x00);
        payloadCroAry[3] = 0;
        payloadCroAry[4] = (byte)(args_.stationAddr() & 0x00FF);
        payloadCroAry[5] = (byte)((args_.stationAddr() & 0xFF00) >> 8);
        sendCro(/*noContentBytes*/ 6);
        _logger.debug("CRO message DISCONNECT sent to station {}.", args_.stationAddr());
    }


    /**
     * All CCP commands are implemented as state machines. This method implements a single
     * calculation step of the FSM. It needs to be called regularly.
     *   @return
     * The method returns "pending" until the command has completed. The first time this
     * method returns anything other than "pending" needs to be the last time this method
     * is called -- until the command is re-started and executed again.
     */
    public CcpCroTransmitter.ResultTransmission step()
    {
        final CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
            _logger.info("ECU is disconnected.");
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            errCnt().error();
            _logger.error("Can't disconnect from the ECU. See previous error messages"
                          + " for details."
                         );
        }
        else
        {
            /* DTO has not been received yet. We continue polling. */
        }

        return resultTxRx;

    } /* step */

} /* End of class CcpCommandDisconnect definition. */




