package it.bz.idm.bdp.dcbikesharingmoqo;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import it.bz.idm.bdp.dcbikesharingmoqo.dto.AvailabilityDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.BikeDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.BikesharingMoqoDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.BikesharingMoqoPageDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.LocationDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.PaginationDto;
import it.bz.idm.bdp.dto.DataTypeDto;

@Component
@PropertySource({ "classpath:/META-INF/spring/application.properties", "classpath:/META-INF/spring/types.properties" })
public class BikesharingMoqoDataRetriever {

    private static final Logger LOG = LogManager.getLogger(BikesharingMoqoDataRetriever.class.getName());

    @Autowired
    private Environment env;

    @Autowired
    private BikesharingMoqoDataConverter converter;

//    @Autowired
//    private BikesharingMoqoDataPusher pusher;

    private HttpClientBuilder builderStations = HttpClients.custom();
    private HttpClientBuilder builderMeasurements = HttpClients.custom();

    private CloseableHttpClient clientStations;
    private CloseableHttpClient clientMeasurements;

//    private ObjectMapper mapper = new ObjectMapper();

    private String endpointMethodStations;
    private String serviceUrlStations;
    private List<ServiceCallParam> stationsParams;

    private String endpointMethodMeasurements;
    private String serviceUrlMeasurements;
    //private List<ServiceCallParam> measurementsParams;

    public BikesharingMoqoDataRetriever() {
        LOG.debug("Create instance");
    }

