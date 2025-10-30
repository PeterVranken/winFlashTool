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

    /**
     * A new instance of CcpCommandDisconnect is created.
     */
    protected CcpCommandDisconnect()
    {
    } /* CcpCommandDisconnect.CcpCommandDisconnect */


    /**
     * Inform the base class, which particular CCP commands are implemented by this class.
     *   @return
     * Get the set of enumerated values, which represent CCP commands that are implemented
     * by this derived class.
     */
    protected Set<CroCommandId> myCcpCmdIds()
    {
        final Set<CroCommandId> setOfCmdIds = new HashSet<CroCommandId>(1);
        setOfCmdIds.add(CroCommandId.DISCONNECT);
        return setOfCmdIds;
    }
    
    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @param argAry
     * The argument list consists of two arguments:<p>
     *   - The station address of the ECU to connect to. It is a 16 Bit value, expected as
     * an Integer.
     *   - True if the disconnect is final or false if it is temporay, expected as a
     * Boolean.
     */
    public void start(Object... argAry)
    {
        /* Parse argument list. */
        assert argAry.length == 2
               &&  argAry[0] instanceof Integer
               &&  argAry[1] instanceof Boolean
             : "Bad argument list. Expect an Integer and a Boolean object";
        final int stationAddr = ((Integer)argAry[0]).intValue();
        final boolean isEndOfSession = ((Boolean)argAry[1]).booleanValue();

        /* Send CAN CRO message with command DISCONNECT. */
        _payloadCroAry[0] = CroCommandId.DISCONNECT.getCode();
        /* 0: temporary, 1: end of session. */
        _payloadCroAry[2] = (byte)(isEndOfSession? 0x01: 0x00);
        _payloadCroAry[3] = 0;
        _payloadCroAry[4] = (byte)(stationAddr & 0x00FF);
        _payloadCroAry[5] = (byte)((stationAddr & 0xFF00) >> 8);
        sendCro(/*noContentBytes*/ 6);
        _logger.debug("CRO message DISCONNECT sent to station {}.", stationAddr);
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
            _errCnt.error();
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




