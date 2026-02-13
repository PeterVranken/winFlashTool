/**
 * @file CcpCommandSetMta.java
 * CCP command SET_MTA.
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
/* Interface of class CcpCommandSetMta
 *   CcpCommandSetMta
 *   isSkippedInDryRun
 *   setup
 *   step
 *   toString
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;


/**
 * CCP command SET_MTA.
 */
public class CcpCommandSetMta extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandSetMta.class);

    /** Lambda object for getting the memory address to set with SET_MTA. The supplied
        value will become the new MTA when the ECU acknowledges the command. */
    private final LongSupplier supplierMemoryAddr_;

    /** The index of the affected MTA, either 0 or 1. */
    private final int idxMta_;

    /**
     * A new instance of CcpCommandSetMta is created and configured for the CCP SET_MTA
     * command.
     *   @param args
     * A record with all required configuration data.
     */
    CcpCommandSetMta(CcpCommandArgs.SetMta args) {
        supplierMemoryAddr_ = args.supplierAddress();

        idxMta_ = args.idxMta();

        /* For now, we do not implement any CCP which would ever make use of MTA1. */
        assert idxMta_ == 0: "MTA1 is not suppoted, yet";

        /* We do not support the address extension, which is just a relict from the ancient
           16 Bit controllers with paging mechanisms. */
        assert args.addressExt() == 0: "Address extension for CCP SET_MTA is not supported";

    } /* CcpCommandSetMta.CcpCommandSetMta */

    /**
     * The CCP SET_MTA command can be safely done even in dry-run.
     * {@inheritDoc}
     */
    @Override
    protected boolean isSkippedInDryRun() {
        return false;
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

        final byte idxMta = (byte)idxMta_;

        /* Send CAN CRO message with command SET_MTA. */
        final byte[] payloadCroAry = payloadCroAry();
        payloadCroAry[0] = CroCommandId.SET_MTA.getCode();
        payloadCroAry[2] = idxMta; /* The x in MTAx, x=0..1 */
        payloadCroAry[3] = (byte)0; /* Address extension not used in any modern CPU. */

        final long memoryAddr = supplierMemoryAddr_.getAsLong();
        if ((memoryAddr & 0xFFFFFFFF00000000l) == 0l) {
            /* Memory address in MSB endianess. */
            payloadCroAry[4] = (byte)((memoryAddr >> 24) & 0xFF);
            payloadCroAry[5] = (byte)((memoryAddr >> 16) & 0xFF);
            payloadCroAry[6] = (byte)((memoryAddr >>  8) & 0xFF);
            payloadCroAry[7] = (byte)((memoryAddr >>  0) & 0xFF);

            /* Make new MTA available to other commands, e.g., DOWNLOAD and PROGRAM. */
            setMta0(memoryAddr);

            sendCro(/*noContentBytes*/ 8);
            _logger.printf(Level.DEBUG, "CRO message SET_MTA(0x%06X) sent to ECU.",memoryAddr);
            return CcpCroTransmitter.ResultTransmission.PENDING;
        } else {
            errCnt().error();
            _logger.error("CCP command SET_MTA is initiated with invalid address.");
            return CcpCroTransmitter.ResultTransmission.ERROR_BAD_MTA_UPDATE;
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
        final CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if (resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            _logger.debug("ECU acknowledged SET_MTA.");

        } else if (resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            invalidateMta0();
            errCnt().error();
            _logger.error("Can't set MTA in the ECU. See previous error messages"
                          + " for details."
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
        final String addr = isValidMta0() 
                            ? "0x" + Long.toHexString(mta0()).toUpperCase()
                            : "?";
        return "SET_MTA(mta" + idxMta_ + "=" + addr + ")";
    }
} /* End of class CcpCommandSetMta definition. */




