package jp.example.budsswitch.privileged;

interface ISscWireCaptureService {
    String ping();
    String getSnoopStatus();
    String runMarkedToggleExperiment(String address);
    void destroy();
}
