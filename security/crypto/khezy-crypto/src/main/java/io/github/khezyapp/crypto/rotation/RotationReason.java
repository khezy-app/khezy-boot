package io.github.khezyapp.crypto.rotation;

/**
 * Why a rotation was requested.
 */
public enum RotationReason {

    SCHEDULED,
    MANUAL,
    USAGE_LIMIT,
    COMPROMISE,
    ALGORITHM_CHANGE
}
