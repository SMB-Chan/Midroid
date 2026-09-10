package jp.example.budsswitch.model

data class BondedDevice(
    val name: String,
    val address: String
) {
    override fun toString(): String = "$name  [$address]"
}
