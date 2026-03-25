package org.pdfium4j.model;

/**
 * Information about a single character on a PDF page, including its
 * position, bounding box, and font size.
 *
 * All coordinates are in PDF page units (points, 1 pt = 1/72 inch).
 * Origin is bottom-left of the page.
 *
 * @param charCode the Unicode code point
 * @param left left edge of the character bounding box
 * @param bottom bottom edge of the character bounding box
 * @param right right edge of the character bounding box
 * @param top top edge of the character bounding box
 * @param fontSize the font size in points
 */
public record TextCharInfo(
        int charCode,
        double left,
        double bottom,
        double right,
        double top,
        double fontSize
) {
    public TextCharInfo {
        left = sanitize(left);
        bottom = sanitize(bottom);
        right = sanitize(right);
        top = sanitize(top);
        fontSize = sanitize(fontSize);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
    /** Width of the character bounding box in points. */
    public double width() {
        return right - left;
    }

    /** Height of the character bounding box in points. */
    public double height() {
        return top - bottom;
    }

    /** The character as a Java String. */
    public String character() {
        if (!Character.isValidCodePoint(charCode)) {
            return "\uFFFD";
        }
        return new String(Character.toChars(charCode));
    }
}
