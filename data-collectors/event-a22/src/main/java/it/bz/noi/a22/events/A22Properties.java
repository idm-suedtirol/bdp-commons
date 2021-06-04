/*
 *  A22 Properties
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-06-04  1.0 - thomas.nocker@catch-solve.tech
 */

package it.bz.noi.a22.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Properties;

public class A22Properties extends Properties {

    private static Logger log = LogManager.getLogger(A22Properties.class);

    public A22Properties(String propertiesFile) {
        try {
            URL url = getClass().getResource(propertiesFile);
            try (Reader in = new InputStreamReader(getClass().getResourceAsStream(propertiesFile)))
            {
                this.load(in);
            }
        } catch (Exception e) {
            log.error(e);
        }
    }
}
