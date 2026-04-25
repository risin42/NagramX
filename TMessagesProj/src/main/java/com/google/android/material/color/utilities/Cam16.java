package com.google.android.material.color.utilities;

/**
 * CAM16, a color appearance model. Colors are not just defined by their hex code, but rather, a hex
 * code and viewing conditions.
 *
 * <p>CAM16 instances also have coordinates in the CAM16-UCS space, called J*, a*, b*, or jstar,
 * astar, bstar in code. CAM16-UCS is included in the CAM16 specification, and should be used when
 * measuring distances between colors.
 *
 * <p>In traditional color spaces, a color can be identified solely by the observer's measurement of
 * the color. Color appearance models such as CAM16 also use information about the environment where
 * the color was observed, known as the viewing conditions.
 *
 * <p>For example, white under the traditional assumption of a midday sun white point is accurately
 * measured as a slightly chromatic blue by CAM16. (roughly, hue 203, chroma 3, lightness 100)
 */
public final class Cam16 {
  // Transforms XYZ color space coordinates to 'cone'/'RGB' responses in CAM16.
  static final double[][] XYZ_TO_CAM16RGB = {
    {0.401288, 0.650173, -0.051461},
    {-0.250268, 1.204414, 0.045854},
    {-0.002079, 0.048952, 0.953127}
  };

  // CAM16 color dimensions, see getters for documentation.
  private final double hue;
  private final double chroma;
  private final double j;
  private final double q;
  private final double m;
  private final double s;

  /** Hue in CAM16 */
  public double getHue() {
    return hue;
  }

  /** Chroma in CAM16 */
  public double getChroma() {
    return chroma;
  }

  /** Lightness in CAM16 */
  public double getJ() {
    return j;
  }

  /**
   * Brightness in CAM16.
   *
   * <p>Prefer lightness, brightness is an absolute quantity. For example, a sheet of white paper is
   * much brighter viewed in sunlight than in indoor light, but it is the lightest object under any
   * lighting.
   */
  public double getQ() {
    return q;
  }

  /**
   * Colorfulness in CAM16.
   *
   * <p>Prefer chroma, colorfulness is an absolute quantity. For example, a yellow toy car is much
   * more colorful outside than inside, but it has the same chroma in both environments.
   */
  public double getM() {
    return m;
  }

  /**
   * Saturation in CAM16.
   *
   * <p>Colorfulness in proportion to brightness. Prefer chroma, saturation measures colorfulness
   * relative to the color's own brightness, where chroma is colorfulness relative to white.
   */
  public double getS() {
    return s;
  }

  /**
   * All of the CAM16 dimensions can be calculated from 3 of the dimensions, in the following
   * combinations: - {j or q} and {c, m, or s} and hue - jstar, astar, bstar Prefer using a static
   * method that constructs from 3 of those dimensions. This constructor is intended for those
   * methods to use to return all possible dimensions.
   *
   * @param hue for example, red, orange, yellow, green, etc.
   * @param chroma informally, colorfulness / color intensity. like saturation in HSL, except
   *     perceptually accurate.
   * @param j lightness
   * @param q brightness; ratio of lightness to white point's lightness
   * @param m colorfulness
   * @param s saturation; ratio of chroma to white point's chroma
   */
  private Cam16(
      double hue,
      double chroma,
      double j,
      double q,
      double m,
      double s) {
    this.hue = hue;
    this.chroma = chroma;
    this.j = j;
    this.q = q;
    this.m = m;
    this.s = s;
  }

  /**
   * Create a CAM16 color from a color, assuming the color was viewed in default viewing conditions.
   *
   * @param argb ARGB representation of a color.
   */
  public static Cam16 fromInt(int argb) {
    return fromIntInViewingConditions(argb);
  }

  /**
   * Create a CAM16 color from a color in defined viewing conditions.
   *
   * @param argb ARGB representation of a color.
   */
  // The RGB => XYZ conversion matrix elements are derived scientific constants. While the values
  // may differ at runtime due to floating point imprecision, keeping the values the same, and
  // accurate, across implementations takes precedence.
  @SuppressWarnings("FloatingPointLiteralPrecision")
  static Cam16 fromIntInViewingConditions(int argb) {
    // Transform ARGB int to XYZ
    int red = (argb & 0x00ff0000) >> 16;
    int green = (argb & 0x0000ff00) >> 8;
    int blue = (argb & 0x000000ff);
    double redL = ColorUtils.linearized(red);
    double greenL = ColorUtils.linearized(green);
    double blueL = ColorUtils.linearized(blue);
    double x = 0.41233895 * redL + 0.35762064 * greenL + 0.18051042 * blueL;
    double y = 0.2126 * redL + 0.7152 * greenL + 0.0722 * blueL;
    double z = 0.01932141 * redL + 0.11916382 * greenL + 0.95034478 * blueL;

    return fromXyzInViewingConditions(x, y, z);
  }