    @PostConstruct
    private void initClient() {
        LOG.debug("Init");
        if (clientStations==null) {
            //Read config data from external bundle
            String strEndpointMethod   = env.getProperty("endpoint.stations.method");
            String strEndpointProtocol = env.getProperty("endpoint.stations.protocol");
            String strEndpointHost     = env.getProperty("endpoint.stations.host");
            String strEndpointPort     = env.getProperty("endpoint.stations.port");
            String strEndpointPath     = env.getProperty("endpoint.stations.path");

            LOG.debug("Read config:"+
                    "  endpoint.stations.protocol='"+strEndpointProtocol+"'"+
                    "  endpoint.stations.method='"+strEndpointMethod+"'"+
                    "  endpoint.stations.host='"+strEndpointHost+"'"+
                    "  endpoint.stations.port='"+strEndpointPort+"'"+
                    "  endpoint.stations.path='"+strEndpointPath+"'");

            //Create HTTP Client
            endpointMethodStations   = DCUtils.allowNulls(strEndpointMethod).trim();
            String  endpointProtocol = "http".equalsIgnoreCase(DCUtils.allowNulls(strEndpointProtocol).trim()) ? "http" : "https";
            String  defaultPort      = "http".equalsIgnoreCase(DCUtils.allowNulls(strEndpointProtocol).trim()) ? "80" : "443";
            String  endpointHost = DCUtils.mustNotEndWithSlash(DCUtils.allowNulls(strEndpointHost).trim());
            String  endpointPath = DCUtils.mustNotEndWithSlash(DCUtils.allowNulls(strEndpointPath).trim());
            Integer endpointPort = DCUtils.convertStringToInteger(DCUtils.defaultNulls(strEndpointPort, defaultPort));
            serviceUrlStations = endpointProtocol + "://" + endpointHost + ":" + endpointPort + "/" + endpointPath;

            clientStations = builderStations.build();

            stationsParams = new ArrayList<ServiceCallParam>();
            boolean hasNext = true;
            int i=0;
            while ( hasNext ) {
                //Example of parameters to add
                //endpoint.stations.param.0.param_name=include_unavailable_cars
                //endpoint.stations.param.0.param_value=true
                String paramName  = DCUtils.allowNulls(env.getProperty("endpoint.stations.param."+i+".param_name")).trim();
                String paramValue = DCUtils.allowNulls(env.getProperty("endpoint.stations.param."+i+".param_value")).trim();
                String functionName = DCUtils.allowNulls(env.getProperty("endpoint.stations.param."+i+".function_name")).trim();
                if ( DCUtils.paramNotNull(paramName) ) {
                    ServiceCallParam param = new ServiceCallParam(paramName);
                    if ( DCUtils.paramNotNull(functionName) ) {
                        param.type = ServiceCallParam.TYPE_FUNCTION;
                        param.value = functionName;
                    } else if ( DCUtils.paramNotNull(paramValue) ) {
                        param.type = ServiceCallParam.TYPE_FIXED_VALUE;
                        param.value = paramValue;
                    }
                    if ( param.type!=null && param.value!=null ) {
                        stationsParams.add(param);
                    } else {
                        LOG.warn("UNRECOGNIZED parameter type in application.properties file: '"+paramName+"'  index="+i+"");
                    }
                    i++;
                } else {
                    hasNext = false;
                }
            }

            LOG.debug("Http Client Stations created");
        }

        if (clientMeasurements==null) {
            //Read config data from external bundle
            String strEndpointMethod   = env.getProperty("endpoint.measurements.method");
            String strEndpointProtocol = env.getProperty("endpoint.measurements.protocol");
            String strEndpointHost     = env.getProperty("endpoint.measurements.host");
            String strEndpointPort     = env.getProperty("endpoint.measurements.port");
            String strEndpointPath     = env.getProperty("endpoint.measurements.path");

            LOG.debug("Read config:"+
                    "  endpoint.measurements.protocol='"+strEndpointProtocol+"'"+
                    "  endpoint.measurements.method='"+strEndpointMethod+"'"+
                    "  endpoint.measurements.host='"+strEndpointHost+"'"+
                    "  endpoint.measurements.port='"+strEndpointPort+"'"+
                    "  endpoint.measurements.path='"+strEndpointPath+"'");

            //Create HTTP Client
            endpointMethodMeasurements = DCUtils.allowNulls(strEndpointMethod).trim();
            String  endpointProtocol = "http".equalsIgnoreCase(DCUtils.allowNulls(strEndpointProtocol).trim()) ? "http" : "https";
            String  defaultPort      = "http".equalsIgnoreCase(DCUtils.allowNulls(strEndpointProtocol).trim()) ? "80" : "443";
            String  endpointHost = DCUtils.mustNotEndWithSlash(DCUtils.allowNulls(strEndpointHost).trim());
            String  endpointPath = DCUtils.mustNotEndWithSlash(DCUtils.allowNulls(strEndpointPath).trim());
            Integer endpointPort = DCUtils.convertStringToInteger(DCUtils.defaultNulls(strEndpointPort, defaultPort));
            serviceUrlMeasurements = endpointProtocol + "://" + endpointHost + ":" + endpointPort + "/" + endpointPath;

            clientMeasurements = builderMeasurements.build();

//            measurementsParams = new ArrayList<ServiceCallParam>();
//            boolean hasNext = true;
//            int i=0;
//            while ( hasNext ) {
//                //Example of parameters to add
//                //endpoint.measurements.param.0.param_name
//                //endpoint.measurements.param.0.station_attr_name
//                String paramName  = DCUtils.allowNulls(env.getProperty("endpoint.measurements.param."+i+".param_name")).trim();
//                String paramValue = DCUtils.allowNulls(env.getProperty("endpoint.measurements.param."+i+".param_value")).trim();
//                String stationAttrName = DCUtils.allowNulls(env.getProperty("endpoint.measurements.param."+i+".station_attr_name")).trim();
//                String sensorAttrName  = DCUtils.allowNulls(env.getProperty("endpoint.measurements.param."+i+".sensor_attr_name")).trim();
//                String functionName = DCUtils.allowNulls(env.getProperty("endpoint.measurements.param."+i+".function_name")).trim();
//                if ( DCUtils.paramNotNull(paramName) ) {
//                    ServiceCallParam param = new ServiceCallParam(paramName);
//                    if ( DCUtils.paramNotNull(stationAttrName) ) {
//                        param.type = ServiceCallParam.TYPE_STATION_VALUE;
//                        param.value = stationAttrName;
//                    } else if ( DCUtils.paramNotNull(sensorAttrName) ) {
//                        param.type = ServiceCallParam.TYPE_SENSOR_VALUE;
//                        param.value = sensorAttrName;
//                    } else if ( DCUtils.paramNotNull(functionName) ) {
//                        param.type = ServiceCallParam.TYPE_FUNCTION;
//                        param.value = functionName;
//                    } else if ( DCUtils.paramNotNull(paramValue) ) {
//                        param.type = ServiceCallParam.TYPE_FIXED_VALUE;
//                        param.value = paramValue;
//                    }
//                    if ( param.type!=null && param.value!=null ) {
//                        measurementsParams.add(param);
//                    } else {
//                        LOG.warn("UNRECOGNIZED parameter type in application.properties file: '"+paramName+"'  index="+i+"");
//                    }
//                    i++;
//                } else {
//                    hasNext = false;
//                }
//            }

            LOG.debug("Http Client Measurements created");
        }
    }

