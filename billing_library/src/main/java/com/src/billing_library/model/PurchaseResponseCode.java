package com.src.billing_library.model;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface PurchaseResponseCode {
    int OK = 0;
    int USER_CANCELED = 1;
    int ERROR = 6;
    int ITEM_ALREADY_OWNED = 7;
}