/**
 * @file ParameterSet.java
 * The application parameter set.
 *
 * Copyright (C) 2015-2022 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class ParameterSet
 *   defineArguments
 *   isIdent
 *   isIndex
 *   isInIntSet
 *   isRowSupported
 *   isColSupported
 *   parseGetNextArg
 *   parseIntRange
 *   parseStateWorksheetRef
 *   parseStateColumnAttributes
 *   parseStateUserOption
 *   cloneLinkedHashMap
 *   strToSortOrder
 *   parseCmdLine
 *   loadLog4jLogger
 *   toString
 */

package winFlashTool.applicationInterface;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;
import winFlashTool.basics.ErrorCounter;


/**
 * The application parameter set.<p>
 *   <b>Remark:</b> The naming convention of indicating local and static members with a
 * trailing or leading underscore is not applied as this class is subject to rendering with
 * StringTemplate. For the same reasons most members are held public.
 */

public class ParameterSet
{
    /** The global logger object for all progress and error reporting. */
    private static Logger _logger = null;

    /** The error counter for parameter parsing. */
    private ErrorCounter errCnt_ = null;

    /** The name of the data cluster. */
    public String clusterName = null;

    /** The Boolean flag, whether the application logger uses the standard file based
        configuration of the Apache log4j 2 package or the programatic configuration
        through the command line arguments of this application.
          @remark This field is not used by {@link ParameterSet} besides that it reports
        the value in toString. The client code of this class is responsible of writing a
        reasonable value into this field. */
    public boolean useStdLog4j2Config = false;
    
    /** The logging level used for this run of the application.
          @remark This field is not used by {@link ParameterSet} besides that it reports
        the value in toString. The client code of this class is responsible of writing a
        reasonable value into this field. */
    public String logLevel = "INFO";
    
    /** The file name of the application log or null if no log file is written.
          @remark This field is not used by {@link ParameterSet} besides that it reports
        the value in toString. The client code of this class is responsible of writing a
        reasonable value into this field. */
    public String logFileName = null;
    
    /** The pattern, according to which the Apache logger will format the log entries
          @remark This field is not used by {@link ParameterSet} besides that it reports
        the value in toString. The client code of this class is responsible of writing a
        reasonable value into this field. */
    public String log4j2Pattern = "%d %-5p - %m%n";

//    /** The description of an input Excel workbook. */
//    public class WorkbookDesc
//    {
//        /** The name of the workbook under which the data read from the input file is
//            stored in the data model. This name can be used to look for the workbook in a
//            map of such. Can be null then the name is derived from the file name. */
//        public String name = null;
//
//        /** Parsing of the workbook means to make a selection by defining a sub-set of
//            worksheets. One referenced worksheet, one element of the sub-set, is selected
//            by an instance of this class. */
//        public class WorksheetRef
//        {
//            /** After parsing, the worksheet will be stored into one or more groups in the
//                data model. This is the name under which it is stored. The name is derived
//                from the tab's name in the workbook if null is stated. */
//            public String name = null;
//
//        } /* End class WorkbookDesc.WorksheetRef */
//
//    } /* End of class WorkbookDesc */


    /**
     * Define all command line arguments.
     *   Define the command line arguments, which are required to fill the application's
     * parameter set.
     *   @param clp
     * The command line parser object.
     */
    static public void defineArguments(CmdLineParser clp)
    {
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
    } /* End of ParameterSet.defineArguments */


    /**
     * Check if a string is a valid identifier.
     *   @throws CmdLineParser.InvalidArgException
     * The function operates silently. As long as we don't have a syntactic problem it will
     * just return. In case of an unexpected string it throws an exception.
     *   @param validatedName The checked name.
     *   @param context An documentary string for error reporting: In which context has the
     * check been made?
     */
    public static void isIdent(String validatedName, String context)
        throws CmdLineParser.InvalidArgException
    {
        if(!validatedName.matches("(?i)[a-z][a-z_0-9]*"))
        {
            throw new CmdLineParser.InvalidArgException
                      (context + "An identifier is expected as command line"
                       + " argument but got " + validatedName
                       + ". Any characters other than letters, digits or the"
                       + " underscore are not permitted"
                      );
        }
    } /* End isIdent */



    /**
     * Filter arguments, which relate to this class.
     *   @return Next relevant argument or null if there's no one left.
     *   @param argStream
     * The stream object, which delivers all arguments.
     */
    private String parseGetNextArg(Iterator<String> argStream)
    {
        while(argStream.hasNext())
        {
            String arg = argStream.next();
            switch(arg)
            {
            case "cluster-name":

                return arg;
                
            default:
            }
        }

        return null;

    } /* End ParameterSet.parseGetNextArg */



    /**
     * Fill the parameter object with actual values.
     *   After successful command line parsing this function iterates along the parse
     * result to fill all fields of this parameter object.
     *   @throws CmdLineParser.InvalidArgException
     * The parser can't find all structural problems of the given set of command line
     * arguments. This function returns only if all arguments were given in an accepted
     * order and if all mandatory arguments were found. Otherwise the exception is thrown
     * and the application can't continue to work. An according error message is part of
     * the exception.
     *   @param clp
     * The command line parser object after succesful run of CmdLineParser.parseArgs.
     */
    public void parseCmdLine(CmdLineParser clp)
        throws CmdLineParser.InvalidArgException
    {
        Iterator<String> it = clp.iterator();

        String arg = null;

        while(it.hasNext())
        {
            _logger.debug("Next command line argument: " + it.next());

        } /* End while(For all command line arguments) */

    } /* End of ParameterSet.parseCmdLine */



    /**
     * log4j logging is initialized for this class. Different to usual this can't be done as
     * a static expression at class initialization time. The reason is that this class is
     * involved in the preparatory log4j configuration work, which must be completed prior
     * to the class loading of log4j. Call this method once after having done all log4j
     * configuration and before the further activity of this class wants to make use of the
     * log4j logger.
     */
    public static void loadLog4jLogger()
        {_logger = LogManager.getLogger(ParameterSet.class);}


    /**
     * Render the current settings as a string.
     */
    public String toString()
    {
        return "(ParameterSet.toString is not implemented yet)";
        
    } /* End of ParameterSet.toString() */

} /* End of class ParameterSet definition. */
