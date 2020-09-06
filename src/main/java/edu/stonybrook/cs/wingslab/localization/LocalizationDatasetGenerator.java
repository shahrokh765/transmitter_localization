package edu.stonybrook.cs.wingslab.localization;

import edu.stonybrook.cs.wingslab.commons.*;

/**This class calculates sensor received power based on the set of sensors and transmitters*/
public class LocalizationDatasetGenerator {
    private SpectrumSensor[] sss;   // list of sensors
    private Shape shape;            // shape of the field
    private TX[] txs;               // list of transmitters
    private PropagationModel propagationModel;    // propagation model
    private int cellSize;           // cell size of the field
    private boolean sssLocation;    // if sensors location need to be written

    /**LocalizationDatasetGenerator constructor
     * @param txs array of TX
     * @param sss array of SpectrumSensor
     * @param propagationModel PropagationModel
     * @param shape Shape of field
     * @param cellSize cell size in square
     * @param sssLocation if sensors' location be written*/
    public LocalizationDatasetGenerator(TX[] txs, SpectrumSensor[] sss, Shape shape,
                                        PropagationModel propagationModel, int cellSize, boolean sssLocation){
        super();
        this.sss = sss;
        this.txs = txs;
        this.shape = shape;
        this.propagationModel = propagationModel;
        this.cellSize = cellSize;
        this.sssLocation = sssLocation;
        computeSensorReceivedPower();
    }

    /**LocalizationDatasetGenerator constructor
     * @param txs array of TX
     * @param sss array of SpectrumSensor
     * @param propagationModel PropagationModel
     * @param shape Shape of field
     * @param cellSize cell size in square*/
    public LocalizationDatasetGenerator(TX[] txs, SpectrumSensor[] sss, Shape shape,
                                        PropagationModel propagationModel, int cellSize){
        this(txs, sss, shape, propagationModel, cellSize, false);
    }

    private void computeSensorReceivedPower(){
        if (this.sss == null) // if there is no sensors
            return;
        for (SpectrumSensor spectrumSensor : this.sss)
            spectrumSensor.getRx().setReceived_power(Double.NEGATIVE_INFINITY); // resetting for new computations
        for (SpectrumSensor spectrumSensor : this.sss)
            for (TX tx : this.txs){
                spectrumSensor.getRx().setReceived_power(powerWithPathLoss(tx,
                        spectrumSensor.getRx()));
            }
    }

    // this method power value of receiver after transmitter effect. (return = rx_power + tx_power - path_loss)
    private double powerWithPathLoss(TX tx, RX rx){
        if (tx.getPower() == Double.NEGATIVE_INFINITY)
            return rx.getReceived_power();
        double loss = this.propagationModel.pathLoss(tx.getElement().mul(this.cellSize),
                rx.getElement().mul(this.cellSize));
        return WirelessTools.getDB(WirelessTools.getDecimal(tx.getPower() - loss) +
                WirelessTools.getDecimal(rx.getReceived_power()));
    }

    private String txsInfo(){
        StringBuilder puInformation = new StringBuilder(""); // better to use StringBuilder for concatenation
        for (int txId = 0; txId < this.txs.length - 1; txId++) {
            TX tx = this.txs[txId];
            puInformation.append(tx.getElement().getLocation().getCartesian().getX())
                    .append(",").append(tx.getElement().getLocation().getCartesian().getY())
                    .append(",").append(String.format("%.3f", tx.getPower())).append(",");
        }
        //append the last tx
        TX tx  = this.txs[this.txs.length - 1];
        puInformation.append(tx.getElement().getLocation().getCartesian().getX())
                .append(",").append(tx.getElement().getLocation().getCartesian().getY())
                .append(",").append(String.format("%.3f", tx.getPower()));
        return String.format("%1$d,%2$s", this.txs.length, puInformation);
    }

    private String sssInfo(){
        StringBuilder sssInformation = new StringBuilder("");
        for (int ssId = 0; ssId < this.sss.length - 1; ssId++){
            SpectrumSensor ss = this.sss[ssId];
            if (sssLocation)
                sssInformation.append(ss.getRx().getElement().getLocation().getCartesian().getX())
                        .append(",").append(ss.getRx().getElement().getLocation().getCartesian().getY())
                        .append(",");
            sssInformation.append(String.format("%.3f",ss.getRx().getReceived_power())).append(",");
        }
        SpectrumSensor ss = this.sss[this.sss.length - 1];
        if (sssLocation)
            sssInformation.append(ss.getRx().getElement().getLocation().getCartesian().getX())
                    .append(",").append(ss.getRx().getElement().getLocation().getCartesian().getY())
                    .append(",");
        sssInformation.append(String.format("%.3f",ss.getRx().getReceived_power()));
        return sssInformation.toString();
    }

    @Override
    public String toString(){
        return String.format("%s,%s", sssInfo(), txsInfo());
    }
}
