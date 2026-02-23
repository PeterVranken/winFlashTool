/**
 * @file Mpc5748G_C55FMC.java
 * The flash ROM description for MPC5748G.
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
/* Interface of class Mpc5748G_C55FMC
 *   Mpc5748G_C55FMC
 */

package winFlashTool.mcu;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;

/**
 * The flash ROM description for MPC5748G.
 */
public class Mpc5748G_C55FMC extends Flash {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(Mpc5748G_C55FMC.class);

    /** The one and only instance of Mpc5748G_C55FMC. */
    private static final Mpc5748G_C55FMC _theOnlyInstance;

    /** A static initialization block is applied to create the one and only instance of
        Mpc5748G_C55FMC. */
    static {
        _theOnlyInstance = new Mpc5748G_C55FMC();
    }

    /**
     * Get an immutable instance of the flash ROM description of the microcontroller
     * MPC5748G.
     *   @return
     * Get the flash ROM description with lists of sectors and partitions.
     */
    public static Mpc5748G_C55FMC getFlashRomDescription() {
        return _theOnlyInstance;
    }

    /**
     * An instance of Mpc5748G_C55FMC is created.<p>
     *   The constructor is not accessible. Instead, use getFlashRomDescription() to get an
     * immutable instance of the flash ROM description.
     */
    private Mpc5748G_C55FMC()
    {
        /* We only allow to erase and program the Flash code blocks after 0xF90000, intended for
           the normal application code, if they don't belong to read-while-write (RWW)
           partitions 2 and 3. RWW 2 and 3, 192 KB in total, are reserved for the FBL
           itself. */
        super( /*isIdxPartitionZeroBased*/ true
                         /*from*/    /*till*/     /*Partition*/       /*isProgrammable*/
             , new Range(0x00400000, 0x00404000), Integer.valueOf(0), Boolean.valueOf(false) /* UTEST */
             , new Range(0x00404000, 0x00408000), Integer.valueOf(1), Boolean.valueOf(false) /* BAF Block */
             , new Range(0x00610000, 0x00620000), Integer.valueOf(0), Boolean.valueOf(false) /* HSM code block 2 */
             , new Range(0x00620000, 0x00630000), Integer.valueOf(1), Boolean.valueOf(false) /* HSM code block 3 */
             , new Range(0x00F80000, 0x00F84000), Integer.valueOf(4), Boolean.valueOf(false) /* HSM block0 */
             , new Range(0x00F84000, 0x00F88000), Integer.valueOf(5), Boolean.valueOf(false) /* HSM block1 */
             , new Range(0x00F8C000, 0x00F90000), Integer.valueOf(0), Boolean.valueOf(false) /* 16KB HSM code block */
             , new Range(0x00F90000, 0x00F94000), Integer.valueOf(2), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00F94000, 0x00F98000), Integer.valueOf(2), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00F98000, 0x00F9C000), Integer.valueOf(2), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00F9C000, 0x00FA0000), Integer.valueOf(2), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00FA0000, 0x00FA4000), Integer.valueOf(3), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00FA4000, 0x00FA8000), Integer.valueOf(3), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00FA8000, 0x00FAC000), Integer.valueOf(3), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00FAC000, 0x00FB0000), Integer.valueOf(3), Boolean.valueOf(false) /* 16KB Code Flash block */
             , new Range(0x00FB0000, 0x00FB8000), Integer.valueOf(2), Boolean.valueOf(false) /* 32KB Code Flash block */
             , new Range(0x00FB8000, 0x00FC0000), Integer.valueOf(3), Boolean.valueOf(false) /* 32KB Code Flash block */
             , new Range(0x00FC0000, 0x00FC8000), Integer.valueOf(0), Boolean.valueOf(false) /* 32KB Code Flash block */
             , new Range(0x00FC8000, 0x00FD0000), Integer.valueOf(0), Boolean.valueOf(true)  /* 32KB Code Flash block */
             , new Range(0x00FD0000, 0x00FD8000), Integer.valueOf(1), Boolean.valueOf(true)  /* 32KB Code Flash block */
             , new Range(0x00FD8000, 0x00FE0000), Integer.valueOf(1), Boolean.valueOf(true)  /* 32KB Code Flash block */
             , new Range(0x00FE0000, 0x00FF0000), Integer.valueOf(0), Boolean.valueOf(true)  /* 64KB Code Flash block */
             , new Range(0x00FF0000, 0x01000000), Integer.valueOf(1), Boolean.valueOf(true)  /* 64KB Code Flash block */
             , new Range(0x01000000, 0x01040000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01040000, 0x01080000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01080000, 0x010C0000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x010C0000, 0x01100000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01100000, 0x01140000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01140000, 0x01180000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01180000, 0x011C0000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x011C0000, 0x01200000), Integer.valueOf(6), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01200000, 0x01240000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01240000, 0x01280000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01280000, 0x012C0000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x012C0000, 0x01300000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01300000, 0x01340000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01340000, 0x01380000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01380000, 0x013C0000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x013C0000, 0x01400000), Integer.valueOf(7), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01400000, 0x01440000), Integer.valueOf(8), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01440000, 0x01480000), Integer.valueOf(8), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01480000, 0x014C0000), Integer.valueOf(8), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x014C0000, 0x01500000), Integer.valueOf(9), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01500000, 0x01540000), Integer.valueOf(9), Boolean.valueOf(true)  /* 256KB Code Flash block */
             , new Range(0x01540000, 0x01580000), Integer.valueOf(9), Boolean.valueOf(true)  /* 256KB Code Flash block */
             );
    } /* Mpc5748G_C55FMC.Mpc5748G_C55FMC */


} /* End of class Mpc5748G_C55FMC definition. */




