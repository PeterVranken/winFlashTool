/**
 * @file CanId.java
 * A CAN Id.
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
/* Interface of class CcpCanIds
 *   CanId
 *   getCanId
 *   getMsgType
 *   getMsgMode
 *   canIdToString
 */

package winFlashTool.ccp;

import org.apache.logging.log4j.*;
import peak.can.basic.*;

/**
 * A CAN Id in a representation, which is useful for the configuration of a PEAK PCAN device.
 */
class CanId
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CanId.class);

    /** The CAN ID as a raw number. */
    private final int canId_;

    /** PCAN_MESSAGE_EXTENDED if canId_ is an extended 29 Bit ID, PCAN_MESSAGE_STANDARD
        otherwise. */
    private final TPCANMode kindOfCanId_;

    /** The CAN ID of all CRO messages in textual representation. */
    private String canIdAsStr_ = null;

    /**
     * Create the CAN ID.
     *   @param canId
     * The CAN ID as a raw number.
     *   @param isExtId
     * Pass true if canId is an extended 29 Bit ID and false if it is an 11 Bit ID.
     */
    public CanId(int canId, boolean isExtId)
    {
        canId_       = canId;
        kindOfCanId_ = isExtId? TPCANMode.PCAN_MODE_EXTENDED: TPCANMode.PCAN_MODE_STANDARD;

    } /* CanId.CanId */

    /**
     * Get the CAN ID.
     *   @return
     * Get the ID as raw number.
     */
    public int getCanId()
    {
        return canId_;
    }

    /**
     * Get the message type (standard, extended).
     *   @return
     * Get either TPCANMessageType.PCAN_MESSAGE_STANDARD or
     * TPCANMessageType.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMessageType getMsgType()
    {
        if(kindOfCanId_ == TPCANMode.PCAN_MODE_STANDARD)
            return TPCANMessageType.PCAN_MESSAGE_STANDARD;
        else
        {
            assert kindOfCanId_ == TPCANMode.PCAN_MODE_EXTENDED;
            return TPCANMessageType.PCAN_MESSAGE_EXTENDED;
        }
    }

    /**
     * Get the message mode (standard, extended).
     *   @return
     * Get either TPCANMode.PCAN_MESSAGE_STANDARD or
     * TPCANMode.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMode getMsgMode()
    {
        return kindOfCanId_;
    }

    /**
     * Get the CAN ID in textual representation.
     *   @return
     * The CAN ID in decimal and hex as String.
     */
    public String canIdToString()
    {
        if(canIdAsStr_ == null)
        {
            canIdAsStr_ = "" + canId_
                          + (kindOfCanId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                          + " (0x" + Integer.toHexString(canId_)
                          + (kindOfCanId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                          + ")";
        }
        return canIdAsStr_;
    }

} /* End of class CcpCanIds definition. */