  static Cam16 fromXyzInViewingConditions(
      double x, double y, double z) {
    // Transform XYZ to 'cone'/'rgb' responses
    double[][] matrix = XYZ_TO_CAM16RGB;
    double rT = (x * matrix[0][0]) + (y * matrix[0][1]) + (z * matrix[0][2]);
    double gT = (x * matrix[1][0]) + (y * matrix[1][1]) + (z * matrix[1][2]);
    double bT = (x * matrix[2][0]) + (y * matrix[2][1]) + (z * matrix[2][2]);

    // Discount illuminant
    double rD = ViewingConditions.DEFAULT.getRgbD()[0] * rT;
    double gD = ViewingConditions.DEFAULT.getRgbD()[1] * gT;
    double bD = ViewingConditions.DEFAULT.getRgbD()[2] * bT;

    // Chromatic adaptation
    double rAF = Math.pow(ViewingConditions.DEFAULT.getFl() * Math.abs(rD) / 100.0, 0.42);
    double gAF = Math.pow(ViewingConditions.DEFAULT.getFl() * Math.abs(gD) / 100.0, 0.42);
    double bAF = Math.pow(ViewingConditions.DEFAULT.getFl() * Math.abs(bD) / 100.0, 0.42);
    double rA = Math.signum(rD) * 400.0 * rAF / (rAF + 27.13);
    double gA = Math.signum(gD) * 400.0 * gAF / (gAF + 27.13);
    double bA = Math.signum(bD) * 400.0 * bAF / (bAF + 27.13);

    // redness-greenness
    double a = (11.0 * rA + -12.0 * gA + bA) / 11.0;
    // yellowness-blueness
    double b = (rA + gA - 2.0 * bA) / 9.0;

    // auxiliary components
    double u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0;
    double p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0;

    // hue
    double atan2 = Math.atan2(b, a);
    double atanDegrees = Math.toDegrees(atan2);
    double hue =
        atanDegrees < 0
            ? atanDegrees + 360.0
            : atanDegrees >= 360 ? atanDegrees - 360.0 : atanDegrees;
    // achromatic response to color
    double ac = p2 * ViewingConditions.DEFAULT.getNbb();

    // CAM16 lightness and brightness
    double j =
        100.0
            * Math.pow(
                ac / ViewingConditions.DEFAULT.getAw(),
                ViewingConditions.DEFAULT.getC() * ViewingConditions.DEFAULT.getZ());
    double q =
        4.0
            / ViewingConditions.DEFAULT.getC()
            * Math.sqrt(j / 100.0)
            * (ViewingConditions.DEFAULT.getAw() + 4.0)
            * ViewingConditions.DEFAULT.getFlRoot();

    // CAM16 chroma, colorfulness, and saturation.
    double huePrime = (hue < 20.14) ? hue + 360 : hue;
    double eHue = 0.25 * (Math.cos(Math.toRadians(huePrime) + 2.0) + 3.8);
    double p1 = 50000.0 / 13.0 * eHue * ViewingConditions.DEFAULT.getNc() * ViewingConditions.DEFAULT.getNcb();
    double t = p1 * Math.hypot(a, b) / (u + 0.305);
    double alpha =
        Math.pow(1.64 - Math.pow(0.29, ViewingConditions.DEFAULT.getN()), 0.73) * Math.pow(t, 0.9);
    // CAM16 chroma, colorfulness, saturation
    double c = alpha * Math.sqrt(j / 100.0);
    double m = c * ViewingConditions.DEFAULT.getFlRoot();
    double s =
        50.0 * Math.sqrt((alpha * ViewingConditions.DEFAULT.getC()) / (ViewingConditions.DEFAULT.getAw() + 4.0));

    // CAM16-UCS components

      return new Cam16(hue, c, j, q, m, s);
  }

}
