package com.vastufirst.shared.update

/**
 * The public half of the pair that signs everything the Control Room publishes.
 *
 * ⭐ WHY IT IS A CONSTANT AND NOT A FILE. This is what makes a downloaded rule set trustworthy, so
 * it has to be inside the app, not beside it. A constant is compiled into the build and travels
 * with it; a resource file is one packaging mistake away from being missing, and a missing key
 * means every phone silently refuses every rule set we ever publish — a failure with no symptom
 * except "nobody ever seems to get the update".
 *
 * ⚠ It is the PUBLIC half. It can only check a signature, never make one, so there is nothing
 * secret about it and nothing is lost by it being readable in the app. The private half lives only
 * as a Cloudflare secret on the Control Room and is in no repository.
 *
 * ⚠ RSA, not Ed25519. Android can only verify Ed25519 from version 13 and this app runs from
 * version 8, so every older phone would have thrown away every publish, silently, for ever. The
 * reasoning is written out in full in [RulesetUpdate.verify]. Do not "upgrade" it.
 *
 * ⚠ Changing this key makes the app refuse everything already published. If it ever has to change,
 * the new key ships FIRST, in a release everybody installs, and only then does the Control Room
 * start signing with the new private half.
 *
 * A test pins this against the fixture the signature tests use, so the two can never drift apart.
 */
const val UPDATE_PUBLIC_KEY_SPKI_B64: String =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvabmjSpDUJkp8Qaaa4uWZLG44908PaTFAiTRMFBtrVEl" +
        "ybdIyzFb/h0Xlp34Dq70hWH8tknJPRu5msb9YBOklm0wcbrgdYab3QYjWMQ9DJcnyd9JGSHsjyR95v9v1YVBQi" +
        "nuDecTfd8PO9Wc+W9zUlnpas3udDJEVmawXpKgpJsL8YCfpkXoVMpE2j+g9S/eF5bQ2u5RO12u7Pe1pYYMZau4" +
        "z4zlHMAvCJWQ25E4WMKD3qEdIfkJ87TL5FnT+3+NS1WN0Bp2SeOrCDzhFM2wY4+rEpH067aO6OrZSQrnceEaL0" +
        "Oy1RYrJQJEcU6L2ReWrDl2BlYWSknk+2ht+RWhpwIDAQAB"

/** Where a phone asks what the latest published rules and prices are. */
const val UPDATE_URL: String = "https://admin.vastufirst.com/rules/latest"
