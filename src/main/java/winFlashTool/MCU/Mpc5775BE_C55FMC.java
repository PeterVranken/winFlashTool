/**
 * @file Mpc5775BE_C55FMC.java
 * The flash ROM description for MPC5775B/E.
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
/* Interface of class Mpc5775BE_C55FMC
 *   Mpc5775BE_C55FMC
 */

package winFlashTool.MCU;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;

/**
 * The flash ROM description for MPC5775B/E.
 */
public class Mpc5775BE_C55FMC extends Flash {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(Mpc5775BE_C55FMC.class);

    /** The one and only instance of Mpc5775BE_C55FMC. */
    private static final Mpc5775BE_C55FMC _theOnlyInstance;
    
    /** A static initialization block is applied to create the one and only instance of
        Mpc5775BE_C55FMC. */
    static {
        _theOnlyInstance = new Mpc5775BE_C55FMC();
    }
    
    /** 
     * Get an immutable instance of the flash ROM description of the microcontroller
     * MPC5775B/E.
     *   @return
     * Get the flash ROM description with lists of sectors and partitions.
     */
    public static Mpc5775BE_C55FMC getFlashRomDescription() {
        return _theOnlyInstance;
    }
    
    /**
     * An instance of Mpc5775BE_C55FMC is created.<p>
     *   The constructor is not accessible. Instead, use getFlashRomDescription() to get an
     * immutable instance of the flash ROM description.
     */
    private Mpc5775BE_C55FMC()
    {
        /* We only allow to erase and program the 4 MB at 0x0080000, which are intended for
           the normal application code..
             The first 256k are declared non-programmable as these sectors (and partitions)
           hold the code of the flash boot loader, which must not overwrite itself.
             The CSE High blocks are declared non-programmable as they are in use by the
           HSM and the HSM has its own way of being programmed. */           
        super( /*isIdxPartitionZeroBased*/ true
                         /*from*/    /*till*/     /*Partition*/       /*isProgrammable*/
             , new Range(0x00000000, 0x00010000), Integer.valueOf(0), Boolean.valueOf(false) /* Block type Low, EEPROM */
             , new Range(0x00010000, 0x00020000), Integer.valueOf(1), Boolean.valueOf(false) /* Block type Low, EEPROM */
             , new Range(0x00020000, 0x00030000), Integer.valueOf(2), Boolean.valueOf(false) /* Block type Mid, EEPROM */
             , new Range(0x00030000, 0x00040000), Integer.valueOf(3), Boolean.valueOf(false) /* Block type Mid, EEPROM */
             , new Range(0x00600000, 0x00604000), Integer.valueOf(4), Boolean.valueOf(false) /* Block type High, CSE */
             , new Range(0x00640000, 0x00608000), Integer.valueOf(5), Boolean.valueOf(false) /* Block type High, CSE */
             , new Range(0x00800000, 0x00840000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Boot */
             , new Range(0x00840000, 0x00880000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00880000, 0x008C0000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x008C0000, 0x00900000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00900000, 0x00940000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00940000, 0x00980000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00980000, 0x009C0000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x009C0000, 0x00A00000), Integer.valueOf(8), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00A00000, 0x00A40000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00A40000, 0x00A80000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00A80000, 0x00AC0000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00AC0000, 0x00B00000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00B00000, 0x00B40000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00B40000, 0x00B80000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00B80000, 0x00BC0000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             , new Range(0x00BC0000, 0x00C00000), Integer.valueOf(9), Boolean.valueOf(true)  /* Block type Large, Application code */
             );
             
        /* Note, section UTEST doesn't belong to a normal partition. It contains some
         * end-of-line information from the chip production and can't be used like the
         * other sections.
         *   new Range(0x00400000, 0x00404000), Integer.valueOf(???), Boolean.valueOf(false) // Block type UTEST
         */
    } /* Mpc5775BE_C55FMC.Mpc5775BE_C55FMC */


} /* End of class Mpc5775BE_C55FMC definition. */