    /**
     * Performs the call to Meteo service and returns exactly the response String without particular processing or formatting
     * 
     * @return
     * @throws Exception
     */
    private String callRemoteService(CloseableHttpClient client, String serviceUrl, String endpointMethod, List<NameValuePair> endpointParams) throws Exception {
        String url = serviceUrl;
        LOG.debug("Start call to service: " + url);

        HttpRequestBase request = null;
        if ( "GET".equalsIgnoreCase(endpointMethod) ) {
            request = new HttpGet(url);
        } else {
            request = new HttpPost(url);
        }

        // We must add headers for bearer authorization and Selected-team
        //app.auth.token=Bearer AUTH_TOKEN
        //app.auth.selectedTeam=SELECTED_TEAM
        //conn.setRequestProperty("Authorization", authHeader);
        //conn.setRequestProperty("X-Selected-Team", teamHeader);

        String authToken = converter.getAuthToken();
        String selectedTeam = converter.getSelectedTeam();
        if (DCUtils.paramNotNull(authToken)) {
            request.setHeader("Authorization", authToken);
        }
        if (DCUtils.paramNotNull(selectedTeam)) {
            request.setHeader("X-Selected-Team", selectedTeam);
        }
        request.setHeader("Accept", "application/json");

        URIBuilder uriBuilder = new URIBuilder(request.getURI());
        if ( endpointParams!=null && endpointParams.size()>0 ) {
            uriBuilder.addParameters(endpointParams);
        }
        URI uri = uriBuilder.build();
        request.setURI(uri);

        LOG.debug("URI = " + uri);

        CloseableHttpResponse response = client.execute(request);
        StatusLine statusLine = response.getStatusLine();
        if ( response.getStatusLine()==null || statusLine.getStatusCode()!=HttpStatus.SC_OK ) {
            LOG.error("FAILED Call to service "+url+"  Status line is "+statusLine);
        }
        InputStream entity = response.getEntity().getContent();
        StringWriter writer = new StringWriter();
        IOUtils.copy(entity, writer);
        String responseData = writer.toString();
        response.close();

        if (LOG.isDebugEnabled()) {
            LOG.debug("End call responseData = '" + responseData + "'");
        }
        return responseData;
    }

    /**
     * Converts the string returned by the Bikesharing "/cars/{id}" service in a more useful internal representation
     * 
     * @param responseString
     * @return
     * @throws Exception
     */
    public List<BikeDto> convertCarDetailsResponseToInternalDTO(String responseString) throws Exception {
        List<BikeDto> dtoList = new ArrayList<BikeDto>();


        if (LOG.isDebugEnabled()) {
            LOG.debug("dtoList: "+dtoList); 
        }
        return dtoList;
    }

//    /**
//     * Converts the string returned by the Meteo service in a more useful internal representation.
//     * Data regarding the measurements are added to the corresponding BikesharingMoqoDto.
//     * 
//     * Example JSON returned by the service
//     * [
//     *   {
//     *     "SCODE":"89940PG",
//     *     "TYPE":"WT",
//     *     "DESC_D":"Wassertemperatur",
//     *     "DESC_I":"Temperatura acqua",
//     *     "DESC_L":"Temperatura dl’ega",
//     *     "UNIT":"°C",
//     *     "DATE":"2019-02-20T11:10:00CET",
//     *     "VALUE":3.8
//     *   },
//     *   ...
//     * ]
//     * 
//     * @param responseString
//     * @return
//     * @throws Exception
//     */
//    public List<DataTypeDto> convertSensorsResponseToInternalDTO(String responseString, List<BikesharingMoqoDto> stationList) throws Exception {
//
//        List<DataTypeDto> dtoList = new ArrayList<DataTypeDto>();
//
//        //Convert JSON string to External DTO
//        List<SensorDto> dataList = mapper.readValue(responseString, new TypeReference<List<SensorDto>>() {});
//        Set<String> sensorNames = new HashSet<String>();
//
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("dataList: "+dataList);
//        }
//
//        if ( dataList == null ) {
//            return null;
//        }
//
//        //Put Station data in a map for more convenience handling
//        Map<String, BikesharingMoqoDto> stationMap = null;
//        if ( stationList != null ) {
//            stationMap = new HashMap<String, BikesharingMoqoDto>();
//            for (BikesharingMoqoDto meteoDto : stationList) {
//                StationDto stationDto = meteoDto.getStation();
//                String id = stationDto.getId();
//                stationMap.put(id, meteoDto);
//            }
//        }
//
//        //Convert External DTO to Internal DTO
//        for (SensorDto sensorObj : dataList) {
//            DataTypeDto dataTypeDto = converter.convertExternalSensorDtoToDataTypeDto(sensorObj, stationMap);
//            String name = dataTypeDto!=null ? dataTypeDto.getName() : null;
//            if ( name!=null && !sensorNames.contains(name) ) {
//                sensorNames.add(name);
//                dtoList.add(dataTypeDto);
//            }
//        }
//
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("dtoList: "+dtoList); 
//        }
//        return dtoList;
//    }

//    /**
//     * Converts the string returned by the Meteo service in a more useful internal representation
//     * 
//     * Example of JSON retrieved for measures of one station and sensor
//     * 
//     * [
//     *   {
//     *     "DATE":"2019-06-01T13:20:00CEST",
//     *     "VALUE":21.5
//     *   },
//     *   ...
//     * ]
//     * 
//     * @param responseString
//     * @return
//     * @throws Exception
//     */
//    public List<TimeSerieDto> convertMeasurementsResponseToInternalDTO(String responseString) throws Exception {
//
//        //Convert JSON string to External DTO
//        List<TimeSerieDto> dataList = mapper.readValue(responseString, new TypeReference<List<TimeSerieDto>>() {});
//
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("dataList: "+dataList); 
//        }
//        return dataList;
//    }

