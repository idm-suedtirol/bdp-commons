package it.bz.idm.bdp.dcbikesharingmoqo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import it.bz.idm.bdp.dcbikesharingmoqo.dto.BikeDto;
import it.bz.idm.bdp.dcbikesharingmoqo.dto.TimeSerieDto;
import it.bz.idm.bdp.dto.DataMapDto;
import it.bz.idm.bdp.dto.DataTypeDto;
import it.bz.idm.bdp.dto.ProvenanceDto;
import it.bz.idm.bdp.dto.RecordDtoImpl;
import it.bz.idm.bdp.dto.SimpleRecordDto;
import it.bz.idm.bdp.dto.StationDto;
import it.bz.idm.bdp.dto.StationList;
import it.bz.idm.bdp.json.JSONPusher;

@Service
public class BikesharingMoqoDataPusher extends JSONPusher {

    private static final Logger LOG = LogManager.getLogger(BikesharingMoqoDataPusher.class.getName());

    @Autowired
    private Environment env;

    @Autowired
    private BikesharingMoqoDataConverter converter;

    public BikesharingMoqoDataPusher() {
        LOG.debug("START.constructor.");
        LOG.debug("END.constructor.");
    }

	@PostConstruct
    private void initMeteo() {
        LOG.debug("START.init.");
//        //Ensure the JSON converter is used instead of the XML converter (otherwise we get an HTTP 415 error)
//        //this must be done because we added dependencies to com.fasterxml.jackson.dataformat.xml.XmlMapper to read data from IIT web service!
//        List<HttpMessageConverter<?>> messageConverters = restTemplate.getMessageConverters();
//        List<HttpMessageConverter<?>> newMessageConverters = new ArrayList<HttpMessageConverter<?>>();
//        for (HttpMessageConverter<?> messageConverter : messageConverters) {
//            if ( messageConverter instanceof MappingJackson2XmlHttpMessageConverter || messageConverter instanceof Jaxb2RootElementHttpMessageConverter ) {
//                LOG.debug("REMOVE   converter: " + messageConverter.getClass().getName());
//            } else {
//                LOG.debug("PRESERVE converter: " + messageConverter.getClass().getName());
//                newMessageConverters.add(messageConverter);
//            }
//        }
//        restTemplate.setMessageConverters(newMessageConverters);
        LOG.debug("END.init.");
    }

    @Override
    public String initIntegreenTypology() {
        String stationType = "Meteostation";
        return stationType;
    }

    @Override
    public <T> DataMapDto<RecordDtoImpl> mapData(T rawData) {
        LOG.debug("START.mapData");

        @SuppressWarnings("unchecked")
        List<BikeDto> data = (List<BikeDto>) rawData;
        if (data == null) {
            return null;
        }

        DataMapDto<RecordDtoImpl> map = new DataMapDto<>();
        Integer period = converter.getPeriod();

        int countStations = 0;
        int countBranches = 0;
        int countMeasures = 0;

        for (BikeDto dto : data) {

            StationDto stationDto = converter.convertBikeDtoToStationDto(dto);

            String stationId = stationDto.getId();
//            Map<String, List<TimeSerieDto>> measurementsByType = dto.getTimeSeriesMap();
//            Map<String, DataTypeDto> dataTypeMap = dto.getDataTypeMap();
            Map<String, List<TimeSerieDto>> measurementsByType = null;
            Map<String, DataTypeDto> dataTypeMap = null;
            Set<String> dataTypeNames = dataTypeMap!=null ? dataTypeMap.keySet() : new HashSet<>();

            //Check if we already treated this station (branch), if not found create the map and the list of records
            DataMapDto<RecordDtoImpl> recordsByStation = map.getBranch().get(stationId);
            if ( recordsByStation == null ) {
                recordsByStation = new DataMapDto<RecordDtoImpl>();
                map.getBranch().put(stationId, recordsByStation);
                List<RecordDtoImpl> dataList = new ArrayList<RecordDtoImpl>();
                recordsByStation.setData(dataList);
                countStations++;
            }

            for (String dataTypeName : dataTypeNames) {

                List<TimeSerieDto> measurements = measurementsByType.get(dataTypeName);
                DataTypeDto dataType = dataTypeMap.get(dataTypeName);

                for (TimeSerieDto measurementDto : measurements) {

                    //Get values from MeasutementDto and convert to SimpleRecordDto
                    String typeName = dataType.getName();
                    String strDate = measurementDto.getDATE();
                    Date date = DCUtils.convertStringTimezoneToDate(strDate);
                    long timestamp = date!=null ? date.getTime() : -1;
                    Object value = measurementDto.getVALUE();

                    if ( timestamp>0 && date!=null && value!=null ) {
                        SimpleRecordDto record = new SimpleRecordDto();
                        record.setValue(value);
                        record.setTimestamp(timestamp);
                        record.setPeriod(period);

                        //Check if we already treated this type (branch), if not found create the map and the list of records
                        DataMapDto<RecordDtoImpl> recordsByType = recordsByStation.getBranch().get(typeName);
                        if ( recordsByType == null ) {
                            recordsByType = new DataMapDto<RecordDtoImpl>();
                            recordsByStation.getBranch().put(typeName, recordsByType);
                            List<RecordDtoImpl> dataList = new ArrayList<RecordDtoImpl>();
                            recordsByType.setData(dataList);
                            countBranches++;
                        }

                        //Add the measure in the list
                        List<RecordDtoImpl> records = recordsByType.getData();
                        records.add(record);
                        countMeasures++;
                        LOG.debug("ADD  MEASURE:  id="+stationDto.getId()+", typeName="+typeName+"  value="+value);
                    } else {
                        LOG.warn( "SKIP MEASURE:  id="+stationDto.getId()+", typeName="+typeName+"  value="+value+"  timestamp="+timestamp+"  date="+date+"  strDate="+strDate);
                    }

                }

            }

        }

        LOG.debug("countStations="+countStations+"  countBranches="+countBranches+", countMeasures="+countMeasures);
        LOG.debug("map: "+map);
        LOG.debug("END.mapData");
        return map;
    }

