/**
 * @file WinFlashTool.java
 * Main entry point into the Excel exporter of the COM framework.
 *
 * Copyright (C) 2015-2026 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class WinFlashTool
 *   WinFlashTool
 *   createDir
 *   defineArguments
 *   parseCmdLine
 *   getBaudRate
 *   run
 *   main
 */

package winFlashTool;

import java.util.*;
import java.io.*;
import java.text.*;
import winFlashTool.mcu.Flash;
import winFlashTool.mcu.Mpc5775BE_C55FMC;
import winFlashTool.mcu.Mpc5748G_C55FMC;

import org.apache.logging.log4j.*;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;
import winFlashTool.applicationInterface.loggerConfiguration.Log4j2Configurator;
import winFlashTool.can.PCANBasicEx;
import winFlashTool.ccp.CCP;
//import winFlashTool.ccp.CcpCroTransmitter;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.srecParser.MemoryMap;
import winFlashTool.srecParser.SRecord;
import winFlashTool.srecParser.SRecordSequence;
import winFlashTool.can.CanDevice;
import winFlashTool.can.CanId;
import peak.can.basic.TPCANHandle;
import peak.can.basic.TPCANBaudrate;
import winFlashTool.applicationInterface.AddressRangeSequence;
import winFlashTool.srecParser.SrecWriter;
import winFlashTool.srecParser.EraseSectorSequence;

/**
 * This class has a main function, which implements the excel exporter application.
 *   @author Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
 *   @see WinFlashTool#main
 */

public class WinFlashTool
{
    /** The name of this Java application. */
    public static final String _applicationName = "winFlashTool";

    /** Version designation as four numeric parts. */
    private static int[] _versionAry = {0, 9, 0, GitRevision.getProjectRevision()};

    /** The first three parts of the version of the tool, which relate to functional
        changes of the application.
          @todo This version designation needs to be kept in sync with the version of the
        data model so that writing of safe templates (with respect to unexpected tool
        changes) becomes possible. Any change of the data model needs to be reflected in
        the major parts of this version designation and the other version designation
        _versionDataModel needs to be synchronized then. Tool changes without a change of
        the data model - be it in the minor or major parts of the tool's version - won't
        lead to an update of _versionDataModel. */
    public static final String _version = "" + _versionAry[0]
                                          + "." + _versionAry[1]
                                          + "." + _versionAry[2];

    /** The full version of the tool, including the forth part, the build number. */
    public static final String _versionFull = _version + "." + _versionAry[3];
    
    /** The global logger object for all progress and error reporting. It is initialized to
        null in order to give time to the other class {@link Log4j2Configurator} to first
        configure the loggers according to the command line settings. */
    private static Logger _logger = null;

    /** A help text, which is printed together with the usage message derived from the
        command line arguments. */
    private static final String _applicationHelp =
        // (setq fill-column 89 fill-prefix "        + \"")
        "";

    /** The structure that holds all command line parameters. */
    private CmdLineParser cmdLineParser_ = null;

    /** The correct EOL in abbreviated form. */
    private static final String NL = System.lineSeparator();

    /* A single error counter is used for all operations. A reference to this error counter
       is passed to involved modules and objects. */
    final ErrorCounter errCnt_ = ErrorCounter.getGlobalErrorCounter();

    /** The log4j configurator provides access to the logging settings of this application
        run. */
    private Log4j2Configurator log4j2Configurator_ = null;


    /**
     * The nested directories required for file creation are created.
     * The method extracts the path from the given file name and creates all directories
     * required to make this path existing.
     *   @return
     * true, if method succeeded, else false.
     *   @param fileName
     * The file name of file to be created. May be relative or absolute.
     */
    static private boolean createDir(String fileName)
    {
        return createDir(new File(fileName));

    } /* End of WinFlashTool.createDir. */

    /**
     * The nested directories required for file creation are created.
     * The method extracts the path from the given file name and creates all directories
     * required to make this path existing.
     *   @return
     * true, if method succeeded, else false.
     *   @param fileNameObj
     * The file name of a file to be created as a File object. May be relative or absolute.
     */
    static private boolean createDir(File fileNameObj)
    {
        boolean success = true;

        /* The following operation will only fail if a root directory has been specified.
           This is caught with the if. */
        if(!fileNameObj.isDirectory())
            fileNameObj.getAbsoluteFile().getParentFile().mkdirs();
        else
            success = false;

        return success;

    } /* End of WinFlashTool.createDir. */