    /**
     * Fetch data from Meteo service, to be integrated into the Open Data Hub.
     * A loop on all provided stations is performed:
     *    for every station a call to the meteo service is done and at the end 
     *    all data is collected in a single list.
     * Do not prevent exceptions from being thrown to not hide any malfunctioning.
     *
     * @throws Exception
     *             on error propagate exception to caller
     */
    public List<BikeDto> fetchData() throws Exception {
        LOG.info("START.fetchData");
        List<BikeDto> dtoList = new ArrayList<BikeDto>();
        try {
            StringBuffer err = new StringBuffer();

//            //Call service that retrieves the list of stations
//            String responseString = callRemoteService(clientStations, serviceUrlStations, endpointMethodStations, null);
//            dtoList = convertStationsResponseToInternalDTO(responseString);
//            int size = dtoList!=null ? dtoList.size() : 0;
//
//            //fetch also DataTypes to fill sensor data list
//            fetchDataTypes(dtoList);
//
//            //Call service that retrieves the measurements for each station
//            for (int i=0 ; i<size ; i++) {
//                BikesharingMoqoDto meteoDto = dtoList.get(i);
//                String stationId = meteoDto.getStation()!=null ? meteoDto.getStation().getId() : null;
//                LOG.info("fetchData, "+i+" of "+size+": stationId="+stationId);
//                try {
//                    fetchDataByStation(meteoDto);
//                } catch (Exception ex) {
//                    LOG.error("ERROR in fetchData: " + ex.getMessage(), ex);
//                    String stackTrace = DCUtils.extractStackTrace(ex, -1);
//                    err.append("\n***** EXCEPTION RETRIEVING DATA FOR STATION: '"+stationId+"' ******" + stackTrace);
//                }
//            }
            if ( dtoList.size()==0 && err.length()>0 ) {
                throw new RuntimeException("NO DATA FETCHED: "+err);
            }
        } catch (Exception ex) {
            LOG.error("ERROR in fetchData: " + ex.getMessage(), ex);
            throw ex;
        }
        LOG.info("END.fetchData");
        return dtoList;
    }

