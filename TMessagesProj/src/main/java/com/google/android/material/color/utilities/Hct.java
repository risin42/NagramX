package com.google.android.material.color.utilities;

/**
 * A color system built using CAM16 hue and chroma, and L* from L*a*b*.
 *
 * <p>Using L* creates a link between the color system, contrast, and thus accessibility. Contrast
 * ratio depends on relative luminance, or Y in the XYZ color space. L*, or perceptual luminance can
 * be calculated from Y.
 *
 * <p>Unlike Y, L* is linear to human perception, allowing trivial creation of accurate color tones.
 *
 * <p>Unlike contrast ratio, measuring contrast in L* is linear, and simple to calculate. A
 * difference of 40 in HCT tone guarantees a contrast ratio >= 3.0, and a difference of 50
 * guarantees a contrast ratio >= 4.5.
 */

public final class Hct {
  private double hue;
  private double chroma;
  private double tone;
  private int argb;

  /**
   * Create an HCT color from hue, chroma, and tone.
   *
   * @param hue 0 <= hue < 360; invalid values are corrected.
   * @param chroma 0 <= chroma < ?; Informally, colorfulness. The color returned may be lower than
   *     the requested chroma. Chroma has a different maximum for any given hue and tone.
   * @param tone 0 <= tone <= 100; invalid values are corrected.
   * @return HCT representation of a color in default viewing conditions.
   */
  public static Hct from(double hue, double chroma, double tone) {
    int argb = HctSolver.solveToInt(hue, chroma, tone);
    return new Hct(argb);
  }

  /**
   * Create an HCT color from a color.
   *
   * @param argb ARGB representation of a color.
   * @return HCT representation of a color in default viewing conditions
   */
  public static Hct fromInt(int argb) {
    return new Hct(argb);
  }

  private Hct(int argb) {
    setInternalState(argb);
  }

  public double getHue() {
    return hue;
  }

  public double getChroma() {
    return chroma;
  }

  public double getTone() {
    return tone;
  }

  public int toInt() {
    return argb;
  }

  private void setInternalState(int argb) {
    this.argb = argb;
    Cam16 cam = Cam16.fromInt(argb);
    hue = cam.getHue();
    chroma = cam.getChroma();
    this.tone = ColorUtils.lstarFromArgb(argb);
  }
}
