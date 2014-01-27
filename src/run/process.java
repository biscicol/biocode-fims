package run;

import com.hp.hpl.jena.util.FileManager;
import digester.*;
import fims.fimsModel;
import fims.fimsQueryBuilder;
import org.apache.commons.cli.*;
import org.xml.sax.SAXException;
import reader.ReaderManager;
import reader.plugins.TabularDataReader;
import settings.*;
import triplify.triplifier;
import org.apache.commons.digester.Digester;
import org.apache.log4j.Level;
import org.apache.commons.cli.HelpFormatter;


import java.io.*;
import java.net.URL;

/**
 * Core class for running fims processes.  Here you specify the input file, configuration file, output folder, and
 * a project code, which is used to specify identifier roots in the BCID (http://code.google.com/p/bcid/) system.
 * The main class is configured to run this from the command-line while the class itself can be extended to run
 * in different situations, while specifying  fimsPrinter and fimsInputter classes for a variety of styles of output and
 * input
 */
public class process {

    File configFile;
    String inputFilename;
    String outputFolder;
    String outputPrefix;
    String project_code;
    Boolean write_spreadsheet;
    Boolean triplify;
    Boolean upload;
    String username;
    String password;
    Integer expedition_id;


    /**
     * Setup class variables for processing FIMS data.
     *
     * @param inputFilename     The data to run.process, usually an Excel spreadsheet
     * @param outputFolder      Where to store output files
     * @param project_code      A distinct project code for the project being loaded, used to lookup projects in the BCID system
     *                          for assigning identifier roots.
     * @param write_spreadsheet Write back a spreadsheet to test to the entire cycle
     */
    public process(
            String inputFilename,
            String outputFolder,
            String project_code,
            Boolean write_spreadsheet,
            Boolean triplify,
            Boolean upload,
            String username,
            String password,
            Integer expedition_id) throws FIMSException {
        // Set class variables
        this.inputFilename = inputFilename;
        this.outputFolder = outputFolder;
        this.project_code = project_code;
        this.write_spreadsheet = write_spreadsheet;
        this.triplify = triplify;
        this.upload = upload;
        this.username = username;
        this.password = password;
        this.expedition_id = expedition_id;
        // Control the file outputPrefix... set them here to project codes.
        this.outputPrefix = project_code + "_output";

        // Setup logging
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.ERROR);
    }

    /**
     * A smaller constructor for when we're running queries
     *
     * @param outputFolder
     * @param configFile
     * @throws FIMSException
     */
    public process(
            String outputFolder,
            File configFile
    ) throws FIMSException {
        this.outputFolder = outputFolder;
        this.configFile = configFile;
        this.outputPrefix = "output";
    }

    /**
     * runAll method is designed to go through entire fims run.process: validate, triplify, upload
     * TODO: clean up FIMSExceptions to throw only unexpected errors so they can be handled more elegantly
     */
    public void runAll() throws FIMSException {
        //if (1==1) throw new FIMSException("TEST exception handling");
        boolean validationGood = true;
        boolean triplifyGood = true;
        boolean updateGood = true;
        boolean projectCheck = false;
        Validation validation = null;
        bcidConnector bcidConnector = new bcidConnector();

        // Authenticate all the time, even if not uploading
        fimsPrinter.out.println("Authenticating ...");
        boolean authenticationSuccess = false;
        try {
            authenticationSuccess = bcidConnector.authenticate(username, password);
        } catch (Exception e) {
            throw new FIMSException("You indicated you wanted to upload data but we were unable to authenticate using the supplied credentials!");
        }
        if (!authenticationSuccess)
            throw new FIMSException("You indicated you wanted to upload data but we were unable to authenticate using the supplied credentials!");
        // force triplify to true if we want to upload

        if (upload) {
            triplify = true;
        }

        try {
            // Initializing
            fimsPrinter.out.println("Initializing ...");
            fimsPrinter.out.println("\tinputFilename = " + inputFilename);

            try {

                configFile = new configurationFileFetcher(expedition_id, outputFolder).getOutputFile();
            } catch (Exception e) {
                throw new FIMSException("Unable to obtain configuration file from server... \nPlease check that your project code is valid.  Project codes Must be between 4 and 6 characters in length.");
            }
            fimsPrinter.out.println("\tconfiguration file = " + configFile.getAbsoluteFile());


            // Check that the user that is logged in also owns the project_code
            try {
                projectCheck = bcidConnector.validateProject(project_code, expedition_id);
            } catch (Exception e) {
                //e.printStackTrace();
                throw new FIMSException(e.getMessage(), e);
            }

            if (projectCheck) {
                // Read the input file & create the ReaderManager and load the plugins.
                try {
                    ReaderManager rm = new ReaderManager();
                    TabularDataReader tdr = null;

                    rm.loadReaders();
                    tdr = rm.openFile(inputFilename);

                    // Validation
                    validation = new Validation();
                    addValidationRules(new Digester(), validation);
                    validation.run(tdr, outputPrefix, outputFolder);
                    validationGood = validation.printMessages();

                    // Print out column names!!
                    //System.out.println(validation.getTabularDataReader().getColNames());
                    //if (1==1) return;

                    // Triplify if we validate
                    if (triplify & validationGood) {

                        Mapping mapping = new Mapping();
                        addMappingRules(new Digester(), mapping);

                        triplifyGood = mapping.run(
                                validation,
                                new triplifier(outputPrefix, outputFolder),
                                project_code,
                                validation.getTabularDataReader().getColNames());
                        mapping.print();

                        // Upload after triplifying
                        if (upload & triplifyGood) {
                            Fims fims = new Fims(mapping);
                            addFimsRules(new Digester(), fims);
                            fims.run(bcidConnector, project_code);
                            fims.print();

                            if (write_spreadsheet)
                                fimsPrinter.out.println("\tspreadsheet = " +
                                        fims.getFIMSModel(FileManager.get().loadModel(
                                                fims.getMetadata().getTarget() +
                                                        "/data?graph=" +
                                                        fims.getUploader().getEncodedGraph(false))
                                        ));
                        }
                    }
                } catch (Exception e) {
                    throw new FIMSException(e.getMessage(), e);
                }
            }
        } finally {
            if (validation != null)
                validation.close();
        }
    }

    /**
     * Run a query from the command-line. This is not meant to be a full-featured query service but a simple way of
     * fetching results
     *
     * @throws FIMSException
     */
    public String query(String graphs, String format, String filter) throws FIMSException {

        try {
            // Build Mapping object
            Mapping mapping = new Mapping();
            addMappingRules(new Digester(), mapping);

            // Build FIMS object
            Fims fims = new Fims(mapping);
            addFimsRules(new Digester(), fims);

            // Code a reference to the Sparql Query Server
            String sparqlServer = fims.getMetadata().getTarget().toString() + "/query";

            // Build a query model, passing in an String[] array of graph identifiers
            fimsQueryBuilder q = new fimsQueryBuilder(graphs.split(","), sparqlServer);

            // Filter
            if (filter != null && !filter.trim().equals(""))
                q.setObjectFilter(filter);

            // Construct a  fimsModel
            fimsModel fimsModel = fims.getFIMSModel(q.getModel());


            // Output the results
            fimsPrinter.out.println("Writing results ... ");

            if (format == null)
                format = "json";

            if (format.equals("excel"))
                return fimsModel.writeExcel(PathManager.createUniqueFile(outputPrefix + ".xls", outputFolder));
            else if (format.equals("html"))
                return fimsModel.writeHTML(PathManager.createUniqueFile(outputPrefix + ".html", outputFolder));
            else if (format.equals("kml"))
                return fimsModel.writeKML(PathManager.createUniqueFile(outputPrefix + ".kml", outputFolder));
            else
                return fimsModel.writeJSON(PathManager.createUniqueFile(outputPrefix + ".json", outputFolder));
        } catch (Exception e) {
            e.printStackTrace();
            throw new FIMSException(e.getMessage(), e);
        }
    }

    /**
     * Process metadata component rules
     *
     * @param d
     */
    private synchronized void addFimsRules(Digester d, Fims fims) throws IOException, SAXException {
        d.push(fims);
        d.addObjectCreate("fims/metadata", Metadata.class);
        d.addSetProperties("fims/metadata");
        d.addCallMethod("fims/metadata", "addText_abstract", 0);
        d.addSetNext("fims/metadata", "addMetadata");

        d.parse(configFile);
    }

    /**
     * Process validation component rules
     *
     * @param d
     */
    private synchronized void addValidationRules(Digester d, Validation validation) throws IOException, SAXException {
        d.push(validation);

        // Create worksheet objects
        d.addObjectCreate("fims/validation/worksheet", Worksheet.class);
        d.addSetProperties("fims/validation/worksheet");
        d.addSetNext("fims/validation/worksheet", "addWorksheet");

        // Create rule objects
        d.addObjectCreate("fims/validation/worksheet/rule", Rule.class);
        d.addSetProperties("fims/validation/worksheet/rule");
        d.addSetNext("fims/validation/worksheet/rule", "addRule");
        d.addCallMethod("fims/validation/worksheet/rule/field", "addField", 0);

        // Create list objects
        d.addObjectCreate("fims/validation/lists/list", List.class);
        d.addSetProperties("fims/validation/lists/list");
        d.addSetNext("fims/validation/lists/list", "addList");
        d.addCallMethod("fims/validation/lists/list/field", "addField", 0);

        // Create column objects
        d.addObjectCreate("fims/validation/worksheet/column", Column_trash.class);
        d.addSetProperties("fims/validation/worksheet/column");
        d.addSetNext("fims/validation/worksheet/column", "addColumn");

        d.parse(configFile);
    }

    /**
     * Process mapping component rules
     *
     * @param d
     */
    private synchronized void addMappingRules(Digester d, Mapping mapping) throws IOException, SAXException {
        d.push(mapping);

        // Create entity objects
        d.addObjectCreate("fims/mapping/entity", Entity.class);
        d.addSetProperties("fims/mapping/entity");
        d.addSetNext("fims/mapping/entity", "addEntity");

        // Add attributes associated with this entity
        d.addObjectCreate("fims/mapping/entity/attribute", Attribute.class);
        d.addSetProperties("fims/mapping/entity/attribute");
        d.addSetNext("fims/mapping/entity/attribute", "addAttribute");

        // Create relation objects
        d.addObjectCreate("fims/mapping/relation", Relation.class);
        d.addSetNext("fims/mapping/relation", "addRelation");
        d.addCallMethod("fims/mapping/relation/subject", "addSubject", 0);
        d.addCallMethod("fims/mapping/relation/predicate", "addPredicate", 0);
        d.addCallMethod("fims/mapping/relation/object", "addObject", 0);

        d.parse(configFile);
    }

    /**
     * Run the program from the command-line
     *
     * @param args
     */
    public static void main(String args[]) {
        String defaultOutputDirectory = System.getProperty("user.dir") + File.separator + "tripleOutput";
        String username = "";
        String password = "";
        Integer expedition_id = 0;

        // Test configuration :
        // -d -t -u -i sampledata/Apogon***.xls

        // Direct output using the standardPrinter subClass of fimsPrinter which send to fimsPrinter.out (for command-line usage)
        fimsPrinter.out = new standardPrinter();
        // Set the input format
        fimsInputter.in = new standardInputter();

        // Some classes to help us
        CommandLineParser clp = new GnuParser();
        HelpFormatter helpf = new HelpFormatter();
        CommandLine cl;

        // The project code corresponds to a project recognized by BCID
        String project_code = "";
        // The configuration template
        //String configuration = "";
        // The input file
        String input_file = "";
        // The directory that we write all our files to
        String output_directory = "";
        // Write spreadsheet content back to a spreadsheet file, for testing
        Boolean write_spreadsheet = false;
        Boolean triplify = false;
        Boolean upload = false;


        // Define our commandline options
        Options options = new Options();
        options.addOption("h", "help", false, "print this help message and exit");
        options.addOption("q", "query", true, "Run a query and pass in graph UUIDs to look at for this query -- Use this along with options C and S");
        options.addOption("f", "format", true, "excel|html|json  specifying the return format for the query");
        options.addOption("F", "filter", true, "Filter results based on a keyword search");


        options.addOption("C", "configURL", true, "A URL specifying a configuration file, only for running queries");
        options.addOption("p", "project_code", true, "Project code.  You will need to obtain a project code before " +
                "loading data, or use the demo_mode.");
        options.addOption("o", "output_directory", true, "Output Directory");
        options.addOption("i", "input_file", true, "Input Spreadsheet");
        options.addOption("e", "expedition_id", true, "Expedition Identifier.  A numeric integer corresponding to your expedition");

        options.addOption("d", "demo_mode", false, "Demonstration mode.  Do not need to specify project_code, " +
                "configuration, or output_directory.  You still need to specify an input file.");
        options.addOption("t", "triplify", false, "Triplify");
        options.addOption("u", "upload", false, "Upload");
        options.addOption("w", "write_spreadsheet", false, "Write back an excel spreadsheet from the triplestore.  " +
                "This option is useful for testing output from flatfile -> RDF -> flatfile but not necessary.");
        options.addOption("U", "username", true, "Username");
        options.addOption("P", "password", true, "Password");

        // Create the commands parser and parse the command line arguments.
        try {
            cl = clp.parse(options, args);
        } catch (UnrecognizedOptionException e) {
            fimsPrinter.out.println("Error: " + e.getMessage());
            return;
        } catch (ParseException e) {
            fimsPrinter.out.println("Error: " + e.getMessage());
            return;
        }

        if (cl.hasOption("U")) {
            username = cl.getOptionValue("U");
        }
        if (cl.hasOption("P")) {
            password = cl.getOptionValue("P");
        }
        if (cl.hasOption("u") && (username.equals("") || password.equals(""))) {
            fimsPrinter.out.println("Must specify a valid username or password for uploading data!");
            return;
        }
        // If help was requested, print the help message and exit.
        if (cl.hasOption("q")) {
            if (!cl.hasOption("C")) {
                helpf.printHelp("fims ", options, true);
                return;
            }
        } else if (cl.hasOption("h") ||
                (cl.hasOption("d") && !cl.hasOption("i")) ||
                (!cl.hasOption("d") && (!cl.hasOption("p") || !cl.hasOption("i")))) {
            helpf.printHelp("fims ", options, true);
            return;
        }


        if (cl.hasOption("d")) {
            project_code = "DEMOH";
            output_directory = defaultOutputDirectory;
            triplify = true;
            upload = true;
        }
        if (cl.hasOption("e")) {
            try {
                expedition_id = new Integer(cl.getOptionValue("e"));
            } catch (Exception e) {
                fimsPrinter.out.println("Bad option for expedition_id");
                helpf.printHelp("fims ", options, true);
                return;
            }
        }
        if (cl.hasOption("u") && expedition_id < 1) {
            fimsPrinter.out.println("Must specify a valid expedition_id when uploading data");
            return;
        }
        if (cl.hasOption("i"))
            input_file = cl.getOptionValue("i");
        if (cl.hasOption("o"))
            output_directory = cl.getOptionValue("o");
        if (cl.hasOption("p"))
            project_code = cl.getOptionValue("p");
        if (cl.hasOption("w"))
            write_spreadsheet = true;
        if (cl.hasOption("t"))
            triplify = true;
        if (cl.hasOption("u"))
            upload = true;
        if (!cl.hasOption("o") && !cl.hasOption("d")) {
            fimsPrinter.out.println("Using default output directory " + defaultOutputDirectory);
            output_directory = defaultOutputDirectory;
        }

        // Check that output directory is writable
        if (!new File(output_directory).canWrite()) {
            fimsPrinter.out.println("Unable to write to output directory " + output_directory);
            return;
        }


        // Run the command
        try {
            /*
            Run a query
             */
            if (cl.hasOption("q")) {

                File file = new configurationFileFetcher(new URL(cl.getOptionValue("C")), output_directory).getOutputFile();

                process p = new process(
                        output_directory,
                        file
                );

                p.query(cl.getOptionValue("q"), cl.getOptionValue("f"), cl.getOptionValue("F"));
                /*
                Run the validator
                 */
            } else {

                process p = new process(
                        input_file,
                        output_directory,
                        project_code,
                        write_spreadsheet,
                        triplify,
                        upload,
                        username,
                        password,
                        expedition_id
                );
                p.runAll();
            }
        } catch (Exception e) {
            fimsPrinter.out.println("\nError: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

}
