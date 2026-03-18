/**
 * @file CcpCommandConnect.java
 * CCP command CONNECT.
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
/* Interface of class CcpCommandConnect
 *   CcpCommandConnect
 *   isSkippedInDryRun
 *   getRequiredTimeoutCroTillDto
 *   setup
 *   step
 *   toString
 */

package winFlashTool.ccp;

//import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;


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

        return Math.max(args_.tiCroToDtoInMs(), 1);

    } /* CcpCroTransmitter.CcpCroTransmitter */

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

        /* Send CAN CRO message with command CONNECT. */
        invalidateMta0();
        setCroCmdCounter(0);
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = CroCommandId.CONNECT.getCode();
        payloadCroAry[2] = (byte)(args_.stationAddr() & 0x00FF);
        payloadCroAry[3] = (byte)((args_.stationAddr() & 0xFF00) >> 8);
        sendCro(/*noContentBytes*/ 4);
        _logger.debug("CRO message CONNECT sent to station {}.", args_.stationAddr());
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
        final CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if (resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            _logger.info("ECU is connected.");
        } else if (resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. No error is immediately reported for
               the CONNECT command: Most typical, the attempt to connect i srepeated
               several times and an error is reported only if all attempty have failed.
               Nothing else to do. */
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
        return "CONNECT(stationAddress=" + args_.stationAddr() + ")";
    }
} /* End of class CcpCommandConnect definition. */




