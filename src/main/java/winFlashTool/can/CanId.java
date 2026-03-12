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

package winFlashTool.can;

import org.apache.logging.log4j.*;
import peak.can.basic.*;
import winFlashTool.basics.Range;

/**
 * A CAN Id in a representation, which is useful for the configuration of a PEAK PCAN device.
 */
public class CanId
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CanId.class);

    /** The (range of) CAN ID(s) as a raw number. */
    private final Range canIdRange_;

    /** PCAN_MESSAGE_EXTENDED if canId_ is an extended 29 Bit ID, PCAN_MESSAGE_STANDARD
        otherwise. */
    private final TPCANMode kindOfCanId_;

    /** The CAN ID of all CRO messages in textual representation. */
    private String canIdAsStr_ = null;

    /**
     * Create a range of CAN IDs. This can be useful to set the acceptance filter in the
       PCAN Basic API.
     *   @param canIdRange
     * The CAN IDs as a range from..till of raw numbers.
     *   @param isExtId
     * Pass true if all can IDs in the range are extended 29 Bit ID and false if they are
     * 11 Bit IDs.
     */
    public CanId(Range canIdRange, boolean isExtId) {
        canIdRange_  = canIdRange;
        kindOfCanId_ = isExtId? TPCANMode.PCAN_MODE_EXTENDED: TPCANMode.PCAN_MODE_STANDARD;
        assert checkCanId(): "CAN ID out of range";
    } /* CanId.CanId */

    /**
     * Create a single CAN ID.
     *   @param canId
     * The CAN ID as a raw number.
     *   @param isExtId
     * Pass true if canId is an extended 29 Bit ID and false if it is an 11 Bit ID.
     */
    public CanId(int canId, boolean isExtId) {
        this(new Range(canId, canId+1), isExtId);
        assert checkCanId(): "CAN ID out of range";
    } /* CanId.CanId */

    /**
     * Check numeric range of CAN ID.
     */
    private boolean checkCanId() {
        final long max = kindOfCanId_ == TPCANMode.PCAN_MODE_EXTENDED? 0x1FFFFFFF: 0x7FF;
        return canIdRange_.from() >= 0  
               &&  canIdRange_.till() >= canIdRange_.from()
               &&  canIdRange_.till() <= max;
    }
    
    /**
     * Get the number of CAN IDs in the range.
     *   @return
     * Get the number of CAN IDs, which are defined in this objects.
     */
    public int getNoCanIds()
    {
        return (int)canIdRange_.size();
    }

    /**
     * Get the CAN ID. Only allowed if this is a single CAN ID, if getNoCanIds() returns 1.
     *   @return
     * Get the ID as raw number.
     */
    public int getCanId()
    {
        assert getNoCanIds() == 1: "Not allowed for a range of CAN IDs";
        return getCanIdFirst();
    }

    /**
     * Get the entire range of CAN IDs.
     *   @return
     * Get the IDs as a range from..till.
     */
    public Range getCanIds()
    {
        return canIdRange_;
    }

    /**
     * Get the first CAN ID from the range of those.
     *   @return
     * Get the ID.
     */
    public int getCanIdFirst()
    {
        return (int)canIdRange_.from();
    }

    /**
     * Get the last CAN ID from the range of those.
     *   @return
     * Get the ID. The result is identical to getCanIdFirst() for single CAN IDs created
     * with CanId(int canId, boolean isExtId).
     */
    public int getCanIdLast()
    {
        /* -1: Ranges are always exclusive. */
        return (int)canIdRange_.till() - 1;
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
    @Override
    public String toString()
    {
        if(canIdAsStr_ == null)
        {
            final String x = kindOfCanId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "";
            if (canIdRange_.size() == 1) {
                canIdAsStr_ = "" + getCanId() + x
                              + " (0x" + Integer.toHexString(getCanId()).toUpperCase()
                              + x + ")";
            } else {
                final Range canIdRange = getCanIds();
                canIdAsStr_ = "" + getCanIdFirst() + x + ".." + getCanIdLast() + x
                              + " (0x" + Integer.toHexString(getCanIdFirst()).toUpperCase()
                              + x + ".." + "(0x"
                              + Integer.toHexString(getCanIdLast()).toUpperCase() + x + ")";
            }
        }
        return canIdAsStr_;
    }
} /* End of class CanId definition. */









