package com.google.android.material.color.utilities;

public class ColorUtils {
  private ColorUtils() {}

  static final double[][] SRGB_TO_XYZ =
      new double[][] {
        new double[] {0.41233895, 0.35762064, 0.18051042},
        new double[] {0.2126, 0.7152, 0.0722},
        new double[] {0.01932141, 0.11916382, 0.95034478},
      };

  static final double[] WHITE_POINT_D65 = new double[] {95.047, 100.0, 108.883};

  /** Converts a color from RGB components to ARGB format. */
  public static int argbFromRgb(int red, int green, int blue) {
    return (255 << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
  }

  /** Converts a color from linear RGB components to ARGB format. */
  public static int argbFromLinrgb(double[] linrgb) {
    int r = delinearized(linrgb[0]);
    int g = delinearized(linrgb[1]);
    int b = delinearized(linrgb[2]);
    return argbFromRgb(r, g, b);
  }

  /** Returns the red component of a color in ARGB format. */
  public static int redFromArgb(int argb) {
    return (argb >> 16) & 255;
  }

  /** Returns the green component of a color in ARGB format. */
  public static int greenFromArgb(int argb) {
    return (argb >> 8) & 255;
  }

  /** Returns the blue component of a color in ARGB format. */
  public static int blueFromArgb(int argb) {
    return argb & 255;
  }

  /** Converts a color from XYZ to ARGB. */
  public static double[] xyzFromArgb(int argb) {
    double r = linearized(redFromArgb(argb));
    double g = linearized(greenFromArgb(argb));
    double b = linearized(blueFromArgb(argb));
    return MathUtils.matrixMultiply(new double[] {r, g, b}, SRGB_TO_XYZ);
  }

  /**
   * Converts an L* value to an ARGB representation.
   *
   * @param lstar L* in L*a*b*
   * @return ARGB representation of grayscale color with lightness matching L*
   */
  public static int argbFromLstar(double lstar) {
    double y = yFromLstar(lstar);
    int component = delinearized(y);
    return argbFromRgb(component, component, component);
  }

  /**
   * Computes the L* value of a color in ARGB representation.
   *
   * @param argb ARGB representation of a color
   * @return L*, from L*a*b*, coordinate of the color
   */
  public static double lstarFromArgb(int argb) {
    double y = xyzFromArgb(argb)[1];
    return 116.0 * labF(y / 100.0) - 16.0;
  }

  /**
   * Converts an L* value to a Y value.
   *
   * <p>L* in L*a*b* and Y in XYZ measure the same quantity, luminance.
   *
   * <p>L* measures perceptual luminance, a linear scale. Y in XYZ measures relative luminance, a
   * logarithmic scale.
   *
   * @param lstar L* in L*a*b*
   * @return Y in XYZ
   */
  public static double yFromLstar(double lstar) {
    return 100.0 * labInvf((lstar + 16.0) / 116.0);
  }

  /**
   * Linearizes an RGB component.
   *
   * @param rgbComponent 0 <= rgb_component <= 255, represents R/G/B channel
   * @return 0.0 <= output <= 100.0, color channel converted to linear RGB space
   */
  public static double linearized(int rgbComponent) {
    double normalized = rgbComponent / 255.0;
    if (normalized <= 0.040449936) {
      return normalized / 12.92 * 100.0;
    } else {
      return Math.pow((normalized + 0.055) / 1.055, 2.4) * 100.0;
    }
  }

  /**
   * Delinearizes an RGB component.
   *
   * @param rgbComponent 0.0 <= rgb_component <= 100.0, represents linear R/G/B channel
   * @return 0 <= output <= 255, color channel converted to regular RGB space
   */
  public static int delinearized(double rgbComponent) {
    double normalized = rgbComponent / 100.0;
    double delinearized = 0.0;
    if (normalized <= 0.0031308) {
      delinearized = normalized * 12.92;
    } else {
      delinearized = 1.055 * Math.pow(normalized, 1.0 / 2.4) - 0.055;
    }
    return MathUtils.clampInt(0, 255, (int) Math.round(delinearized * 255.0));
  }

  /**
   * Returns the standard white point; white on a sunny day.
   *
   * @return The white point
   */
  public static double[] whitePointD65() {
    return WHITE_POINT_D65;
  }

  static double labF(double t) {
    double e = 216.0 / 24389.0;
    double kappa = 24389.0 / 27.0;
    if (t > e) {
      return Math.pow(t, 1.0 / 3.0);
    } else {
      return (kappa * t + 16) / 116;
    }
  }

  static double labInvf(double ft) {
    double e = 216.0 / 24389.0;
    double kappa = 24389.0 / 27.0;
    double ft3 = ft * ft * ft;
    if (ft3 > e) {
      return ft3;
    } else {
      return (116 * ft - 16) / kappa;
    }
  }
}
