package com.hardbacknutter.tinyzxingwrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultMetadataType;

import java.util.Collection;
import java.util.Objects;

/**
 * User friendly API to the low-level input Intent
 * to create the input options for {@link ScanContract}.
 */
@SuppressWarnings("unused")
public class ScanOptions
        extends ScanIntent {

    public ScanOptions() {
    }

    /**
     * Set a prompt to display on the capture screen.
     * <p>
     * The default capture activity will display this instead of the predefined message.
     * Custom capture-activity are free to ignore this.
     *
     * @param prompt the prompt to display
     */
    @NonNull
    public final ScanOptions setPrompt(@Nullable final String prompt) {
        intent.putExtra(ToolOptionKey.PROMPT_MESSAGE,
                Objects.requireNonNullElse(prompt, ""));
        return this;
    }

    /**
     * Enable the torch.
     *
     * @param enabled {@code true} to enable the torch
     *
     * @return this
     */
    @NonNull
    public ScanOptions setTorchEnabled(final boolean enabled) {
        intent.putExtra(OptionKey.TORCH_ENABLED, enabled);
        return this;
    }

    /**
     * Select a specific camera - i.e. with the lens facing the given direction.
     * Note this is <strong>not</strong> a camera id!
     *
     * @param lensFacing either {@link CameraSelector#LENS_FACING_FRONT}
     *                   or {@link CameraSelector#LENS_FACING_BACK}
     *
     * @return this
     */
    public ScanOptions setUseCameraWithLensFacing(final int lensFacing) {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT
                || lensFacing == CameraSelector.LENS_FACING_BACK) {
            intent.putExtra(OptionKey.CAMERA_LENS_FACING, lensFacing);
        }
        return this;
    }

    /**
     * Enable a (hard) timer to finish/cancel the scan on the given timeout.
     * <p>
     * The returned {@code resultCode} will be Activity.RESULT_CANCELED.
     * The returned {@code intent} will contain the
     * key {@link ScanResult.Failure#REASON} with
     * value {@link ScanResult.Failure#REASON_TIMEOUT}.
     *
     * @return this
     */
    public ScanOptions setTimeout(final long timeout) {
        intent.putExtra(ToolOptionKey.TIMEOUT_MS, timeout);
        return this;
    }

    /**
     * Enable a (soft) timer to finish/cancel the scan on what the device considers
     * inactivity after the given timeout.
     * <p>
     * The returned {@code resultCode} will be Activity.RESULT_CANCELED.
     * The returned {@code intent} will contain the
     * key {@link ScanResult.Failure#REASON} with
     * value {@link ScanResult.Failure#REASON_INACTIVITY}.
     *
     * @return this
     */
    public ScanOptions setInactivityTimeout(final long timeout) {
        intent.putExtra(ToolOptionKey.INACTIVITY_TIMEOUT_MS, timeout);
        return this;
    }

    /**
     * Tell the scanner what type of barcodes to expect.
     *
     * @param decoderType one of the {@link DecoderType} constants.
     *
     * @return this
     */
    public ScanOptions setDecoderType(@NonNull final DecoderType decoderType) {
        intent.putExtra(OptionKey.DECODER_TYPE, decoderType.type);
        return this;
    }

    /**
     * Set the desired barcode formats to scan.
     *
     * @param barcodeFamily names of the {@link BarcodeFamily}s to scan for
     *
     * @return this
     *
     * @see BarcodeFamily
     */
    public ScanOptions setBarcodeFamily(@NonNull final BarcodeFamily barcodeFamily) {
        intent.putExtra(OptionKey.CODE_FAMILY, barcodeFamily.getName());
        return this;
    }

    /**
     * Set the desired barcode formats to scan.
     * Overrides {@link #setBarcodeFamily(BarcodeFamily)}.
     *
     * @param barcodeFormats names of {@link BarcodeFormat}s to scan for
     *
     * @return this
     *
     * @see BarcodeFormat
     * @see #setBarcodeFormats(String...)
     */
    @NonNull
    public ScanOptions setBarcodeFormats(@NonNull final Collection<String> barcodeFormats) {
        intent.putExtra(DecodeHintType.POSSIBLE_FORMATS.name(),
                String.join(",", barcodeFormats));
        return this;
    }

    /**
     * Set the desired barcode formats to scan.
     * Overrides {@link #setBarcodeFamily(BarcodeFamily)}.
     *
     * @param barcodeFormats names of {@link BarcodeFormat}s to scan for
     *
     * @return this
     *
     * @see BarcodeFormat
     * @see #setBarcodeFormats(Collection)
     */
    public ScanOptions setBarcodeFormats(@NonNull final String... barcodeFormats) {
        intent.putExtra(DecodeHintType.POSSIBLE_FORMATS.name(),
                String.join(",", barcodeFormats));
        return this;
    }

    /**
     * Request extra/available meta data to be returned.
     * <p>
     * This should be a csv String list with {@link ResultMetadataType} key names.
     * Unknown (or misspelled) entries will be ignored.
     *
     * @param csvKeyList csv String list
     *
     * @return this
     */
    public ScanOptions setReturnMetadata(@NonNull final String csvKeyList) {
        if (!csvKeyList.isBlank()) {
            intent.putExtra(OptionKey.RETURN_META_DATA, csvKeyList);
        }
        return this;
    }
}
