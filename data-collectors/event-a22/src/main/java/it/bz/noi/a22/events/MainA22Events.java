/*
 *  A22 Events Data Collector - Main Application
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-06-04  1.0 - thomas.nocker@catch-solve.tech
 */

package it.bz.noi.a22.events;

import it.bz.idm.bdp.dto.StationDto;
import it.bz.idm.bdp.dto.StationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

@Component
public class MainA22Events {

    private static final String STATION_METADATA_AUTOSTRADA = "autostrada";
    private static final String STATION_METADATA_DATA_INIZIO = "data_inizio";
    private static final String STATION_METADATA_DATA_FINE = "data_fine";
    private static final String STATION_METADATA_FASCIA_ORARIA = "fascia_oraria";
    private static final String STATION_METADATA_IDCORSIA = "idcorsia";
    private static final String STATION_METADATA_IDDIREZIONE = "iddirezione";
    private static final String STATION_METADATA_IDSOTTOTIPOEVENTO = "idsottotipoevento";
    private static final String STATION_METADATA_IDTIPOEVENTO = "idtipoevento";
    private static final String STATION_METADATA_LAT_INIZIO = "lat_inizio";
    private static final String STATION_METADATA_LAT_FINE = "lat_fine";
    private static final String STATION_METADATA_LON_INIZIO = "lon_inizio";
    private static final String STATION_METADATA_LON_FINE = "lon_fine";
    private static final String STATION_METADATA_METRO_INIZIO = "metro_fine";
    private static final String STATION_METADATA_METRO_FINE = "metro_inizio";

    private static Logger LOG = LogManager.getLogger(MainA22Events.class);

    private final A22Properties metadataMappingProperties;
    private final A22Properties a22EventsProperties;
    @Autowired
    private A22EventEventsJSONPusher pusher;
    private String stationCodePrefix;

    public MainA22Events() {
        this.metadataMappingProperties = new A22Properties("a22eventsmetadatamapping.properties");
        this.a22EventsProperties = new A22Properties("a22events.properties");
        this.stationCodePrefix = a22EventsProperties.getProperty("stationcode-prefix");
    }

