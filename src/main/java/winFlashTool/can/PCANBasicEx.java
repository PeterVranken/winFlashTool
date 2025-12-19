/**
 * @file PCANBasicEx.java
 * A collection of support functions for using the PCANBasic API.
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
/* Interface of class PCANBasicEx
 *   getPcanApi
 *   checkReturnCode (2 variants)
 *   b2i
 *   getFirstAvailableChannel
 *   printAttachedChannels
 *   identifyChannel (2 variants)
 * Private methods:
 *   getListOfAttachedChannels
 */

package winFlashTool.can;

import java.util.*;
import org.apache.logging.log4j.*;
import peak.can.basic.*;
import peak.can.MutableInteger;
import winFlashTool.basics.ErrorCounter;

/**
 * A collection of static support functions for using the PCANBasic API.
 */
public class PCANBasicEx
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(PCANBasicEx.class);

    /** The PEAK CAN API; the connection of Java to the external DLL. */
    private static PCANBasic _pcanApi = null;

    /** The error counter to be used for all problems. */
    private static ErrorCounter _errCnt = null;

    /** The duration of the device identification. This number of seconds, the selected
     * device's LED flashes oranges, while the application blocks. */
    final static int TI_WAIT_IDENTIFY_IN_S = 5;
    
    /**
     * Initialization of module. Needs to be successfully called prior to using any other
     * method.
     *   @return
     * Get true, if initialization succeeds. If \a false is returned then CAN operation
     * won't be possible and the application shouldn't start up.
     *   @param errCnt
     * The error counter to be used for all PCAN Basic API related problem reporting.
     */    
    public static boolean initClass(ErrorCounter errCnt)
    {
        boolean success = false;
        
        _errCnt = errCnt;
        
        /* Initialize the API opject, which connects us to the PEAK DLLs. */
        assert _pcanApi == null;
        _pcanApi = new PCANBasic();
        if(_pcanApi.initializeAPI())
        {
            _logger.debug("PCANBasic API successfully initialized.");
            success = true;
        }
        else
        {
            errCnt.error();
            _pcanApi = null;
            _logger.fatal("Unable to initialize the PEAK PCAN Basic API. Most probable"
                          + " reason is the application installation; the PEAK DLLs might"
                          + " be not localized. Check application configuration and"
                          + " Windows search path. Files PCANBasic.dll and"
                          + " PCANBasic_JNI.dll need to be found."
                         );
        }
        
        return success;
        
    } /* initClass */

    /** No need to ever create an instance of this class. */
    private PCANBasicEx() {
    } /* PCANBasicEx.PCANBasicEx */

    /**
     * Get the initialized PCAN Basic API for use in other classes.
     *   @return
     * Get the API object.
     */
    public static PCANBasic getPcanBasicApi() { 
        assert _pcanApi != null: "Class not initialized";
        return _pcanApi;
    }
    
    /**
     * Check the return code got from the PCAN API.<p>
     *   An error message is logged if it is not OK. The error from the PCANBasic API is
     * embedded into some caller provided context information.
     *   @return
     * Get true if the status is alright or false if an error has been reported.
     *   @param errCode
     * The error code received from an API call of the PCAN Basic API.
     *   @param context
     * Some text, which explains the context of the now checked operation. The text should
     * end with punctuation.<p>
     *   Can be null if no context information should be added to the potentially logged
     * error message.
     */
    public static boolean checkReturnCode(TPCANStatus errCode, String context) {
        if(errCode == TPCANStatus.PCAN_ERROR_OK)
            return true;
        else
        {
            /* Translate last error into human understandable text. */
            final StringBuffer errMsg = new StringBuffer();
            TPCANStatus errCodeGet = _pcanApi.GetErrorText( errCode
                                                          , /*Language*/ (short)0
                                                          , errMsg
                                                          );
            if (context == null) {
                context = "";
            } else if (!context.endsWith(" ")) {
                context += " ";
            }
            if(errCodeGet == TPCANStatus.PCAN_ERROR_OK)
            {
                _errCnt.error();
                _logger.error( "{}PEAK PCAN Basic API returned error code {}: {}"
                             , context
                             , errCode.toString()
                             , errMsg.toString()
                             );
            }
            else
            {
                _errCnt.error();
                _logger.error( "{}PEAK PCAN Basic API returned error code {} (No message"
                               + " text is available for this code.)"
                             , context
                             , errCode.toString()
                             );
            }
            return false;
        }
    } /* checkReturnCode */
    
    
    /**
     * Check the return code got from the PCAN API.<p>
     *   An error message is logged if it is not OK.
     *   @return
     * Get true if the status is alright or false if an error has been reported.
     *   @param errCode
     * The error code received from an API call of the PCAN Basic API.
     */
    public static boolean checkReturnCode(TPCANStatus errCode) {
        return checkReturnCode(errCode, /*context*/ null);
    }
    
    
    /**
     * Convert the frequently used byte type into an unsigned integer.
     *   @return
     * Get the value of b as an int in the range 0..255.
     *   @param b
     * The byte value, which is a number -127..128 for Java.
     */
    public static int b2i(byte b) {
        return (int)b & 0xFF;
    }


    /**
     * Print a list of connected PEAK PCAN-USB devices into the application log.
     *   @return
     * Get the array of attached devices, which is the value of PCAN API parameter
     * PCAN_ATTACHED_CHANNELS or null in case of an error.
     */
    private static TPCANChannelInformation[] getListOfAttachedChannels() {
    
        /* Step 1: Get number of attached channels. */
        MutableInteger countBuffer = new MutableInteger(0);
        TPCANStatus status = _pcanApi.GetValue( TPCANHandle.PCAN_NONEBUS
                                              , TPCANParameter.PCAN_ATTACHED_CHANNELS_COUNT
                                              , countBuffer
                                              , 4 /* Size of int in underlaying PCAN C lib. */
                                              );
        if (!checkReturnCode(status, "Error getting channel count.")) {
            return null;
        }

        final int channelCount = countBuffer.getValue();

        if (channelCount == 0) {
            _errCnt.warning();
            _logger.warn("No PEAK PCAN-USB device is connected.");
            return null;
        }

        /* Step 2: Allocate array for channel info. */
        final TPCANChannelInformation[] channels = new TPCANChannelInformation[channelCount];
        for (int i=0; i<channelCount; ++i) {
            /* The caller is in charge of initializing each array element. */
            channels[i] = new TPCANChannelInformation(); 
        }

        /* Step 3: Get channel details. */
        status = _pcanApi.GetValue( TPCANHandle.PCAN_NONEBUS
                                  , TPCANParameter.PCAN_ATTACHED_CHANNELS
                                  , channels
                                  , channels.length/* Byte size is computed by PEAK JNI layer*/
                                  );
        if (!checkReturnCode(status, "Error getting channel information.")) {
            return null;
        }
        
        /* Everyting is ok, we can return the filled array. */
        return channels;
        
    } /* getListOfAttachedChannels */
    
    /**
     * Print a list of connected PEAK PCAN-USB devices into the application log.
     *   @return
     * Get the PCABN Basic device handle of the first attached device, which is not
     * occupied by another application. Or null if no device was found.
     */
    public static TPCANHandle getFirstAvailableChannel() {
    
        TPCANHandle hDev = null;
        TPCANChannelInformation[] channelAry = getListOfAttachedChannels();
        if (channelAry != null) {
            /* Iterate array until first available device is found. */
            boolean success = false;
            for (int i=0; i<channelAry.length; ++i) {
                TPCANChannelInformation info = channelAry[i];
                if (info.getChannelCondition() == 1 /*available*/) {
                    hDev = info.getChannelHandle();
                    break;
                }
            }
        }       
        return hDev;

    } /* getFirstAvailableChannel */

    /**
     * Print a list of connected PEAK PCAN-USB devices into the application log.
     *   @return
     * Get true if at least one device is found and presented with its properties.
     * Otherwise false.
     */
    public static boolean printAttachedChannels() {
        TPCANChannelInformation[] channelAry = getListOfAttachedChannels();
        if (channelAry != null) {
            _logger.info("Found {} attached channels:", channelAry.length);

            /* Step 4: Iterate array to print channel info. */
            boolean success = false;
            for(int i=0; i<channelAry.length; ++i) {
                final Level logLevel;
                TPCANChannelInformation info = channelAry[i];
                final String availability;
                switch(info.getChannelCondition()) {
                case 0:
                    logLevel = Level.ERROR;
                    availability = "Invalid handle, device is not available";
                    break;
                case 1:
                    success = true;
                    logLevel = Level.INFO;
                    availability = "Device is available to this application";
                    break;
                case 2:
                    success = true;
                    logLevel = Level.WARN;
                    availability = "Device is occupied by another application";
                    break;
                default:
                    logLevel = Level.ERROR;
                    availability = "Invalid state, got unknown availabilty value "
                                   + info.getChannelCondition();
                }
                _logger.log( logLevel
                           , "{} (0x{}, type: {}): {}"
                           , info.getChannelHandle().toString()
                           , Integer.toHexString(info.getChannelHandle().getValue())
                           , info.getDeviceName()
                           , availability
                           );
            }

            return success;
            
        } else {
            return false;
        }
    } /* printAttachedChannels */

    /**
     * Apply the "identification" mode to make clear to the user, which connected
     * device is the one they meant: The LED of the selected device is flashed.<p>
     *   This variant of the method selects the device by name.
     *   @return
     * Get false if the selected device name is illegal. Otherwise true.
     *   @param selectedCanDev
     * A PEAK PCAN-USB CAN device by name. The LED of this is flashed in orange - a
     * normally unused color to indicate, which one it is.<p>
     *   Note, the function blocks for a few seconds during which the LED is flashed.
     * Therefore, the function should be used only in a kind of debug or configuration mode
     * of the application but not in normal operation.
     */
    public static boolean identifyChannel(String nameOfSelectedCanDev) {
        TPCANHandle selectedCanDev = null;
        if (nameOfSelectedCanDev != null) {
            try {
                selectedCanDev = TPCANHandle.valueOf(nameOfSelectedCanDev);
            } catch(IllegalArgumentException e) {
                _errCnt.error();
                _logger.error( "Can't identify PEAK PCAN-USB CAN device {}. No such device"
                               + " is known. {}"
                             , nameOfSelectedCanDev
                             , e.getMessage()
                             );
            }
        }
        if (selectedCanDev != null) {
            identifyChannel(selectedCanDev);
            return true;
        } else {
            return false;
        }
    } /* identifyChannel (by name) */
    
    /**
     * Apply the "identification" mode to make clear to the user, which connected
     * device is the one they meant: The LED of the selected device is flashed.<p>
     *   This variant of the method selects the device by handle.
     *   @param selectedCanDev
     * The LED of this PEAK PCAN-USB CAN device is flashed in orange - a normally unused
     * color to indicate, which one it is.<p>
     *   Note, the function blocks for a few seconds during which the LED is flashed.
     * Therefore, the function should be used only in a kind of debug or configuration mode
     * of the application but not in normal operation.
     */
    public static void identifyChannel(TPCANHandle selectedCanDev) {
        assert _pcanApi != null: "Class not initialized";
        TPCANStatus status = _pcanApi.SetValue
                                        ( selectedCanDev
                                        , TPCANParameter.PCAN_CHANNEL_IDENTIFYING
                                        , TPCANParameterValue.PCAN_PARAMETER_ON
                                        , 4 /* Size of int in underlaying PCANBasic lib. */
                                        );
        if (!PCANBasicEx.checkReturnCode( status
                                        , "Error enabling the PEAK PCAN-USB CAN device"
                                          + " identification."
                                        )
           ) {
            return;
        }
        
        _logger.info("Device identification: PEAK PCAN-USB CAN device {} will now blink"
                     + " in orange for about three seconds..."
                    );
        for (int i=TI_WAIT_IDENTIFY_IN_S; i>0; --i) {
            _logger.info("{} seconds remaining", i);
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
                _errCnt.error();
                _logger.error("Pause time for LED blinking can't be implemented. {}"
                             , e.getMessage()
                             );
            }
        }
        
        status = _pcanApi.SetValue( selectedCanDev
                                  , TPCANParameter.PCAN_CHANNEL_IDENTIFYING
                                  , TPCANParameterValue.PCAN_PARAMETER_OFF
                                  , 4 /* Size of int in underlaying PCANBasic lib. */
                                  );
        PCANBasicEx.checkReturnCode( status
                                   , "Error disabling the PEAK PCAN-USB CAN device"
                                     + "  identification."
                                   );
    } /* identifyChannel (by handle) */

} /* End of class PCANBasicEx definition. */





