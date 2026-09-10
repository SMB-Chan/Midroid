# SSC / SSC-UHQ analysis plan

BudsSwitch v0.2.4 adds on-device A2DP codec diagnostics for the selected Bluetooth device.

The first target is to compare the same Galaxy Buds3 Pro while connected to:

1. Samsung Galaxy S25 (expected SSC / SSC-UHQ capable)
2. Motorola edge 40 neo (expected SBC/AAC without SSC)

The app queries the hidden `BluetoothA2dp.getCodecStatus(BluetoothDevice)` API from the
Shizuku UserService and records:

- current codec configuration
- local codec capabilities
- selectable capabilities shared by phone + earbuds
- sample rate / bit depth / channel mode
- codecSpecific1..4 values

This distinguishes two important cases:

- **SSC advertised by Buds but unsupported locally**: Samsung-specific codec appears only on the
  Galaxy side or as a vendor/extended codec capability, while Motorola lacks a matching local
  codec implementation.
- **SSC capability gated by Samsung device**: the Buds do not expose the same vendor codec
  capability to Motorola at all. HCI snoop comparison is then required to identify the gating
  handshake.

Known public facts to validate against captures:

- Samsung SSC UHQ on Galaxy Buds3 Pro supports up to 24-bit / 96 kHz on supported Galaxy phones.
- Historical Samsung Scalable Codec is associated with Samsung Bluetooth SIG company/vendor ID
  0x0075 and commonly observed vendor codec ID 0x0103. This identifier should not be assumed to
  equal SSC-UHQ until confirmed from Galaxy S25 captures.

For decisive identification, enable Bluetooth HCI snoop logging on both phones and capture one
fresh Buds3 Pro connection from each. Search AVDTP Media Codec capabilities for Samsung vendor
specific fields, then correlate the raw bytes with the codec status reported by BudsSwitch.