    /**
     * Create a command line parser and define all command line arguments. This method
     * defines the arguments owned by the application main class and it calls the argument
     * definition functions of all other modules that have their own arguments.
     *   @remark The command line processor will detect unwanted redefinitions of command
     * line arguments and reports them as runtime exception. This strongly supports safe
     * development of different modules each having its individual command line arguments.
     */
    private void defineArguments()
    {
        assert cmdLineParser_ == null: "Don't parse the command line twice";
        final CmdLineParser clp = new CmdLineParser();

        /* Define all expected arguments ... */
        clp.defineArgument( "h"
                          , "help"
                          , /* cntMax */ 1
                          , "Demand this help."
                          );

        /* Let the logger configurator define its command line arguments. */
        Log4j2Configurator.defineArguments(clp);

        /* Define all command line arguments, which are not logging related. */
        clp.defineArgument
            ( "m", "mcu-target"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ null
            , "The MCU target to program. Supported targets are:"
              + "\n  MPC5748G"
              + "\n  MPC5775B"
              + "\n  MPC5775E"
              + "\nThis argument is mandatory for normal operation but it is not required"
              + " if --enumerate-CAN-devices is used to check the hardware setup."
            );
        clp.defineArgument
            ( "d", "CAN-device"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ ""
            , "The CAN device to operate with. Consider using --enumerate-CAN-devices to"
              + " get a list of connected and available devices."
              + "\nOptional, default is using the first found available device, which ever"
              + " that is."
            );
        clp.defineArgument
            ( "cfg", "CAN-configuration"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ "500"
            , "The CAN device configuration. If classic CAN is used then this argument is"
              + " a simple integral number specifying the CAN Baud rate in kBd. If CAN FD is"
              + " used then this is the configuration string as defined by the PCAN Basic"
              + " API. Please see PCAN dcoumentation for details."
              + "\nOptional, default is using classic CAN with 500 kBd."
            );
        clp.defineArgument
            ( "t", "CAN-ID-CRO"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ 0x600
            , "The CAN ID for all CCP CRO Tx messages. Please note, extended CAN IDs are not"
              + " supported yet."
              + "\nOptional, default is the 11 Bit ID 0x600."
            );
        clp.defineArgument
            ( "r", "CAN-ID-DTO"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ 0x650
            , "The CAN ID for all CCP DTO Rx messages. Please note, extended CAN IDs are not"
              + " supported yet."
              + "\nOptional, default is the 11 Bit ID 0x650."
            );
        clp.defineArgument
            ( "o", "srec-output-file"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ null
            , "The srec file with the uploaded memory contents."
              + "\nIf this argument is used then an upload of memory contents from the target"
              + " device is commanded."
              + "\nIf this argument is combined with a command to erase and/or program"
              + " the flash ROM, then the upload is done first, prior to modifying the flash"
              + " ROM contents."
            );
        clp.defineArgument
            ( "i", "srec-input-file"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ null
            , "The srec file with the memory contents to either flash or verify."
              + "\nIf this argument is used and --verify-only is not given then a download"
              + " of the file to the target device is commanded."
            );
        clp.defineArgument
            ( "vo", "verify-only"
            , /*cntMax*/ 1
            , "The srec input file is loaded and compared to the memory contents of the"
              + " target. The memories of the target are not at all modified."
              + "\nOptional, default is programming the srec file."
            );
        clp.defineArgument
            ( "nv", "no-verify"
            , /*cntMax*/ 1
            , "Flash programming is normally followed by an upload of the programmed memory"
              + " contents to see if all bytes have the value specified in the input"
              + " srec file. However, this roughly doubles the duration of the complete"
              + " programming procedure. Using option --no-verify means to skip the upload"
              + " and data comparison to save the additional time."
              + "\nOptional, default is doing the verification."
              + "\nPlease note, regardless of this switch, an immediate verify of each"
              + " flash programming step is always performed. However, this validates only"
              + " the physical flash programming but not potential data transmission errors." 
            );
        clp.defineArgument
            ( "e", "erase-all"
            , /*cntMin, cntMax*/ 1
            , "If given, then all flash ROM under control of the FBL is erased. This can be"
              + " used with or without download and programming."
              + "\nIf not given, although"
              + " --srec-input-file is used to command the download, then only those"
              + " flash blocks will be erased, which are required to house the contents of"
              + " the srec file."
            );
        clp.defineArgument
            ( "dev", "enumerate-CAN-devices"
            , /*cntMax*/ 1
            , "If given, then the application will only search for connected, available"
              + " CAN devices. It stops after listing available devices."
              + "\nIf --CAN-device is given, too, then the identification mode is"
              + " enabled: The LED on the selected device will blink in orange for a"
              + " short while before the application terminates."
              + "\nUseful for a check of the setup."
              + "\nOptional, default is false."
            );
        clp.defineArgument
            ( "n", "dry-run"
            , /*cntMax*/ 1
            , "The dry-run is a means to check the hardware and flash tool setup without"
              + " impacting the target ECU. The flash tool executes as usual but all CCP"
              + " commands that could impact the flash of the target are skipped. CONNECT"
              + " and DISCONNECT are still executed. Using the dry-run, many sources of"
              + " problems can be detected without any risk for the target."
              + "\nOptional, default is normal operation."
            );
        clp.defineArgument
            ( "a", "station-address"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ 0
            , "The 16 Bit station address of the CCP target."
              + "\nOptional, default is station address 0."
            );
        clp.defineArgument
            ( "nr", "no-retries-ccp-connect"
            , /*cntMin, cntMax*/ 0, 1
            , /*defaultValue*/ 2
            , "The number of re-tries (after short delay) if the initial CCP connect fails."
              + " The supported range is [0, 10]"
              + "\nOptional, default is 2."
            );
        clp.defineArgument
            ( "u", "address-range"
            , /*cntMin, cntMax*/ 0, Integer.MAX_VALUE
            , /*defaultValue*/ ""
            , "Address range for upload. A colon-separated pair of two hexadecimal"
              + " addresses, from:to, e.g., 00800000:008E0000. This argument can be used"
              + " any number of times."
              + "\nThis argument is mandatory if the other argument srec-output-file is used"
              + " to command an upload."
            );
//        clp.defineArgument
//            ( "$(point)", ""
//            , /* cntMin, cntMax */ 0, -1
//            , /* defaultValue */ null
//            , ""
//              + " "
//              + " "
//              + " "
//              + ".\nOptional, default is"
//              + " "
//              + ".\nThis parameter is mandatory unless --enumerate-CAN-devices is given."
//            );

        /* No unnamed arguments are expected. */
        //clp.defineArgument( /* cntMin, cntMax */ 0, -1
        //                  , /* defaultValue */ null
        //                  , ""
        //                  );

        cmdLineParser_ = clp;

    } /* defineArguments */


