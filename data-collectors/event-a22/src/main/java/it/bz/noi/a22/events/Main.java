/*
 *  A22 Event Connector - Demo Application
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-05-16  1.0 - chris@1006.org
 */

package it.bz.noi.a22.events;

import java.io.IOException;
import java.util.ArrayList;

/**
 * command line application to demo the usage of the A22EventConnector class
 */
public class Main {

    /**
     * @param args required three command line arguments: A22 web service base URL, username, password
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 3 || isEmpty(args[0]) || isEmpty(args[1]) || isEmpty(args[2])) {
            System.err.println("expected arguments: <A22 web service URL> <username> <password>");
            System.exit(1);
        }

        // step 1
        // create a Connector instance: this will perform authentication and store the session
        //
        // the session will last 24 hours unless de-authenticated before - however, if a user
        // deauthenticates one session, all sessions of the same user will be de-authenticated;
        // this means each running application neeeds their own username

        A22EventConnector A22Service = new A22EventConnector(args[0], args[1], args[2]);

        // step 2
        // fetch and print all events ("eventi/lista/storici") in a certain time range

        try {
            /*
            $ date  -d  "Jan 1 00:00:00 UTC 2018" +%s
            1514764800
            $ date  -d  "Jan 31 00:00:00 UTC 2018" +%s
            1517356800
             */
            ArrayList<A22Event> events = A22Service.getEvents(1622789265L, 1622813245L);
            System.out.println("got " + events.size() + " events");
            for (A22Event e : events) {
                System.out.println(e);
            }

        } catch (Exception e) {
            System.out.println("step 2 failed, continuing anyway to de-auth...");
        }

        // step 3
        // fetch and print all current events ("eventi/lista/attivi")

        try {
            ArrayList<A22Event> events = A22Service.getEvents(null, null);
            System.out.println("got " + events.size() + " events");
            for (A22Event e : events) {
                System.out.println(e);
            }
        } catch (Exception e) {
            System.out.println("step 3 failed, continuing anyway to de-auth...");
        }

        // step 4
        // fetch and print all BrennerLEC events ("eventi/brennerlec/limititratte")

        try {
            ArrayList<A22BrennerLECEvent> events = A22Service.getBrennerLECEvents();
            System.out.println("got " + events.size() + " events");
            for (A22BrennerLECEvent e : events) {
                System.out.println(e);
            }
        } catch (Exception e) {
            System.out.println("step 4 failed, continuing anyway to de-auth...");
        }

        // step 5
        // de-authentication

        A22Service.close();

        System.out.println("'k thx bye");

    }

    private static Boolean isEmpty(String a) {
        return (a == null || a.trim().equals(""));
    }

}
