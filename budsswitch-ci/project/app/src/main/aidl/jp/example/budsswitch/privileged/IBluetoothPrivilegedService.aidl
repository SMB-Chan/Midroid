package jp.example.budsswitch.privileged;

interface IBluetoothPrivilegedService {
    String ping();
    String connectDevice(String address);
    String disconnectDevice(String address);
    String connectionSummary(String address);
    void destroy();
}