    /**
     * Fetch anagrafic data from Meteo service for all stations.
     * 
     * @return
     * @throws Exception
     */
    public BikesharingMoqoDto fetchStations() throws Exception {
        LOG.debug("START.fetchStations");
        BikesharingMoqoDto retval = new BikesharingMoqoDto();
        List<BikeDto> dtoList = new ArrayList<BikeDto>();
        retval.setBikeList(dtoList);
        try {
            StringBuffer err = new StringBuffer();

            //Call service that retrieves the list of stations
            //We call the "/cars" service that returns paginated data. pagination info is stored in structure "pagination" that looks like this: 
            //    "pagination": {"total_pages": 6, "current_page": 1, "next_page": 2, "prev_page": null}
            long pageNum = 1;
            boolean lastPage = false;
            while ( !lastPage ) {

                //Fill endpoint params
                List<NameValuePair> endpointParams = new ArrayList<NameValuePair>();
                if ( stationsParams!=null && stationsParams.size()>0 ) {
                    for (ServiceCallParam entry : stationsParams) {
                        String paramName  = entry.name;
                        String paramValue = null;
                        BasicNameValuePair param = null;

                        //Parameters can be of various type
                        if ( ServiceCallParam.TYPE_FIXED_VALUE.equals(entry.type) ) {
                            //If parameter is of type FIXED_VALUE take the value read from env property
                            paramValue = entry.value;
                            if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
                                param = new BasicNameValuePair(paramName, paramValue);
                            }
                        } else if ( ServiceCallParam.TYPE_FUNCTION.equals(entry.type) ) {
                            if ( ServiceCallParam.FUNCTION_NAME_PAGE_NUM.equals(entry.value) ) {
                                //If parameter is of type FUNCTION PAGE_NUM take the value from current page to fetch
                                paramValue = String.valueOf(pageNum);
                                if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
                                    param = new BasicNameValuePair(paramName, paramValue);
                                }
                            }
                        }

                        if ( param != null ) {
                            endpointParams.add(param);
                        }
                    }
                }

                String responseStringCars = callRemoteService(clientStations, serviceUrlStations, endpointMethodStations, endpointParams);

                //Convert to internal representation
                BikesharingMoqoPageDto pageDto = converter.convertCarsResponseToInternalDTO(responseStringCars);
                List<BikeDto> pageList = pageDto.getBikeList();
                PaginationDto pagination = pageDto.getPagination();
                dtoList.addAll(pageList);

                //Call service to get availability for each bike
                for (BikeDto bikeDto : pageList) {
                    String bikeId = bikeDto.getId();
                    String bikeLicense = bikeDto.getLicense();
                    String bikeUrl = serviceUrlMeasurements.replace(ServiceCallParam.FUNCTION_NAME_STATION_ID, bikeId);
                    String responseStringAvail = callRemoteService(clientMeasurements, bikeUrl, endpointMethodMeasurements, null);
                    List<AvailabilityDto> availDtoList = converter.convertAvailabilityResponseToInternalDTO(responseStringAvail);
                    bikeDto.setAvailabilityList(availDtoList);

                    //Evaluate attributes available, until and from for the bike, looking into the Availability slots
                    Boolean bikeAvailable   = false;
                    Date bikeAvailableFrom  = null;
                    Date bikeAvailableUntil = null;
                    Long bikeAvailableDuration = null;
                    long dtNowMillis = System.currentTimeMillis();
                    //Date dtNow = new Date(dtNowMillis);
                    for ( int i=0 ; availDtoList!=null && i<availDtoList.size() ; i++ ) {
                        AvailabilityDto availDto = availDtoList.get(i);
                        Boolean slotAvailable = availDto.getAvailable();
                        Date slotFrom = availDto.getFrom();
                        Date slotUntil = availDto.getUntil();
                        Long slotDuration = availDto.getDuration();
                        long slotFromMillis  = slotFrom!=null  ? slotFrom.getTime()  : 0;
                        long slotUntilMillis = slotUntil!=null ? slotUntil.getTime() : 0;
                        boolean slotFromBeforeNow = slotFrom == null  || slotFromMillis <= dtNowMillis;
                        boolean slotUntilAfterNow = slotUntil == null || slotUntilMillis > dtNowMillis;
                        if ( LOG.isDebugEnabled() ) {
                            LOG.debug("Bike id="+bikeId+"  rn="+bikeLicense+" : "+availDto);
                        }

                        if ( slotFromBeforeNow && slotUntilAfterNow ) {
                            //NOW is BETWEEN current slot ==> take values from it
                            LOG.debug("Bike id="+bikeId+"  rn="+bikeLicense+" SLOT IS BETWEEN NOW: "+availDto);
                            bikeAvailable = slotAvailable;
                            bikeAvailableFrom = null;
                            bikeAvailableUntil = slotUntil;
                            if ( Boolean.TRUE.equals(slotAvailable) ) {
                                bikeAvailableDuration = slotDuration;
                            }
                        } else if ( !slotFromBeforeNow && slotUntilAfterNow ) {
                            //NOW is BEFORE  current slot ==> 
                            LOG.debug("Bike id="+bikeId+"  rn="+bikeLicense+" SLOT IS BEFORE  NOW: "+availDto);
                            if ( Boolean.TRUE.equals(slotAvailable) ) {
                                //Bike will be available in the future, take "from" and "until" but do not change value of available
                                bikeAvailableFrom = slotFrom;
                                bikeAvailableUntil = slotUntil;
                                //bikeAvailableDuration = (slotFromMillis-dtNowMillis) / 1000;
                            } else {
                                //Bike will be unavailable in the future, take "from" and calculate availability duration
                                bikeAvailableUntil = slotFrom;
                                bikeAvailableDuration = (slotFromMillis-dtNowMillis) / 1000;
                            }
                        } else if ( slotFromBeforeNow && !slotUntilAfterNow ) {
                            //NOW is AFTER   current slot, DO NOT CONSIDER IT!!!
                        } else {
                            //Impossible situation!!!
                            LOG.warn("Inconsistent availability slot for bike id="+bikeId+"  rn="+bikeLicense+" : "+availDto);
                        }

                    }

                    bikeDto.setAvailable(bikeAvailable);
                    bikeDto.setAvailableFrom(bikeAvailableFrom);
                    bikeDto.setAvailableUntil(bikeAvailableUntil);
                    bikeDto.setAvailableDuration(bikeAvailableDuration);
                }

                //Exit if this is the last page, otherwise continue loop with next page
                Long nextPage = pagination.getNextPage();
                if ( nextPage != null ) {
                    pageNum = nextPage;
                } else {
                    lastPage = true;
                }

            }

