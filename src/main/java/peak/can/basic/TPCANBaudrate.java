/* SPDX-License-Identifier: LGPL-2.1-only */
/*
 * $Id: TPCANBaudrate.java 7391 2020-08-10 08:32:30Z Fabrice $
 * @LastChange $Date: 2020-08-10 10:32:30 +0200 (Mon, 10 Aug 2020) $
 * 
 * PCANBasic JAVA Interface.
 *
 * Copyright (C) 2001-2020  PEAK System-Technik GmbH <www.peak-system.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * PCAN is a registered Trademark of PEAK-System Germany GmbH
 *
 * Author: 		 Jonathan Urban/Uwe Wilhelm/Fabrice Vergnaud
 * Contact:      <linux@peak-system.com>
 * Maintainer:   Fabrice Vergnaud <f.vergnaud@peak-system.com>
 */
package peak.can.basic;

/**
 * Baud rate codes = BTR0/BTR1 register values for the CAN controller. You can
 * define your own Baud rate with the BTROBTR1 register. Take a look at
 * www.peak-system.com for our free software "BAUDTOOL" to calculate the
 * BTROBTR1 register for every baudrate and sample point.
 */
public enum TPCANBaudrate {

    /**
     * 1 MBit/s
     */
    PCAN_BAUD_1M(0x0014, 1000000, false),
    /**
     * 800 kBit/s
     */
    PCAN_BAUD_800K(0x0016, 800000, false),
    /**
     * 500 kBit/s
     */
    PCAN_BAUD_500K(0x001C, 500000, false),
    /**
     * 250 kBit/s
     */
    PCAN_BAUD_250K(0x011C, 250000, false),
    /**
     * 125 kBit/s
     */
    PCAN_BAUD_125K(0x031C, 125000, false),
    /**
     * 100 kBit/s
     */
    PCAN_BAUD_100K(0x432F, 100000, false),
    /**
     * 95,238 kBit/s
     */
    PCAN_BAUD_95K(0xC34E, 95000, false),
    /**
     * 83,33 kBit/s
     */
    PCAN_BAUD_83K(0x852B, 83000, false),
    /**
     * 50 kBit/s
     */
    PCAN_BAUD_50K(0x472F, 50000, false),
    /**
     * 47,619 kBit/s
     */
    PCAN_BAUD_47K(0x1414, 47000, false),
    /**
     * 33,333 kBit/s
     */
    PCAN_BAUD_33K(0x8B2F, 33000, false),
    /**
     * 20 kBit/s
     */
    PCAN_BAUD_20K(0x532F, 20000, false),
    /**
     * 10 kBit/s
     */
    PCAN_BAUD_10K(0x672F, 10000, false),
    /**
     * 5 kBit/s
     */
    PCAN_BAUD_5K(0x7F7F, 5000, false),
    /**
     * User
     */
    PCAN_BAUD_User0(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User1(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User2(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User3(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User4(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User5(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User6(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User7(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User8(0x0, 0, true),
    /**
     * User
     */
    PCAN_BAUD_User9(0x0, 0, true);

    private int value;
    private int baudRate;
    private final boolean user;

    private TPCANBaudrate(int value, int baudRate, boolean user) {
        this.value = value;
        this.baudRate = baudRate;
        this.user = user;
    }

    /**
     * Returns the value of the baud rate code.
     * @return value of the baud rate code
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Returns the value of the baud rate.
     * @return Value of the baud rate in Hz.
     */
    public int getBaudRate() {
        return this.baudRate;
    }

    /**
     * Sets the user-defined value.
     * @param value Baud rate code.
     * @param baudRate Baud rate in Hz.
     */
    public void setValue(int value, int baudRate) {
        if (user) {
            this.value = value;
            this.baudRate = baudRate;
        } else {
            assert false: "Attempt to reprogram a standard Baud rate";
        }
    }

    /**
     * Clears the user-defined value
     */
    public void clearValue() {
        if (user) {
            this.value = 0;
            this.baudRate = 0;
        } else {
            assert false: "Attempt to reprogram a standard Baud rate";
        }
    }

    /**
     * Returns a TPCANBaudrate matching the corresponding Baud rate code.
     *   @return
     * A TPCANBaudrate matching the Baud rate code or null if the Baud rate code is
     * not valid.
     *   @param value 
     * The Baud rate code to look for.
     */
    public static TPCANBaudrate valueOf(int value) {
        for (TPCANBaudrate rate : values()) {
            if (rate.getValue() == value) {
                return rate;
            }
        }
        return null;
    }
    
    /**
     * Returns a TPCANBaudrate matching the corresponding Baud rate.
     *   @return 
     * A TPCANBaudrate matching the Baud rate or null if the Baud rate is not supported.
     *   @param value 
     * The needed Baud rate in Hz.
     */
    public static TPCANBaudrate valueOfBaudRate(int baudRateInHz) {
        /* Don't consider cleared or unset user-defined values a valid choice. */
        if (baudRateInHz > 0) {
            /* Linear search for an enumerated value, which has the right baud rate. */
            for (TPCANBaudrate rate: values()) {
                if (rate.getBaudRate() == baudRateInHz) {
                    return rate;
                }
            }
        }
        return null;
    }
};