    public void execute() {
        long startTime = System.currentTimeMillis();
        try {
            LOG.info("Start MainA22Events");


            long delaySeconds = 3600; // 2019-06-21 d@vide.bz: a22 data realtime delay

            // step 1
            // create a Connector instance: this will perform authentication and store the session
            //
            // the session will last 24 hours unless de-authenticated before - however, if a user
            // deauthenticates one session, all sessions of the same user will be de-authenticated;
            // this means each running application neeeds their own username
            A22EventConnector A22Service = setupA22ServiceConnector();

            // step 2
            // fetch and print all events ("eventi/lista/storici") in a certain time range
            LOG.info("step 2: fetch and print all events (\"eventi/lista/storici\")");
            HashMap<String, StationDto> inactiveStations = new HashMap<>();
            try {
                long scanWindowSeconds = Long.parseLong(a22EventsProperties.getProperty("scanWindowSeconds"));
                long lastTimeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(a22EventsProperties.getProperty("lastTimestamp")).getTime() / 1000;

                do {
                    HashMap<String, StationDto> stationDtoMap = new HashMap<>();
                    LOG.debug("Get all events between {} and {}",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastTimeStamp * 1000)),
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date((lastTimeStamp + scanWindowSeconds) * 1000)));
                    ArrayList<A22Event> events = A22Service.getEvents(lastTimeStamp, lastTimeStamp + scanWindowSeconds);
                    LOG.debug("got " + events.size() + " events");
                    for (A22Event event : events) {
                        if(!inactiveStations.containsKey(event.getId().toString())) {
                            StationDto stationDto = getStationDtoFromA22Event(event);
                            inactiveStations.put(stationDto.getId(), stationDto);
                            stationDtoMap.put(stationDto.getId(), stationDto);
                        }
                    }
                    pusher.syncStations(new StationList(stationDtoMap.values()));
                    lastTimeStamp += scanWindowSeconds;
                } while (lastTimeStamp < System.currentTimeMillis() / 1000);
            } catch (Exception e) {
                LOG.error("step 2 failed, continuing anyway to read de-auth...", e);
            }

            // step 3
            // fetch and print all current events ("eventi/lista/attivi")
            LOG.info("step 3: fetch and print all current events (\"eventi/lista/attivi\")");
            try {
                StationList stationList = new StationList();
                ArrayList<A22Event> events = A22Service.getEvents(null, null);
                LOG.debug("got " + events.size() + " events");
                for (A22Event event : events) {
                    StationDto stationDto = getStationDtoFromA22Event(event);
                    stationList.add(stationDto);
                }
                pusher.syncStations(stationList);
            } catch (Exception e) {
                LOG.error("step 3 failed, continuing anyway to read de-auth...", e);
            }

            // step 4
            A22Service.close();
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error(e);
        } finally {
            long stopTime = System.currentTimeMillis();
            LOG.debug("elaboration time (millis): " + (stopTime - startTime));
        }
    }

    private  A22EventConnector setupA22ServiceConnector() throws IOException {
        String url;
        String user;
        String password;

        // read connector auth informations
        A22Properties prop = new A22Properties("a22connector.properties");
        url = prop.getProperty("url");
        user = prop.getProperty("user");
        password = prop.getProperty("password");

        A22EventConnector a22ParkingConnector = new A22EventConnector(url, user, password);

        return a22ParkingConnector;
    }

    public StationDto getStationDtoFromA22Event(A22Event event) {
        StationDto stationDto = new StationDto(this.stationCodePrefix + event.getId().toString(),
                event.getId().toString(), // TODO not optional
                event.getLat_inizio(),
                event.getLon_inizio());
        stationDto.setOrigin(a22EventsProperties.getProperty("origin"));
        stationDto.setStationType(a22EventsProperties.getProperty("stationtype"));

        stationDto.getMetaData().put(STATION_METADATA_AUTOSTRADA, event.getAutostrada());
        stationDto.getMetaData().put(STATION_METADATA_DATA_INIZIO, event.getData_inizio());
        stationDto.getMetaData().put(STATION_METADATA_DATA_FINE, event.getData_fine());
        stationDto.getMetaData().put(STATION_METADATA_LAT_INIZIO, event.getLat_inizio());
        stationDto.getMetaData().put(STATION_METADATA_LAT_FINE, event.getLat_fine());
        stationDto.getMetaData().put(STATION_METADATA_LON_INIZIO, event.getLon_inizio());
        stationDto.getMetaData().put(STATION_METADATA_LON_FINE, event.getLon_fine());
        stationDto.getMetaData().put(STATION_METADATA_METRO_INIZIO, event.getMetro_inizio());
        stationDto.getMetaData().put(STATION_METADATA_METRO_FINE, event.getMetro_fine());
        stationDto.getMetaData().put(STATION_METADATA_FASCIA_ORARIA, event.getFascia_oraria());
        stationDto.getMetaData().put(STATION_METADATA_IDCORSIA,
                getMappingStringByPropertyId(STATION_METADATA_IDCORSIA, event.getIdcorsia()));
        stationDto.getMetaData().put(STATION_METADATA_IDDIREZIONE,
                getMappingStringByPropertyId(STATION_METADATA_IDDIREZIONE, event.getIddirezione()));
        stationDto.getMetaData().put(STATION_METADATA_IDTIPOEVENTO,
                getMappingStringByPropertyId(STATION_METADATA_IDTIPOEVENTO, event.getIdtipoevento()));
        stationDto.getMetaData().put(STATION_METADATA_IDSOTTOTIPOEVENTO, event.getIdsottotipoevento());
        return stationDto;
    }

    public String getMappingStringByPropertyId(String idProperty, Long id) {
        String defaultValue = metadataMappingProperties.getProperty("a22_events.metadata." + idProperty + ".*");
        String ret = metadataMappingProperties.getProperty("a22_events.metadata." + idProperty + "." + id, defaultValue);
        if(ret == null) {
            LOG.warn("Unable to find the following '{}' string: {}", idProperty, id.toString());
            ret = id.toString();
        }
        return ret;
    }

}
