package io.github.mainalisandeep.cvgen.enums;

/**
 * How finished a CV is, as declared by its owner.
 * <p>
 * Editorial state only: nothing in the API branches on it, and it never gates a read.
 * It exists so the list screen can separate work in progress from CVs ready to send.
 */
public enum CvStatus {

    /** Still being written. The default for a newly created CV. */
    DRAFT,

    /** The owner considers it finished and sendable. */
    READY
}
