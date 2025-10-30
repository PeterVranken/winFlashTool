/**
 * @file WinFlashTool.java
 * Main entry point into the Excel exporter of the COM framework.
 *
 * Copyright (C) 2015-2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
 *   run
 *   main
 */

package winFlashTool;

import java.util.*;
import java.io.*;
import java.text.*;

import org.apache.logging.log4j.*;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;
import winFlashTool.applicationInterface.loggerConfiguration.Log4j2Configurator;
import winFlashTool.ccp.PCANBasicEx;
import winFlashTool.ccp.CCP;
//import winFlashTool.ccp.CcpCroTransmitter;
import winFlashTool.basics.ErrorCounter;


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
    private static int[] _versionAry = {0, 1, 0, GitRevision.getProjectRevision()};

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
    final ErrorCounter errCnt_ = new ErrorCounter();

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
        clp.defineArgument( "c", "cluster-name"
                          , /* cntMin, cntMax */ 0, 1
                          , /* defaultValue */ "cluster"
                          , "The name of the complete data cluster. Optional, may be"
                            + " given once in the global context. Default is"
                            + " \"cluster\""
                          );
//        clp.defineArgument( "$(point)", ""
//                          , /* cntMin, cntMax */ 0, -1
//                          , /* defaultValue */ null
//                          , ""
//                            + " "
//                            + " "
//                            + " "
//                            + ".\nOptional, default is"
//                            + " "
//                            + ".\nThis parameter is Mandatory"
//                          );

        /* No unnamed arguments are expected. */
        //clp.defineArgument( /* cntMin, cntMax */ 0, -1
        //                  , /* defaultValue */ null
        //                  , ""
        //                  );

        cmdLineParser_ = clp;

    } /* End of defineArguments */



    /**
     * Read and check the command line arguments.
     *   The command line is evaluated. If an error is found the usage is displayed.
     *   @return true is returned if the method succeeds. Then retrieve all command line
     * options from member cmdLineParser_. If the function fails it returns false. The main
     * program should end silently.
     *   @param argAry
     * The command line arguments of the application.
     */
    private boolean parseCmdLine(String[] argAry)
    {
        assert cmdLineParser_ != null;
        try
        {
            cmdLineParser_.parseArgs(argAry);

            if(cmdLineParser_.getBoolean("h"))
            {
                /* ... and explain them. */
                greeting();
                System.out.print(cmdLineParser_.getUsageInfo( _applicationName
                                                            , /*argumentsTabularOnly*/ true
                                                            )
                                 + NL + _applicationHelp + NL
                                );
                return false;
            }
        }
        catch(CmdLineParser.InvalidArgException e)
        {
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

    } /* End of WinFlashTool.parseCmdLine. */



    /**
     * This method implements the application behavior. Call it once from the main function
     * run is synchronous and does not fork another task or process.
     *   @return
     * <b>true</b>, if method succeeded, else <b>false</b>.
     */
    public boolean run()
    {
        boolean success = true;

        _logger.info("Starting endless Rx/Tx loop. Use Ctrl-C to stop application.");
        
        /* Test of basic CAN Tx/Rx. */
        //MinimalisticProgram.main(/*args*/ null);

        /* Test of CCP implementation: Open device and repeatedly do a CONNECT/DISCONNECT. */
        long tiTest = System.nanoTime();
        final int noTestCycles = 1;
        for(int cycle=0; cycle<noTestCycles; ++cycle)
        {
            _logger.debug("Start connection test {}/{}.", cycle+1, noTestCycles);
            success = true;

            CCP ccp = new CCP(errCnt_);
            assert ccp.getProcessState() == CCP.StateFlashProcess.DISCONNECTED;
            boolean deviceOpened = false;
            if(success)
                deviceOpened = success = ccp.openCanDevice();
            if(success)
                assert ccp.getProcessState() == CCP.StateFlashProcess.CONNECTING;
            while(success && !ccp.step())
                assert ccp.getProcessState() != CCP.StateFlashProcess.DISCONNECTED;

            assert ccp.getProcessState() == CCP.StateFlashProcess.DISCONNECTED;
            if(deviceOpened)
                success = ccp.closeCanDevice();

            _logger.debug("Result of test of CCP process: {}.", success? "Ok": "failed");
        }
        tiTest = System.nanoTime() - tiTest;
        if(noTestCycles > 0)
        {
            _logger.warn( "Test duration: {}ns for {} cycles. {}ns per cycle."
                        , tiTest
                        , noTestCycles
                        , (tiTest + noTestCycles/2) / noTestCycles
                        );
        }
        
// Application code goes here.
//            String generatedCode = null;
//            if(errCnt.getNoErrors() == 0)
//            {
//                final PrintStream out;
//                if("stdout".equalsIgnoreCase(templateOutputPair.outputFileName))
//                    out = System.out;
//                else if("stderr".equalsIgnoreCase(templateOutputPair.outputFileName))
//                    out = System.err;
//                else
//                    out = null;
//
//                if(out != null)
//                {
//                    /* Write generated code into a standard console stream. */
//                    out.print(generatedCode);
//                }
//                else
//                {
//                    /* Write generated code into output file. */
//                    File outputFile = new File(templateOutputPair.outputFileName);
//
//                    BufferedWriter writer = null;
//                    try
//                    {
//                        /* This will output the full path where the file is written
//                           to. */
//                        _logger.info( "The rendered input is written into file {}"
//                                    , outputFile /*.getCanonicalPath()*/
//                                    );
//                        /* Ensure that all needed parents exist for the file. */
//                        createDir(outputFile);
//
//                        FileOutputStream outputFileStream = new FileOutputStream(outputFile);
//                        writer = new BufferedWriter
//                                        (new OutputStreamWriter( outputFileStream
//                                                               , "UTF-8"
//                                                               //, "ISO-8859-1"
//                                                               //, "UTF-16"
//                                                               )
//                                        );
//                        writer.write(generatedCode);
//                    }
//                    catch(IOException e)
//                    {
//                        success = false;
//                        errCnt.error();
//                        _logger.error( "Error writing generated file. {}"
//                                     , e.getMessage()
//                                     );
//                    }
//
//                    /* Close the writer regardless of what happened. */
//                    try
//                    {
//                        if(writer != null)
//                            writer.close();
//                    }
//                    catch(IOException e)
//                    {
//                        success = false;
//                        errCnt.error();
//                        _logger.error( "Error closing generated file. {}"
//                                     , e.getMessage()
//                                     );
//                    }
//                }
//            }
//            else
//            {
//                success = false;
//                _logger.info( "Output file {} is not generated due to previous errors"
//                            , templateOutputPair.outputFileName
//                            );
//                    }


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
        /* Printing the applied version of ANTLR and StringTemplate is useful but unsafe.
           By experiment, it turned out that the printed values do not depend on the
           run-time jars. Instead the values are frozen at compilation time, i.e., when
           excelExporter-*.jar is made. If the application is run with another classpath
           than the compiler then the printed versions may not match the actually used,
           actually running libraries. */
        final String greeting = _applicationName + " " + _versionFull
                                + " Copyright (C) 2025, Peter Vranken"
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
            /* Print the application greeting. */
            greeting();

            /* Configure log4j2 prior to first use. This is done by side-effect of a
               constructor call. The object is kept only to have access to the configured
               logging settings; they are reported into the application log later. */
            This.log4j2Configurator_ = new Log4j2Configurator( This.cmdLineParser_
                                                             , This.errCnt_
                                                             );

            /* Get this class' and ParameterSet's logger instance only after completing the
               log4j2 configuration. Setting the logger for classes, which are involved in
               command line evaluation and logger configuration can't be done statically as
               for all other classes: They are loaded before the logger configuration has
               been done and completed and the static allocation of a logger would create
               (and default configure) the logger before the programatic configuration
               could happen. */
            _logger = LogManager.getLogger(WinFlashTool.class);

            boolean success = This.run();
            _logger.debug( "{} terminating {}"
                         , _applicationName
                         , success? "successfully": "with errors"
                         );
            System.exit(success? 0: 1);
        }
    } /* End of WinFlashTool.main. */

} /* End of class WinFlashTool definition. */
