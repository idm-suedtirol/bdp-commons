/*
 *  A22 BrennerLEC Event
 *
 *  (C) 2021 NOI Techpark Südtirol / Alto Adige
 *
 *  changelog:
 *  2021-05-16  1.0 - chris@1006.org
 */

package it.bz.noi.a22.events;

public class A22BrennerLECEvent {

                                        // rif.: Softech - 2017-002 Autostrada del Brennero S.p.A.
                                        // Manutenzione ordinaria e straordinaria sistema di supervisione CAU - anno 2017 - N. doc: 2017-0046 Revisione: 9

    private String idtratta;            // Identificativo univoco della tratta
    private Long limite;                // Limite di velocità impostato per la tratta (espresso in km/h)
    private Long dataattuazione;        // Data di attuazione del limite di velocità. A null se non presente.

    @Override
    public String toString() {
        return "A22BrennerLECEvent{" +
                "idtratta='" + idtratta + '\'' +
                ", limite=" + limite +
                ", dataattuazione=" + dataattuazione +
                '}';
    }

    public String getIdtratta() {
        return idtratta;
    }

    public void setIdtratta(String idtratta) {
        this.idtratta = idtratta;
    }

    public Long getLimite() {
        return limite;
    }

    public void setLimite(Long limite) {
        this.limite = limite;
    }

    public Long getDataattuazione() {
        return dataattuazione;
    }

    public void setDataattuazione(Long dataattuazione) {
        this.dataattuazione = dataattuazione;
    }
}