    public <T> DataMapDto<RecordDtoImpl> mapSingleStationData2Bdp(BikeDto data) {
        LOG.debug("START.mapSingleStationData2Bdp");
        if (data == null) {
            return null;
        }

        List<BikeDto> list = new ArrayList<BikeDto>();
        list.add(data);
        DataMapDto<RecordDtoImpl> map = mapData(list);

        LOG.debug("END.mapSingleStationData2Bdp");
        return map;
    }

    public StationList mapStations2Bdp(List<BikeDto> data) {
        LOG.debug("START.mapStations2Bdp");
        if (data == null) {
            return null;
        }

        int countStations = 0;
        StationList stations = new StationList();
        for (BikeDto dto : data) {
            StationDto stationDto = converter.convertBikeDtoToStationDto(dto);

            stations.add(stationDto);
            countStations++;
            LOG.debug("ADD  STATION:  id="+stationDto.getId());

        }
        LOG.debug("countStations="+countStations+"  stations: "+stations);
        LOG.debug("END.mapStations2Bdp");
        return stations;
    }

    public Date getLastSavedRecordForStationAndDataType(StationDto station, DataTypeDto dataType) {
        LOG.debug("START.getLastSavedRecordForStation");
        if (station==null || dataType==null) {
            return null;
        }

        Date lastSavedRecord = null;
        Integer period = converter.getPeriod();
        String stationCode = station.getId();
        String typeName = dataType.getName();
        Object dateOfLastRecord = super.getDateOfLastRecord(stationCode, typeName, null);
        if ( dateOfLastRecord instanceof Date ) {
            lastSavedRecord = (Date) dateOfLastRecord;
        }

        LOG.debug("stationCode="+stationCode+"  typeName="+typeName+"  lastSavedRecord="+lastSavedRecord);
        LOG.debug("END.getLastSavedRecordForStation");
        return lastSavedRecord;
    }

    @Override
    public String toString() {
        String str1 = "http://" + config.getString(HOST_KEY) + ":" + config.getString(PORT_KEY) + config.getString("json_endpoint");
        String str2 =
                "integreenTypology=" + this.integreenTypology   + "  " +
                "DEFAULT_HOST="      + DEFAULT_HOST     + "  " +
                "DEFAULT_PORT="      + DEFAULT_PORT     + "  " +
                "DEFAULT_ENDPOINT="  + DEFAULT_ENDPOINT + "  " +
                "";
        return str2 + " ---> " + str1;
    }

	@Override
	public ProvenanceDto defineProvenance() {
		return new ProvenanceDto(null,env.getProperty("provenance.name"), env.getProperty("provenance.version"),  env.getProperty("app.origin"));
	}
}