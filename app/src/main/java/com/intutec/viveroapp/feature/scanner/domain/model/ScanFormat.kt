package com.intutec.viveroapp.feature.scanner.domain.model

enum class ScanFormat(val label: String) {
    QR("QR"),
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    CODE_128("Code 128"),
    MANUAL("Manual"),
}
