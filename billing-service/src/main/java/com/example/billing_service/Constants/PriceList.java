package com.example.billing_service.Constants;

import java.math.BigDecimal;
import java.util.Map;

/** Prices in NIS. BigDecimal, not double: binary floating point can't represent 0.10 exactly. */
public final class PriceList {
    private PriceList() {
    }

    public static final Map<String, BigDecimal> PRICES = Map.of(
            "RAG_QUERY", new BigDecimal("1.00"),
            "CHAT_GPT", new BigDecimal("1.00"),
            "IMAGE_GEN", new BigDecimal("2.00"),
            "TRANSLATE", new BigDecimal("0.50")
    );
}
