# Ble-mesh-app

Offline text chat for Android. No internet, no SIM. Uses Bluetooth LE only.
Phones relay each other's messages, so two phones out of range can still talk through phones in between.

## Design

- Android only. Kotlin. Min SDK 26 (extended advertising needs Android 8+ and hardware support).
- Short text messages only (cap about 160 characters).
- BLE advertising and scanning, not GATT connections. No connection-count limit.
- Flood mesh: every phone with the app also relays.
- End-to-end encryption (planned). Relays cannot read messages.
- Optional ESP32 node (planned): fixed, always-on, store-and-forward relay.

## Packet (v1 draft)

| Field       | Size     | Notes                              |
|-------------|----------|------------------------------------|
| Message ID  | 8 bytes  | random, used for dedup             |
| TTL         | 1 byte   | decremented on each relay          |
| Destination | 4 bytes  | short hash of recipient public key |
| Payload     | variable | encrypted text                     |

## Relay rules

1. Hear a packet. If its ID is in the seen-list, drop it.
2. Otherwise store the ID and decrement TTL. If TTL is 0, stop.
3. Wait a random delay (50-500 ms). If 2-3 other phones relayed the same ID meanwhile, cancel.
4. Rebroadcast.
5. If destination matches me, decrypt and show.

## Build order

1. [ ] Two phones: send a short text via BLE advertising.
2. [ ] Relay logic with a third phone (seen-list, TTL, random delay).
3. [ ] Encryption.
4. [ ] ESP32 store-and-forward node.
5. [ ] Test in a real open area.

## Known limits

- Phone density decides coverage. Sparse areas fail.
- Background BLE gets killed on some vendors. Needs a foreground service and battery optimization off.
- Extended advertising must be supported on both phones. Check `BluetoothAdapter.isLeExtendedAdvertisingSupported()`.
- Local area only. Not a replacement for the internet.