    /**
     * Read and check the command line arguments.
     *   The command line is evaluated. If an error is found the usage is displayed.
     *   @return true is returned if the method succeeds. Then retrieve all command line
     * options from member cmdLineParser_. If the function fails it returns false. The main
     * program should end silently.
     *   @param argAry
     * The command line arguments of the application.
     */
    private boolean parseCmdLine(String[] argAry) {
        assert cmdLineParser_ != null;
        try {
            cmdLineParser_.parseArgs(argAry);

            if (cmdLineParser_.getBoolean("h")) {
                /* ... and explain them. */
                greeting();
                System.out.print(cmdLineParser_.getUsageInfo( _applicationName
                                                            , /*argumentsTabularOnly*/ true
                                                            )
                                 + NL + _applicationHelp + NL
                                );
                return false;
            }
        } catch (CmdLineParser.InvalidArgException e) {
            greeting();
            System.err.print(cmdLineParser_.getUsageInfo( _applicationName
                                                        , /*argumentsTabularOnly*/ true
                                                        )
                             + NL + _applicationHelp + NL
                             + NL + "Invalid command line. " + e.getMessage() + NL
                            );
            return false;
        }

        return true;

    } /* WinFlashTool.parseCmdLine. */


    // TODO This function belongs into the package application interface.
    /**
     * Check the user specified CAN configuration for the simple Baud rate needed for
     * classic CAN.
     *   @return
     * Get the Baud rate or null if the user input doesn't match any supported CAN Baud
     * rate.
     *   @param canCfg
     * The configuration word from the command line. If classic CAN is meant then this is a
     * number literal meaning the Baud rate in kHz.
     */
    private TPCANBaudrate getBaudRate(String canCfg) {
        /* Check if the user input is a simple integer. */
        int baudRateInKHz;
        try{ baudRateInKHz = Integer.valueOf(canCfg); }
        catch(NumberFormatException e)
        {
            /* Substitute user input by a value, which is guaranteed to be no valid Baud
               rate. (This is not an error, canCfg rather is the complex CAN FD
               configuration string.) */
            baudRateInKHz = -1;
        }
        
        /* The translation value to enum returns null if the value doesn't exist in the
           enumeration. */
        return TPCANBaudrate.valueOfBaudRate(baudRateInKHz*1000);

    } /* getBaudRate */
    
    
    /**
     * This method implements the application behavior. Call it once from the main function
     * run is synchronous and does not fork another task or process.
     *   @return
     * <b>true</b>, if method succeeded, else <b>false</b>.
     */
    public boolean run() {
        boolean success = true;

        if (success) {
            success = PCANBasicEx.initClass(errCnt_)
                      && CanDevice.initClass(errCnt_)
                      && SrecWriter.initClass(errCnt_);
        }
            
        final String canDeviceName = cmdLineParser_.getString("CAN-device");
        
        /* Check command line to find out, which tasks are commanded. */
        final String srecInputFileName = cmdLineParser_.getString("srec-input-file")
                   , srecOutputFileName = cmdLineParser_.getString("srec-output-file");
        final boolean eraseAll = cmdLineParser_.getBoolean("erase-all")
                    , verifyOnly = cmdLineParser_.getBoolean("verify-only")
                    , noVerify = cmdLineParser_.getBoolean("no-verify")
                    , dryRun = cmdLineParser_.getBoolean("dry-run")
                    , taskEnumCanDevices = cmdLineParser_.getBoolean("enumerate-CAN-devices")
                    , taskUpload = srecOutputFileName != null
                    , taskProgram = srecInputFileName != null  && !verifyOnly
                    , taskVerify = srecInputFileName != null  && verifyOnly
                    , taskEraseOnly = eraseAll &&  srecInputFileName == null;

        if (verifyOnly &&  srecInputFileName == null) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying --verify-only without giving an input srec file"
                          + " is pointless. Nothing to do."
                         );
        }
        if (verifyOnly && noVerify) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying both, --verify-only and --no-verify, is pointless."
                          + " Nothing is done."
                         );
        }
        if (!taskUpload &&  cmdLineParser_.getNoValues("address-range") > 0) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying an address range for upload without specifying an"
                          + " input srec file is pointless. Upload is not performed."
                         );
        }
        
        if(success && taskEnumCanDevices)
        {
            if (taskUpload || taskProgram) {
                errCnt_.warning();
                _logger.warn( "Please note, if command line argument --enumerate-CAN-devices"
                              + " is used then no up- or download is performed. Arguments"
                              + " --srec-output-file and --srec-input-file are ignored."
                            );
            }
            
            /* Print all connected devices. */
            PCANBasicEx.printAttachedChannels();
            if (!canDeviceName.isEmpty()) {
                success = PCANBasicEx.identifyChannel(canDeviceName);
            }
        } else if (taskUpload || taskEraseOnly || taskProgram || taskVerify) {
        
            /* Set the CN IDs to use for CCP communication. */
            final CanId canIdCro = new CanId( cmdLineParser_.getInteger("CAN-ID-CRO") & 0x7FF
                                            , /*isExtId*/ false
                                            );
            final CanId canIdDto = new CanId( cmdLineParser_.getInteger("CAN-ID-DTO") & 0x7FF
                                            , /*isExtId*/ false
                                            );

            final ArrayList<CanId> listOfRxCanIds = new ArrayList<CanId>(1);
            listOfRxCanIds.add(canIdDto);

            /* Try to open the PCAN-USB CAN device. */
            final CanDevice canDev;
            if (success) {
                canDev = new CanDevice();
                final TPCANBaudrate baudRate = 
                                getBaudRate(cmdLineParser_.getString("CAN-configuration"));
                if (baudRate != null) {
                    success = canDev.open(canDeviceName, baudRate, listOfRxCanIds);
                } else {
                    success = false;
                    errCnt_.error();
                    _logger.error( "\"{}\" doesn't specify a valid, supported Baud rate"
                                   + " for classic CAN and CAN FD is not implemented yet."
                                   + " Consider using -h for more details."
                                 , cmdLineParser_.getString("CAN-configuration")
                                 );
                }
            } else {
                canDev = null;
            }
            
            /* Setup the CCP communication. */
            final CCP ccp;
            if(success) {
                ccp = new CCP( canDev
                             , canIdCro
                             , canIdDto
                             , cmdLineParser_.getInteger("station-address")
                             , cmdLineParser_.getInteger("no-retries-ccp-connect")
                             , errCnt_
                             );
            } else {
                ccp = null;
            }

            if (success && taskUpload) {
                /* Prepare a CCP communication thread for uploading memory contents. */
                _logger.info("Now uploading memory contents from target.");
                
                AddressRangeSequence addrRangeSeqForUpload = new AddressRangeSequence(errCnt_);
                if (!addrRangeSeqForUpload.parseCmdLine(cmdLineParser_)) {
                    success = false;
                }
                if (addrRangeSeqForUpload.size() <= 0) {
                    success = false;
                    errCnt_.error();
                    _logger.error("No address range has been specified for upload of"
                                  + " memory contents from the target device. Please use"
                                  + " --address-range to do so or use -h for help."
                                 );
                }

                /* Check specified address ranges for (unsupported) overlap and allocate
                   memory buffers of required size for the upload. */
                final SRecordSequence srecSeq;
                if (success) {
                    srecSeq = addrRangeSeqForUpload.toSRecordSequence();
                    success = srecSeq != null;
                } else {
                    srecSeq = null;
                }

                if (success) {
                    srecSeq.logSections();
                    ccp.upload(srecSeq, dryRun);
                
                    /* Clock the state machine, which runs the CCP communication. */
                    while(!ccp.step()) {
                        /* Here, we could do other, non-blocking things, e.g., print some
                           progress information. */
                    }
                    success = ccp.getFinalSuccess();
                
                    if (success) {
                        success = SrecWriter.write( srecOutputFileName
                                                  , srecSeq
                                                  , /*noBytesPerLine*/ 16
                                                  );
                    }
                }        
                
                if (success) {
                    _logger.info("Data upload successfully completed.");
                }
            } /* if(Is an upload commanded?) */ 
            
            if (success && (taskProgram || taskEraseOnly || taskVerify)) {

                final String targetMcuName = cmdLineParser_.getString("mcu-target");
                if (targetMcuName == null) {
                    success = false;
                    errCnt_.error();
                    _logger.error( "No MCU target is specified on the command line. Please"
                                   + " use -h for help."
                                 );
                }
            
                final Flash flashROM;
                if (success) {
                    switch(targetMcuName) {
                    case "MPC5748G":
                        flashROM = Mpc5748G_C55FMC.getFlashRomDescription();
                        break;

                    case "MPC5775B":
                    case "MPC5775E":
                        flashROM = Mpc5775BE_C55FMC.getFlashRomDescription();
                        break;

                    default:
                        flashROM = null;
                        success = false;
                        errCnt_.error();
                        _logger.error( "MCU target {} is either unknown or not supported."
                                       + " Please use -h to get a list of all supported"
                                       + " targets."
                                     , targetMcuName
                                     );
                    }
                } else {      
                    flashROM = null;
                }
            
                String task = "";
                if (taskProgram || taskVerify) {
                    final MemoryMap memMap;
                    if (success) {
                        memMap = new MemoryMap(flashROM, errCnt_);
                        if (!memMap.readSrecFile(srecInputFileName)) {
                            success = false;
                            errCnt_.error();
                            _logger.error("Can't read srec input file. Application"
                                          + " terminates."
                                         );
                        }
                    } else {      
                        memMap = null;
                    }

                    if (success) {
                        if(taskProgram) {
                            task = "Flash ROM programming";
                            if(!noVerify) {
                                task += " and verify";
                            }

                            _logger.info("Now downloading data to target for flash"
                                         + " programming."
                                        );

                            /* Prepare a CCP communication thread for erase and program. */
                            ccp.eraseAndProgram(memMap, eraseAll, !noVerify, dryRun);
                        } else {
                            task = "Verifying flash ROM contents";
                            assert taskVerify;
                            _logger.info("Now verifying data in target flash ROM.");

                            /* Prepare a CCP communication thread for erase and program. */
                            ccp.verify(memMap, dryRun);
                        }
                    }
                } else {
                    assert taskEraseOnly;
                    task = "Flash ROM erasure";
                    final EraseSectorSequence eraseSectorSequence = 
                                                    new EraseSectorSequence(flashROM, errCnt_);
                    eraseSectorSequence.eraseAll();                                        
                    
                    /* Prepare a CCP communication thread for erasure. */
                    ccp.erase(eraseSectorSequence, dryRun);
                    _logger.info("Now erasing all flash ROM on the target.");
                }
                
                if (success) {
                    /* Clock the state machine, which runs the CCP communication. */
                    while(!ccp.step()) {
                        /* Here, we could do other, non-blocking things, e.g., print some
                           progress information. */
                    }
                    success = ccp.getFinalSuccess();
                }

                if (success) {
                    _logger.info("{} successfully completed.", task);
                }
            } /* if(Is an erase all or download and program commanded?) */ 
           
            /* Close CAN device; release the PCAN-USB CAN device for other applications. */
            if (canDev != null) {
                canDev.close();
            }
        } else {
            success = false;
            errCnt_.error();
            _logger.error( "No srec file is specified on the command line. You normally"
                           + " need to use argument --srec-output-file and/or"
                           + " --srec-input-file to command an up- or download,"
                           + " respectively. Another option is --erase-all to erase all"
                           + " flash ROM. Please use -h for help."
                         );
        } /* if/else if(Which task to complete?) */

        final String logMsg = _applicationName + " terminating with {} errors and {}"
                              + " warnings.";
        final Level level;
        if(errCnt_.getNoErrors() > 0)
            level = Level.ERROR;
        else if(errCnt_.getNoWarnings() > 0)
            level = Level.WARN;
        else
            level = Level.INFO;
        _logger.log(level, logMsg, errCnt_.getNoErrors(), errCnt_.getNoWarnings());

        return success &&  errCnt_.getNoErrors() == 0;

    } /* End of WinFlashTool.run. */


    /**
     * Print the application's title to stdout.
     */
    private static void greeting()
    {
        final String greeting = _applicationName + " " + _versionFull
                                + " Copyright (C) 2025-2026, Peter Vranken"
                                + " (mailto:Peter_Vranken@Yahoo.de)"
                                + "\n";
        System.out.println(greeting);

    } /* End of greeting */



    /**
     * Main entry point when run via command line.
     *   @throws java.lang.Exception
     * General errors are reported by exception.
     *   @param argAry
     * The command line.
     */
    public static void main(String[] argAry) throws Exception
    {
        /* Create the one and only object of this class. It implements the application's
           behavior. */
        WinFlashTool This = new WinFlashTool();

        /* Create a command line parser and define all command line arguments. Then parse
           the actual command line. This is only pass one of command line parsing, which
           double-checks the static constraints as made in the definitions. */
        This.defineArguments();
        if(This.parseCmdLine(argAry))
        {
            final CmdLineParser clp = This.cmdLineParser_;
            
            /* Print the application greeting, but only if log level is not OFF. */
            final Level logLevel = Level.getLevel(clp.getString("v").toUpperCase());
            if(logLevel == null  ||  logLevel != Level.OFF)
                greeting();

            /* Configure log4j2 prior to first use. This is done by side-effect of a
               constructor call. The object is kept only to have access to the configured
               logging settings; they are reported into the application log later. */
            This.log4j2Configurator_ = new Log4j2Configurator(clp, This.errCnt_);

            /* Get this class' and ParameterSet's logger instance only after completing the
               log4j2 configuration. Setting the logger for classes, which are involved in
               command line evaluation and logger configuration can't be done statically as
               for all other classes: They are loaded before the logger configuration has
               been done and completed and the static allocation of a logger would create
               (and default configure) the logger before the programatic configuration
               could happen. */
            _logger = LogManager.getLogger(WinFlashTool.class);

            /* The actual software execution. */
            boolean success = This.run();
            
            _logger.debug( "{} terminating {}."
                         , _applicationName
                         , success? "successfully": "with errors"
                         );
            System.exit(success? 0: 1);
        }
    } /* End of WinFlashTool.main. */

} /* End of class WinFlashTool definition. */
