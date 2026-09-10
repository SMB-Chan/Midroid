package jp.example.budsswitch.privileged;

interface ISscPrivilegedProbeService {
    String ping();
    String probe(String address);
    void destroy();
}
