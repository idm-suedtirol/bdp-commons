/*
 *  A22 Event
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-05-16  1.0 - chris@1006.org
 */

package it.bz.noi.a22.events;

import java.text.SimpleDateFormat;
import java.util.Date;

public class A22Event {

                                        // rif.: Softech - 2017-002 Autostrada del Brennero S.p.A.
                                        // Manutenzione ordinaria e straordinaria sistema di supervisione CAU - anno 2017 - N. doc: 2017-0046 Revisione: 9

    private Long id;                    // Identificativo univoco dell’evento
    private Long idtipoevento;          // Identificativo univoco della tipologia di evento. Vedi tabella TIPOLOGIE EVENTO 5.9.4
    private Long idsottotipoevento;     // Identificativo del sottotipo evento Vedi tabella SOTTOTIPI EVENTO 5.9.5
    private String autostrada;          // Sigla dell’autostrada dove si è verificato l’evento
    private Long iddirezione;           // Identificativo della direzione dove si è verificato l’evento. Vedi tabella DIREZIONI 5.9.1
    private Long idcorsia;              // Identificativo della corsia dove si è verificato l’evento Vedi tabella CORSIE 5.9.2
    private Long data_inizio;           // Data di inizio dell’evento, in formato UTC
    private Long data_fine;             // Data di fine dell’evento, in formato UTC.  Se l’evento è attivo il campo viene valorizzato a null
    private Boolean fascia_oraria;      // Indica se le date di inizio e fine dell’evento devono essere considerate o meno in fascia oraria 0 = no fascia oraria. L’evento dura dalla data/ora di inizio alla data/ora fine 1 = si fascia oraria L’evento dura dalle ore inizio alle ore fine, dal giorno inizio al giorno fine
    private Long metro_inizio;          // Progressiva chilometrica di inizio evento, espressa in metri
    private Long metro_fine;            // Progressiva chilometrica di fine evento, espressa in metri Se l’evento è puntuale il valore di km_inizio e km_fine coincideranno.
    private Double lat_inizio;          // Coordinate latitudinali del punto di inizio evento, in formato WGS84
    private Double lon_inizio;          // Coordinate longitudinali del punto di inizio evento, in formato WGS84
    private Double lat_fine;            // Coordinate latitudinali del punto di fine evento, in formato WGS84. Se l’evento è puntuale il valore di lat_inizio e lat_fine coincideranno.
    private Double lon_fine;            // Coordinate longitudinali del punto di fine evento, in formato WGS84. Se l’evento è puntuale il valore di lon_inizio e lon_fine coincideranno.

    @Override
    public String toString() {
        return "A22Event{" +
                "id=" + id +
                ", idtipoevento=" + idtipoevento +
                ", idsottotipoevento=" + idsottotipoevento +
                ", autostrada='" + autostrada + '\'' +
                ", iddirezione=" + iddirezione +
                ", idcorsia=" + idcorsia +
                ", data_inizio=" + getInizioString() +
                ", data_fine=" + getFineString() +
                ", fascia_oraria=" + fascia_oraria +
                ", metro_inizio=" + metro_inizio +
                ", metro_fine=" + metro_fine +
                ", lat_inizio=" + lat_inizio +
                ", lon_inizio=" + lon_inizio +
                ", lat_fine=" + lat_fine +
                ", lon_fine=" + lon_fine +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) { this.id = id; }

    public Long getIdtipoevento() {
        return idtipoevento;
    }

    public void setIdtipoevento(Long idtipoevento) {
        this.idtipoevento = idtipoevento;
    }

    public Long getIdsottotipoevento() {
        return idsottotipoevento;
    }

    public void setIdsottotipoevento(Long idsottotipoevento) {
        this.idsottotipoevento = idsottotipoevento;
    }

    public String getAutostrada() {
        return autostrada;
    }

    public void setAutostrada(String autostrada) {
        this.autostrada = autostrada;
    }

    public Long getIddirezione() {
        return iddirezione;
    }

    public void setIddirezione(Long iddirezione) {
        this.iddirezione = iddirezione;
    }

    public Long getIdcorsia() {
        return idcorsia;
    }

    public void setIdcorsia(Long idcorsia) {
        this.idcorsia = idcorsia;
    }

    public Long getData_inizio() {
        return data_inizio;
    }

    public void setData_inizio(Long data_inizio) {
        this.data_inizio = data_inizio;
    }

    public Long getData_fine() {
        return data_fine;
    }

    public void setData_fine(Long data_fine) {
        this.data_fine = data_fine;
    }

    public Boolean getFascia_oraria() {
        return fascia_oraria;
    }

    public void setFascia_oraria(Boolean fascia_oraria) {
        this.fascia_oraria = fascia_oraria;
    }

    public Long getMetro_inizio() {
        return metro_inizio;
    }

    public void setMetro_inizio(Long metro_inizio) {
        this.metro_inizio = metro_inizio;
    }

    public Long getMetro_fine() {
        return metro_fine;
    }

    public void setMetro_fine(Long metro_fine) {
        this.metro_fine = metro_fine;
    }

    public Double getLat_inizio() {
        return lat_inizio;
    }

    public void setLat_inizio(Double lat_inizio) {
        this.lat_inizio = lat_inizio;
    }

    public Double getLon_inizio() {
        return lon_inizio;
    }

    public void setLon_inizio(Double lon_inizio) {
        this.lon_inizio = lon_inizio;
    }

    public Double getLat_fine() {
        return lat_fine;
    }

    public void setLat_fine(Double lat_fine) {
        this.lat_fine = lat_fine;
    }

    public Double getLon_fine() {
        return lon_fine;
    }

    public void setLon_fine(Double lon_fine) {
        this.lon_fine = lon_fine;
    }

    public String getInizioString() {
        return data_inizio != null? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data_inizio * 1000)) : "null";
    }

    public String getFineString() {
        return data_fine != null? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data_fine * 1000)) : "null";
    }

}
