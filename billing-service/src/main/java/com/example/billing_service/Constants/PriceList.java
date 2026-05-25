package com.example.billing_service.Constants;

import java.util.Map;

//THE PRICES ARE IN NIS
public final class PriceList {
    private PriceList() {
    }

    public static final Map<String, Double> PRICES = Map.of(
            "CHAT_GPT", 1.0,
            "IMAGE_GEN", 2.0,
            "TRANSLATE", 0.5
    );
}
