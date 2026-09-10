package jp.example.budsswitch.privileged;

interface ISscWireCaptureService {
    String ping();
    String getSnoopStatus();
    String restartBluetoothForSnoop();
    String runMarkedToggleExperiment(String address);
    String generateBugreport(String prefix);
    void destroy();
}
