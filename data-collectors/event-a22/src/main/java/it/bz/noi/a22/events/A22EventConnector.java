/*
 *  A22 Event Connector
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-05-16  1.0 - chris@1006.org
 */

package it.bz.noi.a22.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 *  encapsulates the communication with the A22 web service
 */
public class A22EventConnector {


    private static final String user_agent = "NOI/A22EventConnector";
    private static final int WS_CONN_TIMEOUT_MSEC = 30000;
    private static final int WS_READ_TIMEOUT_MSEC = 1800000;

    private static final Logger LOG = LogManager.getLogger(A22EventConnector.class);

    private static final boolean DEBUG = true;

    private String token = null;
    private String url = null;


    /**
     * get a new authentication token and store it in the instance
     *
     * @param url       A22 web service URL
     * @param username  A22 web service username
     * @param password  A22 web service password
     *
     * @throws IOException in case of communication failure
     *
     */
    public A22EventConnector(String url, String username, String password) throws IOException {

        if (DEBUG) {
            if ( this.selfTestExtractors()) {
                LOG.debug("selfTestExtractors() success");
            } else {
                LOG.warn("selfTestExtractors() FAILED");
                throw new IOException("self test failed");
            }
        }

        String auth_json = "{\"request\":{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}}";

        // make authentication request
        HttpURLConnection conn = (HttpURLConnection) (new URL(url + "/token")).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", user_agent);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(WS_CONN_TIMEOUT_MSEC);
        conn.setReadTimeout(WS_READ_TIMEOUT_MSEC);
        conn.setDoOutput(true);
        OutputStreamWriter os = new OutputStreamWriter(conn.getOutputStream());
        os.write(auth_json + "\n");
        os.flush();
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("authentication failure (response code was " + status + ")");
        }

