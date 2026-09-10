package jp.example.budsswitch.privileged;

interface IMotorolaSscInjectorService {
    String ping();
    String diagnose(String address);
    String tryActivate(String address, boolean uhq);
    void destroy();
}
