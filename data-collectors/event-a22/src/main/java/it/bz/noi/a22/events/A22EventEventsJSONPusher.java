/*
 *  A22 Parking Json Pusher
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-06-04  1.0 - thomas.nocker@catch-solve.tech
 */

package it.bz.noi.a22.events;

import it.bz.idm.bdp.dto.DataMapDto;
import it.bz.idm.bdp.dto.ProvenanceDto;
import it.bz.idm.bdp.dto.RecordDtoImpl;
import it.bz.idm.bdp.json.NonBlockingJSONPusher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class A22EventEventsJSONPusher extends NonBlockingJSONPusher
{

	private String stationtype;
	private String origin;

	@Autowired
	private Environment env;

	public <T> DataMapDto<RecordDtoImpl> mapData(T arg0)
	{
		throw new IllegalStateException("it is used by who?");
	}

	@PostConstruct
	public void init() {
		A22Properties prop = new A22Properties("a22events.properties");

		stationtype = prop.getProperty("stationtype");
		origin = prop.getProperty("origin");
		super.init();
	}

	@Override
	public String initIntegreenTypology()
	{
		return stationtype;
	}

	@Override
	public ProvenanceDto defineProvenance() {
		return new ProvenanceDto(null, env.getProperty("provenance_name"), env.getProperty("provenance_version"),  origin);
	}
}