        // get response
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder response = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null) {
            response.append(s);
        }
        os.close();
        conn.disconnect();

        // parse response and store authentication token
        String session_id = null;
        try {
            JSONObject response_json = (JSONObject) JSONValue.parse(response.toString());
            JSONObject subscribe_result = (JSONObject) response_json.get("SubscribeResult");
            session_id = (String) subscribe_result.get("sessionId");
        } catch (Exception e) {
            // null pointer or cast exception in case the json hasn't the expected form
            e.printStackTrace();
            throw new RuntimeException("authentication failure (could not parse response)");
        }

        if (session_id == null) {
            throw new RuntimeException("authentication failure (could not find sessionId in response)");
        }

        this.url = url;
        this.token = session_id;

        if (DEBUG) {
            LOG.debug("authentication OK new token = " + token);
        }

    }


    /**
     * release the authentication token
     *
     * @throws IOException in case of communication failure
     */
    public void close() throws IOException {

        if (url == null || token == null) {
            throw new RuntimeException("de-authentication failure (currently not authenticated)");
        }

        // make de-authentication request
        HttpURLConnection conn = (HttpURLConnection) (new URL(url + "/token/" + token)).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", user_agent);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(WS_CONN_TIMEOUT_MSEC);
        conn.setReadTimeout(WS_READ_TIMEOUT_MSEC);
        conn.setDoOutput(true);
        OutputStreamWriter os = new OutputStreamWriter(conn.getOutputStream());
        os.write("\n");
        os.flush();
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("de-authentication failure (response code was " + status + ")");
        }

        // get response
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder response = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null) {
            response.append(s);
        }
        os.close();
        conn.disconnect();

        // parse response
        Boolean result = null;
        try {
            JSONObject response_json = (JSONObject) JSONValue.parse(response.toString());
            result = (Boolean) response_json.get("RemoveSubscribeResult");
        } catch (Exception e) {
            // null pointer or cast exception in case the json hasn't the expected form
            e.printStackTrace();
            throw new RuntimeException("de-authentication failure (could not parse response)");
        }
        if (result == null || !result) {
            throw new RuntimeException("de-authentication failure (de-authentication was not confirmed)");
        }

        if (DEBUG) {
            LOG.debug("de-authentication OK old token = " + token);
        }

        this.url = null;
        this.token = null;

    }

    /**
     * fetch past ("eventi/lista/storici") or current ("eventi/lista/attivi") events
     *
     *   ---
     *   a note about the timestamp format used by A22:
     *
     *   before May 2021:
     *
     *   "/Date(1522195200000+0200)/" corresponds to "Wed Mar 28 00:00:00 UTC 2018"
     *
     *   they use the Unix Epoch in UTC + msec (always 000) + time zone to be ignored
     *   (we send +0000, they answer +0100 or +0200 which is to be ignored as the epoch is always UTC)
     *
     *   starting from May 2021 they stopped sending the time zone part, now it looks like this:
     *
     *   "/Date(1522195200000)/" corresponds to "Wed Mar 28 00:00:00 UTC 2018"
     *   ---
     *
     * @param fr search events from this timestamp (Unix epoch in UTC),
     *           set to null to get current events
     *
     * @param to search events up to *and* *including* this timestamp (Unix epoch in UTC),
     *           set to null to get current events
     *
     * @return an ArrayList of A22Event objects
     *
     * @throws IOException in case of communication failure
     */
    public ArrayList<A22Event> getEvents(Long fr, Long to) throws IOException {

        if (url == null || token == null) {
            throw new RuntimeException("not authenticated");
        }

        String a22path = null;
        String a22name = null;
        String a22request = null;

        if (fr == null && to == null) {
            a22path = "/eventi/lista/attivi";
            a22name = "Eventi_ListaAttiviResult";
            a22request = "{\"request\":{\"sessionId\":\"" + token + "\"}}\n";
        } else if (fr != null && to != null) {
            a22path = "/eventi/lista/storici";
            a22name = "Eventi_ListaStoriciResult";
            String frTS = "\"/Date(" + fr + "000+0000)/\"";
            String toTS = "\"/Date(" + to + "000+0000)/\"";
            a22request = "{\"request\":{\"sessionId\":\"" + token + "\",\"fromData\":" + frTS + ",\"toData\":" + toTS + "}}\n";
        } else {
            throw new RuntimeException("timestamp arguments need to be both null or both not null");
        }

        HttpURLConnection conn = (HttpURLConnection) (new URL(url + a22path)).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", user_agent);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(WS_CONN_TIMEOUT_MSEC);
        conn.setReadTimeout(WS_READ_TIMEOUT_MSEC);
        conn.setDoOutput(true);
        OutputStreamWriter os = new OutputStreamWriter(conn.getOutputStream());
        os.write(a22request);
        os.flush();
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("could not get events (response code was " + status + ")");
        }

        // get response
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder response = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null) {
            response.append(s);
        }
        os.close();
        conn.disconnect();

        // parse response

        ArrayList<A22Event> output = new ArrayList<>();

        JSONArray json_array;
        try {
            json_array = (JSONArray) ((JSONObject) JSONValue.parse(response.toString())).get(a22name);
        } catch (Exception e) {
            // throw an error in case not even the top level element cannot be extracted as expected
            if (DEBUG) {
                LOG.warn("---");
                LOG.warn("getEvents() ERROR: unparsable response:");
                LOG.warn("vvv");
                LOG.warn(response.toString());
                LOG.warn("^^^");
                LOG.warn(e.getMessage(), e);
                e.printStackTrace();
                LOG.warn("---");
            }
            throw new IOException("ERROR: unparsable response");
        }

        int skipped = 0;
        int i;
        for (i = 0; i < json_array.size(); i++) {

            try {

                JSONObject json_record = (JSONObject) json_array.get(i);
                A22Event record = new A22Event();

                record.setId(extractLong(json_record, "id"));
                record.setIdtipoevento(extractLong(json_record, "idtipoevento"));
                record.setIdsottotipoevento(extractLong(json_record, "idsottotipoevento"));
                record.setAutostrada(extractString(json_record, "autostrada"));
                record.setIddirezione(extractLong(json_record, "iddirezione"));
                record.setIdcorsia(extractLong(json_record, "idcorsia"));
                record.setData_inizio(extractEpoch(json_record, "data_inizio"));
                record.setData_fine(extractEpoch(json_record, "data_fine"));
                record.setFascia_oraria(extractBoolean(json_record, "fascia_oraria"));
                record.setMetro_inizio(extractLong(json_record, "metro_inizio"));
                record.setMetro_fine(extractLong(json_record, "metro_fine"));
                record.setLat_inizio(extractDouble(json_record, "lat_inizio"));
                record.setLon_inizio(extractDouble(json_record, "lon_inizio"));
                record.setLat_fine(extractDouble(json_record, "lat_fine"));
                record.setLon_fine(extractDouble(json_record, "lon_fine"));

                output.add(record);

            } catch (Exception e) {

                // null pointer, cast or number format exception in case the json hasn't the expected form
                // or has incompatible data types: log and skip the record
                if (DEBUG) {
                    LOG.warn("---");
                    LOG.warn("getEvents() ERROR: skipping unparsable record:");
                    LOG.warn("vvv");
                    LOG.warn(json_array.get(i).toString());
                    LOG.warn("^^^");
                    LOG.warn(e.getMessage(), e);
                    e.printStackTrace();
                    LOG.warn("---");
                }
                skipped++;
                continue;
            }

        }

        if (DEBUG) {
            LOG.debug("getEvents() OK: got " + output.size() + " events, " + skipped + " skipped");
        }

        return output;

    }

    /**
     * fetch BrennerLEC Events ("eventi/brennerlec/limititratte")
     *
     * @return an ArrayList of A22BrennerLECEvent objects
     *
     * @throws IOException in case of communication failure
     */
    public ArrayList<A22BrennerLECEvent> getBrennerLECEvents() throws IOException {

        if (url == null || token == null) {
            throw new RuntimeException("not authenticated");
        }

        String a22path = "/eventi/brennerlec/limititratte";
        String a22name = "Eventi_LimitiTratteResult";
        String a22request = "{\"request\":{\"sessionId\":\"" + token + "\"}}\n";

        HttpURLConnection conn = (HttpURLConnection) (new URL(url + a22path)).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", user_agent);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(WS_CONN_TIMEOUT_MSEC);
        conn.setReadTimeout(WS_READ_TIMEOUT_MSEC);
        conn.setDoOutput(true);
        OutputStreamWriter os = new OutputStreamWriter(conn.getOutputStream());
        os.write(a22request);
        os.flush();
        int status = conn.getResponseCode();
        if (status != 200) {
            throw new RuntimeException("could not get events (response code was " + status + ")");
        }

        // get response
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        StringBuilder response = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null) {
            response.append(s);
        }
        os.close();
        conn.disconnect();

        // parse response

        ArrayList<A22BrennerLECEvent> output = new ArrayList<>();

        JSONArray json_array;
        try {
            json_array = (JSONArray) ((JSONObject) JSONValue.parse(response.toString())).get(a22name);
        } catch (Exception e) {
            // throw an error in case not even the top level element cannot be extracted as expected
            if (DEBUG) {
                LOG.warn("---");
                LOG.warn("getBrennerLECEvents() ERROR: unparsable response:");
                LOG.warn("vvv");
                LOG.warn(response.toString());
                LOG.warn("^^^");
                LOG.warn(e.getMessage(), e);
                e.printStackTrace();
                LOG.warn("---");
            }
            throw new IOException("ERROR: unparsable response");
        }

        int skipped = 0;
        int i;
        for (i = 0; i < json_array.size(); i++) {

            try {

                JSONObject json_record = (JSONObject) json_array.get(i);
                A22BrennerLECEvent record = new A22BrennerLECEvent();
                record.setIdtratta(extractString(json_record, "idtratta"));
                record.setLimite(extractLong(json_record, "limite"));
                record.setDataattuazione(extractEpoch(json_record, "dataattuazione"));

                output.add(record);

            } catch (Exception e) {

                // null pointer, cast or number format exception in case the json hasn't the expected form
                // or has incompatible data types: log and skip the record
                if (DEBUG) {
                    LOG.warn("---");
                    LOG.warn("getBrennerLECEvents() ERROR: skipping unparsable record:");
                    LOG.warn("vvv");
                    LOG.warn(json_array.get(i).toString());
                    LOG.warn("^^^");
                    LOG.warn(e.getMessage(), e);
                    e.printStackTrace();
                    LOG.warn("---");
                }
                skipped++;
                continue;
            }

        }

        if (DEBUG) {
            LOG.debug("getBrennerLECEvents() OK: got " + output.size() + " events, " + skipped + " skipped");
        }

        return output;

    }


    private Boolean selfTestExtractors() {
        return      "str".equals(this.extractString((JSONObject)JSONValue.parse("{\"data\":\"str\"}"), "data"))
                && "true".equals(this.extractString((JSONObject)JSONValue.parse("{\"data\":true}"),    "data"))
                &&    "3".equals(this.extractString((JSONObject)JSONValue.parse("{\"data\":3}"),       "data"))
                && "3.14".equals(this.extractString((JSONObject)JSONValue.parse("{\"data\":3.14}"),    "data"))

                && Boolean .TRUE.equals(this.extractBoolean((JSONObject)JSONValue.parse("{\"data\":true}"), "data"))
                && Boolean.FALSE.equals(this.extractBoolean((JSONObject)JSONValue.parse("{\"data\":false}"),"data"))

                && Long.valueOf(3).equals(this.extractLong((JSONObject)JSONValue.parse("{\"data\":3}"),     "data"))
                && Long.valueOf(3).equals(this.extractLong((JSONObject)JSONValue.parse("{\"data\":\"3\"}"), "data"))

                && Long.valueOf(1517735920).equals(this.extractEpoch((JSONObject)JSONValue.parse("{\"data\":\"/Date(1517735920000)/\"}"), "data"))
                && Long.valueOf(1517735920).equals(this.extractEpoch((JSONObject)JSONValue.parse("{\"data\":\"/Date(1517735920000+0200)/\"}"), "data"))

                && Double.valueOf(3.0).equals(this.extractDouble((JSONObject)JSONValue.parse("{\"data\":3}"),        "data"))
                && Double.valueOf(3.0).equals(this.extractDouble((JSONObject)JSONValue.parse("{\"data\":3.0}"),     "data"))
                && Double.valueOf(3.0).equals(this.extractDouble((JSONObject)JSONValue.parse("{\"data\":\"3.0\"}"), "data"));
    }

    private String extractString(JSONObject obj, String key) throws NumberFormatException {
        // accept all scalar types from the JSON API (but not objects or arrays)
        Object test = obj.get(key);
        if (test == null) {
            return null;
        }
        if ("String".equals(test.getClass().getSimpleName())) {
            return (String)(test);
        }
        if ("Boolean".equals(test.getClass().getSimpleName())) {
            return String.valueOf((Boolean)(test));
        }
        if ("Long".equals(test.getClass().getSimpleName())) {
            return String.valueOf((Long)(test));
        }
        if ("Double".equals(test.getClass().getSimpleName())) {
            return String.valueOf((Double)(test));
        }
        throw new NumberFormatException("value cannot be interpreted as String");
    }


    private Boolean extractBoolean(JSONObject obj, String key) throws NumberFormatException {
        // accept a Boolean from the JSON API and nothing else
        Object test = obj.get(key);
        if (test == null) {
            return null;
        }
        if ("Boolean".equals(test.getClass().getSimpleName())) {
            return (Boolean)test;
        }
        throw new NumberFormatException("value cannot be interpreted as Boolean");
    }

    private Long extractLong(JSONObject obj, String key) throws NumberFormatException {
        // 3 and "3" are both acceptable as Long,
        // the JSON API returns these as Long or String
        Object test = obj.get(key);
        if (test == null) {
            return null;
        }
        if ("Long".equals(test.getClass().getSimpleName())) {
            return (Long)test;
        }
        if ("String".equals(test.getClass().getSimpleName())) {
            return Long.valueOf((String)test); // might throw java.lang.NumberFormatException
        }
        throw new NumberFormatException("value cannot be interpreted as Long");
    }

    private Long extractEpoch(JSONObject obj, String key) throws NumberFormatException {
        // extract the epoch in seconds from the epochs in msec given in the A22 format as "/Date(1517735920000)/";
        // use some tolerance as the format might change (again)
        String str = extractString(obj, key);
        if (str == null) {
            return null;
        }
        int bracket_pos = str.indexOf("(");
        if (bracket_pos == -1) {
            throw new NumberFormatException("value cannot be interpreted as A22 date");
        }
        if (bracket_pos + 11 > str.length()) {
            throw new NumberFormatException("value cannot be interpreted as A22 date");
        }
        long ret;
        ret = Long.parseLong(str.substring(bracket_pos + 1, bracket_pos + 11));
        if (ret < 1000000000L || ret > 4000000000L) {  // ~ 2000 .. ~ 2096
            throw new NumberFormatException("value cannot be interpreted as A22 date");
        }
        return ret;
    }

    private Double extractDouble(JSONObject obj, String key) throws NumberFormatException {
        // 3, 3.0 and "3.0" are all acceptable as Double,
        // the JSON API returns these as Long, Double or String
        Object test = obj.get(key);
        if (test == null) {
            return null;
        }
        if ("Long".equals(test.getClass().getSimpleName())) {
            return Double.valueOf((Long)test);
        }
        if ("Double".equals(test.getClass().getSimpleName())) {
            return (Double)test;
        }
        if ("String".equals(test.getClass().getSimpleName())) {
            return Double.valueOf((String)test); // might throw java.lang.NumberFormatException
        }
        throw new NumberFormatException("value cannot be interpreted as Double");
    }



}
