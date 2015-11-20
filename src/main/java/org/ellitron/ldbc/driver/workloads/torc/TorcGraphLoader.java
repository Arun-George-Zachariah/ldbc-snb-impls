/*
 * Copyright 2015 Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ellitron.ldbc.driver.workloads.torc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.ellitron.tinkerpop.gremlin.torc.measurement.MeasurementClient;
import org.ellitron.tinkerpop.gremlin.torc.structure.TorcGraph;
import org.ellitron.tinkerpop.gremlin.torc.structure.util.TorcHelper;

/**
 *
 * @author Jonathan Ellithorpe <jde@cs.stanford.edu>
 */
public class TorcGraphLoader {

    private static final Logger logger = Logger.getLogger(TorcGraphLoader.class.getName());

    public static void loadVertices(TorcGraph graph, Path filePath, Long idPrefix, boolean printLoadingDots) throws IOException {
        long count = 0;
        String[] colNames = null;
        boolean firstLine = true;
        Map<Object, Object> propertiesMap;
        String fileName = filePath.getFileName().toString();
        String label = fileName.substring(0, fileName.indexOf("_"));
        for (String line : Files.readAllLines(filePath)) {
            if (firstLine) {
                colNames = line.split("\\|");
                firstLine = false;
                continue;
            }

            String[] colVals = line.split("\\|");
            propertiesMap = new HashMap<>();

            for (int i = 0; i < colVals.length; ++i) {
                if (colNames[i].equals("id")) {
                    propertiesMap.put(T.id, TorcHelper.makeVertexId(idPrefix, Long.decode(colVals[i])));
                } else {
                    propertiesMap.put(colNames[i], colVals[i]);
                }
            }

            propertiesMap.put(T.label, label);

            List<Object> keyValues = new ArrayList<>();
            propertiesMap.forEach((key, val) -> {
                keyValues.add(key);
                keyValues.add(val);
            });

            graph.addVertex(keyValues.toArray());

            count++;
            if (count % 100 == 0) {
                graph.tx().commit();
                if (printLoadingDots && (count % 100000 == 0)) {
                    System.out.print(". ");
                }
            }
        }
        graph.tx().commit();
    }
    
    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption("C", "coordinator", true, "Service locator where the coordinator can be contacted.");
        options.addOption(null, "numMasters", true, "Total master servers.");
        options.addOption(null, "graphName", true, "Name for this graph.");
        options.addOption(null, "input", true, "Input file directory.");
        options.addOption("h", "help", false, "Print usage.");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
            return;
        }

        if (cmd.hasOption("h")) {
            formatter.printHelp("LDBCSNBBulkLoader", options);
            return;
        }

        // Required parameters.
        String coordinatorLocator;
        if (cmd.hasOption("coordinator")) {
            coordinatorLocator = cmd.getOptionValue("coordinator");
        } else {
            logger.log(Level.SEVERE, "Missing required argument: coordinator");
            return;
        }

        int numMasters;
        if (cmd.hasOption("numMasters")) {
            numMasters = Integer.decode(cmd.getOptionValue("numMasters"));
        } else {
            logger.log(Level.SEVERE, "Missing required argument: numMasters");
            return;
        }

        String graphName;
        if (cmd.hasOption("graphName")) {
            graphName = cmd.getOptionValue("graphName");
        } else {
            logger.log(Level.SEVERE, "Missing required argument: graphName");
            return;
        }
        
        String inputBaseDir;
        if (cmd.hasOption("input")) {
            inputBaseDir = cmd.getOptionValue("input");
        } else {
            logger.log(Level.SEVERE, "Missing required argument: input");
            return;
        }

        /**
         * TODO: Use environment variables for the source of configuration
         * information.
         */
        BaseConfiguration config = new BaseConfiguration();
        config.setDelimiterParsingDisabled(true);
        config.setProperty(TorcGraph.CONFIG_GRAPH_NAME, graphName);
        config.setProperty(TorcGraph.CONFIG_COORD_LOC, coordinatorLocator);
        config.setProperty(TorcGraph.CONFIG_NUM_MASTER_SERVERS, numMasters);

        TorcGraph graph = TorcGraph.open(config);
        
        try {
            for (Entity entity : Entity.values()) {
                String fileName = entity.getName() + "_0_0.csv";
                System.out.print("Loading " + fileName + " ");
                
                loadVertices(graph, Paths.get(inputBaseDir + "/" + fileName), entity.getNumber(), true);
                
                System.out.println("Finished");
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e);
            e.printStackTrace();
        } finally {
            //graph.deleteDatabaseAndCloseConnection();
            graph.close();
        }
    }
}