            Map<String, LocationDto> distinctLocations = converter.getDistinctLocations(dtoList);
            retval.setLocationMap(distinctLocations);

            if ( dtoList.size()==0 && err.length()>0 ) {
                throw new RuntimeException("NO DATA FETCHED: "+err);
            }
        } catch (Exception ex) {
            LOG.error("ERROR in fetchData: " + ex.getMessage(), ex);
            throw ex;
        }
        LOG.debug("END.fetchStations");
        return retval;
    }

    /**
     * Fetch anagrafic data from Meteo service for all dataTypes.
     * All available DataTypes are fetched using the sensors service which provides a list of all measures,
     * from the measures only a single distinct instance of each DataType is added to the output list.
     * 
     * @return
     * @throws Exception
     */
    public List<DataTypeDto> fetchDataTypes() throws Exception {
        return fetchDataTypes(null);
    }

    /**
     * Fetch anagrafic data from Meteo service for all dataTypes.
     * All available DataTypes are fetched using the sensors service which provides a list of all measures,
     * in each BikesharingMoqoDto (representing a station), all measurements for that station are recorded.
     * 
     * If stationList is null, only the distinct list of DataTypes is returned.
     * 
     * @return
     * @throws Exception
     */
    public List<DataTypeDto> fetchDataTypes(List<BikeDto> stationList) throws Exception {
        LOG.debug("START.fetchDataTypes");
        List<DataTypeDto> dtoList = new ArrayList<DataTypeDto>();
        try {
            StringBuffer err = new StringBuffer();

//            //Call service that retrieves the list of stations
//            String responseString = callRemoteService(clientSensors, serviceUrlSensors, endpointMethodSensors, null);
//
//            //Convert to internal representation
//            dtoList = convertSensorsResponseToInternalDTO(responseString, stationList);
            if ( dtoList.size()==0 && err.length()>0 ) {
                throw new RuntimeException("NO DATA FETCHED: "+err);
            }
        } catch (Exception ex) {
            LOG.error("ERROR in fetchData: " + ex.getMessage(), ex);
            throw ex;
        }
        LOG.debug("END.fetchDataTypes");
        return dtoList;
    }

