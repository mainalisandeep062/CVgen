package io.github.mainalisandeep.cvgen.enums;

/**
 * What a stored blob is, for filtering and upload validation.
 * <p>
 * Metadata only: no read path branches on it, and it never decides where the bytes live.
 */
public enum FileType {
    PROFILE_PICTURE,
    CV_PDF
}
