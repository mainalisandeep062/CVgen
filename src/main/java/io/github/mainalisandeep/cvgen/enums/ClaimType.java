package io.github.mainalisandeep.cvgen.enums;

public enum ClaimType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    ClaimType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClaimType fromValue(String value) {
        for (ClaimType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown token type: " + value);
    }
}