//    /**
//     * Fetch measurement data from Meteo service for one specific station.
//     * 
//     * @param cityKey
//     * @return
//     * @throws Exception
//     */
//    public void fetchDataByStation(BikesharingMoqoDto meteoDto) throws Exception {
//        LOG.debug("START.fetchDataByStation("+meteoDto+")");
//
//        if ( meteoDto==null || meteoDto.getStation()==null|| meteoDto.getSensorDataList()==null || meteoDto.getSensorDataList().size()==0 ) {
//            return;
//        }
//
//        StationDto stationDto = meteoDto.getStation();
//        Map<String, DataTypeDto> dataTypeMap = meteoDto.getDataTypeMap();
//        for (SensorDto sensorDto : meteoDto.getSensorDataList()) {
//
//            String stationId  = stationDto.getId();
//            String sensorType = sensorDto.getTYPE();
//
//            try {
//
//                //Fetch last record for sensor and station present in the data hub
//                //As default set value from env param "app.min_date_from"
//                String strDateFrom = null;
//                String strDateTo = null;
//
//                DataTypeDto dataTypeDto = dataTypeMap!=null ? dataTypeMap.get(sensorType) : null;
//                Date lastSavedRecord = null;
//                String strMinDateFrom = converter.getMinDateFrom();
//                if ( strMinDateFrom == null ) {
//                    //If env param is not set, use a fixed default
//                    strMinDateFrom = "201701010800";
//                    LOG.warn("MIN DATE PARAM '"+BikesharingMoqoDataConverter.MIN_DATE_FROM+"' NOT SET, USING DEFAULT VALUE: " + strMinDateFrom);
//                }
//                strDateFrom = strMinDateFrom;
//                try {
//                    lastSavedRecord = pusher.getLastSavedRecordForStationAndDataType(stationDto, dataTypeDto);
//                } catch (Exception ex) {
//                    LOG.warn("ERROR in getLastSavedRecordForStationAndDataType(stationId="+stationId+", dataType="+dataTypeDto+"): " + ex.getMessage());
//                    LOG.warn("USING DEFAULT VALUE: " + strDateFrom);
//                }
//                //If lastSavedRecord is found, compare with minimum and take the greater between the two
//                if ( lastSavedRecord != null ) {
//                    meteoDto.getLastSavedRecordMap().put(sensorType, lastSavedRecord);
//                    String strLastDate = DCUtils.convertDateToString(lastSavedRecord, "yyyyMMddHHmm");
//                    if ( strLastDate.compareTo(strMinDateFrom) > 0 ) {
//                        strDateFrom = strLastDate;
//                    }
//                }
//
//                //Fill endpoint params
//                List<NameValuePair> endpointParams = new ArrayList<NameValuePair>();
//                String paramNameDateFrom = null;
//                String paramNameDateTo   = null;
//                Date dtNow = new Date(System.currentTimeMillis());
//                String strNow = DCUtils.convertDateToString(dtNow, "yyyyMMddHHmm");
//                if ( measurementsParams!=null && measurementsParams.size()>0 ) {
//                    for (ServiceCallParam entry : measurementsParams) {
//                        String paramName  = entry.name;
//                        String paramValue = null;
//                        BasicNameValuePair param = null;
//
//                        //Parameters can be of various type
//                        if ( ServiceCallParam.TYPE_FIXED_VALUE.equals(entry.type) ) {
//                            //If parameter is of type FIXED_VALUE take the value read from env property
//                            paramValue = entry.value;
//                            if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
//                                param = new BasicNameValuePair(paramName, paramValue);
//                            }
//                        } else if ( ServiceCallParam.TYPE_STATION_VALUE.equals(entry.type) ) {
//                            //If parameter is of type STATION_VALUE take the value from the station object (attribute name is read from env property)
//                            String attrName = entry.value;
//                            paramValue = DCUtils.allowNulls(DCUtils.getProperty(attrName, stationDto));
//                            if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
//                                param = new BasicNameValuePair(paramName, paramValue);
//                            }
//                        } else if ( ServiceCallParam.TYPE_SENSOR_VALUE.equals(entry.type) ) {
//                            //If parameter is of type SENSOR_VALUE take the value from the sensor object (attribute name is read from env property)
//                            String attrName = entry.value;
//                            paramValue = DCUtils.allowNulls(DCUtils.getProperty(attrName, sensorDto));
//                            if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
//                                param = new BasicNameValuePair(paramName, paramValue);
//                            }
//                        } else if ( ServiceCallParam.TYPE_FUNCTION.equals(entry.type) ) {
//                            if ( ServiceCallParam.FUNCTION_NAME_CURR_DATE.equals(entry.value) ) {
//                                //If parameter is of type FUNCTION CURR_DATE take the value from current date
//                                paramValue = strNow;
//                                strDateTo = strNow;
//
//                                //Optimization: we get only one day of measurements otherwise fetch af too many data is time consuming
//                                long millisFetchPeriod = converter.getFetchPeriod() * BikesharingMoqoDataConverter.MILLIS_ONE_DAY;
//                                Date tmpDateFrom = DCUtils.convertStringToDate(strDateFrom, "yyyyMMddHHmm");
//                                Date tmpDateTo   = new Date(tmpDateFrom.getTime() + millisFetchPeriod);
//                                String strTmpDateTo = DCUtils.convertDateToString(tmpDateTo, "yyyyMMddHHmm");
//                                if ( strTmpDateTo.compareTo(strDateTo) < 0 ) {
//                                    strDateTo = strTmpDateTo;
//                                    paramValue = strTmpDateTo;
//                                }
//
//                                if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
//                                    param = new BasicNameValuePair(paramName, paramValue);
//                                    paramNameDateTo = paramName;
//                                }
//                            } else if ( ServiceCallParam.FUNCTION_NAME_LAST_DATE.equals(entry.value) ) {
//                                //If parameter is of type FUNCTION LAST_DATE take the value from last record saved in Open Data Hub
//                                paramValue = strDateFrom;
//                                if ( DCUtils.paramNotNull(paramName) && DCUtils.paramNotNull(paramValue) ) {
//                                    param = new BasicNameValuePair(paramName, paramValue);
//                                    paramNameDateFrom = paramName;
//                                }
//                            }
//                        }
//
//                        if ( param != null ) {
//                            endpointParams.add(param);
//                        }
//                    }
//                }
//
//                LOG.debug("Fetch timeseries: endpointParams=" + endpointParams);
//
//                String responseString = callRemoteService(clientMeasurements, serviceUrlMeasurements, endpointMethodMeasurements, endpointParams);
//
//                List<TimeSerieDto> timeSeriesList = convertMeasurementsResponseToInternalDTO(responseString);
//                int size = timeSeriesList!=null ? timeSeriesList.size() : -1;
//
//                //If no data found, fetch data adding one day to the fetched period, until we get some data or dateTo is greater than dateNow
//                boolean isPastPeriod = strDateTo.compareTo(strNow) < 0;
//                while ( size==0 && isPastPeriod ) {
//                    strDateFrom = strDateTo;
//                    Date nextDateFrom = DCUtils.convertStringToDate(strDateFrom, "yyyyMMddHHmm");
//                    Date nextDateTo   = new Date(nextDateFrom.getTime() + BikesharingMoqoDataConverter.MILLIS_ONE_DAY);
//                    strDateTo = DCUtils.convertDateToString(nextDateTo, "yyyyMMddHHmm");
//                    List<NameValuePair> nextEndpointParams = new ArrayList<NameValuePair>();
//                    for (NameValuePair nvp : endpointParams) {
//                        String name = nvp.getName();
//                        if ( name.equals(paramNameDateTo) ) {
//                            nvp = new BasicNameValuePair(name, strDateTo);
//                        } else if ( name.equals(paramNameDateFrom) ) {
//                            nvp = new BasicNameValuePair(name, strDateFrom);
//                        }
//                        nextEndpointParams.add(nvp);
//                    }
//                    endpointParams = nextEndpointParams;
//                    LOG.debug("RETRY Fetch timeseries: nextEndpointParams=" + endpointParams);
//                    responseString = callRemoteService(clientMeasurements, serviceUrlMeasurements, endpointMethodMeasurements, endpointParams);
//
//                    timeSeriesList = convertMeasurementsResponseToInternalDTO(responseString);
//                    size = timeSeriesList!=null ? timeSeriesList.size() : -1;
//
//                    isPastPeriod = strDateTo.compareTo(strNow) < 0;
//                }
//
//                //Store TimeSerieList in DTO
//                if ( size > 0 ) {
//                    Map<String, List<TimeSerieDto>> timeSeriesMap = meteoDto.getTimeSeriesMap();
//                    List<TimeSerieDto> list = timeSeriesMap.get(sensorType);
//                    if ( list == null ) {
//                        list = new ArrayList<TimeSerieDto>(); 
//                        timeSeriesMap.put(sensorType, list);
//                    } else {
//                        LOG.warn("ALREADY PRESENT VALUES:  stationId="+stationId+"  dataType="+dataTypeDto.getName()+"  endpointParams="+endpointParams);
//                    }
//                    //In some cases we have duplicate values, before adding a new value check if it already exists
////                    if ( list != null ) {
////                        list.addAll(timeSeriesList);
////                    } else {
////                        timeSeriesMap.put(sensorType, timeSeriesList);
////                    }
//                    for (TimeSerieDto newDto : timeSeriesList) {
//                        String newDate = newDto.getDATE();
//                        boolean duplicated = false;
//                        for (int i=0 ; !duplicated && i<list.size() ; i++) {
//                            TimeSerieDto oldDto = list.get(i);
//                            String oldDate = oldDto.getDATE();
//                            if ( oldDate.equals(newDate) ) {
//                                duplicated = true;
//                                LOG.warn("DUPLICATED VALUE:  stationId="+stationId+"  dataType="+dataTypeDto.getName()+"  date="+oldDate+"  value="+oldDto.getVALUE()+"  endpointParams="+endpointParams);
//                            }
//                        }
//                        if ( !duplicated ) {
//                            list.add(newDto);
//                        }
//                    }
//                }
//
//                LOG.debug("Data fetched for station="+stationId+", sensor="+sensorType+": "+size);
//            } catch (Exception ex) {
//                LOG.error("ERROR in fetchData: " + ex.getMessage(), ex);
//                throw ex;
//            }
//
//        }
//
//        LOG.debug("END.fetchDataByStation("+stationDto+")");
//    }

}
