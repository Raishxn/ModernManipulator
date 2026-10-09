package com.raishxn.modern_manipulator.common.uplink;

public enum UplinkStatus {

    OK,
    NO_PLASMA,
    AE_OFFLINE,
    NO_HATCH;

    public String toUnlocalizedString() {
        return switch (this) {
            case OK -> "mm.uplink.status.ok";
            case NO_PLASMA -> "mm.uplink.status.no_plasma";
            case AE_OFFLINE -> "mm.uplink.status.ae_offline";
            case NO_HATCH -> "mm.uplink.status.no_hatch";
        };
    }
}
