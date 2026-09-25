package com.somil.jobportal.ai.store;

/** TiDB's text form of a vector: {@code [0.1,-0.2,0.3]}. */
public final class VectorText {
    private VectorText() { }

    public static String format(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) throw new IllegalArgumentException("TiDB vectors cannot hold NaN or Infinity");
            if (i > 0) text.append(',');
            // Plain decimals only: Float.toString can produce exponents such as 1.0E-5.
            text.append(new java.math.BigDecimal(Float.toString(vector[i])).stripTrailingZeros().toPlainString());
        }
        return text.append(']').toString();
    }
}
